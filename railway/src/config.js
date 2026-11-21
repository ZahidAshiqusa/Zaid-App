'use strict';

const path = require('path');
const fs = require('fs');
require('dotenv').config();

const ROOT_DIR = path.resolve(__dirname, '..');
const DATA_DIR = path.join(ROOT_DIR, 'data');
const SESSIONS_DIR = path.join(DATA_DIR, 'sessions');
const TMP_DIR = path.join(ROOT_DIR, 'tmp');

for (const dir of [DATA_DIR, SESSIONS_DIR, TMP_DIR]) {
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
    }
}

function cleanEnv(name, fallback = '') {
    const raw = process.env[name];
    if (raw === undefined || raw === null) return fallback;
    return String(raw).trim().replace(/^['"]|['"]$/g, '').trim() || fallback;
}

const databaseUrl = cleanEnv('DATABASE_URL') || cleanEnv('SUPABASE_DB_URL');

module.exports = {
    ROOT_DIR,
    DATA_DIR,
    SESSIONS_DIR,
    TMP_DIR,
    port: parseInt(cleanEnv('PORT', '3000'), 10) || 3000,
    corsOrigin: cleanEnv('CORS_ORIGIN', '*'),
    appName: cleanEnv('APP_NAME', 'Zaid Ashiq Chat'),
    ownerNumber: cleanEnv('OWNER_NUMBER', '').replace(/\D/g, ''),
    timezone: cleanEnv('TIMEZONE', 'Asia/Karachi'),
    databaseUrl,
    supabaseUrl: cleanEnv('SUPABASE_URL'),
    supabasePublishableKey: cleanEnv('SUPABASE_PUBLISHABLE_KEY'),
    supabaseSecretKey: cleanEnv('SUPABASE_SECRET_KEY'),
    giphyApiKey: cleanEnv('GIPHY_API_KEY')
};
