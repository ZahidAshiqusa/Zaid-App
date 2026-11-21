'use strict';

const express = require('express');
const multer = require('multer');
const config = require('./config');
const store = require('./store');
const { baileysManager, toWhatsAppJid } = require('./baileysManager');
const { searchGiphy, resolveMediaPayload } = require('./mediaDownloader');

const upload = multer({
    storage: multer.memoryStorage(),
    limits: { fileSize: 100 * 1024 * 1024 } // 100 MB max for video/voice status uploads
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

    // ── Contacts, Chats, Groups, Channels, Calls ─────────────────────────
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

    router.get('/chats/view', (req, res) => {
        const number = String(req.query.number || '').trim();
        if (!number) {
            return res.status(400).json({ success: false, error: 'Missing number parameter.' });
        }
        const data = store.getChatMessages(number);
        res.json({
            success: true,
            cleanNumber: data.cleanNumber,
            contactName: data.contactName,
            totalMessages: data.messages.length,
            messages: data.messages
        });
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

            const sent = await sock.sendMessage(jid, { text: cleanText });
            const msgId = sent?.key?.id || `out_${Date.now()}`;
            const now = Date.now();
            store.recordMessage(jid, {
                id: msgId,
                fromMe: true,
                senderJid: jid,
                senderName: 'You',
                text: cleanText,
                mediaType: 'text',
                timestamp: now
            });

            res.json({
                success: true,
                id: msgId,
                recipient: jid,
                text: cleanText,
                timestamp: now
            });
        } catch (err) {
            res.status(500).json({ success: false, error: err.message || 'Failed to send text.' });
        }
    });

    router.post('/chat/download-and-send', async (req, res) => {
        try {
            const { recipient, platform = 'downloader', query, caption = '' } = req.body || {};
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

            const sent = await sock.sendMessage(jid, resolved.baileysPayload);
            const msgId = sent?.key?.id || `dl_${Date.now()}`;

            store.recordMessage(jid, {
                id: msgId,
                fromMe: true,
                senderJid: jid,
                senderName: 'You',
                text: `${resolved.title} (${resolved.mediaType})`,
                mediaType: resolved.mediaType,
                timestamp: Date.now()
            });

            res.json({
                success: true,
                id: msgId,
                platform: resolved.platform,
                mediaType: resolved.mediaType,
                title: resolved.title,
                message: `Sent "${resolved.title}" directly as ${resolved.mediaType}!`
            });
        } catch (err) {
            console.error('[api/download-and-send] Error:', err.message);
            res.status(500).json({
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
                await sock.sendMessage(jid, {
                    video: buffer,
                    mimetype: mimetype || 'video/mp4',
                    ptv: true
                });
                store.recordMessage(jid, {
                    id: `ptv_${Date.now()}`,
                    fromMe: true,
                    senderJid: jid,
                    senderName: 'You',
                    text: 'Video Note',
                    mediaType: 'ptv',
                    timestamp: Date.now()
                });
                return res.json({ success: true, simulated: false, message: 'Circular video note sent!' });
            }

            await sock.sendMessage(jid, {
                audio: buffer,
                mimetype: mimetype || 'audio/mp4',
                ptt: true
            });
            store.recordMessage(jid, {
                id: `ptt_${Date.now()}`,
                fromMe: true,
                senderJid: jid,
                senderName: 'You',
                text: 'Voice Note',
                mediaType: 'voice',
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

            await sock.sendMessage(jid, {
                location: {
                    degreesLatitude: latitude,
                    degreesLongitude: longitude,
                    name: `Live Location (${Math.round(durationSeconds / 60)}m)`
                }
            });

            const trackingId = `loc_${Date.now()}`;
            store.recordMessage(jid, {
                id: trackingId,
                fromMe: true,
                senderJid: jid,
                senderName: 'You',
                text: `Location: ${latitude.toFixed(4)}, ${longitude.toFixed(4)}`,
                mediaType: 'location',
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

    // ── Status Feed & Status Uploader (JSON + Multipart Streaming) ───────
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

            // Multipart upload (Video / Voice / Image from Android ProgressRequestBody)
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
                    return res.json({ success: true, message: 'Voice status posted to WhatsApp!' });
                }

                if (mime.startsWith('video/')) {
                    await sock.sendMessage(
                        'status@broadcast',
                        { video: buf, mimetype: mime, caption },
                        { statusJidList }
                    );
                    return res.json({ success: true, message: 'Video status posted to WhatsApp!' });
                }

                await sock.sendMessage(
                    'status@broadcast',
                    { image: buf, mimetype: mime, caption },
                    { statusJidList }
                );
                return res.json({ success: true, message: 'Photo status posted to WhatsApp!' });
            }

            // JSON upload (Text status or Base64 fallback)
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

    // ── Channel Auto-React Rules ─────────────────────────────────────────
    router.post('/char/rules', (req, res) => {
        const { channelUrl, channelName = '', emojis = ['❤️', '🔥', '👍'] } = req.body || {};
        if (!String(channelUrl || '').trim()) {
            return res.status(400).json({ success: false, error: 'Channel link or JID is required.' });
        }
        store.addChannelRule({ channelUrl, channelName, emojis });
        res.json({ success: true, message: 'Channel auto-react rule saved!' });
    });

    router.delete('/char/rules/:id', (req, res) => {
        store.deleteChannelRule(req.params.id);
        res.json({ success: true, message: 'Channel rule deleted.' });
    });

    // ── Full No-Crop Profile Picture Tool ────────────────────────────────
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
            res.json({ success: true, message: 'Full profile picture updated without cropping!' });
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

    return router;
}

module.exports = createApiRouter;
