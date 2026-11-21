'use strict';

/**
 * Native Android Chat App API Bridge for Zaid Ashiq Chat
 * Exposes clean chat messaging, groups/channels listing, Giphy search,
 * and server-side Media Download-and-Send (Song, Video, TikTok, Facebook,
 * Instagram, Universal Downloader) that delivers actual media files via Baileys
 * instead of raw links or bot commands.
 */

const axios = require('axios');
const ytPariApi = require('./ytPariApi');

let ruhendScraper = null;
try {
    ruhendScraper = require('ruhend-scraper');
} catch (_) {}

let pinterestDownloader = null;
try {
    pinterestDownloader = require('./pinterestDownloader');
} catch (_) {}

const recentCallsBuffer = [];
const MAX_RECENT_CALLS = 50;

function recordCallEvent(entry) {
    recentCallsBuffer.unshift({
        id: entry.id || `call_${Date.now()}`,
        callerJid: entry.callerJid || '',
        callerName: entry.callerName || entry.callerNumber || 'Unknown',
        callerNumber: entry.callerNumber || '',
        isVideo: Boolean(entry.isVideo),
        status: entry.status || 'missed',
        timestamp: entry.timestamp || Date.now()
    });
    if (recentCallsBuffer.length > MAX_RECENT_CALLS) {
        recentCallsBuffer.length = MAX_RECENT_CALLS;
    }
}

async function downloadBufferFromUrl(url, extraHeaders = {}) {
    const response = await axios.get(url, {
        responseType: 'arraybuffer',
        timeout: 60000,
        maxContentLength: 60 * 1024 * 1024,
        headers: {
            'User-Agent': 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36',
            ...extraHeaders
        }
    });
    const buffer = Buffer.from(response.data);
    if (!buffer || buffer.length < 256) {
        throw new Error('Downloaded media stream was empty or corrupted');
    }
    return {
        buffer,
        contentType: response.headers['content-type'] || ''
    };
}

async function resolveTikTokVideo(url) {
    // Primary: TikWM API (watermark-free HD/SD MP4)
    try {
        const res = await axios.post(
            'https://www.tikwm.com/api/',
            new URLSearchParams({ url, hd: '1' }).toString(),
            {
                timeout: 25000,
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
                }
            }
        );
        const data = res.data?.data;
        const videoUrl = data?.hdplay || data?.play || data?.wmplay;
        if (videoUrl) {
            return {
                videoUrl,
                title: data?.title || 'TikTok Video',
                author: data?.author?.nickname || ''
            };
        }
    } catch (err) {
        console.warn('[nativeChatApi] TikWM primary failed:', err.message);
    }

    // Fallback: ruhend-scraper ttdl
    if (ruhendScraper && typeof ruhendScraper.ttdl === 'function') {
        const scraped = await ruhendScraper.ttdl(url);
        const videoUrl = scraped?.video?.[0] || scraped?.hd || scraped?.sd || scraped?.url;
        if (videoUrl) {
            return {
                videoUrl,
                title: scraped?.title || 'TikTok Video',
                author: scraped?.author || ''
            };
        }
    }

    throw new Error('Could not extract downloadable TikTok video from that link.');
}

async function resolveFacebookVideo(url) {
    if (ruhendScraper && typeof ruhendScraper.fbdl === 'function') {
        const result = await ruhendScraper.fbdl(url);
        const list = Array.isArray(result?.data) ? result.data : (Array.isArray(result) ? result : []);
        const hd = list.find(i => /hd/i.test(i.resolution || i.quality || '')) || list[0];
        const videoUrl = hd?.url || result?.hd || result?.sd || result?.url;
        if (videoUrl) {
            return { videoUrl, title: result?.title || 'Facebook Video' };
        }
    }
    throw new Error('Could not extract downloadable Facebook video from that link.');
}

async function resolveInstagramMedia(url) {
    if (ruhendScraper && typeof ruhendScraper.igdl === 'function') {
        const result = await ruhendScraper.igdl(url);
        const list = Array.isArray(result?.data) ? result.data : (Array.isArray(result) ? result : []);
        if (list.length > 0) {
            const first = list[0];
            const mediaUrl = first.url || first.downloadUrl;
            const isVideo = /\.(mp4|mov|webm)/i.test(mediaUrl) ||
                first.type === 'video' ||
                /\/reel\/|\/tv\//i.test(url);
            return { mediaUrl, isVideo, title: first.title || 'Instagram Media' };
        }
    }
    throw new Error('Could not extract downloadable Instagram media from that link.');
}

async function resolveYoutubeMedia(queryOrUrl, mode) {
    const info = await ytPariApi.resolveYoutubeVideo(queryOrUrl);
    if (!info || !info.url) {
        throw new Error(`No YouTube results found for "${queryOrUrl}"`);
    }

    let mediaUrl = null;
    try {
        const pariData = await ytPariApi.fetchYtPariDownloadData(info.url);
        if (pariData) {
            if (mode === 'audio') {
                mediaUrl = pariData.audioUrl || pariData.audio?.url || pariData.mp3 || pariData.downloadUrl;
            } else {
                mediaUrl = pariData.videoUrl || pariData.video?.url || pariData.mp4 || pariData.downloadUrl;
            }
        }
    } catch (err) {
        console.warn('[nativeChatApi] ytPariApi primary failed:', err.message);
    }

    if (!mediaUrl && typeof ytPariApi.fetchCnvDownloadUrl === 'function') {
        try {
            mediaUrl = await ytPariApi.fetchCnvDownloadUrl(info.url, mode === 'audio' ? 'mp3' : 'mp4');
        } catch (err) {
            console.warn('[nativeChatApi] CNV fallback failed:', err.message);
        }
    }

    if (!mediaUrl) {
        throw new Error(`Failed to fetch downloadable ${mode === 'audio' ? 'MP3 audio' : 'MP4 video'} stream for "${info.title}".`);
    }

    return {
        mediaUrl,
        title: info.title || (mode === 'audio' ? 'Audio Track' : 'YouTube Video'),
        thumbnail: info.thumbnail || '',
        duration: info.timestamp || ''
    };
}

function detectPlatformFromUrl(input) {
    const str = String(input || '').trim();
    if (/tiktok\.com|vm\.tiktok\.com|vt\.tiktok\.com/i.test(str)) return 'tiktok';
    if (/instagram\.com|instagr\.am/i.test(str)) return 'instagram';
    if (/facebook\.com|fb\.watch|fb\.com/i.test(str)) return 'facebook';
    if (/youtube\.com|youtu\.be/i.test(str)) return 'video';
    if (/giphy\.com/i.test(str)) return 'giphy';
    if (/pinterest\.com|pin\.it/i.test(str)) return 'pinterest';
    if (/^https?:\/\/.+\.(mp4|mov|webm|mkv)(\?|$)/i.test(str)) return 'direct_video';
    if (/^https?:\/\/.+\.(mp3|m4a|ogg|wav)(\?|$)/i.test(str)) return 'direct_audio';
    if (/^https?:\/\/.+\.(jpg|jpeg|png|webp|gif)(\?|$)/i.test(str)) return 'direct_image';
    return 'song';
}

module.exports = function mountNativeChatApi(app, deps) {
    const { sessionManager, contactsAndChatsService, channelReactService, toWhatsAppJid } = deps;

    // 1. Send Native Text Message
    app.post('/api/chat/send-text', async (req, res) => {
        try {
            const { recipient, text } = req.body || {};
            if (!recipient || !String(text || '').trim()) {
                return res.status(400).json({ success: false, error: 'Recipient and message text are required.' });
            }
            const jid = toWhatsAppJid(recipient);
            if (!jid) {
                return res.status(400).json({ success: false, error: 'Invalid recipient phone number or JID.' });
            }
            const sock = sessionManager?.getActiveSocket();
            if (!sock || typeof sock.sendMessage !== 'function') {
                return res.status(503).json({
                    success: false,
                    error: 'WhatsApp is not linked yet. Please pair your phone number first.'
                });
            }

            const cleanText = String(text).trim();
            const sentMsg = await sock.sendMessage(jid, { text: cleanText });
            const msgId = sentMsg?.key?.id || `out_${Date.now()}`;

            try {
                if (typeof contactsAndChatsService?.recordOutgoingMessage === 'function') {
                    contactsAndChatsService.recordOutgoingMessage(jid, cleanText, 'text', msgId);
                }
            } catch (_) {}

            res.json({
                success: true,
                id: msgId,
                recipient: jid,
                text: cleanText,
                timestamp: Date.now()
            });
        } catch (err) {
            console.error('[nativeChatApi] send-text error:', err.message);
            res.status(500).json({ success: false, error: err.message || 'Failed to send message.' });
        }
    });

    // 2. Unified Media Download-and-Send (Sends actual MP3/MP4/Image/GIF to WhatsApp, NEVER raw links)
    app.post('/api/chat/download-and-send', async (req, res) => {
        try {
            const { recipient, platform = 'downloader', query, caption = '' } = req.body || {};
            if (!recipient) {
                return res.status(400).json({ success: false, error: 'Recipient JID or phone number is required.' });
            }
            const cleanQuery = String(query || '').trim();
            if (!cleanQuery) {
                return res.status(400).json({ success: false, error: 'Please provide a URL or search query.' });
            }
            const jid = toWhatsAppJid(recipient);
            if (!jid) {
                return res.status(400).json({ success: false, error: 'Invalid recipient format.' });
            }

            const sock = sessionManager?.getActiveSocket();
            if (!sock || typeof sock.sendMessage !== 'function') {
                return res.status(503).json({
                    success: false,
                    error: 'WhatsApp is not linked yet. Please complete Baileys pairing first.'
                });
            }

            let resolvedPlatform = String(platform || 'downloader').toLowerCase().trim();
            if (resolvedPlatform === 'downloader' || resolvedPlatform === 'auto') {
                resolvedPlatform = detectPlatformFromUrl(cleanQuery);
            }

            const cleanCaption = String(caption || '').trim();
            console.log(`[nativeChatApi] Download-and-send [${resolvedPlatform}] -> ${jid} (${cleanQuery})`);

            if (resolvedPlatform === 'song') {
                const yt = await resolveYoutubeMedia(cleanQuery, 'audio');
                const { buffer } = await downloadBufferFromUrl(yt.mediaUrl);
                const sent = await sock.sendMessage(jid, {
                    audio: buffer,
                    mimetype: 'audio/mpeg',
                    ptt: false,
                    fileName: `${yt.title.replace(/[^\w\s-]/g, '').slice(0, 60) || 'song'}.mp3`
                });
                return res.json({
                    success: true,
                    id: sent?.key?.id || `song_${Date.now()}`,
                    platform: 'song',
                    mediaType: 'audio',
                    title: yt.title,
                    message: `Sent song "${yt.title}" as audio file!`
                });
            }

            if (resolvedPlatform === 'video') {
                const yt = await resolveYoutubeMedia(cleanQuery, 'video');
                const { buffer } = await downloadBufferFromUrl(yt.mediaUrl);
                const sent = await sock.sendMessage(jid, {
                    video: buffer,
                    mimetype: 'video/mp4',
                    caption: cleanCaption || yt.title
                });
                return res.json({
                    success: true,
                    id: sent?.key?.id || `video_${Date.now()}`,
                    platform: 'video',
                    mediaType: 'video',
                    title: yt.title,
                    message: `Sent video "${yt.title}"!`
                });
            }

            if (resolvedPlatform === 'tiktok') {
                const tt = await resolveTikTokVideo(cleanQuery);
                const { buffer } = await downloadBufferFromUrl(tt.videoUrl);
                const sent = await sock.sendMessage(jid, {
                    video: buffer,
                    mimetype: 'video/mp4',
                    caption: cleanCaption
                });
                return res.json({
                    success: true,
                    id: sent?.key?.id || `tt_${Date.now()}`,
                    platform: 'tiktok',
                    mediaType: 'video',
                    title: tt.title,
                    message: 'TikTok video downloaded and sent directly!'
                });
            }

            if (resolvedPlatform === 'facebook') {
                const fb = await resolveFacebookVideo(cleanQuery);
                const { buffer } = await downloadBufferFromUrl(fb.videoUrl);
                const sent = await sock.sendMessage(jid, {
                    video: buffer,
                    mimetype: 'video/mp4',
                    caption: cleanCaption
                });
                return res.json({
                    success: true,
                    id: sent?.key?.id || `fb_${Date.now()}`,
                    platform: 'facebook',
                    mediaType: 'video',
                    title: fb.title,
                    message: 'Facebook video downloaded and sent directly!'
                });
            }

            if (resolvedPlatform === 'instagram') {
                const ig = await resolveInstagramMedia(cleanQuery);
                const { buffer } = await downloadBufferFromUrl(ig.mediaUrl);
                const payload = ig.isVideo
                    ? { video: buffer, mimetype: 'video/mp4', caption: cleanCaption }
                    : { image: buffer, caption: cleanCaption };
                const sent = await sock.sendMessage(jid, payload);
                return res.json({
                    success: true,
                    id: sent?.key?.id || `ig_${Date.now()}`,
                    platform: 'instagram',
                    mediaType: ig.isVideo ? 'video' : 'image',
                    title: ig.title,
                    message: `Instagram ${ig.isVideo ? 'video' : 'photo'} downloaded and sent directly!`
                });
            }

            if (resolvedPlatform === 'giphy') {
                let gifMp4Url = cleanQuery;
                if (!/^https?:\/\//i.test(cleanQuery)) {
                    const giphyKey = process.env.GIPHY_API_KEY || req.headers['x-giphy-key'] || '';
                    if (!giphyKey) {
                        return res.status(400).json({ success: false, error: 'GIPHY_API_KEY is not configured.' });
                    }
                    const gRes = await axios.get('https://api.giphy.com/v1/gifs/search', {
                        params: { api_key: giphyKey, q: cleanQuery, limit: 1, rating: 'g' },
                        timeout: 15000
                    });
                    gifMp4Url = gRes.data?.data?.[0]?.images?.original_mp4?.mp4;
                    if (!gifMp4Url) {
                        return res.status(404).json({ success: false, error: 'No matching GIF found.' });
                    }
                }
                const { buffer } = await downloadBufferFromUrl(gifMp4Url);
                const sent = await sock.sendMessage(jid, {
                    video: buffer,
                    gifPlayback: true,
                    mimetype: 'video/mp4',
                    caption: cleanCaption
                });
                return res.json({
                    success: true,
                    id: sent?.key?.id || `gif_${Date.now()}`,
                    platform: 'giphy',
                    mediaType: 'gif',
                    title: 'GIF',
                    message: 'GIF sent directly to chat!'
                });
            }

            if (resolvedPlatform === 'pinterest' && pinterestDownloader) {
                const fn = pinterestDownloader.downloadPinterest || pinterestDownloader;
                const pinData = typeof fn === 'function' ? await fn(cleanQuery) : null;
                const mediaUrl = pinData?.url || pinData?.video || pinData?.image;
                if (!mediaUrl) throw new Error('Could not extract Pinterest media.');
                const { buffer, contentType } = await downloadBufferFromUrl(mediaUrl);
                const isVideo = /video/i.test(contentType) || /\.mp4/i.test(mediaUrl);
                const sent = await sock.sendMessage(
                    jid,
                    isVideo
                        ? { video: buffer, mimetype: 'video/mp4', caption: cleanCaption }
                        : { image: buffer, caption: cleanCaption }
                );
                return res.json({
                    success: true,
                    id: sent?.key?.id || `pin_${Date.now()}`,
                    platform: 'pinterest',
                    mediaType: isVideo ? 'video' : 'image',
                    title: 'Pinterest Media',
                    message: 'Pinterest media downloaded and sent directly!'
                });
            }

            // Direct media URL fallback
            const { buffer, contentType } = await downloadBufferFromUrl(cleanQuery);
            let payload;
            let mediaType = 'video';
            if (/audio/i.test(contentType) || resolvedPlatform === 'direct_audio') {
                payload = { audio: buffer, mimetype: 'audio/mpeg', ptt: false };
                mediaType = 'audio';
            } else if (/image/i.test(contentType) || resolvedPlatform === 'direct_image') {
                payload = { image: buffer, caption: cleanCaption };
                mediaType = 'image';
            } else {
                payload = { video: buffer, mimetype: 'video/mp4', caption: cleanCaption };
                mediaType = 'video';
            }

            const sent = await sock.sendMessage(jid, payload);
            return res.json({
                success: true,
                id: sent?.key?.id || `dl_${Date.now()}`,
                platform: resolvedPlatform,
                mediaType,
                title: 'Downloaded Media',
                message: 'Media downloaded and sent directly!'
            });
        } catch (err) {
            console.error('[nativeChatApi] download-and-send error:', err.message);
            res.status(500).json({
                success: false,
                error: err.message || 'Failed to download and send media.'
            });
        }
    });

    // 3. Giphy Search Proxy (uses server GIPHY_API_KEY or client key header)
    app.get('/api/chat/giphy-search', async (req, res) => {
        try {
            const q = String(req.query.q || '').trim();
            const giphyKey = process.env.GIPHY_API_KEY || req.headers['x-giphy-key'] || '';
            if (!giphyKey || giphyKey === 'your_giphy_api_key') {
                return res.status(400).json({
                    success: false,
                    error: 'GIPHY_API_KEY is not configured yet.',
                    results: []
                });
            }
            const endpoint = q
                ? 'https://api.giphy.com/v1/gifs/search'
                : 'https://api.giphy.com/v1/gifs/trending';
            const params = q
                ? { api_key: giphyKey, q, limit: 16, rating: 'g' }
                : { api_key: giphyKey, limit: 16, rating: 'g' };
            const response = await axios.get(endpoint, { params, timeout: 12000 });
            const items = (response.data?.data || []).map(item => ({
                id: item.id,
                title: item.title || 'GIF',
                previewUrl: item.images?.fixed_width_small?.url || item.images?.fixed_width?.url || '',
                mp4Url: item.images?.original_mp4?.mp4 || item.images?.fixed_width?.mp4 || ''
            })).filter(i => i.mp4Url);

            res.json({ success: true, results: items });
        } catch (err) {
            res.status(500).json({ success: false, error: err.message, results: [] });
        }
    });

    // 4. Groups List from Active Baileys Socket
    app.get('/api/chats/groups', async (req, res) => {
        try {
            const sock = sessionManager?.getActiveSocket();
            if (!sock || typeof sock.groupFetchAllParticipating !== 'function') {
                return res.json({ success: true, connected: false, groups: [] });
            }
            const participating = await sock.groupFetchAllParticipating();
            const groups = Object.values(participating || {}).map(g => ({
                jid: g.id,
                name: g.subject || g.id,
                participantsCount: Array.isArray(g.participants) ? g.participants.length : 0,
                description: g.desc || '',
                creation: g.creation ? g.creation * 1000 : Date.now()
            })).sort((a, b) => a.name.localeCompare(b.name));

            res.json({ success: true, connected: true, total: groups.length, groups });
        } catch (err) {
            res.status(500).json({ success: false, error: err.message, groups: [] });
        }
    });

    // 5. Channels List (combines configured channel auto-react rules & known channels)
    app.get('/api/chats/channels', (req, res) => {
        try {
            const rules = channelReactService && typeof channelReactService.getRules === 'function'
                ? channelReactService.getRules()
                : [];
            const channels = (Array.isArray(rules) ? rules : []).map(r => ({
                id: r.id || r.channelJid || r.channelUrl,
                jid: r.channelJid || r.channelUrl || '',
                name: r.channelName || r.name || r.channelJid || 'WhatsApp Channel',
                emojis: Array.isArray(r.emojis) ? r.emojis : [r.emoji || '❤️'],
                active: r.enabled !== false
            }));
            res.json({ success: true, total: channels.length, channels });
        } catch (err) {
            res.status(500).json({ success: false, error: err.message, channels: [] });
        }
    });

    // 6. Recent Calls Feed
    app.get('/api/calls/recent', (req, res) => {
        res.json({
            success: true,
            total: recentCallsBuffer.length,
            calls: recentCallsBuffer
        });
    });

    // Expose call recorder hook for sessionManager if needed
    app.locals.recordCallEvent = recordCallEvent;
};
