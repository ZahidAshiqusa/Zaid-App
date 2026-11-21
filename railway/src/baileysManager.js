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

const logger = pino({ level: 'silent' });
const msgRetryCounterCache = new NodeCache({ stdTTL: 600, checkperiod: 120 });

// In-memory recent raw Baileys messages for Anti-Delete recovery & getMessage retries
const rawMessageCache = new Map();
const MAX_RAW_CACHE = 2000;

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
        this.sessions = new Map(); // cleanNumber -> { sock, connected, pending, reconnecting, sessionDir, saveCreds, pairingExpiresAt }
        this.startedAt = Date.now();
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
            markOnlineOnConnect: true,
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

    _attachEventHandlers(sock, cleanNumber, sessionDir, saveCreds) {
        sock.ev.on('creds.update', async () => {
            try {
                await saveCreds();
                store.backupSessionDirToPostgres(cleanNumber, sessionDir).catch(() => {});
            } catch (_) {}
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

        sock.ev.on('messages.upsert', async ({ messages }) => {
            if (!Array.isArray(messages)) return;
            for (const msg of messages) {
                try {
                    await this._handleIncomingMessage(sock, cleanNumber, msg);
                } catch (err) {
                    console.warn('[baileys] Message processing error:', err.message);
                }
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
                console.log(`[baileys] WhatsApp session +${cleanNumber} connected!`);
                try {
                    await saveCreds();
                    await store.backupSessionDirToPostgres(cleanNumber, sessionDir);
                } catch (_) {}
            } else if (connection === 'close') {
                entry.connected = false;
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

                // Do not interrupt active uncompleted pairing code window unless handshake just finished (515)
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
            if (autoCfg.enabled) {
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
            }
            return;
        }

        // 2. Handle WhatsApp Channels (@newsletter) Auto-React Rules
        if (remoteJid.endsWith('@newsletter')) {
            const matchingRule = store.state.channelRules.find(
                r => r.active !== false && (r.jid === remoteJid || (r.channelUrl && r.channelUrl.includes(remoteJid)))
            );
            if (matchingRule && matchingRule.emojis.length > 0 && msg.key.server_id) {
                const emoji = matchingRule.emojis[Math.floor(Math.random() * matchingRule.emojis.length)];
                if (typeof sock.newsletterReactMessage === 'function') {
                    await sock.newsletterReactMessage(remoteJid, String(msg.key.server_id), emoji).catch(() => {});
                }
            }
            return;
        }

        // 3. Anti-Delete Protocol Message Detection
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
                        }
                    }
                }
            }
            return;
        }

        // Cache message for future anti-delete lookup & retries
        cacheRawMessage(remoteJid, msgId, msg);

        if (!fromMe && msg.pushName) {
            store.upsertContact(participant, msg.pushName);
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
            store.recordMessage(remoteJid, {
                id: msgId,
                fromMe,
                senderJid: participant,
                senderName,
                text: extracted.text,
                mediaType: extracted.mediaType,
                timestamp
            });
        }
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

        // Start clean ephemeral pairing socket
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

        // Wait briefly for Noise handshake to initialize
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
                return { buttonCount: cleanButtons.length, fallback: false };
            } catch (err) {
                console.warn('[baileys] kango-wa sendButtons fallback:', err.message);
            }
        }

        // Universal fallback that renders cleanly on all WhatsApp versions
        const formattedLines = [
            title ? `*${title}*` : '',
            bodyText,
            '',
            ...cleanButtons.map((b, i) => `${i + 1}. ${b.text}`)
        ].filter(Boolean).join('\n');

        await sock.sendMessage(jid, { text: formattedLines });
        return { buttonCount: cleanButtons.length, fallback: true };
    }

    async setFullProfilePicture(sock, { targetType, groupJid, imageBuffer }) {
        let finalBuffer = imageBuffer;

        // Square-pad image using Jimp so WhatsApp never crops full portrait/landscape photos
        if (JimpLib) {
            try {
                const JimpClass = JimpLib.Jimp || JimpLib;
                if (typeof JimpClass.fromBuffer === 'function') {
                    const img = await JimpClass.fromBuffer(imageBuffer);
                    const size = Math.max(img.width, img.height, 640);
                    const canvas = new JimpClass({ width: size, height: size, color: 0x075e54ff });
                    const x = Math.floor((size - img.width) / 2);
                    const y = Math.floor((size - img.height) / 2);
                    canvas.composite(img, x, y);
                    finalBuffer = await canvas.getBuffer('image/jpeg', { quality: 90 });
                } else if (typeof JimpClass.read === 'function') {
                    const img = await JimpClass.read(imageBuffer);
                    const size = Math.max(img.bitmap.width, img.bitmap.height, 640);
                    const bg = new JimpClass(size, size, 0x075e54ff);
                    const x = Math.floor((size - img.bitmap.width) / 2);
                    const y = Math.floor((size - img.bitmap.height) / 2);
                    bg.composite(img, x, y);
                    finalBuffer = await bg.getBufferAsync(JimpClass.MIME_JPEG);
                }
            } catch (err) {
                console.warn('[baileys] Jimp square pad skipped, uploading original buffer:', err.message);
            }
        }

        const targetJid = targetType === 'group' && groupJid
            ? toWhatsAppJid(groupJid)
            : (sock.user?.id ? `${sock.user.id.split(':')[0]}@s.whatsapp.net` : '');

        if (!targetJid) {
            throw new Error('Could not resolve target JID for profile picture update.');
        }

        await sock.updateProfilePicture(targetJid, finalBuffer);
        return targetJid;
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
}

module.exports = {
    baileysManager: new BaileysManager(),
    cleanPhoneNumber,
    toWhatsAppJid
};
