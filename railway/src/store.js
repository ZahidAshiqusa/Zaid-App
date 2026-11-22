'use strict';

const fs = require('fs');
const path = require('path');
const { Pool } = require('pg');
const config = require('./config');

const STATE_FILE = path.join(config.DATA_DIR, 'app_state.json');

const DEFAULT_STATE = {
    chats: {},           // jid -> chat summary
    messages: {},        // cleanKey -> Array<{ id, fromMe, senderJid, senderName, text, mediaType, timestamp, isoTime, buttons, status }>
    contacts: {},        // jid -> { number, jid, name }
    lastSeenMap: {},     // cleanKey -> epochMs (receiver's WhatsApp last seen time)
    capturedStatuses: [],
    vaultItems: [],
    calls: [],
    vaultSettings: {
        antiDeleteEnabled: true,
        viewOnceEnabled: true,
        autoForwardToOwner: false
    },
    autoReactConfig: {
        enabled: true,
        autoViewStatus: true,
        autoReactStatus: true,
        autoReplyStatus: false,
        emoji: '💚',
        replyMessage: 'Nice status!'
    },
    channelRules: []     // Array<{ id, jid, channelUrl, name, emojis, active }>
};

class AppStore {
    constructor() {
        this.state = this._loadLocal();
        this.pool = null;
        this.dbReady = false;
        this._saveTimer = null;
    }

    _loadLocal() {
        try {
            if (fs.existsSync(STATE_FILE)) {
                const parsed = JSON.parse(fs.readFileSync(STATE_FILE, 'utf8'));
                return {
                    ...DEFAULT_STATE,
                    ...parsed,
                    lastSeenMap: parsed.lastSeenMap || {},
                    vaultSettings: { ...DEFAULT_STATE.vaultSettings, ...(parsed.vaultSettings || {}) },
                    autoReactConfig: { ...DEFAULT_STATE.autoReactConfig, ...(parsed.autoReactConfig || {}) }
                };
            }
        } catch (err) {
            console.warn('[store] Failed loading local state, starting clean:', err.message);
        }
        return JSON.parse(JSON.stringify(DEFAULT_STATE));
    }

    _scheduleSave() {
        if (this._saveTimer) return;
        this._saveTimer = setTimeout(() => {
            this._saveTimer = null;
            this.saveLocalNow();
        }, 600);
    }

    saveLocalNow() {
        try {
            fs.writeFileSync(STATE_FILE, JSON.stringify(this.state, null, 2), 'utf8');
        } catch (err) {
            console.error('[store] Failed saving local state:', err.message);
        }
    }

    async initPostgres() {
        if (!config.databaseUrl) return false;
        try {
            const needsSsl = !/localhost|127\.0\.0\.1/i.test(config.databaseUrl);
            this.pool = new Pool({
                connectionString: config.databaseUrl,
                ssl: needsSsl ? { rejectUnauthorized: false } : false,
                max: 5,
                idleTimeoutMillis: 30000,
                connectionTimeoutMillis: 10000
            });
            await this.pool.query(`
                CREATE TABLE IF NOT EXISTS zaid_chat_sessions (
                    session_id TEXT PRIMARY KEY,
                    files_json JSONB NOT NULL,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                );
            `);
            await this.pool.query(`
                CREATE TABLE IF NOT EXISTS zaid_chat_kv (
                    key TEXT PRIMARY KEY,
                    payload JSONB NOT NULL,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                );
            `);
            this.dbReady = true;
            await this.pullConfigFromPostgres();
            console.log('[store] PostgreSQL cloud persistence connected.');
            return true;
        } catch (err) {
            console.warn('[store] PostgreSQL unavailable (running in local disk mode):', err.message);
            this.dbReady = false;
            return false;
        }
    }

    async pullConfigFromPostgres() {
        if (!this.dbReady || !this.pool) return;
        try {
            const { rows } = await this.pool.query(
                'SELECT key, payload FROM zaid_chat_kv WHERE key = ANY($1)',
                [['vaultSettings', 'autoReactConfig', 'channelRules']]
            );
            for (const row of rows) {
                if (row.key === 'vaultSettings' && row.payload) {
                    this.state.vaultSettings = { ...DEFAULT_STATE.vaultSettings, ...row.payload };
                } else if (row.key === 'autoReactConfig' && row.payload) {
                    this.state.autoReactConfig = { ...DEFAULT_STATE.autoReactConfig, ...row.payload };
                } else if (row.key === 'channelRules' && Array.isArray(row.payload)) {
                    this.state.channelRules = row.payload;
                }
            }
            this.saveLocalNow();
        } catch (err) {
            console.warn('[store] Pull config from Postgres failed:', err.message);
        }
    }

    async pushConfigToPostgres() {
        if (!this.dbReady || !this.pool) return false;
        const pairs = [
            ['vaultSettings', this.state.vaultSettings],
            ['autoReactConfig', this.state.autoReactConfig],
            ['channelRules', this.state.channelRules]
        ];
        for (const [key, payload] of pairs) {
            await this.pool.query(
                `INSERT INTO zaid_chat_kv (key, payload, updated_at)
                 VALUES ($1, $2::jsonb, NOW())
                 ON CONFLICT (key) DO UPDATE SET payload = EXCLUDED.payload, updated_at = NOW()`,
                [key, JSON.stringify(payload)]
            );
        }
        return true;
    }

    async backupSessionDirToPostgres(cleanNumber, sessionDir) {
        if (!this.dbReady || !this.pool || !fs.existsSync(sessionDir)) return false;
        const files = {};
        for (const fileName of fs.readdirSync(sessionDir)) {
            if (!fileName.endsWith('.json')) continue;
            const fullPath = path.join(sessionDir, fileName);
            try {
                const stat = fs.statSync(fullPath);
                if (stat.isFile() && stat.size < 2 * 1024 * 1024) {
                    files[fileName] = fs.readFileSync(fullPath, 'utf8');
                }
            } catch (_) {}
        }
        if (!files['creds.json']) return false;
        await this.pool.query(
            `INSERT INTO zaid_chat_sessions (session_id, files_json, updated_at)
             VALUES ($1, $2::jsonb, NOW())
             ON CONFLICT (session_id) DO UPDATE SET files_json = EXCLUDED.files_json, updated_at = NOW()`,
            [cleanNumber, JSON.stringify(files)]
        );
        return true;
    }

    async restoreAllSessionsFromPostgres() {
        if (!this.dbReady || !this.pool) return [];
        const restored = [];
        try {
            const { rows } = await this.pool.query('SELECT session_id, files_json FROM zaid_chat_sessions');
            for (const row of rows) {
                const cleanNumber = String(row.session_id || '').replace(/\D/g, '');
                if (!cleanNumber || !row.files_json || typeof row.files_json !== 'object') continue;
                const sessionDir = path.join(config.SESSIONS_DIR, cleanNumber);
                fs.mkdirSync(sessionDir, { recursive: true });
                for (const [fileName, content] of Object.entries(row.files_json)) {
                    if (!/^[a-zA-Z0-9._-]+\.json$/.test(fileName)) continue;
                    const target = path.join(sessionDir, fileName);
                    if (!fs.existsSync(target) && typeof content === 'string') {
                        fs.writeFileSync(target, content, 'utf8');
                    }
                }
                restored.push(cleanNumber);
            }
        } catch (err) {
            console.warn('[store] Restore sessions from Postgres failed:', err.message);
        }
        return restored;
    }

    normalizeChatKey(jidOrNumber) {
        const raw = String(jidOrNumber || '').trim();
        if (!raw) return '';
        if (raw.includes('@g.us') || raw.includes('@newsletter')) return raw;
        return raw.split('@')[0].split(':')[0].replace(/\D/g, '') || raw;
    }

    recordLastSeen(jidOrNumber, epochMs) {
        const key = this.normalizeChatKey(jidOrNumber);
        if (!key || !epochMs) return;
        const normalized = epochMs < 1e12 ? epochMs * 1000 : epochMs;
        if (!this.state.lastSeenMap) this.state.lastSeenMap = {};
        const prev = this.state.lastSeenMap[key] || 0;
        if (normalized >= prev) {
            this.state.lastSeenMap[key] = normalized;
            this._scheduleSave();
        }
    }

    getLastSeen(jidOrNumber) {
        const key = this.normalizeChatKey(jidOrNumber);
        const ts = this.state.lastSeenMap?.[key] || 0;
        return {
            lastSeen: ts,
            lastSeenFormatted: ts > 0 ? this.formatWhatsAppLastSeen(ts) : ''
        };
    }

    formatWhatsAppLastSeen(timestampMs) {
        if (!timestampMs || timestampMs <= 0) return '';
        try {
            const dt = new Date(timestampMs);
            const now = new Date();
            const tz = config.timezone || 'Asia/Karachi';
            const timeStr = new Intl.DateTimeFormat('en-US', {
                hour: 'numeric',
                minute: '2-digit',
                hour12: true,
                timeZone: tz
            }).format(dt);
            const dateFmt = new Intl.DateTimeFormat('en-CA', {
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
                timeZone: tz
            });
            const todayStr = dateFmt.format(now);
            const yesterday = new Date(now.getTime() - 86400000);
            const yesterdayStr = dateFmt.format(yesterday);
            const targetStr = dateFmt.format(dt);

            if (targetStr === todayStr) {
                return `last seen today at ${timeStr}`;
            }
            if (targetStr === yesterdayStr) {
                return `last seen yesterday at ${timeStr}`;
            }
            const dayMonth = new Intl.DateTimeFormat('en-GB', {
                day: 'numeric',
                month: 'short',
                timeZone: tz
            }).format(dt);
            return `last seen ${dayMonth} at ${timeStr}`;
        } catch (_) {
            return '';
        }
    }

    upsertContact(jid, name) {
        if (!jid || jid === 'status@broadcast' || jid.endsWith('@g.us') || jid.endsWith('@newsletter')) return;
        const number = jid.split('@')[0].split(':')[0].replace(/\D/g, '');
        if (!number) return;
        const normalizedJid = `${number}@s.whatsapp.net`;
        const existing = this.state.contacts[normalizedJid];
        const resolvedName = String(name || '').trim() || existing?.name || `+${number}`;
        this.state.contacts[normalizedJid] = {
            number,
            jid: normalizedJid,
            name: resolvedName
        };
        if (this.state.chats[normalizedJid] && resolvedName && resolvedName !== `+${number}`) {
            this.state.chats[normalizedJid].contactName = resolvedName;
            this.state.chats[normalizedJid].name = resolvedName;
        }
        this._scheduleSave();
    }

    upsertChatSummary(jid, meta = {}) {
        if (!jid || jid === 'status@broadcast' || jid.endsWith('@newsletter')) return;
        const isGroup = jid.endsWith('@g.us');
        const cleanNumber = isGroup ? jid : this.normalizeChatKey(jid);
        if (!cleanNumber) return;
        const normalizedJid = isGroup ? jid : `${cleanNumber}@s.whatsapp.net`;
        const existing = this.state.chats[normalizedJid];
        const ts = Number(meta.lastTimestamp) || existing?.lastTimestamp || 0;
        const contactName =
            String(meta.contactName || meta.name || '').trim() ||
            existing?.contactName ||
            this.state.contacts[normalizedJid]?.name ||
            (isGroup ? normalizedJid.replace('@g.us', '') : `+${cleanNumber}`);

        this.state.chats[normalizedJid] = {
            number: cleanNumber,
            cleanNumber,
            jid: normalizedJid,
            contactName,
            name: contactName,
            lastMessage: meta.lastMessage !== undefined ? meta.lastMessage : (existing?.lastMessage || ''),
            lastMessageType: meta.lastMessageType || existing?.lastMessageType || 'text',
            lastTimestamp: Math.max(ts, existing?.lastTimestamp || 0),
            messageCount: existing?.messageCount || 0,
            unreadCount: meta.unreadCount !== undefined ? Number(meta.unreadCount) : (existing?.unreadCount || 0),
            isGroup
        };
        this._scheduleSave();
    }

    recordMessage(jid, msg, options = {}) {
        if (!jid || jid === 'status@broadcast') return;
        const isGroup = jid.endsWith('@g.us');
        const cleanNumber = isGroup ? jid : this.normalizeChatKey(jid);
        const normalizedJid = isGroup ? jid : `${cleanNumber}@s.whatsapp.net`;
        const chatKey = this.normalizeChatKey(normalizedJid);
        const timestamp = Number(msg.timestamp) || Date.now();
        const isHistory = Boolean(options.isHistory);

        if (!this.state.messages[chatKey]) {
            this.state.messages[chatKey] = [];
        }
        const list = this.state.messages[chatKey];

        // Default outgoing status = 2 (SERVER_ACK = single tick until receiver device ACKs delivery!)
        const defaultStatus = msg.fromMe ? 2 : 4;
        const resolvedStatus = typeof msg.status === 'number' && msg.status > 0 ? msg.status : defaultStatus;

        const entry = {
            id: msg.id || `msg_${timestamp}_${Math.random().toString(36).slice(2, 6)}`,
            fromMe: Boolean(msg.fromMe),
            senderJid: msg.senderJid || normalizedJid,
            senderName: msg.senderName || this.state.contacts[normalizedJid]?.name || `+${cleanNumber}`,
            text: String(msg.text || ''),
            mediaType: msg.mediaType || 'text',
            timestamp,
            isoTime: new Date(timestamp).toISOString(),
            buttons: Array.isArray(msg.buttons) ? msg.buttons : [],
            status: resolvedStatus
        };

        const dupIdx = list.findIndex(m => m.id === entry.id);
        if (dupIdx >= 0) {
            list[dupIdx] = {
                ...list[dupIdx],
                ...entry,
                status: Math.max(list[dupIdx].status || 2, entry.status)
            };
        } else {
            list.push(entry);
            list.sort((a, b) => (a.timestamp || 0) - (b.timestamp || 0));
            if (list.length > 500) {
                list.splice(0, list.length - 500);
            }
        }

        // Track incoming sender's activity timestamp as a fallback last-seen reference
        if (!entry.fromMe && !isGroup) {
            this.recordLastSeen(normalizedJid, timestamp);
        }

        const existingChat = this.state.chats[normalizedJid];
        const latestMsg = list[list.length - 1] || entry;
        const contactName =
            (!entry.fromMe && msg.senderName && msg.senderName !== `+${cleanNumber}` ? msg.senderName : null) ||
            existingChat?.contactName ||
            this.state.contacts[normalizedJid]?.name ||
            (isGroup ? (msg.groupName || normalizedJid.replace('@g.us', '')) : `+${cleanNumber}`);

        this.state.chats[normalizedJid] = {
            number: cleanNumber,
            cleanNumber,
            jid: normalizedJid,
            contactName,
            name: contactName,
            lastMessage: latestMsg.text || `[${latestMsg.mediaType}]`,
            lastMessageType: latestMsg.mediaType,
            lastTimestamp: Math.max(latestMsg.timestamp || 0, existingChat?.lastTimestamp || 0),
            messageCount: list.length,
            unreadCount: (entry.fromMe || isHistory)
                ? (existingChat?.unreadCount || 0)
                : ((existingChat?.unreadCount || 0) + 1),
            isGroup
        };

        this._scheduleSave();
    }

    updateMessageStatus(jid, msgId, newStatus) {
        if (!jid || !msgId || typeof newStatus !== 'number') return;
        const key = this.normalizeChatKey(jid);
        const list = this.state.messages[key];
        if (!Array.isArray(list)) return;
        const target = list.find(m => m.id === msgId);
        if (target && newStatus > (target.status || 0)) {
            target.status = newStatus;
            this._scheduleSave();
        }
    }

    getChatsList() {
        return Object.values(this.state.chats)
            .sort((a, b) => (b.lastTimestamp || 0) - (a.lastTimestamp || 0));
    }

    getChatMessages(numberOrJid) {
        const key = this.normalizeChatKey(numberOrJid);
        const list = this.state.messages[key] || [];
        const jid = String(numberOrJid).includes('@') ? numberOrJid : `${key}@s.whatsapp.net`;
        const chat = this.state.chats[jid];
        if (chat && chat.unreadCount > 0) {
            chat.unreadCount = 0;
            this._scheduleSave();
        }
        const lastSeenInfo = this.getLastSeen(jid);
        return {
            cleanNumber: key,
            contactName: chat?.contactName || this.state.contacts[jid]?.name || `+${key}`,
            lastSeen: lastSeenInfo.lastSeen,
            lastSeenFormatted: lastSeenInfo.lastSeenFormatted,
            messages: list
        };
    }

    getContactsList() {
        return Object.values(this.state.contacts)
            .sort((a, b) => (a.name || '').localeCompare(b.name || ''));
    }

    addCapturedStatus(item) {
        this.state.capturedStatuses.unshift({
            id: item.id || `st_${Date.now()}`,
            senderName: item.senderName || 'Contact',
            senderNumber: item.senderNumber || '',
            senderJid: item.senderJid || '',
            mediaType: item.mediaType || 'text',
            caption: item.caption || '',
            timestamp: item.timestamp || Date.now()
        });
        if (this.state.capturedStatuses.length > 100) {
            this.state.capturedStatuses.length = 100;
        }
        this._scheduleSave();
    }

    addVaultItem(item) {
        this.state.vaultItems.unshift({
            id: item.id || `vault_${Date.now()}`,
            type: item.type || 'deleted',
            senderName: item.senderName || 'Unknown',
            senderNumber: item.senderNumber || '',
            chatJid: item.chatJid || '',
            text: item.text || '',
            mediaType: item.mediaType || 'text',
            timestamp: item.timestamp || Date.now()
        });
        if (this.state.vaultItems.length > 150) {
            this.state.vaultItems.length = 150;
        }
        this._scheduleSave();
    }

    clearVault() {
        this.state.vaultItems = [];
        this._scheduleSave();
    }

    addCallRecord(call) {
        this.state.calls.unshift({
            id: call.id || `call_${Date.now()}`,
            callerJid: call.callerJid || '',
            callerName: call.callerName || call.callerNumber || 'Unknown',
            callerNumber: call.callerNumber || '',
            isVideo: Boolean(call.isVideo),
            status: call.status || 'missed',
            timestamp: call.timestamp || Date.now()
        });
        if (this.state.calls.length > 60) {
            this.state.calls.length = 60;
        }
        this._scheduleSave();
    }

    updateVaultSettings(partial) {
        this.state.vaultSettings = {
            antiDeleteEnabled: partial.antiDeleteEnabled !== undefined ? Boolean(partial.antiDeleteEnabled) : this.state.vaultSettings.antiDeleteEnabled,
            viewOnceEnabled: partial.viewOnceEnabled !== undefined ? Boolean(partial.viewOnceEnabled) : this.state.vaultSettings.viewOnceEnabled,
            autoForwardToOwner: partial.autoForwardToOwner !== undefined ? Boolean(partial.autoForwardToOwner) : this.state.vaultSettings.autoForwardToOwner
        };
        this._scheduleSave();
        this.pushConfigToPostgres().catch(() => {});
        return this.state.vaultSettings;
    }

    updateAutoReactConfig(partial) {
        this.state.autoReactConfig = {
            enabled: partial.enabled !== undefined ? Boolean(partial.enabled) : this.state.autoReactConfig.enabled,
            autoViewStatus: partial.autoViewStatus !== undefined ? Boolean(partial.autoViewStatus) : this.state.autoReactConfig.autoViewStatus,
            autoReactStatus: partial.autoReactStatus !== undefined ? Boolean(partial.autoReactStatus) : this.state.autoReactConfig.autoReactStatus,
            autoReplyStatus: partial.autoReplyStatus !== undefined ? Boolean(partial.autoReplyStatus) : this.state.autoReactConfig.autoReplyStatus,
            emoji: String(partial.emoji || this.state.autoReactConfig.emoji || '💚'),
            replyMessage: String(partial.replyMessage ?? this.state.autoReactConfig.replyMessage ?? 'Nice status!')
        };
        this._scheduleSave();
        this.pushConfigToPostgres().catch(() => {});
        return this.state.autoReactConfig;
    }

    addChannelRule({ channelUrl, resolvedJid, channelName, emojis }) {
        const cleanUrl = String(channelUrl || '').trim();
        const jid = String(resolvedJid || (cleanUrl.endsWith('@newsletter') ? cleanUrl : cleanUrl)).trim();
        const id = `ch_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;
        // Replace existing rule for same JID or URL to prevent duplicates
        this.state.channelRules = this.state.channelRules.filter(
            r => r.jid !== jid && r.channelUrl !== cleanUrl
        );
        const rule = {
            id,
            jid,
            channelUrl: cleanUrl,
            name: String(channelName || '').trim() || jid,
            emojis: Array.isArray(emojis) && emojis.length > 0 ? emojis : ['❤️', '🔥', '👍'],
            active: true
        };
        this.state.channelRules.unshift(rule);
        this._scheduleSave();
        this.pushConfigToPostgres().catch(() => {});
        return rule;
    }

    deleteChannelRule(id) {
        this.state.channelRules = this.state.channelRules.filter(r => r.id !== id && r.jid !== id);
        this._scheduleSave();
        this.pushConfigToPostgres().catch(() => {});
    }
}

module.exports = new AppStore();
