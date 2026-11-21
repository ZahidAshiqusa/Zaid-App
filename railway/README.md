# Zaid Ashiq Chat — Standalone Railway Baileys Backend

Deploy this `railway/` folder directly to **Railway** to power the **Zaid Ashiq Chat** native Android application 24/7 via WhatsApp Baileys.

## 1. Deploy to Railway

1. Create a new project on [Railway](https://railway.app) and connect your GitHub repository.
2. Open **Service Settings → Source → Root Directory** and set it to:
   ```
   railway
   ```
3. Railway automatically detects `Dockerfile` and `railway.json` (Node.js 22 + `ffmpeg` + `/api/health`).
4. (Recommended) Attach a **Railway Volume** mounted at `/app/data` so WhatsApp sessions and Vault captures survive redeploys.

## 2. Configure Variables in Railway

In Railway **Variables**, add:
- `DATABASE_URL` — Your PostgreSQL connection string
- `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_SECRET_KEY`, `SUPABASE_DB_URL` — For cloud session sync
- `GIPHY_API_KEY` — Enables GIF search and send inside the Android app
- `OWNER_NUMBER` — Your WhatsApp number with country code (digits only)
- `CORS_ORIGIN=*`

## 3. Connect Your Android App

1. Generate a Public Domain in Railway (**Settings → Networking → Generate Domain**), e.g. `https://zaid-ashiq-backend.up.railway.app`.
2. In **Google AI Studio → Secrets Panel**, set:
   - `RAILWAY_BACKEND_URL` = your Railway public URL
   - `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `GIPHY_API_KEY`
3. Open **Zaid Ashiq Chat** on Android, enter your phone number, copy the 8-digit pairing code into WhatsApp (*Linked Devices → Link with phone number*), and start chatting!
