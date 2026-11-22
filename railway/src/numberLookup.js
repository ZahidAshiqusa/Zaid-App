'use strict';

const axios = require('axios');
const config = require('./config');

const UA = 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36';

function detectPakOperator(digits) {
    let local = String(digits || '').replace(/\D/g, '');
    if (local.startsWith('92')) local = '0' + local.slice(2);
    if (!local.startsWith('03')) return '';
    const prefix3 = local.slice(0, 3);
    const prefix4 = local.slice(0, 4);
    if (prefix4 === '0355') return 'SCOM (SCO)';
    if (prefix3 === '030' || prefix3 === '032') return 'Jazz / Warid';
    if (prefix3 === '031') return 'Zong (CMPak)';
    if (prefix3 === '033') return 'Ufone (PTML)';
    if (prefix3 === '034') return 'Telenor Pakistan';
    return 'Pakistan Mobile';
}

function normalizeFormats(rawInput) {
    let digits = String(rawInput || '').replace(/\D/g, '');
    if (digits.startsWith('00')) digits = digits.slice(2);
    let international = digits;
    let localPak = digits;
    if (digits.startsWith('0') && digits.length === 11) {
        international = '92' + digits.slice(1);
        localPak = digits;
    } else if (digits.startsWith('92') && digits.length === 12) {
        international = digits;
        localPak = '0' + digits.slice(2);
    }
    const shortTen = international.startsWith('92') && international.length === 12
        ? international.slice(2)
        : (localPak.startsWith('0') ? localPak.slice(1) : digits);
    return { digits, international, localPak, shortTen };
}

async function queryTruecaller({ international, localPak }) {
    const candidates = [];

    // 1. Custom configured Truecaller API URL (Railway Variable)
    if (config.truecallerApiUrl) {
        const url = config.truecallerApiUrl.includes('{number}')
            ? config.truecallerApiUrl.replace('{number}', encodeURIComponent(international))
            : `${config.truecallerApiUrl}${config.truecallerApiUrl.includes('?') ? '&' : '?'}number=${encodeURIComponent(international)}`;
        const headers = { 'User-Agent': UA, Accept: 'application/json' };
        if (config.truecallerToken) {
            headers.Authorization = `Bearer ${config.truecallerToken}`;
        }
        candidates.push({ url, headers });
    }

    // 2. Official Truecaller v2 search when installation token is configured
    if (config.truecallerToken) {
        candidates.push({
            url: `https://search5-noneu.truecaller.com/v2/search?q=${encodeURIComponent('+' + international)}&countryCode=PK&type=4&locAddr=&placement=SEARCHRESULTS,HISTORY,DETAILS&encoding=json`,
            headers: {
                Authorization: `Bearer ${config.truecallerToken}`,
                'User-Agent': 'Truecaller/13.42.7 (Android;14)',
                Accept: 'application/json'
            }
        });
    }

    // 3. Public Truecaller lookup APIs used in Queen-Riam / WhatsApp suites
    candidates.push(
        {
            url: `https://api.nexoracle.com/details/truecaller?apikey=free_key@maher_apis&q=${encodeURIComponent(international)}`,
            headers: { 'User-Agent': UA, Accept: 'application/json' }
        },
        {
            url: `https://truecaller-api.vercel.app/api?number=${encodeURIComponent('+' + international)}`,
            headers: { 'User-Agent': UA, Accept: 'application/json' }
        },
        {
            url: `https://api.giftedtech.web.id/api/tools/truecaller?apikey=gifted&number=${encodeURIComponent(international)}`,
            headers: { 'User-Agent': UA, Accept: 'application/json' }
        }
    );

    for (const req of candidates) {
        try {
            const res = await axios.get(req.url, { headers: req.headers, timeout: 10000 });
            const raw = res.data;
            const node = raw?.result || raw?.data?.[0] || raw?.data || raw;
            if (!node || typeof node !== 'object') continue;

            const name =
                node.name ||
                node.fullName ||
                node.owner ||
                node.Name ||
                node.title ||
                '';
            const carrier =
                node.carrier ||
                node.phones?.[0]?.carrier ||
                node.operator ||
                node.network ||
                detectPakOperator(localPak);
            const country =
                node.country ||
                node.countryCode ||
                node.addresses?.[0]?.countryCode ||
                (international.startsWith('92') ? 'Pakistan (PK)' : '');
            const address =
                node.address ||
                node.city ||
                node.addresses?.[0]?.city ||
                node.location ||
                '';
            const email =
                node.email ||
                node.internetAddresses?.[0]?.id ||
                '';
            const spamScore =
                node.spamScore !== undefined ? String(node.spamScore) : '';

            if (name || address || email || carrier) {
                return {
                    found: Boolean(name),
                    name: String(name || '').trim(),
                    carrier: String(carrier || '').trim(),
                    country: String(country || '').trim(),
                    address: String(address || '').trim(),
                    email: String(email || '').trim(),
                    spamScore: spamScore.trim()
                };
            }
        } catch (_) {
            // Try next API
        }
    }

    return {
        found: false,
        name: '',
        carrier: detectPakOperator(localPak),
        country: international.startsWith('92') ? 'Pakistan (PK)' : '',
        address: '',
        email: '',
        spamScore: ''
    };
}

function normalizeSimRecords(payload, fallbackNetwork) {
    let list = [];
    if (Array.isArray(payload)) {
        list = payload;
    } else if (Array.isArray(payload?.result)) {
        list = payload.result;
    } else if (Array.isArray(payload?.data)) {
        list = payload.data;
    } else if (Array.isArray(payload?.records)) {
        list = payload.records;
    } else if (payload?.result && typeof payload.result === 'object') {
        list = [payload.result];
    } else if (payload?.data && typeof payload.data === 'object') {
        list = [payload.data];
    } else if (payload && typeof payload === 'object' && (payload.name || payload.Name || payload.cnic || payload.CNIC)) {
        list = [payload];
    }

    return list
        .map(item => ({
            name: String(item.name || item.Name || item.full_name || item.owner || '').trim(),
            number: String(item.number || item.Number || item.mobile || item.phone || item.cell || '').trim(),
            cnic: String(item.cnic || item.CNIC || item.id_card || '').trim(),
            address: String(item.address || item.Address || item.city || '').trim(),
            network: String(item.network || item.operator || item.sim || fallbackNetwork || '').trim()
        }))
        .filter(r => r.name || r.cnic || r.address);
}

async function querySimDatabase({ localPak, shortTen, digits }) {
    const queryKey = shortTen.length === 10 ? shortTen : (localPak || digits);
    const fallbackNetwork = detectPakOperator(localPak);
    const endpoints = [];

    if (config.simDbApiUrl) {
        const url = config.simDbApiUrl.includes('{number}')
            ? config.simDbApiUrl.replace('{number}', encodeURIComponent(queryKey))
            : `${config.simDbApiUrl}${config.simDbApiUrl.includes('?') ? '&' : '?'}num=${encodeURIComponent(queryKey)}`;
        const headers = { 'User-Agent': UA, Accept: 'application/json' };
        if (config.simDbApiKey) headers['x-api-key'] = config.simDbApiKey;
        endpoints.push({ url, headers });
    }

    endpoints.push(
        {
            url: `https://api.nexoracle.com/details/pak-sim-database-free?apikey=free_key@maher_apis&key=${encodeURIComponent(queryKey)}`,
            headers: { 'User-Agent': UA, Accept: 'application/json' }
        },
        {
            url: `https://fam-official.serv00.net/sim/api.php?num=${encodeURIComponent(localPak)}`,
            headers: { 'User-Agent': UA, Accept: 'application/json' }
        },
        {
            url: `https://api.giftedtech.web.id/api/tools/simdata?apikey=gifted&number=${encodeURIComponent(localPak)}`,
            headers: { 'User-Agent': UA, Accept: 'application/json' }
        }
    );

    for (const ep of endpoints) {
        try {
            const res = await axios.get(ep.url, { headers: ep.headers, timeout: 10000 });
            const records = normalizeSimRecords(res.data, fallbackNetwork);
            if (records.length > 0) {
                return { found: true, records };
            }
        } catch (_) {
            // Try next SIM DB API
        }
    }

    return { found: false, records: [] };
}

async function lookupNumberDetails(rawNumber, sock, store) {
    const formats = normalizeFormats(rawNumber);
    if (!formats.international || formats.international.length < 7) {
        throw new Error('Please provide a valid phone number or CNIC.');
    }

    const jid = `${formats.international}@s.whatsapp.net`;

    // Query Truecaller, SIM Database, and WhatsApp profile in parallel for speed
    const [truecaller, simDb, waInfo] = await Promise.all([
        queryTruecaller(formats),
        querySimDatabase(formats),
        (async () => {
            const info = {
                registered: false,
                about: '',
                profilePicUrl: '',
                contactName: store?.state?.contacts?.[jid]?.name || ''
            };
            if (!sock) return info;
            try {
                if (typeof sock.onWhatsApp === 'function') {
                    const check = await sock.onWhatsApp(formats.international);
                    info.registered = Boolean(check?.[0]?.exists);
                }
            } catch (_) {}
            try {
                if (typeof sock.fetchStatus === 'function') {
                    const st = await sock.fetchStatus(jid);
                    info.about = String(st?.status || st?.[0]?.status?.status || '').trim();
                }
            } catch (_) {}
            try {
                if (typeof sock.profilePictureUrl === 'function') {
                    info.profilePicUrl = await sock.profilePictureUrl(jid, 'image');
                }
            } catch (_) {}
            return info;
        })()
    ]);

    return {
        success: true,
        query: formats.international,
        formattedNumber: `+${formats.international}`,
        localNumber: formats.localPak,
        operator: truecaller.carrier || simDb.records[0]?.network || detectPakOperator(formats.localPak),
        truecaller,
        simRecords: simDb.records,
        whatsapp: waInfo
    };
}

module.exports = {
    lookupNumberDetails,
    detectPakOperator
};
