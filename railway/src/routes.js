'use strict';

const express = require('express');
const multer = require('multer');
const config = require('./config');
const store = require('./store');
const { baileysManager, toWhatsAppJid } = require('./baileysManager');
const { searchGiphy, resolveMediaPayload } = require('./mediaDownloader');
const { lookupNumberDetails, formatNumberDetailsForChat } = require('./numberLookup');

const upload = multer({
    storage: multer.memoryStorage(),
    limits: { fileSize: 100 * 1024 * 1024 }
});

function requireActiveSocket(res) {
    const sock = baileysManager.getActiveSocket();
    if (!sock || typeof sock.sendMessage !== 'function') {
        res.status(503).json({
            success: false,
            error: 'WhatsApp is not linked yet. Please pair your phone number first.'
        });
        return null;
    }
    return sock;
}

function createApiRouter() {
    const router = express.Router();

    // ── Health & Status ──────────────────────────────────────────────────
    router.get('/health', (req, res) => {
        res.json({
            status: 'ok',
            uptime: process.uptime()
        });
    });

    router.get('/status', (req, res) => {
        const connectedSessions = baileysManager.getConnectedSessions();
        res.json({
            status: 'running',
            botName: config.appName,
            uptimeFormatted: baileysManager.getUptimeFormatted(),
            connectedSessions,
            activeSessionId: connectedSessions[0] || null
        });
    });

    // ── Pairing Code (8-digit Baileys Link) ──────────────────────────────
    router.post('/pair', async (req, res) => {
        try {
            const { phoneNumber } = req.body || {};
            const result = await baileysManager.generatePairingCode(phoneNumber);
            res.json(result);
        } catch (err) {
            console.error('[api/pair] Error:', err.message);
            res.status(400).json({
                success: false,
                error: err.message || 'Failed to generate pairing code.'
            });
        }
    });

    // ── Truecaller + SIM Database + WhatsApp Number Details Lookup ───────
    router.get('/lookup/number', async (req, res) => {
        try {
            const number = String(req.query.number || '').trim();
            if (!number) {
                return res.status(400).json({ success: false, error: 'Phone number is required.' });
            }
            const sock = baileysManager.getActiveSocket();
            const details = await lookupNumberDetails(number, sock, store);
            res.json(details);
        } catch (err) {
            console.error('[api/lookup/number] Error:', err.message);
            res.status(400).json({
                success: false,
                error: err.message || 'Number lookup failed.'
            });
        }
    });

    // ── Contacts, Chats, Older History, Presence Last Seen, Groups, Channels, Calls
    router.get('/contacts', (req, res) => {
        const contacts = store.getContactsList();
        res.json({
            success: true,
            totalContacts: contacts.length,
            contacts
        });
    });

    router.get('/chats/list', (req, res) => {
        const chats = store.getChatsList();
        res.json({
            success: true,
            totalChats: chats.length,
            chats
        });
    });

    router.get('/chats/view', async (req, res) => {
        const number = String(req.query.number || '').trim();
        if (!number) {
            return res.status(400).json({ success: false, error: 'Missing number parameter.' });
        }
        const jid = toWhatsAppJid(number);
        if (jid && !jid.endsWith('@g.us')) {
            baileysManager.subscribeLastSeen(jid).catch(() => {});
        }
        const data = store.getChatMessages(number);
        res.json({
            success: true,
            cleanNumber: data.cleanNumber,
            contactName: data.contactName,
            lastSeen: data.lastSeen,
            lastSeenFormatted: data.lastSeenFormatted,
            totalMessages: data.messages.length,
            messages: data.messages
        });
    });

    router.get('/chats/presence', async (req, res) => {
        const raw = String(req.query.jid || req.query.number || '').trim();
        const jid = toWhatsAppJid(raw);
        if (!jid) {
            return res.status(400).json({ success: false, error: 'Invalid JID or phone number.' });
        }
        const info = await baileysManager.subscribeLastSeen(jid);
        res.json({
            success: true,
            jid,
            lastSeen: info.lastSeen,
            lastSeenFormatted: info.lastSeenFormatted
        });
    });

    router.post('/chats/load-older', async (req, res) => {
        try {
            const { jid = '', oldestMsgId = '', oldestTimestamp = 0, fromMe = false } = req.body || {};
            const resolvedJid = jid ? toWhatsAppJid(jid) : '';
            const result = await baileysManager.fetchOlderMessages(
                resolvedJid,
                oldestMsgId,
                oldestTimestamp,
                fromMe
            );
            res.json({
                success: true,
                fetched: result.fetched,
                message: result.message
            });
        } catch (err) {
            res.status(500).json({ success: false, error: err.message || 'Failed loading older chats.' });
        }
    });

    router.post('/chats/clear-all', (req, res) => {
        try {
            store.clearAllChats();
            res.json({
                success: true,
                message: 'All old chats cleared.'
            });
        } catch (err) {
            res.status(500).json({ success: false, error: err.message || 'Failed clearing chats.' });
        }
    });

    router.get('/chats/groups', async (req, res) => {
        try {
            const sock = baileysManager.getActiveSocket();
            if (!sock || typeof sock.groupFetchAllParticipating !== 'function') {
                return res.json({ success: true, connected: false, groups: [] });
            }
            const participating = await sock.groupFetchAllParticipating();
            const groups = Object.values(participating || {})
                .map(g => ({
                    jid: g.id,
                    name: g.subject || g.id,
                    participantsCount: Array.isArray(g.participants) ? g.participants.length : 0,
                    description: g.desc || '',
                    creation: g.creation ? g.creation * 1000 : Date.now()
                }))
                .sort((a, b) => a.name.localeCompare(b.name));

            res.json({ success: true, connected: true, groups });
        } catch (err) {
            res.status(500).json({ success: false, connected: false, groups: [], error: err.message });
        }
    });

    router.get('/chats/channels', (req, res) => {
        const channels = store.state.channelRules.map(r => ({
            id: r.id,
            jid: r.jid || r.channelUrl || '',
            name: r.name || r.jid || 'WhatsApp Channel',
            emojis: Array.isArray(r.emojis) ? r.emojis : ['❤️'],
            active: r.active !== false
        }));
        res.json({ success: true, channels });
    });

    router.get('/calls/recent', (req, res) => {
        res.json({
            success: true,
            calls: store.state.calls
        });
    });

    // ── Chat Messaging & + Power Menu Tools ──────────────────────────────
    router.post('/chat/send-text', async (req, res) => {
        try {
            const { recipient, text } = req.body || {};
            const jid = toWhatsAppJid(recipient);
            const cleanText = String(text || '').trim();
            if (!jid || !cleanText) {
                return res.status(400).json({ success: false, error: 'Recipient and message text are required.' });
            }
            const sock = requireActiveSocket(res);
            if (!sock) return;

            // Intercept ALL integrated bot commands OR pasted song/social links right in chat!
            const playMatch = cleanText.match(/^\.(?:play|song|music|mp3)\s+(.+)$/i);
            const videoMatch = cleanText.match(/^\.(?:video|ytv|mp4)\s+(.+)$/i);
            const tiktokMatch = cleanText.match(/^\.(?:tiktok|tt)\s+(.+)$/i);
            const fbMatch = cleanText.match(/^\.(?:fb|facebook)\s+(.+)$/i);
            const igMatch = cleanText.match(/^\.(?:ig|insta|instagram)\s+(.+)$/i);
            const dlMatch = cleanText.match(/^\.(?:dl|download)\s+(.+)$/i);
            const numMatch = cleanText.match(/^\.(?:number|sim|tc|truecaller)\s+(.+)$/i);
            const menuMatch = cleanText.match(/^\.(?:menu|help|commands|cmds)$/i);

            const isPastedSongLink = /^https?:\/\/(?:(?:www\.|m\.|music\.)?youtube\.com\/(?:watch\?[^\s]*v=[\w-]+|shorts\/[\w-]+|live\/[\w-]+)[^\s]*|youtu\.be\/[\w-]+[^\s]*|open\.spotify\.com\/track\/[\w-]+[^\s]*|spotify\.link\/[\w-]+[^\s]*|(?:www\.|m\.|on\.)?soundcloud\.com\/[^\s]+|[^\s]+\.(?:mp3|m4a|aac|ogg|opus|wav)(?:\?[^\s]*)?)$/i.test(cleanText);
            const isPastedSocialLink = /^https?:\/\/(?:(?:www\.|vm\.|vt\.|m\.)?tiktok\.com\/[^\s]+|(?:www\.|m\.)?instagram\.com\/(?:reel|p|tv)\/[^\s]+|(?:www\.|m\.|fb\.watch)[^\s]*facebook\.com\/[^\s]+|fb\.watch\/[^\s]+)$/i.test(cleanText);

            if (menuMatch) {
                const menuText = [
                    `✨ *${config.appName} — Integrated Bot Commands*`,
                    '',
                    '🎵 *.play <song name/link>* — Direct YouTube MP3 + buttons',
                    '🎬 *.video <name/link>* — Direct YouTube MP4 + buttons',
                    '📱 *.tiktok <url>* — HD TikTok Video (no watermark)',
                    '📘 *.fb <url>* — Facebook Reel / Video',
                    '📸 *.ig <url>* — Instagram Reel / Post',
                    '⬇️ *.dl <url>* — Universal Media Downloader',
                    '🔎 *.number <phone>* — Truecaller + SIM DB + WhatsApp Profile',
                    '🖼️ *Full .fdp DP* — Available in + menu & 3-dot menu',
                    '👻 *Ghost Privacy & Account* — Available in 3-dot menu',
                    '',
                    '_Tip: Paste any YouTube, Spotify, SoundCloud, TikTok, IG, or FB link directly in chat!_'
                ].join('\n');
                const sentMenu = await sock.sendMessage(jid, { text: menuText });
                baileysManager._keepStealthUnavailable(sock);
                const now = Date.now();
                const id = sentMenu?.key?.id || `menu_${now}`;
                store.recordMessage(jid, { id, fromMe: true, senderJid: jid, senderName: 'You', text: menuText, mediaType: 'text', status: 2, timestamp: now });
                return res.json({ success: true, id, recipient: jid, text: menuText, status: 2, timestamp: now });
            }

            if (numMatch) {
                const queryNum = numMatch[1].trim();
                const details = await lookupNumberDetails(queryNum, sock, store);
                const reportText = formatNumberDetailsForChat(details);
                const sentReport = await sock.sendMessage(jid, { text: reportText });
                baileysManager._keepStealthUnavailable(sock);
                const now = Date.now();
                const id = sentReport?.key?.id || `num_${now}`;
                store.recordMessage(jid, { id, fromMe: true, senderJid: jid, senderName: 'You', text: reportText, mediaType: 'text', status: 2, timestamp: now });
                return res.json({ success: true, id, recipient: jid, text: reportText, status: 2, timestamp: now });
            }

            if (playMatch || videoMatch || tiktokMatch || fbMatch || igMatch || dlMatch || isPastedSongLink || isPastedSocialLink) {
                let targetPlatform = 'song';
                let targetQuery = cleanText;
                if (videoMatch) {
                    targetPlatform = 'video';
                    targetQuery = videoMatch[1].trim();
                } else if (playMatch) {
                    targetPlatform = 'song';
                    targetQuery = playMatch[1].trim();
                } else if (tiktokMatch) {
                    targetPlatform = 'tiktok';
                    targetQuery = tiktokMatch[1].trim();
                } else if (fbMatch) {
                    targetPlatform = 'facebook';
                    targetQuery = fbMatch[1].trim();
                } else if (igMatch) {
                    targetPlatform = 'instagram';
                    targetQuery = igMatch[1].trim();
                } else if (dlMatch) {
                    targetPlatform = 'downloader';
                    targetQuery = dlMatch[1].trim();
                } else if (isPastedSocialLink) {
                    targetPlatform = 'downloader';
                    targetQuery = cleanText;
                }

                const resolved = await resolveMediaPayload({
                    platform: targetPlatform,
                    query: targetQuery,
                    caption: '',
                    giphyHeaderKey: req.headers['x-giphy-key']
                });
                const delivered = await baileysManager.sendMediaWithButtons(sock, jid, resolved, true);
                const now = Date.now();
                store.recordMessage(jid, {
                    id: delivered.id,
                    fromMe: true,
                    senderJid: jid,
                    senderName: 'You',
                    text: resolved.title,
                    mediaType: resolved.mediaType || (videoMatch ? 'video' : 'audio'),
                    buttons: delivered.buttons,
                    status: 2,
                    timestamp: now
                });
                return res.json({
                    success: true,
                    id: delivered.id,
                    recipient: jid,
                    text: resolved.title,
                    status: 2,
                    timestamp: now
                });
            }

            const sent = await sock.sendMessage(jid, { text: cleanText });
            baileysManager._keepStealthUnavailable(sock);
            const msgId = sent?.key?.id || `out_${Date.now()}`;
            const now = Date.now();
            store.recordMessage(jid, {
                id: msgId,
                fromMe: true,
                senderJid: jid,
                senderName: 'You',
                text: cleanText,
                mediaType: 'text',
                status: 2, // Single tick until receiver comes online & device ACKs
                timestamp: now
            });

            res.json({
                success: true,
                id: msgId,
                recipient: jid,
                text: cleanText,
                status: 2,
                timestamp: now
            });
        } catch (err) {
            console.error('[api/chat/send-text] Error:', err.message);
            res.status(422).json({ success: false, error: err.message || 'Failed to send message.' });
        }
    });

    router.post('/chat/download-and-send', async (req, res) => {
        try {
            const { recipient, platform = 'downloader', query, caption = '', withButtons = true } = req.body || {};
            const jid = toWhatsAppJid(recipient);
            if (!jid || !String(query || '').trim()) {
                return res.status(400).json({ success: false, error: 'Recipient and media link/query are required.' });
            }
            const sock = requireActiveSocket(res);
            if (!sock) return;

            const resolved = await resolveMediaPayload({
                platform,
                query,
                caption,
                giphyHeaderKey: req.headers['x-giphy-key']
            });

            const delivered = await baileysManager.sendMediaWithButtons(
                sock,
                jid,
                resolved,
                Boolean(withButtons)
            );

            store.recordMessage(jid, {
                id: delivered.id,
                fromMe: true,
                senderJid: jid,
                senderName: 'You',
                text: resolved.title,
                mediaType: resolved.mediaType,
                buttons: delivered.buttons,
                status: 2,
                timestamp: Date.now()
            });

            res.json({
                success: true,
                id: delivered.id,
                platform: resolved.platform,
                mediaType: resolved.mediaType,
                title: resolved.title,
                buttons: delivered.buttons,
                status: 2,
                message: `Sent "${resolved.title}" directly!`
            });
        } catch (err) {
            console.error('[api/download-and-send] Error:', err.message);
            res.status(422).json({
                success: false,
                error: err.message || 'Failed to download and send media.'
            });
        }
    });

    router.get('/chat/giphy-search', async (req, res) => {
        try {
            const results = await searchGiphy(req.query.q, req.headers['x-giphy-key']);
            res.json({ success: true, results });
        } catch (err) {
            res.status(400).json({ success: false, results: [], error: err.message });
        }
    });

    router.post('/media/send', async (req, res) => {
        try {
            const { recipient, type, mediaData, mimetype } = req.body || {};
            const jid = toWhatsAppJid(recipient);
            if (!jid || !mediaData) {
                return res.status(400).json({ success: false, error: 'Recipient and media data are required.' });
            }
            const sock = requireActiveSocket(res);
            if (!sock) return;

            const buffer = Buffer.from(String(mediaData), 'base64');
            const cleanType = String(type || 'voice').toLowerCase();

            if (cleanType === 'ptv') {
                const sent = await sock.sendMessage(jid, {
                    video: buffer,
                    mimetype: mimetype || 'video/mp4',
                    ptv: true
                });
                if (typeof sock.sendPresenceUpdate === 'function') sock.sendPresenceUpdate('unavailable').catch(() => {});
                store.recordMessage(jid, {
                    id: sent?.key?.id || `ptv_${Date.now()}`,
                    fromMe: true,
                    senderJid: jid,
                    senderName: 'You',
                    text: 'Video Note',
                    mediaType: 'ptv',
                    status: 2,
                    timestamp: Date.now()
                });
                return res.json({ success: true, simulated: false, message: 'Circular video note sent!' });
            }

            const sent = await sock.sendMessage(jid, {
                audio: buffer,
                mimetype: mimetype || 'audio/mp4',
                ptt: true
            });
            if (typeof sock.sendPresenceUpdate === 'function') sock.sendPresenceUpdate('unavailable').catch(() => {});
            store.recordMessage(jid, {
                id: sent?.key?.id || `ptt_${Date.now()}`,
                fromMe: true,
                senderJid: jid,
                senderName: 'You',
                text: 'Voice Note',
                mediaType: 'voice',
                status: 2,
                timestamp: Date.now()
            });
            res.json({ success: true, simulated: false, message: 'Voice note sent!' });
        } catch (err) {
            res.status(500).json({ success: false, error: err.message || 'Failed to send media note.' });
        }
    });

    router.post('/location/send-live', async (req, res) => {
        try {
            const { recipient, latitude, longitude, durationSeconds = 900 } = req.body || {};
            const jid = toWhatsAppJid(recipient);
            if (!jid || typeof latitude !== 'number' || typeof longitude !== 'number') {
                return res.status(400).json({ success: false, error: 'Recipient, latitude, and longitude are required.' });
            }
            const sock = requireActiveSocket(res);
            if (!sock) return;

            const sent = await sock.sendMessage(jid, {
                location: {
                    degreesLatitude: latitude,
                    degreesLongitude: longitude,
                    name: `Live Location (${Math.round(durationSeconds / 60)}m)`
                }
            });
            if (typeof sock.sendPresenceUpdate === 'function') sock.sendPresenceUpdate('unavailable').catch(() => {});

            const trackingId = sent?.key?.id || `loc_${Date.now()}`;
            store.recordMessage(jid, {
                id: trackingId,
                fromMe: true,
                senderJid: jid,
                senderName: 'You',
                text: `Location: ${latitude.toFixed(4)}, ${longitude.toFixed(4)}`,
                mediaType: 'location',
                status: 2,
                timestamp: Date.now()
            });

            res.json({
                success: true,
                trackingId,
                message: 'Location sent to chat!'
            });
        } catch (err) {
            res.status(500).json({ success: false, error: err.message || 'Failed to send location.' });
        }
    });

    router.post('/messenger/buttons', async (req, res) => {
        try {
            const { recipient, title = '', body = '', buttons = [] } = req.body || {};
            const jid = toWhatsAppJid(recipient);
            if (!jid || (!String(body).trim() && !String(title).trim())) {
                return res.status(400).json({ success: false, error: 'Recipient and message body are required.' });
            }
            const sock = requireActiveSocket(res);
            if (!sock) return;

            const result = await baileysManager.sendInteractiveButtons(sock, jid, { title, body, buttons });
            store.recordMessage(jid, {
                id: `btn_${Date.now()}`,
                fromMe: true,
                senderJid: jid,
                senderName: 'You',
                text: String(body || title).trim(),
                mediaType: 'buttons',
                buttons: (Array.isArray(buttons) ? buttons : []).map(b => String(b.text || '')).filter(Boolean),
                status: 2,
                timestamp: Date.now()
            });

            res.json({
                success: true,
                simulated: false,
                buttonCount: result.buttonCount,
                message: 'Button message sent!'
            });
        } catch (err) {
            res.status(500).json({ success: false, error: err.message || 'Failed to send button message.' });
        }
    });

    // ── Status Feed & Status Uploader ────────────────────────────────────
    router.get('/status/captured', (req, res) => {
        res.json({
            success: true,
            statuses: store.state.capturedStatuses
        });
    });

    router.post('/status/captured/:id/forward', async (req, res) => {
        try {
            const sock = requireActiveSocket(res);
            if (!sock) return;
            const item = store.state.capturedStatuses.find(s => s.id === req.params.id);
            if (!item) {
                return res.status(404).json({ success: false, error: 'Status item not found.' });
            }
            const selfJid = sock.user?.id ? `${sock.user.id.split(':')[0]}@s.whatsapp.net` : toWhatsAppJid(config.ownerNumber);
            if (!selfJid) {
                return res.status(400).json({ success: false, error: 'Could not determine your WhatsApp JID.' });
            }
            await sock.sendMessage(selfJid, {
                text: `Saved Status from ${item.senderName} (+${item.senderNumber}):\n${item.caption || `[${item.mediaType}]`}`
            });
            if (typeof sock.sendPresenceUpdate === 'function') sock.sendPresenceUpdate('unavailable').catch(() => {});
            res.json({ success: true, message: 'Status forwarded to your Saved Messages!' });
        } catch (err) {
            res.status(500).json({ success: false, error: err.message });
        }
    });

    router.post('/status/upload', upload.single('file'), async (req, res) => {
        try {
            const sock = requireActiveSocket(res);
            if (!sock) return;

            const statusJidList = store.getContactsList()
                .map(c => c.jid)
                .filter(Boolean)
                .slice(0, 500);
            if (sock.user?.id) {
                const selfJid = `${sock.user.id.split(':')[0]}@s.whatsapp.net`;
                if (!statusJidList.includes(selfJid)) statusJidList.push(selfJid);
            }

            if (req.file) {
                const caption = String(req.body?.caption || '').trim();
                const isVoiceNote = String(req.body?.isVoiceNote || 'false').toLowerCase() === 'true';
                const mime = req.file.mimetype || 'application/octet-stream';
                const buf = req.file.buffer;

                if (isVoiceNote || mime.startsWith('audio/')) {
                    await sock.sendMessage(
                        'status@broadcast',
                        { audio: buf, mimetype: mime.includes('audio') ? mime : 'audio/ogg; codecs=opus', ptt: true },
                        { statusJidList, backgroundColor: '#075E54' }
                    );
                    if (typeof sock.sendPresenceUpdate === 'function') sock.sendPresenceUpdate('unavailable').catch(() => {});
                    return res.json({ success: true, message: 'Voice status posted to WhatsApp!' });
                }

                if (mime.startsWith('video/')) {
                    await sock.sendMessage(
                        'status@broadcast',
                        { video: buf, mimetype: mime, caption },
                        { statusJidList }
                    );
                    if (typeof sock.sendPresenceUpdate === 'function') sock.sendPresenceUpdate('unavailable').catch(() => {});
                    return res.json({ success: true, message: 'Video status posted to WhatsApp!' });
                }

                await sock.sendMessage(
                    'status@broadcast',
                    { image: buf, mimetype: mime, caption },
                    { statusJidList }
                );
                if (typeof sock.sendPresenceUpdate === 'function') sock.sendPresenceUpdate('unavailable').catch(() => {});
                return res.json({ success: true, message: 'Photo status posted to WhatsApp!' });
            }

            const { type = 'text', text = '', caption = '', backgroundColor = '#075E54', mediaData, mimetype } = req.body || {};
            if (type === 'text' || !mediaData) {
                const cleanText = String(text || caption).trim();
                if (!cleanText) {
                    return res.status(400).json({ success: false, error: 'Status text cannot be empty.' });
                }
                await sock.sendMessage(
                    'status@broadcast',
                    { text: cleanText },
                    { statusJidList, backgroundColor, font: 1 }
                );
                if (typeof sock.sendPresenceUpdate === 'function') sock.sendPresenceUpdate('unavailable').catch(() => {});
                return res.json({ success: true, message: 'Text status posted to WhatsApp!' });
            }

            const buf = Buffer.from(String(mediaData), 'base64');
            if (type === 'video') {
                await sock.sendMessage('status@broadcast', { video: buf, mimetype: mimetype || 'video/mp4', caption }, { statusJidList });
            } else if (type === 'voice') {
                await sock.sendMessage('status@broadcast', { audio: buf, mimetype: mimetype || 'audio/ogg; codecs=opus', ptt: true }, { statusJidList, backgroundColor });
            } else {
                await sock.sendMessage('status@broadcast', { image: buf, mimetype: mimetype || 'image/jpeg', caption }, { statusJidList });
            }
            if (typeof sock.sendPresenceUpdate === 'function') sock.sendPresenceUpdate('unavailable').catch(() => {});
            res.json({ success: true, message: 'Status posted to WhatsApp!' });
        } catch (err) {
            console.error('[api/status/upload] Error:', err.message);
            res.status(500).json({ success: false, error: err.message || 'Status upload failed.' });
        }
    });

    // ── Anti-Delete & View-Once Vault ────────────────────────────────────
    router.get('/vault/items', (req, res) => {
        res.json({ success: true, items: store.state.vaultItems });
    });

    router.get('/vault/settings', (req, res) => {
        res.json(store.state.vaultSettings);
    });

    router.post('/vault/settings', (req, res) => {
        const updated = store.updateVaultSettings(req.body || {});
        res.json({ success: true, message: 'Vault settings saved.', ...updated });
    });

    router.post('/vault/forward/:id', async (req, res) => {
        try {
            const sock = requireActiveSocket(res);
            if (!sock) return;
            const item = store.state.vaultItems.find(v => v.id === req.params.id);
            if (!item) {
                return res.status(404).json({ success: false, error: 'Vault item not found.' });
            }
            const selfJid = sock.user?.id ? `${sock.user.id.split(':')[0]}@s.whatsapp.net` : toWhatsAppJid(config.ownerNumber);
            await sock.sendMessage(selfJid, {
                text: `[${item.type.toUpperCase()}] From ${item.senderName} (+${item.senderNumber}):\n${item.text}`
            });
            res.json({ success: true, message: 'Recovered item forwarded to your chat!' });
        } catch (err) {
            res.status(500).json({ success: false, error: err.message });
        }
    });

    router.delete('/vault/clear', (req, res) => {
        store.clearVault();
        res.json({ success: true, message: 'Vault cleared.' });
    });

    // ── Auto-Status View & React Config ──────────────────────────────────
    router.get('/autoreact/config', (req, res) => {
        res.json(store.state.autoReactConfig);
    });

    router.post('/autoreact/config', (req, res) => {
        const updated = store.updateAutoReactConfig(req.body || {});
        res.json({ success: true, message: 'Auto-Status settings saved.', ...updated });
    });

    // ── Channel Auto-React Rules (Resolves invite link -> @newsletter JID immediately!)
    router.post('/char/rules', async (req, res) => {
        try {
            const { channelUrl, channelName = '', emojis = ['❤️', '🔥', '👍'] } = req.body || {};
            if (!String(channelUrl || '').trim()) {
                return res.status(400).json({ success: false, error: 'Channel link or JID is required.' });
            }
            const sock = baileysManager.getActiveSocket();
            const { resolvedJid, resolvedName } = await baileysManager.resolveAndFollowChannel(
                sock,
                channelUrl,
                channelName
            );
            store.addChannelRule({
                channelUrl,
                resolvedJid,
                channelName: resolvedName,
                emojis
            });
            baileysManager.rebuildChannelRuleIndex();
            res.json({
                success: true,
                jid: resolvedJid,
                message: `Instant auto-react enabled for ${resolvedName}!`
            });
        } catch (err) {
            res.status(500).json({ success: false, error: err.message });
        }
    });

    router.delete('/char/rules/:id', (req, res) => {
        store.deleteChannelRule(req.params.id);
        baileysManager.rebuildChannelRuleIndex();
        res.json({ success: true, message: 'Channel rule deleted.' });
    });

    // ── Full 100% Same-Resolution Uncropped Profile Picture (.fdp) ───────
    router.post('/profile-pic/full', async (req, res) => {
        try {
            const { targetType = 'personal', groupJid = '', imageData } = req.body || {};
            if (!imageData) {
                return res.status(400).json({ success: false, error: 'Image data is required.' });
            }
            const sock = requireActiveSocket(res);
            if (!sock) return;

            const imageBuffer = Buffer.from(String(imageData), 'base64');
            await baileysManager.setFullProfilePicture(sock, { targetType, groupJid, imageBuffer });
            res.json({ success: true, message: 'Full uncropped DP applied at 100% same picture resolution (.fdp)!' });
        } catch (err) {
            res.status(500).json({ success: false, error: err.message || 'Failed to update profile picture.' });
        }
    });

    // ── Cloud Session Sync (PostgreSQL / Supabase) ───────────────────────
    router.post('/session/cloud-sync', async (req, res) => {
        try {
            const result = await baileysManager.syncAllToCloud();
            res.json(result);
        } catch (err) {
            res.status(500).json({ success: false, configured: store.dbReady, error: err.message });
        }
    });

    // ── WhatsApp Account Profile (Name, Bio/About, DP) ───────────────────
    router.get('/account/profile', async (req, res) => {
        try {
            const sock = baileysManager.getActiveSocket();
            const profile = await baileysManager.getOwnProfile(sock);
            const priv = await baileysManager.getPrivacySettings(sock);
            res.json({
                success: true,
                connected: Boolean(sock),
                ...profile,
                privacy: priv.privacy,
                ghostPrivacy: priv.ghostPrivacy
            });
        } catch (err) {
            res.status(500).json({ success: false, error: err.message || 'Failed loading account profile.' });
        }
    });

    router.post('/account/profile', async (req, res) => {
        try {
            const sock = requireActiveSocket(res);
            if (!sock) return;
            const { name, about } = req.body || {};
            const updated = [];
            if (typeof name === 'string' && name.trim().length > 0) {
                await baileysManager.updateProfileName(sock, name);
                updated.push('Display Name');
            }
            if (typeof about === 'string' && about.trim().length > 0) {
                await baileysManager.updateProfileAbout(sock, about);
                updated.push('Bio / About');
            }
            if (updated.length === 0) {
                return res.status(400).json({ success: false, error: 'Provide name or about text to update.' });
            }
            const profile = await baileysManager.getOwnProfile(sock);
            res.json({
                success: true,
                message: `Updated ${updated.join(' & ')} on WhatsApp!`,
                ...profile
            });
        } catch (err) {
            res.status(500).json({ success: false, error: err.message || 'Failed updating profile.' });
        }
    });

    // ── Native WhatsApp Privacy + FM-WhatsApp Ghost Privacy ──────────────
    router.get('/privacy/settings', async (req, res) => {
        try {
            const sock = baileysManager.getActiveSocket();
            const data = await baileysManager.getPrivacySettings(sock);
            res.json({
                success: true,
                connected: Boolean(sock),
                ...data
            });
        } catch (err) {
            res.status(500).json({ success: false, error: err.message || 'Failed loading privacy settings.' });
        }
    });

    router.post('/privacy/settings', async (req, res) => {
        try {
            const sock = baileysManager.getActiveSocket();
            const result = await baileysManager.updatePrivacySettings(sock, req.body || {});
            res.json({
                success: true,
                message: result.applied.length > 0
                    ? `Saved: ${result.applied.join(', ')}`
                    : 'Privacy settings saved.',
                privacy: result.privacy,
                ghostPrivacy: result.ghostPrivacy
            });
        } catch (err) {
            res.status(500).json({ success: false, error: err.message || 'Failed saving privacy settings.' });
        }
    });

    // ── Chat-Level Actions (Mute, Pin, Archive, Disappearing, Block, Clear)
    router.post('/chat/action', async (req, res) => {
        try {
            const { recipient, action, value = null } = req.body || {};
            const jid = toWhatsAppJid(recipient);
            if (!jid || !action) {
                return res.status(400).json({ success: false, error: 'Recipient and action are required.' });
            }
            const sock = requireActiveSocket(res);
            if (!sock) return;

            const result = await baileysManager.performChatAction(sock, jid, action, value);
            res.json({
                success: true,
                message: result.message,
                chatMeta: result.chatMeta
            });
        } catch (err) {
            res.status(422).json({ success: false, error: err.message || 'Chat action failed.' });
        }
    });

    // ── Chat Export & Full Backup Snapshot ───────────────────────────────
    router.get('/chat/export', (req, res) => {
        const number = String(req.query.number || req.query.jid || '').trim();
        if (!number) {
            return res.status(400).json({ success: false, error: 'Chat number or JID is required.' });
        }
        const exported = store.exportChatTranscript(number);
        res.json({ success: true, ...exported });
    });

    router.get('/backup/export', (req, res) => {
        const snapshot = store.exportFullBackup();
        res.json({ success: true, ...snapshot });
    });

    // ── Integrated Bot Commands Catalog ──────────────────────────────────
    router.get('/bot/commands', (req, res) => {
        res.json({
            success: true,
            commands: [
                { command: '.play', usage: '.play <song name or link>', title: 'Play Song (MP3)', category: 'Media', description: 'Downloads & sends direct MP3 audio + interactive buttons' },
                { command: '.video', usage: '.video <video title or link>', title: 'Video Song (MP4)', category: 'Media', description: 'Downloads & sends YouTube MP4 video + interactive buttons' },
                { command: '.tiktok', usage: '.tiktok <url>', title: 'TikTok HD Video', category: 'Social', description: 'Downloads watermark-free TikTok video directly in chat' },
                { command: '.fb', usage: '.fb <url>', title: 'Facebook Video', category: 'Social', description: 'Downloads Facebook Reels & videos directly in chat' },
                { command: '.ig', usage: '.ig <url>', title: 'Instagram Reel/Post', category: 'Social', description: 'Downloads Instagram Reels & media directly in chat' },
                { command: '.dl', usage: '.dl <any url>', title: 'Universal Downloader', category: 'Media', description: 'Auto-detects platform and downloads video/audio/GIF' },
                { command: '.number', usage: '.number <phone>', title: 'Number Details Report', category: 'Tools', description: 'Fetches Truecaller + SIM DB + WhatsApp Profile & sends to chat' },
                { command: '.buttons', usage: 'Interactive Buttons Tool', title: 'Interactive Buttons', category: 'WhatsApp', description: 'Compose native interactive reply buttons' },
                { command: '.ptv', usage: 'Circular Video Note', title: 'Video Note (PTV)', category: 'WhatsApp', description: 'Send circular round WhatsApp video note' },
                { command: '.ptt', usage: 'Voice Note', title: 'Voice Note (PTT)', category: 'WhatsApp', description: 'Record or send audio as WhatsApp voice note' },
                { command: '.loc', usage: 'Fake Live Location', title: 'Fake Location', category: 'WhatsApp', description: 'Send custom GPS pin or live location' },
                { command: '.fdp', usage: 'Full Resolution DP', title: 'Full Uncropped DP (.fdp)', category: 'Account', description: 'Set 100% uncropped profile picture at original resolution' },
                { command: '.menu', usage: '.menu', title: 'Send Bot Menu', category: 'Tools', description: 'Sends formatted command reference into chat' }
            ]
        });
    });

    return router;
}

module.exports = createApiRouter;
