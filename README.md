# Zaid Ashiq Chat

Native Android (Kotlin + Jetpack Compose) WhatsApp messaging application backed by a standalone deployable **Railway Baileys Backend** (`railway/`).

## Features

- **Instant Baileys Pairing**: Enter your phone number on launch, receive an 8-digit WhatsApp pairing code, copy & link in WhatsApp (*Linked Devices → Link with phone number*), and enter the messenger automatically.
- **5-Tab Native Messenger Interface**: **CHATS**, **STATUS**, **CALLS**, **GROUPS**, and **CHANNELS** with live search, unread badges, and new chat composer.
- **Floating `+` Power Menu (10 Native Tools)**:
  - **Video Note** (circular WhatsApp PTV)
  - **Voice Note** (live mic recorder & audio picker PTT)
  - **Fake Location** (custom GPS pin / live location presets)
  - **Button Messages** (dedicated interactive button message composer)
  - **Song**, **Video**, **TikTok**, **Facebook**, **Instagram**, and **Downloader** (+ **Giphy GIFs**) — downloads media server-side on Railway and delivers the **actual audio/video/image/GIF file** directly into WhatsApp, never raw links.
- **Top 3-Dot Automation Suite**: Anti-Delete & View-Once Vault, Auto-Status View & React, Full Profile Picture Tool (no-crop HD DP), Channel Auto-React Rules, and Session & Connection Diagnostics.

## Quick Setup

### 1. Deploy the Standalone Railway Backend (`railway/`)
1. Connect this repository to **Railway**.
2. Set **Root Directory** to `railway`.
3. Add your backend variables in Railway (`DATABASE_URL`, `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_SECRET_KEY`, `SUPABASE_DB_URL`, `GIPHY_API_KEY`, `CORS_ORIGIN=*`).
4. Generate a Railway Public Domain (e.g. `https://your-app.up.railway.app`).

### 2. Configure Secrets in Google AI Studio
Open the **Secrets** panel in AI Studio and set:
- `RAILWAY_BACKEND_URL` — your Railway public domain URL
- `SUPABASE_URL` & `SUPABASE_PUBLISHABLE_KEY`
- `GIPHY_API_KEY`
