'use strict';

const fs = require('fs');
const path = require('path');
const pino = require('pino');
const NodeCache = require('node-cache');
const {
    default: makeWASocket,
    useMultiFileAuthState,
    DisconnectReason,
    fetchLatestBaileysVersion,
    makeCacheableSignalKeyStore,
    Browsers,
    S_WHATSAPP_NET,
    delay
} = require('@whiskeysockets/baileys');

let kangoSendButtons = null;
try {
    kangoSendButtons = require('kango-wa').sendButtons;
} catch (_) {
    kangoSendButtons = null;
}

let JimpLib = null;
try {
    JimpLib = require('jimp');
} catch (_) {
    JimpLib = null;
}

const config = require('./config');
const store = require('./store');
const { resolveMediaPayload } = require('./mediaDownloader');

const logger = pino({ level: 'silent' });
const msgRetryCounterCache = new NodeCache({ stdTTL: 600, checkperiod: 120 });

// In-memory recent raw Baileys messages for Anti-Delete recovery & getMessage retries
const rawMessageCache = new Map();
const MAX_RAW_CACHE = 2500;

function cacheRawMessage(jid, id, msgObj) {
    if (!jid || !id || !msgObj) return;
    rawMessageCache.set(`${jid}:${id}`, msgObj);
    if (rawMessageCache.size > MAX_RAW_CACHE) {
        const firstKey = rawMessageCache.keys().next().value;
        rawMessageCache.delete(firstKey);
    }
}

function getRawMessage(jid, id) {
    return rawMessageCache.get(`${jid}:${id}`) || null;
}

function cleanPhoneNumber(input) {
    let digits = String(input || '').replace(/\D/g, '');
    if (digits.startsWith('00')) digits = digits.slice(2);
    if (digits.startsWith('0') && digits.length === 11) {
        digits = '92' + digits.slice(1);
    }
    return digits;
}

function toWhatsAppJid(input) {
    const raw = String(input || '').trim();
    if (!raw) return '';
    if (raw.endsWith('@s.whatsapp.net') || raw.endsWith('@g.us') || raw.endsWith('@newsletter') || raw === 'status@broadcast') {
        return raw;
    }
    const digits = cleanPhoneNumber(raw);
    return digits.length >= 7 ? `${digits}@s.whatsapp.net` : '';
}

function extractChannelInviteCode(urlOrJid) {
    const str = String(urlOrJid || '').trim();
    const match = str.match(/whatsapp\.com\/channel\/([A-Za-z0-9_-]+)/i);
    return match ? match[1] : null;
}

function unwrapMessageContent(message) {
    if (!message || typeof message !== 'object') return { inner: null, isViewOnce: false };
    let isViewOnce = false;
    let m = message;
    if (m.ephemeralMessage?.message) m = m.ephemeralMessage.message;
    if (m.viewOnceMessage?.message) {
        isViewOnce = true;
        m = m.viewOnceMessage.message;
    }
    if (m.viewOnceMessageV2?.message) {
        isViewOnce = true;
        m = m.viewOnceMessageV2.message;
    }
    if (m.viewOnceMessageV2Extension?.message) {
        isViewOnce = true;
        m = m.viewOnceMessageV2Extension.message;
    }
    if (m.documentWithCaptionMessage?.message) {
        m = m.documentWithCaptionMessage.message;
    }
    return { inner: m, isViewOnce };
}

function extractButtonClickId(message) {
    const { inner } = unwrapMessageContent(message);
    if (!inner) return '';
    if (inner.interactiveResponseMessage?.nativeFlowResponseMessage?.paramsJson) {
        try {
            const p = JSON.parse(inner.interactiveResponseMessage.nativeFlowResponseMessage.paramsJson);
            return String(p.id || p.display_text || '').trim();
        } catch (_) {}
    }
    return String(
        inner.buttonsResponseMessage?.selectedButtonId ||
        inner.templateButtonReplyMessage?.selectedId ||
        inner.listResponseMessage?.singleSelectReply?.selectedRowId ||
        ''
    ).trim();
}

function extractReadableMessage(message) {
    const { inner, isViewOnce } = unwrapMessageContent(message);
    if (!inner) return { text: '', mediaType: 'text', isViewOnce };

    if (inner.conversation) {
        return { text: inner.conversation, mediaType: 'text', isViewOnce };
    }
    if (inner.extendedTextMessage?.text) {
        return { text: inner.extendedTextMessage.text, mediaType: 'text', isViewOnce };
    }
    if (inner.imageMessage) {
        return { text: inner.imageMessage.caption || 'Photo', mediaType: 'image', isViewOnce };
    }
    if (inner.videoMessage) {
        const isPtv = Boolean(inner.videoMessage.ptv);
        return {
            text: inner.videoMessage.caption || (isPtv ? 'Video Note' : 'Video'),
            mediaType: isPtv ? 'ptv' : 'video',
            isViewOnce
        };
    }
    if (inner.ptvMessage) {
        return { text: 'Video Note', mediaType: 'ptv', isViewOnce };
    }
    if (inner.audioMessage) {
        const isPtt = Boolean(inner.audioMessage.ptt);
        return {
            text: isPtt ? 'Voice Note' : 'Audio',
            mediaType: isPtt ? 'voice' : 'audio',
            isViewOnce
        };
    }
    if (inner.stickerMessage) {
        return { text: 'Sticker', mediaType: 'sticker', isViewOnce };
    }
    if (inner.documentMessage) {
        return { text: inner.documentMessage.fileName || 'Document', mediaType: 'document', isViewOnce };
    }
    if (inner.liveLocationMessage || inner.locationMessage) {
        return { text: 'Shared Location', mediaType: 'location', isViewOnce };
    }
    if (inner.buttonsResponseMessage) {
        return { text: inner.buttonsResponseMessage.selectedDisplayText || 'Button Reply', mediaType: 'text', isViewOnce };
    }
    return { text: '', mediaType: 'text', isViewOnce };
}

class BaileysManager {
    constructor() {
        this.sessions = new Map();
        this.startedAt = Date.now();
        this.channelRuleIndex = new Map(); // newsletterJid -> rule for O(1) instant reactions
        this.rebuildChannelRuleIndex();
    }

    rebuildChannelRuleIndex() {
        this.channelRuleIndex.clear();
        for (const rule of store.state.channelRules || []) {
            if (rule && rule.active !== false && rule.jid) {
                this.channelRuleIndex.set(rule.jid, rule);
            }
        }
    }

    async resolveAndFollowChannel(sock, channelUrlOrJid, customName) {
        const trimmed = String(channelUrlOrJid || '').trim();
        let resolvedJid = trimmed;
        let resolvedName = String(customName || '').trim();

        const inviteCode = extractChannelInviteCode(trimmed);
        if (inviteCode && sock && typeof sock.newsletterMetadata === 'function') {
            try {
                const meta = await sock.newsletterMetadata('invite', inviteCode);
                if (meta?.id) {
                    resolvedJid = meta.id;
                    if (!resolvedName && meta.name) resolvedName = meta.name;
                }
            } catch (err) {
                console.warn('[channel] newsletterMetadata invite lookup failed:', err.message);
            }
        }

        if (resolvedJid.endsWith('@newsletter') && sock && typeof sock.newsletterFollow === 'function') {
            sock.newsletterFollow(resolvedJid).catch(() => {});
        }

        return { resolvedJid, resolvedName: resolvedName || resolvedJid };
    }

    async ensureAllChannelRulesResolved(sock) {
        if (!sock) return;
        let changed = false;
        for (const rule of store.state.channelRules || []) {
            if (rule.active === false) continue;
            if (!String(rule.jid || '').endsWith('@newsletter')) {
                const { resolvedJid, resolvedName } = await this.resolveAndFollowChannel(sock, rule.channelUrl || rule.jid, rule.name);
                if (resolvedJid.endsWith('@newsletter')) {
                    rule.jid = resolvedJid;
                    if (resolvedName) rule.name = resolvedName;
                    changed = true;
                }
            } else if (typeof sock.newsletterFollow === 'function') {
                sock.newsletterFollow(rule.jid).catch(() => {});
            }
        }
        if (changed) store.saveLocalNow();
        this.rebuildChannelRuleIndex();
    }

    getConnectedSessions() {
        const connected = [];
        for (const [num, entry] of this.sessions.entries()) {
            if (entry?.connected) connected.push(num);
        }
        return connected;
    }

    getActiveEntry() {
        for (const [num, entry] of this.sessions.entries()) {
            if (entry?.connected && entry?.sock) {
                return { cleanNumber: num, ...entry };
            }
        }
        return null;
    }

    getActiveSocket() {
        return this.getActiveEntry()?.sock || null;
    }

    getUptimeFormatted() {
        const totalSec = Math.floor((Date.now() - this.startedAt) / 1000);
        const h = Math.floor(totalSec / 3600);
        const m = Math.floor((totalSec % 3600) / 60);
        const s = totalSec % 60;
        if (h > 0) return `${h}h ${m}m`;
        if (m > 0) return `${m}m ${s}s`;
        return `${s}s`;
    }

    async autoResumeSavedSessions() {
        await store.restoreAllSessionsFromPostgres();
        if (!fs.existsSync(config.SESSIONS_DIR)) return;
        const dirs = fs.readdirSync(config.SESSIONS_DIR, { withFileTypes: true })
            .filter(d => d.isDirectory())
            .map(d => d.name);

        for (const num of dirs) {
            const sessionDir = path.join(config.SESSIONS_DIR, num);
            const credsFile = path.join(sessionDir, 'creds.json');
            if (!fs.existsSync(credsFile)) continue;
            try {
                const creds = JSON.parse(fs.readFileSync(credsFile, 'utf8'));
                if (creds?.registered) {
                    console.log(`[baileys] Auto-resuming saved WhatsApp session +${num}`);
                    this.startSession(num, sessionDir).catch(err => {
                        console.error(`[baileys] Resume error for +${num}:`, err.message);
                    });
                }
            } catch (_) {}
        }
    }

    _createSocket(version, state, cleanNumber) {
        return makeWASocket({
            version,
            logger,
            printQRInTerminal: false,
            browser: Browsers.ubuntu('Chrome'),
            auth: {
                creds: state.creds,
                keys: makeCacheableSignalKeyStore(state.keys, logger)
            },
            msgRetryCounterCache,
            generateHighQualityLinkPreview: false,
            // STEALTH MODE: NEVER broadcast "online" presence when connected!
            markOnlineOnConnect: false,
            // Do not auto-flood old historical chats; new chats & messages save locally on device
            syncFullHistory: false,
            connectTimeoutMs: 45000,
            defaultQueryTimeoutMs: 45000,
            keepAliveIntervalMs: 25000,
            getMessage: async (key) => {
                const cached = getRawMessage(key?.remoteJid, key?.id);
                return cached?.message || undefined;
            }
        });
    }

    _keepStealthUnavailable(sock) {
        const freeze = store.state.ghostPrivacy?.freezeOnlinePresence !== false;
        if (freeze && sock && typeof sock.sendPresenceUpdate === 'function') {
            sock.sendPresenceUpdate('unavailable').catch(() => {});
        }
    }

    _attachEventHandlers(sock, cleanNumber, sessionDir, saveCreds) {
        sock.ev.on('creds.update', async () => {
            try {
                await saveCreds();
                store.backupSessionDirToPostgres(cleanNumber, sessionDir).catch(() => {});
            } catch (_) {}
        });

        // Older chat & message history sync from WhatsApp linked device
        sock.ev.on('messaging-history.set', ({ chats = [], contacts = [], messages = [] }) => {
            try {
                for (const c of contacts) {
                    if (c?.id) store.upsertContact(c.id, c.name || c.notify || c.verifiedName);
                }
                for (const ch of chats) {
                    if (!ch?.id || ch.id === 'status@broadcast' || ch.id.endsWith('@newsletter')) continue;
                    const ts = ch.conversationTimestamp
                        ? Number(ch.conversationTimestamp) * 1000
                        : 0;
                    store.upsertChatSummary(ch.id, {
                        name: ch.name || ch.subject || '',
                        lastTimestamp: ts,
                        unreadCount: Number(ch.unreadCount) || 0
                    });
                }
                for (const msg of messages) {
                    const jid = msg?.key?.remoteJid;
                    if (!jid || jid === 'status@broadcast' || jid.endsWith('@newsletter') || !msg.message) continue;
                    const extracted = extractReadableMessage(msg.message);
                    if (!extracted.text && extracted.mediaType === 'text') continue;
                    const ts = msg.messageTimestamp ? Number(msg.messageTimestamp) * 1000 : Date.now();
                    const statusNum = typeof msg.status === 'number' ? msg.status : (msg.key.fromMe ? 2 : 4);
                    store.recordMessage(
                        jid,
                        {
                            id: msg.key.id,
                            fromMe: Boolean(msg.key.fromMe),
                            senderJid: msg.key.participant || jid,
                            senderName: msg.pushName || '',
                            text: extracted.text,
                            mediaType: extracted.mediaType,
                            timestamp: ts,
                            status: statusNum
                        },
                        { isHistory: true }
                    );
                }
                console.log(`[baileys] History sync ingested: ${chats.length} chats, ${contacts.length} contacts, ${messages.length} older messages.`);
            } catch (err) {
                console.warn('[baileys] messaging-history.set error:', err.message);
            }
        });

        sock.ev.on('contacts.upsert', (contacts) => {
            if (!Array.isArray(contacts)) return;
            for (const c of contacts) {
                store.upsertContact(c.id, c.name || c.notify || c.verifiedName);
            }
        });

        sock.ev.on('contacts.update', (updates) => {
            if (!Array.isArray(updates)) return;
            for (const c of updates) {
                if (c.id) store.upsertContact(c.id, c.name || c.notify || c.verifiedName);
            }
        });

        sock.ev.on('chats.upsert', (chats) => {
            if (!Array.isArray(chats)) return;
            for (const ch of chats) {
                if (!ch?.id || ch.id === 'status@broadcast') continue;
                store.upsertContact(ch.id, ch.name || ch.subject);
                store.upsertChatSummary(ch.id, {
                    name: ch.name || ch.subject || '',
                    lastTimestamp: ch.conversationTimestamp ? Number(ch.conversationTimestamp) * 1000 : 0,
                    unreadCount: Number(ch.unreadCount) || 0
                });
            }
        });

        // Track receiver's WhatsApp Last Seen timestamp (NEVER display online!)
        sock.ev.on('presence.update', ({ id, presences }) => {
            if (!id || !presences) return;
            for (const [jid, data] of Object.entries(presences)) {
                const target = jid || id;
                if (data?.lastSeen) {
                    store.recordLastSeen(target, Number(data.lastSeen) * 1000);
                } else if (data?.lastKnownPresence === 'available' || data?.lastKnownPresence === 'composing') {
                    // Record timestamp as last seen time, never expose "online"
                    store.recordLastSeen(target, Date.now());
                }
            }
        });

        // Track REAL WhatsApp message ack transitions: 2 = single tick (offline), 3 = double tick (delivered), 4 = blue tick (read)
        sock.ev.on('messages.update', (updates) => {
            if (!Array.isArray(updates)) return;
            for (const item of updates) {
                const jid = item?.key?.remoteJid;
                const msgId = item?.key?.id;
                const status = item?.update?.status;
                if (jid && msgId && typeof status === 'number') {
                    store.updateMessageStatus(jid, msgId, status);
                }
            }
        });

        sock.ev.on('message-receipt.update', (receipts) => {
            if (!Array.isArray(receipts)) return;
            for (const item of receipts) {
                const jid = item?.key?.remoteJid;
                const msgId = item?.key?.id;
                const rec = item?.receipt;
                if (!jid || !msgId || !rec) continue;
                if (rec.readTimestamp || rec.playedTimestamp) {
                    store.updateMessageStatus(jid, msgId, 4);
                } else if (rec.receiptTimestamp) {
                    store.updateMessageStatus(jid, msgId, 3);
                }
            }
        });

        sock.ev.on('call', (callEvents) => {
            if (!Array.isArray(callEvents)) return;
            for (const call of callEvents) {
                const callerJid = call.from || '';
                const callerNumber = cleanPhoneNumber(callerJid.split('@')[0]);
                store.addCallRecord({
                    id: call.id || `call_${Date.now()}`,
                    callerJid,
                    callerName: store.state.contacts[callerJid]?.name || `+${callerNumber}`,
                    callerNumber,
                    isVideo: Boolean(call.isVideo),
                    status: call.status || 'missed',
                    timestamp: Date.now()
                });
            }
        });

        // FAST-PATH message handler: Channel Auto-React fires INSTANTLY before anything else!
        sock.ev.on('messages.upsert', async ({ messages }) => {
            if (!Array.isArray(messages)) return;
            for (const msg of messages) {
                const remoteJid = msg?.key?.remoteJid || '';
                if (remoteJid.endsWith('@newsletter')) {
                    this._reactToChannelPostInstantly(sock, msg);
                    continue;
                }
                this._handleIncomingMessage(sock, cleanNumber, msg).catch(err => {
                    console.warn('[baileys] Message processing error:', err.message);
                });
            }
        });

        sock.ev.on('connection.update', async (update) => {
            const { connection, lastDisconnect } = update;
            const entry = this.sessions.get(cleanNumber);
            if (!entry || entry.sock !== sock) return;

            if (connection === 'open') {
                entry.connected = true;
                entry.pending = false;
                entry.reconnecting = false;
                console.log(`[baileys] WhatsApp session +${cleanNumber} connected in stealth mode (offline presence).`);
                this._keepStealthUnavailable(sock);
                if (entry.stealthTimer) clearInterval(entry.stealthTimer);
                entry.stealthTimer = setInterval(() => {
                    if (entry.connected && entry.sock === sock) {
                        this._keepStealthUnavailable(sock);
                    }
                }, 45000);
                this.ensureAllChannelRulesResolved(sock).catch(() => {});
                try {
                    await saveCreds();
                    await store.backupSessionDirToPostgres(cleanNumber, sessionDir);
                } catch (_) {}
            } else if (connection === 'close') {
                entry.connected = false;
                if (entry.stealthTimer) {
                    clearInterval(entry.stealthTimer);
                    entry.stealthTimer = null;
                }
                const statusCode = lastDisconnect?.error?.output?.statusCode;
                const loggedOut = statusCode === DisconnectReason.loggedOut;
                console.log(`[baileys] Session +${cleanNumber} closed (code=${statusCode || 'unknown'})`);

                if (loggedOut) {
                    this.sessions.delete(cleanNumber);
                    try {
                        fs.rmSync(sessionDir, { recursive: true, force: true });
                    } catch (_) {}
                    return;
                }

                if (entry.pending && statusCode !== DisconnectReason.restartRequired && statusCode !== 515) {
                    return;
                }

                if (!entry.reconnecting) {
                    entry.reconnecting = true;
                    const delayMs = statusCode === 515 ? 800 : 3500;
                    setTimeout(() => {
                        this.startSession(cleanNumber, sessionDir).catch(() => {});
                    }, delayMs);
                }
            }
        });
    }

    /**
     * Zero-latency O(1) instant emoji reactor for new WhatsApp Channel (@newsletter) posts.
     */
    _reactToChannelPostInstantly(sock, msg) {
        const remoteJid = msg?.key?.remoteJid;
        if (!remoteJid) return;
        const rule = this.channelRuleIndex.get(remoteJid);
        if (!rule || rule.active === false || !Array.isArray(rule.emojis) || rule.emojis.length === 0) return;

        const serverId = String(
            msg.key?.server_id ||
            msg.message?.newsletterAdminInviteMessage?.server_id ||
            msg.key?.id ||
            ''
        ).trim();
        if (!serverId) return;

        const emoji = rule.emojis[Math.floor(Math.random() * rule.emojis.length)];
        if (typeof sock.newsletterReactMessage === 'function') {
            // Fire immediately without any delay
            sock.newsletterReactMessage(remoteJid, serverId, emoji).catch(err => {
                console.warn(`[channel] Instant react failed on ${remoteJid}#${serverId}:`, err.message);
            });
        }
    }

    async _handleIncomingMessage(sock, cleanNumber, msg) {
        if (!msg?.key || !msg.message) return;
        const remoteJid = msg.key.remoteJid || '';
        const msgId = msg.key.id || '';
        const fromMe = Boolean(msg.key.fromMe);
        const participant = msg.key.participant || remoteJid;
        const senderNumber = cleanPhoneNumber(participant.split('@')[0]);
        const senderName = msg.pushName || store.state.contacts[participant]?.name || `+${senderNumber}`;
        const timestamp = msg.messageTimestamp ? Number(msg.messageTimestamp) * 1000 : Date.now();

        // 1. Handle WhatsApp Status Updates (status@broadcast)
        if (remoteJid === 'status@broadcast') {
            if (fromMe) return;
            const extracted = extractReadableMessage(msg.message);
            store.addCapturedStatus({
                id: msgId || `st_${timestamp}`,
                senderName,
                senderNumber,
                senderJid: participant,
                mediaType: extracted.mediaType,
                caption: extracted.text || `[${extracted.mediaType}]`,
                timestamp
            });

            const autoCfg = store.state.autoReactConfig;
            const ghostStatus = store.state.ghostPrivacy?.ghostStatusView === true;
            // When Ghost Status View is ON: capture contact's status silently WITHOUT sending read receipt!
            if (autoCfg.enabled && !ghostStatus) {
                if (autoCfg.autoViewStatus && typeof sock.readMessages === 'function') {
                    await sock.readMessages([msg.key]).catch(() => {});
                }
                if (autoCfg.autoReactStatus && autoCfg.emoji) {
                    await sock.sendMessage(
                        'status@broadcast',
                        { react: { key: msg.key, text: autoCfg.emoji } },
                        { statusJidList: [participant] }
                    ).catch(() => {});
                }
                if (autoCfg.autoReplyStatus && autoCfg.replyMessage && participant) {
                    await sock.sendMessage(participant, { text: autoCfg.replyMessage }).catch(() => {});
                }
                this._keepStealthUnavailable(sock);
            }
            return;
        }

        // 2. Anti-Delete Protocol Message Detection
        const proto = msg.message.protocolMessage;
        if (proto && proto.type === 0 && proto.key?.id) {
            if (store.state.vaultSettings.antiDeleteEnabled) {
                const original = getRawMessage(remoteJid, proto.key.id);
                if (original) {
                    const extracted = extractReadableMessage(original.message);
                    const origSenderJid = original.key?.participant || original.key?.remoteJid || remoteJid;
                    const origNum = cleanPhoneNumber(origSenderJid.split('@')[0]);
                    const origName = original.pushName || store.state.contacts[origSenderJid]?.name || `+${origNum}`;
                    store.addVaultItem({
                        id: `del_${proto.key.id}`,
                        type: 'deleted',
                        senderName: origName,
                        senderNumber: origNum,
                        chatJid: remoteJid,
                        text: extracted.text || `[Deleted ${extracted.mediaType}]`,
                        mediaType: extracted.mediaType,
                        timestamp: Date.now()
                    });

                    if (store.state.vaultSettings.autoForwardToOwner) {
                        const ownerJid = toWhatsAppJid(config.ownerNumber || cleanNumber);
                        if (ownerJid) {
                            await sock.sendMessage(ownerJid, {
                                text: `Recovered Deleted Message from ${origName} (+${origNum}):\n${extracted.text || `[${extracted.mediaType}]`}`
                            }).catch(() => {});
                            this._keepStealthUnavailable(sock);
                        }
                    }
                }
            }
            return;
        }

        cacheRawMessage(remoteJid, msgId, msg);

        if (!fromMe && msg.pushName) {
            store.upsertContact(participant, msg.pushName);
        }

        // 3. Handle Interactive Song/Video Button Taps (.play <id> / .video <id>)
        const clickedBtn = extractButtonClickId(msg.message);
        if (clickedBtn) {
            const btnPlay = clickedBtn.match(/^\.play\s+(.+)$/i);
            const btnVideo = clickedBtn.match(/^\.video\s+(.+)$/i);
            if (btnPlay || btnVideo) {
                const mode = btnVideo ? 'video' : 'song';
                const q = (btnVideo ? btnVideo[1] : btnPlay[1]).trim();
                try {
                    const resolved = await resolveMediaPayload({ platform: mode, query: q, caption: '' });
                    await this.sendMediaWithButtons(sock, remoteJid, resolved, true);
                } catch (err) {
                    console.warn('[baileys] Button media reply error:', err.message);
                }
                return;
            }
        }

        const extracted = extractReadableMessage(msg.message);

        // 4. View-Once Vault Capture
        if (extracted.isViewOnce && !fromMe && store.state.vaultSettings.viewOnceEnabled) {
            store.addVaultItem({
                id: `vo_${msgId || timestamp}`,
                type: 'view_once',
                senderName,
                senderNumber,
                chatJid: remoteJid,
                text: extracted.text || `[View-Once ${extracted.mediaType}]`,
                mediaType: extracted.mediaType,
                timestamp
            });
        }

        // 5. Record normal chat message into store
        if (extracted.text || extracted.mediaType !== 'text') {
            const statusNum = typeof msg.status === 'number' ? msg.status : (fromMe ? 2 : 4);
            store.recordMessage(remoteJid, {
                id: msgId,
                fromMe,
                senderJid: participant,
                senderName,
                text: extracted.text,
                mediaType: extracted.mediaType,
                timestamp,
                status: statusNum
            });
        }
    }

    async subscribeLastSeen(jid) {
        const sock = this.getActiveSocket();
        if (sock && jid && !jid.endsWith('@g.us') && typeof sock.presenceSubscribe === 'function') {
            try {
                await sock.presenceSubscribe(jid);
                this._keepStealthUnavailable(sock);
            } catch (_) {}
        }
        return store.getLastSeen(jid);
    }

    async fetchOlderMessages(jid, oldestMsgId, oldestTimestamp, fromMe = false) {
        const sock = this.getActiveSocket();
        if (!sock) {
            return { fetched: false, message: 'WhatsApp is not linked yet.' };
        }
        if (jid && oldestMsgId && typeof sock.fetchMessageHistory === 'function') {
            try {
                await sock.fetchMessageHistory(
                    50,
                    { remoteJid: jid, id: oldestMsgId, fromMe: Boolean(fromMe) },
                    Number(oldestTimestamp) || Date.now()
                );
                this._keepStealthUnavailable(sock);
                await delay(1200);
                return { fetched: true, message: 'Requested older WhatsApp messages from linked device.' };
            } catch (err) {
                console.warn('[baileys] fetchMessageHistory error:', err.message);
            }
        }
        // Also refresh participating groups into chats list
        if (typeof sock.groupFetchAllParticipating === 'function') {
            try {
                const groups = await sock.groupFetchAllParticipating();
                for (const g of Object.values(groups || {})) {
                    store.upsertChatSummary(g.id, {
                        name: g.subject || g.id,
                        lastTimestamp: g.creation ? Number(g.creation) * 1000 : 0
                    });
                }
            } catch (_) {}
        }
        return { fetched: true, message: 'Synced available chat history.' };
    }

    async startSession(cleanNumber, sessionDir) {
        fs.mkdirSync(sessionDir, { recursive: true });
        const prev = this.sessions.get(cleanNumber);
        if (prev?.sock) {
            try {
                prev.sock.ev.removeAllListeners();
                prev.sock.end();
            } catch (_) {}
        }

        const { state, saveCreds } = await useMultiFileAuthState(sessionDir);
        const { version } = await fetchLatestBaileysVersion();
        const sock = this._createSocket(version, state, cleanNumber);

        this.sessions.set(cleanNumber, {
            sock,
            connected: false,
            pending: false,
            reconnecting: false,
            sessionDir,
            saveCreds
        });

        this._attachEventHandlers(sock, cleanNumber, sessionDir, saveCreds);
        return sock;
    }

    async generatePairingCode(phoneNumber) {
        const cleanNumber = cleanPhoneNumber(phoneNumber);
        if (!cleanNumber || cleanNumber.length < 7) {
            throw new Error('Please enter a valid WhatsApp phone number with country code (e.g. 923001234567).');
        }

        const existing = this.sessions.get(cleanNumber);
        if (existing?.connected) {
            return {
                success: true,
                alreadyConnected: true,
                cleanNumber,
                message: `WhatsApp +${cleanNumber} is already linked and active.`
            };
        }

        const sessionDir = path.join(config.SESSIONS_DIR, cleanNumber);
        const credsPath = path.join(sessionDir, 'creds.json');
        if (fs.existsSync(credsPath)) {
            try {
                const creds = JSON.parse(fs.readFileSync(credsPath, 'utf8'));
                if (creds?.registered) {
                    await this.startSession(cleanNumber, sessionDir);
                    return {
                        success: true,
                        alreadyConnected: true,
                        cleanNumber,
                        message: `WhatsApp +${cleanNumber} session found and reconnecting now.`
                    };
                }
            } catch (_) {}
        }

        if (existing?.sock) {
            try {
                existing.sock.ev.removeAllListeners();
                existing.sock.end();
            } catch (_) {}
            this.sessions.delete(cleanNumber);
        }

        fs.rmSync(sessionDir, { recursive: true, force: true });
        fs.mkdirSync(sessionDir, { recursive: true });

        const { state, saveCreds } = await useMultiFileAuthState(sessionDir);
        const { version } = await fetchLatestBaileysVersion();
        const sock = this._createSocket(version, state, cleanNumber);

        this.sessions.set(cleanNumber, {
            sock,
            connected: false,
            pending: true,
            reconnecting: false,
            pairingExpiresAt: Date.now() + 180000,
            sessionDir,
            saveCreds
        });

        this._attachEventHandlers(sock, cleanNumber, sessionDir, saveCreds);

        if (typeof sock.waitForSocketOpen === 'function') {
            await Promise.race([sock.waitForSocketOpen().catch(() => {}), delay(5000)]);
        }
        await delay(1500);

        const rawCode = await sock.requestPairingCode(cleanNumber);
        const formatted = String(rawCode || '').replace(/[^A-Z0-9]/gi, '').toUpperCase();
        const displayCode = formatted.length === 8
            ? `${formatted.slice(0, 4)}-${formatted.slice(4)}`
            : formatted;

        return {
            success: true,
            code: displayCode,
            cleanNumber,
            alreadyConnected: false,
            message: 'Enter this 8-digit pairing code in WhatsApp → Linked Devices → Link with phone number.'
        };
    }

    /**
     * Send YouTube Song (MP3) or Video Song (MP4) directly WITH interactive buttons.
     */
    async sendMediaWithButtons(sock, jid, resolved, withButtons = true) {
        const sent = await sock.sendMessage(jid, resolved.baileysPayload);
        const buttonLabels = [];

        if (withButtons && (resolved.platform === 'song' || resolved.platform === 'video') && resolved.videoId) {
            const buttons = resolved.platform === 'song'
                ? [
                    { id: `.video ${resolved.videoId}`, text: '🎬 Video Song' },
                    { id: `.play ${resolved.videoId}`, text: '🎵 Audio MP3' }
                ]
                : [
                    { id: `.play ${resolved.videoId}`, text: '🎵 Audio MP3' },
                    { id: `.video ${resolved.videoId}`, text: '🎬 HD Video' }
                ];

            buttonLabels.push(...buttons.map(b => b.text));

            if (kangoSendButtons) {
                try {
                    const durationText = resolved.duration ? ` • ${resolved.duration}` : '';
                    await kangoSendButtons(sock, jid, {
                        text: `🎵 *${resolved.title}*${durationText}`,
                        footer: config.appName,
                        buttons
                    });
                } catch (err) {
                    console.warn('[baileys] Song/Video interactive button card skipped:', err.message);
                }
            }
        }

        this._keepStealthUnavailable(sock);
        return {
            id: sent?.key?.id || `media_${Date.now()}`,
            buttons: buttonLabels
        };
    }

    async sendInteractiveButtons(sock, jid, { title, body, buttons }) {
        const cleanButtons = (Array.isArray(buttons) ? buttons : [])
            .map((b, idx) => ({
                id: String(b.id || `btn_${idx + 1}`),
                text: String(b.text || '').trim()
            }))
            .filter(b => b.text.length > 0);

        const bodyText = String(body || title || '').trim();

        if (kangoSendButtons && cleanButtons.length > 0) {
            try {
                await kangoSendButtons(sock, jid, {
                    text: bodyText,
                    footer: config.appName,
                    buttons: cleanButtons
                });
                this._keepStealthUnavailable(sock);
                return { buttonCount: cleanButtons.length, fallback: false };
            } catch (err) {
                console.warn('[baileys] kango-wa sendButtons fallback:', err.message);
            }
        }

        const formattedLines = [
            title ? `*${title}*` : '',
            bodyText,
            '',
            ...cleanButtons.map((b, i) => `${i + 1}. ${b.text}`)
        ].filter(Boolean).join('\n');

        await sock.sendMessage(jid, { text: formattedLines });
        this._keepStealthUnavailable(sock);
        return { buttonCount: cleanButtons.length, fallback: true };
    }

    /**
     * .fdp Fundamental: Set 100% full uncropped picture at original aspect ratio & high resolution
     * directly through WhatsApp's w:profile:picture IQ stanza (ZERO crop, ZERO color bars).
     */
    async setFullProfilePicture(sock, { targetType, groupJid, imageBuffer }) {
        let finalBuffer = imageBuffer;

        // Preserve 100% of the full uncropped image (exact aspect ratio, high JPEG quality)
        // Only scale down proportionally if exceeding 1080px so WhatsApp's IQ node accepts it crisply.
        if (JimpLib) {
            try {
                const JimpClass = JimpLib.Jimp || JimpLib;
                if (typeof JimpClass.fromBuffer === 'function') {
                    const img = await JimpClass.fromBuffer(imageBuffer);
                    const maxSide = Math.max(img.width, img.height);
                    if (maxSide > 1080) {
                        img.scale(1080 / maxSide);
                    }
                    finalBuffer = await img.getBuffer('image/jpeg', { quality: 95 });
                } else if (typeof JimpClass.read === 'function') {
                    const img = await JimpClass.read(imageBuffer);
                    const maxSide = Math.max(img.bitmap.width, img.bitmap.height);
                    if (maxSide > 1080) {
                        img.scale(1080 / maxSide);
                    }
                    finalBuffer = await img.quality(95).getBufferAsync(JimpClass.MIME_JPEG);
                }
            } catch (err) {
                console.warn('[fdp] Jimp JPEG normalization skipped, uploading raw full buffer:', err.message);
            }
        }

        const isGroup = targetType === 'group' && Boolean(groupJid);
        const targetJid = isGroup
            ? toWhatsAppJid(groupJid)
            : (sock.user?.id ? `${sock.user.id.split(':')[0]}@s.whatsapp.net` : '');

        if (!targetJid) {
            throw new Error('Could not resolve target JID for full DP update.');
        }

        // Primary .fdp fundamental: direct w:profile:picture IQ stanza preserves 100% uncropped frame
        try {
            const iqAttrs = isGroup
                ? { target: targetJid, to: S_WHATSAPP_NET || '@s.whatsapp.net', type: 'set', xmlns: 'w:profile:picture' }
                : { to: S_WHATSAPP_NET || '@s.whatsapp.net', type: 'set', xmlns: 'w:profile:picture' };

            await sock.query({
                tag: 'iq',
                attrs: iqAttrs,
                content: [
                    {
                        tag: 'picture',
                        attrs: { type: 'image' },
                        content: finalBuffer
                    }
                ]
            });
            this._keepStealthUnavailable(sock);
            return targetJid;
        } catch (iqErr) {
            console.warn('[fdp] Direct IQ stanza fallback to updateProfilePicture:', iqErr.message);
            await sock.updateProfilePicture(targetJid, finalBuffer);
            this._keepStealthUnavailable(sock);
            return targetJid;
        }
    }

    async syncAllToCloud() {
        if (!store.dbReady) {
            return {
                success: false,
                configured: false,
                message: 'Set DATABASE_URL (or SUPABASE_DB_URL) in Railway Variables to enable cloud session sync.'
            };
        }
        await store.pushConfigToPostgres();
        let backedUp = 0;
        for (const [cleanNumber, entry] of this.sessions.entries()) {
            if (entry?.sessionDir) {
                const ok = await store.backupSessionDirToPostgres(cleanNumber, entry.sessionDir);
                if (ok) backedUp += 1;
            }
        }
        return {
            success: true,
            configured: true,
            message: `Cloud sync complete (${backedUp} WhatsApp session${backedUp === 1 ? '' : 's'} & settings backed up to PostgreSQL).`
        };
    }

    // ── WhatsApp Account Profile (Name, About, DP) ───────────────────────
    async getOwnProfile(sock) {
        const rawId = sock?.user?.id || '';
        const phone = cleanPhoneNumber(rawId.split(':')[0].split('@')[0]);
        const jid = phone ? `${phone}@s.whatsapp.net` : '';
        const profile = {
            jid,
            phone,
            pushName: String(sock?.user?.name || config.appName).trim(),
            about: '',
            profilePicUrl: ''
        };
        if (sock && jid) {
            try {
                if (typeof sock.fetchStatus === 'function') {
                    const st = await sock.fetchStatus(jid);
                    profile.about = String(st?.status || st?.[0]?.status?.status || '').trim();
                }
            } catch (_) {}
            try {
                if (typeof sock.profilePictureUrl === 'function') {
                    profile.profilePicUrl = await sock.profilePictureUrl(jid, 'image');
                }
            } catch (_) {}
            this._keepStealthUnavailable(sock);
        }
        return profile;
    }

    async updateProfileName(sock, name) {
        const cleanName = String(name || '').trim();
        if (!cleanName) throw new Error('Display name cannot be empty.');
        if (typeof sock.updateProfileName !== 'function') {
            throw new Error('Baileys updateProfileName is unavailable in this session.');
        }
        await sock.updateProfileName(cleanName);
        if (sock.user) sock.user.name = cleanName;
        this._keepStealthUnavailable(sock);
        return cleanName;
    }

    async updateProfileAbout(sock, about) {
        const cleanAbout = String(about || '').trim();
        if (!cleanAbout) throw new Error('About / Bio text cannot be empty.');
        if (typeof sock.updateProfileStatus !== 'function') {
            throw new Error('Baileys updateProfileStatus is unavailable in this session.');
        }
        await sock.updateProfileStatus(cleanAbout);
        this._keepStealthUnavailable(sock);
        return cleanAbout;
    }

    // ── Native WhatsApp Privacy + FM-WhatsApp Ghost Privacy ──────────────
    async getPrivacySettings(sock) {
        const defaults = {
            lastSeen: 'contacts',
            online: 'match_last_seen',
            profile: 'all',
            status: 'contacts',
            readReceipts: 'all',
            groupAdd: 'contacts',
            defaultDisappearing: 0
        };
        if (sock && typeof sock.fetchPrivacySettings === 'function') {
            try {
                const raw = await sock.fetchPrivacySettings(true);
                if (raw && typeof raw === 'object') {
                    defaults.lastSeen = String(raw.last || defaults.lastSeen);
                    defaults.online = String(raw.online || defaults.online);
                    defaults.profile = String(raw.profile || defaults.profile);
                    defaults.status = String(raw.status || defaults.status);
                    defaults.readReceipts = String(raw.readreceipts || defaults.readReceipts);
                    defaults.groupAdd = String(raw.groupadd || defaults.groupAdd);
                }
                this._keepStealthUnavailable(sock);
            } catch (err) {
                console.warn('[privacy] fetchPrivacySettings fallback:', err.message);
            }
        }
        return {
            privacy: defaults,
            ghostPrivacy: store.state.ghostPrivacy
        };
    }

    async updatePrivacySettings(sock, payload = {}) {
        const applied = [];

        if (payload.ghostPrivacy && typeof payload.ghostPrivacy === 'object') {
            store.updateGhostPrivacy(payload.ghostPrivacy);
            applied.push('Ghost Privacy');
            if (store.state.ghostPrivacy.freezeOnlinePresence && sock) {
                this._keepStealthUnavailable(sock);
            }
        }

        const p = payload.privacy || payload;
        if (sock && p && typeof p === 'object') {
            const safeCall = async (fnName, value, label) => {
                if (value !== undefined && value !== null && value !== '' && typeof sock[fnName] === 'function') {
                    try {
                        await sock[fnName](value);
                        applied.push(label);
                    } catch (err) {
                        console.warn(`[privacy] ${fnName}(${value}) failed:`, err.message);
                    }
                }
            };

            await safeCall('updateLastSeenPrivacy', p.lastSeen, 'Last Seen');
            await safeCall('updateOnlinePrivacy', p.online, 'Online');
            await safeCall('updateProfilePicturePrivacy', p.profile, 'Profile Photo');
            await safeCall('updateStatusPrivacy', p.status, 'Status');
            await safeCall('updateReadReceiptsPrivacy', p.readReceipts, 'Read Receipts');
            await safeCall('updateGroupsAddPrivacy', p.groupAdd, 'Groups Add');
            if (typeof p.defaultDisappearing === 'number' && typeof sock.updateDefaultDisappearingMode === 'function') {
                try {
                    await sock.updateDefaultDisappearingMode(p.defaultDisappearing);
                    applied.push('Default Disappearing Timer');
                } catch (err) {
                    console.warn('[privacy] updateDefaultDisappearingMode failed:', err.message);
                }
            }
            this._keepStealthUnavailable(sock);
        }

        return {
            applied,
            ...(await this.getPrivacySettings(sock))
        };
    }

    // ── Chat-Level WhatsApp Actions (Mute, Pin, Archive, Disappearing, Block, Clear)
    async performChatAction(sock, jid, action, value = null) {
        const cleanAction = String(action || '').toLowerCase().trim();
        let message = 'Chat action applied.';

        if (cleanAction === 'mute' || cleanAction === 'unmute') {
            const isMute = cleanAction === 'mute';
            const muteUntil = isMute ? (Date.now() + (Number(value) || 8 * 3600) * 1000) : null;
            if (typeof sock.chatModify === 'function') {
                await sock.chatModify({ mute: muteUntil }, jid).catch(() => {});
            }
            store.updateChatMeta(jid, { muted: isMute });
            message = isMute ? 'Chat muted.' : 'Chat unmuted.';
        } else if (cleanAction === 'pin' || cleanAction === 'unpin') {
            const isPin = cleanAction === 'pin';
            if (typeof sock.chatModify === 'function') {
                await sock.chatModify({ pin: isPin }, jid).catch(() => {});
            }
            store.updateChatMeta(jid, { pinned: isPin });
            message = isPin ? 'Chat pinned to top.' : 'Chat unpinned.';
        } else if (cleanAction === 'archive' || cleanAction === 'unarchive') {
            const isArchive = cleanAction === 'archive';
            if (typeof sock.chatModify === 'function') {
                const msgs = store.getChatMessages(jid).messages;
                const last = msgs[msgs.length - 1];
                const lastMessages = last ? [{ key: { remoteJid: jid, fromMe: last.fromMe, id: last.id }, messageTimestamp: Math.floor((last.timestamp || Date.now()) / 1000) }] : [];
                await sock.chatModify({ archive: isArchive, lastMessages }, jid).catch(() => {});
            }
            store.updateChatMeta(jid, { archived: isArchive });
            message = isArchive ? 'Chat archived.' : 'Chat unarchived.';
        } else if (cleanAction === 'disappearing') {
            const sec = Number(value) || 0;
            await sock.sendMessage(jid, { disappearingMessagesInChat: sec });
            store.updateChatMeta(jid, { disappearingSeconds: sec });
            message = sec === 0
                ? 'Disappearing messages turned OFF for this chat.'
                : `Disappearing messages set to ${Math.round(sec / 86400)} day(s).`;
        } else if (cleanAction === 'block' || cleanAction === 'unblock') {
            if (jid.endsWith('@g.us')) {
                throw new Error('Cannot block a group chat.');
            }
            if (typeof sock.updateBlockStatus === 'function') {
                await sock.updateBlockStatus(jid, cleanAction);
            }
            store.updateChatMeta(jid, { blocked: cleanAction === 'block' });
            message = cleanAction === 'block' ? 'Contact blocked on WhatsApp.' : 'Contact unblocked on WhatsApp.';
        } else if (cleanAction === 'clear') {
            store.clearChatMessages(jid);
            message = 'Local chat history cleared.';
        } else {
            throw new Error(`Unsupported chat action: ${action}`);
        }

        this._keepStealthUnavailable(sock);
        return { message, chatMeta: store.getChatMeta(jid) };
    }
}

module.exports = {
    baileysManager: new BaileysManager(),
    cleanPhoneNumber,
    toWhatsAppJid
};
