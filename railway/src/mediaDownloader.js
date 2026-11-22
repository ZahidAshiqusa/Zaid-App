'use strict';

const axios = require('axios');
const yts = require('yt-search');
const config = require('./config');

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

async function downloadBufferFromUrl(url, extraHeaders = {}) {
    const response = await axios.get(url, {
        responseType: 'arraybuffer',
        timeout: 60000,
        maxContentLength: 80 * 1024 * 1024,
        headers: {
            'User-Agent': MOBILE_UA,
            ...extraHeaders
        }
    });
    const buffer = Buffer.from(response.data);
    if (!buffer || buffer.length < 256) {
        throw new Error('Downloaded media stream was empty or corrupted.');
    }
    return {
        buffer,
        contentType: String(response.headers['content-type'] || '')
    };
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
    const first = search?.videos?.[0];
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

async function fetchYoutubeStreamUrl(youtubeUrl, mode) {
    // 1. Primary: yt-down-pari API
    try {
        const res = await axios.post(
            YT_PARI_API_URL,
            { url: youtubeUrl },
            {
                timeout: 22000,
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

        if (mode === 'audio') {
            const audioItem =
                unlocked.find(m => /audio/i.test(m.type || '') || /mp3|m4a/i.test(m.extension || '')) ||
                unlocked[0];
            if (audioItem?.url) return { url: audioItem.url, headers: {} };
        } else {
            const videoItem =
                unlocked.find(m => /video/i.test(m.type || '') && /mp4/i.test(m.extension || '')) ||
                unlocked.find(m => /video/i.test(m.type || '')) ||
                unlocked[0];
            if (videoItem?.url) return { url: videoItem.url, headers: {} };
        }
    } catch (err) {
        console.warn('[media] yt-down-pari failed, trying fallback:', err.message);
    }

    // 2. Fallback: cnv.cx converter tunnel
    try {
        const keyRes = await axios.get(CNV_KEY_URL, { headers: CNV_HEADERS, timeout: 15000 });
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
            if (tunnelUrl) {
                return { url: tunnelUrl, headers: CNV_HEADERS };
            }
        }
    } catch (err) {
        console.warn('[media] cnv.cx fallback failed:', err.message);
    }

    // 3. Fallback: GiftedTech / NexOracle / DavidCyril MP3 & MP4 APIs
    const extraEndpoints = mode === 'audio'
        ? [
            `https://api.giftedtech.web.id/api/download/dlmp3?apikey=gifted&url=${encodeURIComponent(youtubeUrl)}`,
            `https://api.nexoracle.com/downloader/yt-audio2?apikey=free_key@maher_apis&url=${encodeURIComponent(youtubeUrl)}`,
            `https://apis.davidcyriltech.my.id/download/ytmp3?url=${encodeURIComponent(youtubeUrl)}`
        ]
        : [
            `https://api.giftedtech.web.id/api/download/dlmp4?apikey=gifted&url=${encodeURIComponent(youtubeUrl)}`,
            `https://api.nexoracle.com/downloader/yt-video2?apikey=free_key@maher_apis&url=${encodeURIComponent(youtubeUrl)}`,
            `https://apis.davidcyriltech.my.id/download/ytmp4?url=${encodeURIComponent(youtubeUrl)}`
        ];

    for (const ep of extraEndpoints) {
        try {
            const res = await axios.get(ep, {
                timeout: 22000,
                headers: { 'User-Agent': MOBILE_UA, Accept: 'application/json' }
            });
            const d = res.data?.result || res.data?.data || res.data || {};
            const dlUrl = d.download_url || d.downloadUrl || d.audio || d.video || d.url || d.link || '';
            if (typeof dlUrl === 'string' && /^https?:\/\//i.test(dlUrl)) {
                return { url: dlUrl, headers: {} };
            }
        } catch (err) {
            console.warn('[media] Extra YT fallback failed:', err.message);
        }
    }

    throw new Error(`Could not extract downloadable ${mode === 'audio' ? 'MP3' : 'MP4'} stream from link.`);
}

async function resolveTikTokVideo(url) {
    try {
        const res = await axios.post(
            'https://www.tikwm.com/api/',
            new URLSearchParams({ url, hd: '1' }).toString(),
            {
                timeout: 25000,
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'User-Agent': MOBILE_UA
                }
            }
        );
        const data = res.data?.data;
        const videoUrl = data?.hdplay || data?.play || data?.wmplay;
        if (videoUrl) {
            return {
                videoUrl,
                title: data?.title || 'TikTok Video'
            };
        }
    } catch (err) {
        console.warn('[media] TikWM failed:', err.message);
    }

    if (ruhendScraper && typeof ruhendScraper.ttdl === 'function') {
        const scraped = await ruhendScraper.ttdl(url);
        const videoUrl = scraped?.video?.[0] || scraped?.hd || scraped?.sd || scraped?.url;
        if (videoUrl) {
            return { videoUrl, title: scraped?.title || 'TikTok Video' };
        }
    }
    throw new Error('Could not extract downloadable TikTok video from that link.');
}

async function resolveFacebookVideo(url) {
    if (ruhendScraper && typeof ruhendScraper.fbdl === 'function') {
        const result = await ruhendScraper.fbdl(url);
        const list = Array.isArray(result?.data) ? result.data : (Array.isArray(result) ? result : []);
        const hd = list.find(item => /hd/i.test(item.resolution || item.quality || '')) || list[0];
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
            const isVideo = /\.(mp4|mov|webm)/i.test(mediaUrl) || first.type === 'video' || /\/reel\/|\/tv\//i.test(url);
            return { mediaUrl, isVideo, title: first.title || 'Instagram Media' };
        }
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
            const { buffer, contentType } = await downloadBufferFromUrl(cleanQuery);
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

        const yt = await resolveYoutubeInfo(cleanQuery);
        const stream = await fetchYoutubeStreamUrl(yt.url, 'audio');
        const { buffer } = await downloadBufferFromUrl(stream.url, stream.headers);
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
                mimetype: 'audio/mpeg',
                ptt: false,
                fileName: `${yt.title.replace(/[^\w\s-]/g, '').slice(0, 60) || 'song'}.mp3`
            }
        };
    }

    if (resolved === 'video') {
        const yt = await resolveYoutubeInfo(cleanQuery);
        const stream = await fetchYoutubeStreamUrl(yt.url, 'video');
        const { buffer } = await downloadBufferFromUrl(stream.url, stream.headers);
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
        const tt = await resolveTikTokVideo(cleanQuery);
        const { buffer } = await downloadBufferFromUrl(tt.videoUrl);
        return {
            platform: 'tiktok',
            mediaType: 'video',
            title: tt.title,
            baileysPayload: {
                video: buffer,
                mimetype: 'video/mp4',
                caption: cleanCaption
            }
        };
    }

    if (resolved === 'facebook') {
        const fb = await resolveFacebookVideo(cleanQuery);
        const { buffer } = await downloadBufferFromUrl(fb.videoUrl);
        return {
            platform: 'facebook',
            mediaType: 'video',
            title: fb.title,
            baileysPayload: {
                video: buffer,
                mimetype: 'video/mp4',
                caption: cleanCaption
            }
        };
    }

    if (resolved === 'instagram') {
        const ig = await resolveInstagramMedia(cleanQuery);
        const { buffer } = await downloadBufferFromUrl(ig.mediaUrl);
        return {
            platform: 'instagram',
            mediaType: ig.isVideo ? 'video' : 'image',
            title: ig.title,
            baileysPayload: ig.isVideo
                ? { video: buffer, mimetype: 'video/mp4', caption: cleanCaption }
                : { image: buffer, caption: cleanCaption }
        };
    }

    if (resolved === 'giphy') {
        let mp4Url = cleanQuery;
        if (!/^https?:\/\//i.test(cleanQuery)) {
            const results = await searchGiphy(cleanQuery, giphyHeaderKey);
            mp4Url = results[0]?.mp4Url;
            if (!mp4Url) throw new Error('No matching GIF found.');
        }
        const { buffer } = await downloadBufferFromUrl(mp4Url);
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

    // Direct URL fallback
    const { buffer, contentType } = await downloadBufferFromUrl(cleanQuery);
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
