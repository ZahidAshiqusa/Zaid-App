# Zaid Ashiq Chat — Railway Backend

Clean, dedicated Node.js + Baileys v7 WhatsApp backend engineered 100% for the **Zaid Ashiq Chat** Android app.

## Deploy on Railway (2 Minutes)

1. Push this repository to GitHub (or upload the `railway/` folder as its own repo).
2. In **Railway Dashboard**, click **New Project → Deploy from GitHub Repo**.
3. If deploying from the full repository, go to **Settings → Root Directory** and set it to:
   ```
   railway
   ```
4. Open **Variables** and add:
   - `DATABASE_URL` — Railway PostgreSQL (or Supabase Postgres) connection string for persistent WhatsApp sessions across redeploys.
   - `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_SECRET_KEY`, `SUPABASE_DB_URL` *(optional)*.
   - `GIPHY_API_KEY` — your Giphy API key for GIF search & send.
   - `OWNER_NUMBER` — your WhatsApp number with country code (e.g. `923001234567`).
   - `TIMEZONE` — e.g. `Asia/Karachi`.
5. Go to **Settings → Networking → Generate Domain** to get your public HTTPS URL (e.g. `https://zaid-ashiq-chat.up.railway.app`).
6. Verify deployment by visiting `https://<your-domain>/api/health` — it returns `{"status":"ok", ...}`.

## Connect to Android App

Add your deployed Railway URL to the **AI Studio Secrets panel** as `RAILWAY_BACKEND_URL` (plus `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `GIPHY_API_KEY`) and rebuild the APK.
