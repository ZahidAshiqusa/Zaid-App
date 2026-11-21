'use strict';

const express = require('express');
const cors = require('cors');
const config = require('./src/config');
const store = require('./src/store');
const { baileysManager } = require('./src/baileysManager');
const createApiRouter = require('./src/routes');

const app = express();

app.set('trust proxy', 1);
app.use(cors({
    origin: config.corsOrigin === '*' ? true : config.corsOrigin.split(',').map(s => s.trim()),
    credentials: true
}));
app.use(express.json({ limit: '100mb' }));
app.use(express.urlencoded({ extended: true, limit: '100mb' }));

// Root status endpoint
app.get('/', (req, res) => {
    res.json({
        name: `${config.appName} Backend`,
        status: 'online',
        uptime: baileysManager.getUptimeFormatted(),
        connectedSessions: baileysManager.getConnectedSessions().length,
        postgresConnected: store.dbReady,
        health: '/api/health'
    });
});

// Mount all native Android chat & Baileys API endpoints
app.use('/api', createApiRouter());

// Global JSON error handler
app.use((err, req, res, next) => {
    console.error('[server] Unhandled error:', err.message);
    res.status(err.status || 500).json({
        success: false,
        error: err.message || 'Internal server error'
    });
});

async function bootstrap() {
    await store.initPostgres();
    await baileysManager.autoResumeSavedSessions();

    app.listen(config.port, '0.0.0.0', () => {
        console.log(`[server] ${config.appName} Railway Backend listening on port ${config.port}`);
    });
}

process.on('SIGTERM', () => {
    store.saveLocalNow();
    process.exit(0);
});

process.on('SIGINT', () => {
    store.saveLocalNow();
    process.exit(0);
});

bootstrap().catch(err => {
    console.error('[server] Fatal startup error:', err);
    process.exit(1);
});
