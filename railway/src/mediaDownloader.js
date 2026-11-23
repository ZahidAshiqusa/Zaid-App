'use strict';

const fs = require('fs');
const path = require('path');
const { execFile } = require('child_process');
const { promisify } = require('util');
const axios = require('axios');
const yts = require('yt-search');
const config = require('./config');

const execFileAsync = promisify(execFile);

let ruhendScraper = null;
try {
    ruhendScraper = require('ruhend-scraper');
} catch (_) {
    ruhendScraper = null;
}

const MOBILE_UA = 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36';
const YT_PARI_API_URL = 'https://yt-down-pari.vercel.app/api/download';
const CNV_KEY_URL = 'https://cnv.cx/v2/sanity/key';
const CNV_CONVERTER_URL = 'https://cnv.cx/v2/converter';
const CNV_HEADERS = {
    origin: 'https://frame.y2meta-uk.com',
    referer: 'https://frame.y2meta-uk.com/',
    'user-agent': MOBILE_UA,
    accept: '*/*'
};

let ytDlpAvailableCache = null;

async function isYtDlpAvailable() {
    if (ytDlpAvailableCache !== null) return ytDlpAvailableCache;
    try {
        await execFileAsync('yt-dlp', ['--version'], { timeout: 5000 });
        ytDlpAvailableCache = true;
    } catch (_) {
        ytDlpAvailableCache = false;
    }
    return ytDlpAvailableCache;
}

function looksLikeTextOrHtml(buffer, contentType = '') {
    if (!buffer || buffer.length === 0) return true;
    const ct = String(contentType || '').toLowerCase();
    if (ct.includes('text/html') || ct.includes('application/json') || ct.includes('text/plain') || ct.includes('application/xml')) {
        return true;
    }
    const head = buffer.subarray(0, Math.min(256, buffer.length)).toString('utf8').trimStart();
    if (
        head.startsWith('<!DOCTYPE') ||
        head.startsWith('<html') ||
        head.startsWith('<HTML') ||
        head.startsWith('<?xml') ||
        (head.startsWith('{') && (head.includes('"error"') || head.includes('"status"') || head.includes('"message"')))
    ) {
        return true;
    }
    return false;
}

function validateMediaBuffer(buffer, contentType, expectedKind = 'any') {
    const minSize = expectedKind === 'image' ? 512 : 4096;
    if (!buffer || buffer.length < minSize) {
        throw new Error(`Downloaded ${expectedKind} file is too small (${buffer?.length || 0} bytes).`);
    }
    if (looksLikeTextOrHtml(buffer, contentType)) {
        throw new Error(`Provider returned HTML/JSON error page instead of ${expectedKind} media.`);
    }
    return true;
}

async function downloadBufferFromUrl(url, extraHeaders = {}, expectedKind = 'any') {
    const response = await axios.get(url, {
        responseType: 'arraybuffer',
        timeout: 65000,
        maxRedirects: 6,
        maxContentLength: 85 * 1024 * 1024,
        validateStatus: (status) => status >= 200 && status < 400,
        headers: {
            'User-Agent': MOBILE_UA,
            Accept: '*/*',
            ...extraHeaders
        }
    });
    const buffer = Buffer.from(response.data);
    const contentType = String(response.headers['content-type'] || '');
    validateMediaBuffer(buffer, contentType, expectedKind);
    return { buffer, contentType };
}

function isDirectAudioUrl(input) {
    return /^https?:\/\/.+\.(mp3|m4a|aac|ogg|opus|wav)(\?.*)?$/i.test(String(input || '').trim());
}

function isSpotifyOrSoundCloudUrl(input) {
    return /^https?:\/\/(open\.spotify\.com\/track\/|spotify\.link\/|soundcloud\.com\/|on\.soundcloud\.com\/)/i.test(String(input || '').trim());
}

async function resolveTrackTitleFromMusicLink(url) {
    try {
        const oembedUrl = /spotify/i.test(url)
            ? `https://open.spotify.com/oembed?url=${encodeURIComponent(url)}`
            : `https://soundcloud.com/oembed?format=json&url=${encodeURIComponent(url)}`;
        const res = await axios.get(oembedUrl, { timeout: 10000, headers: { 'User-Agent': MOBILE_UA } });
        const title = String(res.data?.title || '').trim();
        const author = String(res.data?.author_name || '').trim();
        if (title) return author && !title.toLowerCase().includes(author.toLowerCase()) ? `${title} ${author}` : title;
    } catch (_) {}
    try {
        const res = await axios.get(url, { timeout: 10000, headers: { 'User-Agent': MOBILE_UA } });
        const match = String(res.data || '').match(/<title[^>]*>([^<]+)<\/title>/i);
        if (match && match[1]) {
            return match[1]
                .replace(/\s*\|\s*Spotify.*$/i, '')
                .replace(/\s*-\s*song.*by\s*/i, ' ')
                .replace(/\s*on SoundCloud.*$/i, '')
                .trim();
        }
    } catch (_) {}
    return '';
}

function extractYoutubeId(input) {
    const str = String(input || '').trim();
    if (/^[a-zA-Z0-9_-]{11}$/.test(str)) return str;
    const match = str.match(/(?:youtu\.be\/|(?:m\.|music\.|www\.)?youtube\.com\/(?:watch\?(?:.*&)?v=|shorts\/|embed\/|v\/|live\/))([a-zA-Z0-9_-]{11})/);
    return match ? match[1] : null;
}

async function resolveYoutubeInfo(queryOrUrl) {
    let trimmed = String(queryOrUrl || '').trim();
    if (isSpotifyOrSoundCloudUrl(trimmed)) {
        const resolvedTitle = await resolveTrackTitleFromMusicLink(trimmed);
        if (resolvedTitle) trimmed = resolvedTitle;
    }

    const vid = extractYoutubeId(trimmed);
    if (vid) {
        try {
            const detail = await yts({ videoId: vid });
            if (detail && detail.title) {
                return {
                    videoId: vid,
                    url: `https://www.youtube.com/watch?v=${vid}`,
                    title: detail.title,
                    thumbnail: detail.thumbnail || detail.image || `https://i.ytimg.com/vi/${vid}/hqdefault.jpg`,
                    duration: detail.timestamp || detail.duration?.timestamp || '',
                    author: detail.author?.name || 'YouTube'
                };
            }
        } catch (_) {}
        return {
            videoId: vid,
            url: `https://www.youtube.com/watch?v=${vid}`,
            title: 'YouTube Media',
            thumbnail: `https://i.ytimg.com/vi/${vid}/hqdefault.jpg`,
            duration: '',
            author: 'YouTube'
        };
    }

    const search = await yts(trimmed);
    const videos = Array.isArray(search?.videos) ? search.videos : [];
    // Prefer standard tracks under 20 minutes when searching by song name
    const first = videos.find(v => (v.seconds || 0) > 0 && (v.seconds || 0) <= 1200) || videos[0];
    if (!first) {
        throw new Error(`No YouTube results found for "${trimmed}".`);
    }
    const resolvedId = extractYoutubeId(first.url) || first.videoId;
    return {
        videoId: resolvedId,
        url: `https://www.youtube.com/watch?v=${resolvedId}`,
        title: first.title || 'YouTube Media',
        thumbnail: first.thumbnail || first.image || `https://i.ytimg.com/vi/${resolvedId}/hqdefault.jpg`,
        duration: first.timestamp || first.duration?.timestamp || '',
        author: first.author?.name || 'YouTube'
    };
}

function cleanupTempFiles(prefix) {
    try {
        if (!fs.existsSync(config.TMP_DIR)) return;
        for (const f of fs.readdirSync(config.TMP_DIR)) {
            if (f.startsWith(prefix)) {
                fs.rmSync(path.join(config.TMP_DIR, f), { force: true });
            }
        }
    } catch (_) {}
}

/**
 * Native yt-dlp + ffmpeg downloader (Zero dependency on unreliable third-party scrapers).
 * Supports YouTube (audio MP3 / video MP4), TikTok, Facebook, Instagram, SoundCloud, etc.
 */
async function downloadWithYtDlp(targetUrl, mode = 'audio', isYoutube = true) {
    const available = await isYtDlpAvailable();
    if (!available) {
        throw new Error('yt-dlp binary is not installed in container.');
    }

    fs.mkdirSync(config.TMP_DIR, { recursive: true });
    const runId = `dl_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
    const outTemplate = path.join(config.TMP_DIR, `${runId}.%(ext)s`);

    // Optional cookies support if configured via Railway env var YT_COOKIES_B64
    let cookiesPath = null;
    if (process.env.YT_COOKIES_B64) {
        try {
            cookiesPath = path.join(config.TMP_DIR, `${runId}_cookies.txt`);
            fs.writeFileSync(cookiesPath, Buffer.from(process.env.YT_COOKIES_B64, 'base64').toString('utf8'), 'utf8');
        } catch (_) {
            cookiesPath = null;
        }
    }

    const clientStrategies = isYoutube
        ? [
            ['--extractor-args', 'youtube:player_client=android_vr,ios'],
            ['--extractor-args', 'youtube:player_client=tv_embedded,mweb'],
            ['--extractor-args', 'youtube:player_client=android,web']
        ]
        : [[]];

    let lastError = null;
    try {
        for (const extraStrategy of clientStrategies) {
            cleanupTempFiles(`${runId}.`);
            const args = [
                '--no-playlist',
                '--no-warnings',
                '--no-check-certificates',
                '--geo-bypass',
                '--socket-timeout', '25',
                '--retries', '2',
                '--max-filesize', mode === 'audio' ? '45M' : '65M',
                ...extraStrategy
            ];

            if (cookiesPath) {
                args.push('--cookies', cookiesPath);
            }
            if (process.env.YT_DLP_PROXY) {
                args.push('--proxy', process.env.YT_DLP_PROXY);
            }

            if (mode === 'audio') {
                args.push(
                    '-f', 'bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio/best',
                    '-x',
                    '--audio-format', 'mp3',
                    '--audio-quality', '128K',
                    '-o', outTemplate,
                    targetUrl
                );
            } else {
                args.push(
                    '-f', 'best[height<=480][ext=mp4]/bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best',
                    '--merge-output-format', 'mp4',
                    '-o', outTemplate,
                    targetUrl
                );
            }

            try {
                await execFileAsync('yt-dlp', args, { timeout: 90000, maxBuffer: 4 * 1024 * 1024 });
                const produced = fs.readdirSync(config.TMP_DIR)
                    .filter(f => f.startsWith(`${runId}.`) && !f.endsWith('.part') && !f.endsWith('.ytdl'));
                const targetFile =
                    produced.find(f => f.endsWith(mode === 'audio' ? '.mp3' : '.mp4')) ||
                    produced[0];

                if (targetFile) {
                    const fullPath = path.join(config.TMP_DIR, targetFile);
                    const buf = fs.readFileSync(fullPath);
                    validateMediaBuffer(buf, mode === 'audio' ? 'audio/mpeg' : 'video/mp4', mode);
                    const ext = path.extname(targetFile).toLowerCase();
                    const mime = mode === 'audio'
                        ? (ext === '.m4a' ? 'audio/mp4' : 'audio/mpeg')
                        : 'video/mp4';
                    console.log(`[media] yt-dlp succeeded (${mode}, ${(buf.length / 1024).toFixed(0)} KB)`);
                    return { buffer: buf, contentType: mime };
                }
            } catch (err) {
                lastError = err;
                console.warn(`[media] yt-dlp strategy failed:`, (err.stderr || err.message || '').slice(0, 200));
            }
        }
    } finally {
        cleanupTempFiles(runId);
    }

    throw lastError || new Error('yt-dlp extraction failed.');
}

/**
 * Validated Multi-Provider REST Waterfall for YouTube Audio/Video.
 * CRITICAL: Every provider's URL is downloaded & validated INSIDE the loop so a broken/HTML URL
 * never aborts the fallback chain or triggers HTTP 500!
 */
async function downloadYoutubeViaApis(youtubeUrl, mode) {
    const errors = [];

    // Candidate 1: cnv.cx converter tunnel (reliable direct MP3/MP4)
    try {
        const keyRes = await axios.get(CNV_KEY_URL, { headers: CNV_HEADERS, timeout: 12000 });
        const sanityKey = keyRes.data?.key;
        if (sanityKey) {
            const body = new URLSearchParams({
                link: youtubeUrl,
                format: mode === 'audio' ? 'mp3' : 'mp4',
                audioBitrate: '128',
                videoQuality: '360',
                vCodec: 'h264'
            });
            const convRes = await axios.post(CNV_CONVERTER_URL, body.toString(), {
                timeout: 28000,
                headers: {
                    ...CNV_HEADERS,
                    'content-type': 'application/x-www-form-urlencoded',
                    key: sanityKey
                }
            });
            const tunnelUrl = convRes.data?.url;
            if (tunnelUrl && /^https?:\/\//i.test(tunnelUrl)) {
                return await downloadBufferFromUrl(tunnelUrl, CNV_HEADERS, mode);
            }
        }
    } catch (err) {
        errors.push(`cnv.cx: ${err.message}`);
    }

    // Candidate 2: Multi-provider direct JSON APIs
    const apiEndpoints = mode === 'audio'
        ? [
            `https://api.vreden.my.id/api/ytmp3?url=${encodeURIComponent(youtubeUrl)}`,
            `https://api.siputzx.my.id/api/d/ytmp3?url=${encodeURIComponent(youtubeUrl)}`,
            `https://api.giftedtech.web.id/api/download/dlmp3?apikey=gifted&url=${encodeURIComponent(youtubeUrl)}`,
            `https://apis.davidcyriltech.my.id/download/ytmp3?url=${encodeURIComponent(youtubeUrl)}`,
            `https://api.nexoracle.com/downloader/yt-audio2?apikey=free_key@maher_apis&url=${encodeURIComponent(youtubeUrl)}`,
            `https://api.agatz.xyz/api/ytmp3?url=${encodeURIComponent(youtubeUrl)}`
        ]
        : [
            `https://api.vreden.my.id/api/ytmp4?url=${encodeURIComponent(youtubeUrl)}`,
            `https://api.siputzx.my.id/api/d/ytmp4?url=${encodeURIComponent(youtubeUrl)}`,
            `https://api.giftedtech.web.id/api/download/dlmp4?apikey=gifted&url=${encodeURIComponent(youtubeUrl)}`,
            `https://apis.davidcyriltech.my.id/download/ytmp4?url=${encodeURIComponent(youtubeUrl)}`,
            `https://api.nexoracle.com/downloader/yt-video2?apikey=free_key@maher_apis&url=${encodeURIComponent(youtubeUrl)}`,
            `https://api.agatz.xyz/api/ytmp4?url=${encodeURIComponent(youtubeUrl)}`
        ];

    for (const ep of apiEndpoints) {
        try {
            const res = await axios.get(ep, {
                timeout: 18000,
                headers: { 'User-Agent': MOBILE_UA, Accept: 'application/json' }
            });
            const d = res.data?.result?.download || res.data?.result || res.data?.data || res.data || {};
            const dlUrl =
                d.download_url ||
                d.downloadUrl ||
                d.dl ||
                d.audio ||
                d.video ||
                d.url ||
                d.link ||
                (Array.isArray(d) ? d[0]?.downloadUrl || d[0]?.url : '') ||
                '';
            if (typeof dlUrl === 'string' && /^https?:\/\//i.test(dlUrl)) {
                return await downloadBufferFromUrl(dlUrl, {}, mode);
            }
        } catch (err) {
            errors.push(`${ep.split('/')[2]}: ${err.message}`);
        }
    }

    // Candidate 3: yt-down-pari POST API
    try {
        const res = await axios.post(
            YT_PARI_API_URL,
            { url: youtubeUrl },
            {
                timeout: 18000,
                headers: {
                    'Content-Type': 'application/json',
                    Accept: 'application/json',
                    'User-Agent': MOBILE_UA
                }
            }
        );
        const dataNode = res.data?.result?.data || res.data?.data || {};
        const medias = Array.isArray(dataNode.medias) ? dataNode.medias : [];
        const unlocked = medias.filter(m => m && m.url && m.locked !== true && String(m.url).startsWith('http'));
        const preferred = mode === 'audio'
            ? (unlocked.find(m => /audio/i.test(m.type || '') || /mp3|m4a/i.test(m.extension || '')) || unlocked[0])
            : (unlocked.find(m => /video/i.test(m.type || '') && /mp4/i.test(m.extension || '')) || unlocked.find(m => /video/i.test(m.type || '')) || unlocked[0]);

        if (preferred?.url) {
            return await downloadBufferFromUrl(preferred.url, {}, mode);
        }
    } catch (err) {
        errors.push(`yt-down-pari: ${err.message}`);
    }

    console.warn('[media] All YT REST API providers failed:', errors.join(' | '));
    throw new Error(`Could not download ${mode === 'audio' ? 'song MP3' : 'video MP4'} right now. Try another song title or link.`);
}

async function downloadYoutubeBuffer(youtubeUrl, mode = 'audio') {
    // 1. Primary: Native yt-dlp + ffmpeg inside Railway container (fastest, highest success rate)
    try {
        return await downloadWithYtDlp(youtubeUrl, mode, true);
    } catch (ytDlpErr) {
        console.warn(`[media] yt-dlp failed for ${mode}, falling back to validated API waterfall:`, ytDlpErr.message);
    }

    // 2. Fallback: Validated Multi-Provider REST APIs
    return await downloadYoutubeViaApis(youtubeUrl, mode);
}

async function downloadTikTokBuffer(url) {
    // 1. TikWM (fast, HD, no watermark)
    try {
        const res = await axios.post(
            'https://www.tikwm.com/api/',
            new URLSearchParams({ url, hd: '1' }).toString(),
            {
                timeout: 22000,
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'User-Agent': MOBILE_UA
                }
            }
        );
        const data = res.data?.data;
        const videoUrl = data?.hdplay || data?.play || data?.wmplay;
        if (videoUrl) {
            const dl = await downloadBufferFromUrl(videoUrl, {}, 'video');
            return { buffer: dl.buffer, title: data?.title || 'TikTok Video' };
        }
    } catch (err) {
        console.warn('[media] TikWM failed:', err.message);
    }

    // 2. ruhend-scraper fallback
    if (ruhendScraper && typeof ruhendScraper.ttdl === 'function') {
        try {
            const scraped = await ruhendScraper.ttdl(url);
            const videoUrl = scraped?.video?.[0] || scraped?.hd || scraped?.sd || scraped?.url;
            if (videoUrl) {
                const dl = await downloadBufferFromUrl(videoUrl, {}, 'video');
                return { buffer: dl.buffer, title: scraped?.title || 'TikTok Video' };
            }
        } catch (err) {
            console.warn('[media] ruhend ttdl failed:', err.message);
        }
    }

    // 3. Native yt-dlp fallback
    const dl = await downloadWithYtDlp(url, 'video', false);
    return { buffer: dl.buffer, title: 'TikTok Video' };
}

async function downloadFacebookBuffer(url) {
    // 1. Native yt-dlp first (very reliable for FB reels/watch/share)
    try {
        const dl = await downloadWithYtDlp(url, 'video', false);
        return { buffer: dl.buffer, title: 'Facebook Video' };
    } catch (err) {
        console.warn('[media] yt-dlp Facebook failed, trying scrapers:', err.message);
    }

    // 2. ruhend-scraper fallback
    if (ruhendScraper && typeof ruhendScraper.fbdl === 'function') {
        try {
            const result = await ruhendScraper.fbdl(url);
            const list = Array.isArray(result?.data) ? result.data : (Array.isArray(result) ? result : []);
            const hd = list.find(item => /hd/i.test(item.resolution || item.quality || '')) || list[0];
            const videoUrl = hd?.url || result?.hd || result?.sd || result?.url;
            if (videoUrl) {
                const dl = await downloadBufferFromUrl(videoUrl, {}, 'video');
                return { buffer: dl.buffer, title: result?.title || 'Facebook Video' };
            }
        } catch (_) {}
    }

    // 3. REST API fallback
    const fbApis = [
        `https://api.siputzx.my.id/api/d/facebook?url=${encodeURIComponent(url)}`,
        `https://api.giftedtech.web.id/api/download/facebook?apikey=gifted&url=${encodeURIComponent(url)}`
    ];
    for (const ep of fbApis) {
        try {
            const res = await axios.get(ep, { timeout: 18000, headers: { 'User-Agent': MOBILE_UA } });
            const d = res.data?.result || res.data?.data || res.data || {};
            const videoUrl = d.hd || d.sd || d.video || d.url || (Array.isArray(d) ? d[0]?.url : '');
            if (videoUrl && /^https?:\/\//i.test(videoUrl)) {
                const dl = await downloadBufferFromUrl(videoUrl, {}, 'video');
                return { buffer: dl.buffer, title: d.title || 'Facebook Video' };
            }
        } catch (_) {}
    }

    throw new Error('Could not extract downloadable Facebook video from that link.');
}

async function downloadInstagramBuffer(url) {
    // 1. ruhend-scraper (handles both photos & reels)
    if (ruhendScraper && typeof ruhendScraper.igdl === 'function') {
        try {
            const result = await ruhendScraper.igdl(url);
            const list = Array.isArray(result?.data) ? result.data : (Array.isArray(result) ? result : []);
            if (list.length > 0) {
                const first = list[0];
                const mediaUrl = first.url || first.downloadUrl;
                if (mediaUrl) {
                    const isVideo = /\.(mp4|mov|webm)/i.test(mediaUrl) || first.type === 'video' || /\/reel\/|\/tv\//i.test(url);
                    const dl = await downloadBufferFromUrl(mediaUrl, {}, isVideo ? 'video' : 'image');
                    return { buffer: dl.buffer, isVideo, title: first.title || 'Instagram Media' };
                }
            }
        } catch (err) {
            console.warn('[media] ruhend igdl failed:', err.message);
        }
    }

    // 2. Native yt-dlp fallback (works great on public Reels/Videos)
    try {
        const dl = await downloadWithYtDlp(url, 'video', false);
        return { buffer: dl.buffer, isVideo: true, title: 'Instagram Reel' };
    } catch (err) {
        console.warn('[media] yt-dlp Instagram failed:', err.message);
    }

    // 3. REST API fallback
    const igApis = [
        `https://api.siputzx.my.id/api/d/igdl?url=${encodeURIComponent(url)}`,
        `https://api.giftedtech.web.id/api/download/instadl?apikey=gifted&url=${encodeURIComponent(url)}`
    ];
    for (const ep of igApis) {
        try {
            const res = await axios.get(ep, { timeout: 18000, headers: { 'User-Agent': MOBILE_UA } });
            const d = res.data?.result || res.data?.data || res.data || {};
            const first = Array.isArray(d) ? d[0] : d;
            const mediaUrl = first?.url || first?.download_url || first?.video || first?.image || '';
            if (mediaUrl && /^https?:\/\//i.test(mediaUrl)) {
                const isVideo = /\.(mp4|mov|webm)/i.test(mediaUrl) || /\/reel\/|\/tv\//i.test(url);
                const dl = await downloadBufferFromUrl(mediaUrl, {}, isVideo ? 'video' : 'image');
                return { buffer: dl.buffer, isVideo, title: 'Instagram Media' };
            }
        } catch (_) {}
    }

    throw new Error('Could not extract downloadable Instagram media from that link.');
}

function detectPlatformFromQuery(input) {
    const str = String(input || '').trim();
    if (/tiktok\.com|vm\.tiktok\.com|vt\.tiktok\.com/i.test(str)) return 'tiktok';
    if (/instagram\.com|instagr\.am/i.test(str)) return 'instagram';
    if (/facebook\.com|fb\.watch|fb\.com/i.test(str)) return 'facebook';
    if (/youtube\.com|youtu\.be/i.test(str)) return 'video';
    if (/giphy\.com/i.test(str)) return 'giphy';
    if (/^https?:\/\/.+\.(mp4|mov|webm|mkv)(\?|$)/i.test(str)) return 'direct_video';
    if (/^https?:\/\/.+\.(mp3|m4a|ogg|wav)(\?|$)/i.test(str)) return 'direct_audio';
    if (/^https?:\/\/.+\.(jpg|jpeg|png|webp|gif)(\?|$)/i.test(str)) return 'direct_image';
    return 'song';
}

async function searchGiphy(query, headerKey) {
    const apiKey = config.giphyApiKey || String(headerKey || '').trim();
    if (!apiKey || apiKey === 'your_giphy_api_key') {
        throw new Error('GIPHY_API_KEY is not configured.');
    }
    const q = String(query || '').trim();
    const endpoint = q ? 'https://api.giphy.com/v1/gifs/search' : 'https://api.giphy.com/v1/gifs/trending';
    const params = q ? { api_key: apiKey, q, limit: 16, rating: 'g' } : { api_key: apiKey, limit: 16, rating: 'g' };
    const response = await axios.get(endpoint, { params, timeout: 12000 });
    return (response.data?.data || [])
        .map(item => ({
            id: item.id,
            title: item.title || 'GIF',
            previewUrl: item.images?.fixed_width_small?.url || item.images?.fixed_width?.url || '',
            mp4Url: item.images?.original_mp4?.mp4 || item.images?.fixed_width?.mp4 || ''
        }))
        .filter(item => item.mp4Url);
}

async function resolveMediaPayload({ platform = 'downloader', query, caption = '', giphyHeaderKey = '' }) {
    const cleanQuery = String(query || '').trim();
    const cleanCaption = String(caption || '').trim();
    let resolved = String(platform || 'downloader').toLowerCase().trim();
    if (resolved === 'downloader' || resolved === 'auto') {
        resolved = detectPlatformFromQuery(cleanQuery);
    }

    if (resolved === 'song') {
        // Direct audio file URL (.mp3, .m4a, .ogg, .wav)
        if (isDirectAudioUrl(cleanQuery)) {
            const { buffer, contentType } = await downloadBufferFromUrl(cleanQuery, {}, 'audio');
            const rawName = decodeURIComponent(cleanQuery.split('/').pop()?.split('?')[0] || 'song.mp3');
            const title = rawName.replace(/\.[a-z0-9]{2,4}$/i, '').replace(/[_-]+/g, ' ').trim() || 'Song';
            return {
                platform: 'song',
                mediaType: 'audio',
                title,
                baileysPayload: {
                    audio: buffer,
                    mimetype: /audio\//i.test(contentType) ? contentType.split(';')[0] : 'audio/mpeg',
                    ptt: false,
                    fileName: `${title.replace(/[^\w\s-]/g, '').slice(0, 60) || 'song'}.mp3`
                }
            };
        }

        // Direct SoundCloud URL can also be downloaded straight via yt-dlp before falling back to YouTube search
        if (/soundcloud\.com/i.test(cleanQuery)) {
            try {
                const scTitle = (await resolveTrackTitleFromMusicLink(cleanQuery)) || 'SoundCloud Track';
                const { buffer, contentType } = await downloadWithYtDlp(cleanQuery, 'audio', false);
                return {
                    platform: 'song',
                    mediaType: 'audio',
                    title: scTitle,
                    baileysPayload: {
                        audio: buffer,
                        mimetype: /audio\//i.test(contentType) ? contentType : 'audio/mpeg',
                        ptt: false,
                        fileName: `${scTitle.replace(/[^\w\s-]/g, '').slice(0, 60) || 'song'}.mp3`
                    }
                };
            } catch (_) {
                // Fall through to YouTube search with resolved track title
            }
        }

        const yt = await resolveYoutubeInfo(cleanQuery);
        const { buffer, contentType } = await downloadYoutubeBuffer(yt.url, 'audio');
        const safeMime = /audio\/mp4/i.test(contentType) ? 'audio/mp4' : 'audio/mpeg';
        return {
            platform: 'song',
            mediaType: 'audio',
            title: yt.title,
            videoId: yt.videoId,
            duration: yt.duration,
            author: yt.author,
            thumbnail: yt.thumbnail,
            baileysPayload: {
                audio: buffer,
                mimetype: safeMime,
                ptt: false,
                fileName: `${yt.title.replace(/[^\w\s-]/g, '').slice(0, 60) || 'song'}.mp3`
            }
        };
    }

    if (resolved === 'video') {
        const yt = await resolveYoutubeInfo(cleanQuery);
        const { buffer } = await downloadYoutubeBuffer(yt.url, 'video');
        const videoPayload = {
            video: buffer,
            mimetype: 'video/mp4'
        };
        if (cleanCaption) {
            videoPayload.caption = cleanCaption;
        }
        return {
            platform: 'video',
            mediaType: 'video',
            title: yt.title,
            videoId: yt.videoId,
            duration: yt.duration,
            author: yt.author,
            thumbnail: yt.thumbnail,
            baileysPayload: videoPayload
        };
    }

    if (resolved === 'tiktok') {
        const tt = await downloadTikTokBuffer(cleanQuery);
        return {
            platform: 'tiktok',
            mediaType: 'video',
            title: tt.title,
            baileysPayload: {
                video: tt.buffer,
                mimetype: 'video/mp4',
                caption: cleanCaption
            }
        };
    }

    if (resolved === 'facebook') {
        const fb = await downloadFacebookBuffer(cleanQuery);
        return {
            platform: 'facebook',
            mediaType: 'video',
            title: fb.title,
            baileysPayload: {
                video: fb.buffer,
                mimetype: 'video/mp4',
                caption: cleanCaption
            }
        };
    }

    if (resolved === 'instagram') {
        const ig = await downloadInstagramBuffer(cleanQuery);
        return {
            platform: 'instagram',
            mediaType: ig.isVideo ? 'video' : 'image',
            title: ig.title,
            baileysPayload: ig.isVideo
                ? { video: ig.buffer, mimetype: 'video/mp4', caption: cleanCaption }
                : { image: ig.buffer, caption: cleanCaption }
        };
    }

    if (resolved === 'giphy') {
        let mp4Url = cleanQuery;
        if (!/^https?:\/\//i.test(cleanQuery)) {
            const results = await searchGiphy(cleanQuery, giphyHeaderKey);
            mp4Url = results[0]?.mp4Url;
            if (!mp4Url) throw new Error('No matching GIF found.');
        }
        const { buffer } = await downloadBufferFromUrl(mp4Url, {}, 'video');
        return {
            platform: 'giphy',
            mediaType: 'gif',
            title: 'GIF',
            baileysPayload: {
                video: buffer,
                gifPlayback: true,
                mimetype: 'video/mp4',
                caption: cleanCaption
            }
        };
    }

    // Universal URL fallback: try yt-dlp first for social/video URLs, then direct HTTP download
    if (/^https?:\/\//i.test(cleanQuery) && !['direct_video', 'direct_audio', 'direct_image'].includes(resolved)) {
        try {
            const dl = await downloadWithYtDlp(cleanQuery, 'video', false);
            return {
                platform: 'downloader',
                mediaType: 'video',
                title: 'Downloaded Video',
                baileysPayload: { video: dl.buffer, mimetype: 'video/mp4', caption: cleanCaption }
            };
        } catch (_) {}
    }

    const { buffer, contentType } = await downloadBufferFromUrl(cleanQuery, {}, 'any');
    if (/audio/i.test(contentType) || resolved === 'direct_audio') {
        return {
            platform: 'downloader',
            mediaType: 'audio',
            title: 'Audio File',
            baileysPayload: { audio: buffer, mimetype: 'audio/mpeg', ptt: false }
        };
    }
    if (/image/i.test(contentType) || resolved === 'direct_image') {
        return {
            platform: 'downloader',
            mediaType: 'image',
            title: 'Image File',
            baileysPayload: { image: buffer, caption: cleanCaption }
        };
    }
    return {
        platform: 'downloader',
        mediaType: 'video',
        title: 'Video File',
        baileysPayload: { video: buffer, mimetype: 'video/mp4', caption: cleanCaption }
    };
}

module.exports = {
    downloadBufferFromUrl,
    searchGiphy,
    resolveMediaPayload
};
