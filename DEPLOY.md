# Deploy Guide

One-click hosted demo so judges don't need local setup.

---

## Backend → Railway (Free Tier)

1. Push this repo to GitHub.
2. Go to [railway.app](https://railway.app) → **New Project** → **Deploy from GitHub**.
3. Select the repo. Railway auto-detects the `backend/Dockerfile`.
4. **Settings → Variables**, add:
   | Variable | Value |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `dev` |
   | `PORT` | `8080` (Railway sets this automatically) |
   | `FRONTEND_ORIGIN` | `*` (update after frontend deploy — see below) |
5. Wait for deploy to finish. Railway gives you a URL like `https://your-app.up.railway.app`.
6. Test: visit `https://your-app.up.railway.app/api/metrics` — should return JSON.
7. **Optional**: set `ANTHROPIC_API_KEY` and `LLM_ENABLED=true` to enable the LLM *explanation* layer. The deterministic Next-Best-Action engine always chooses the action, so the app runs identically with or without a key.

---

## Frontend → Vercel (Free Tier)

1. Go to [vercel.com](https://vercel.com) → **New Project** → **Import** the GitHub repo.
2. **Framework Preset**: Vite (auto-detected).
3. **Root Directory**: `frontend/` (set this in Vercel's project settings).
4. **Environment Variables**, add:
   | Variable | Value |
   |---|---|
   | `VITE_API_BASE` | `https://your-app.up.railway.app` (your Railway URL from above) |
5. Deploy. Vercel gives you a URL like `https://your-app.vercel.app`.

---

## Post-Deploy: Lock Down CORS

1. Go back to Railway → your service → **Variables**.
2. Set `FRONTEND_ORIGIN` to your exact Vercel URL (e.g. `https://your-app.vercel.app`).
3. This locks CORS to only your frontend — the default `*` is fine for local dev but should be tightened in production.

---

## Health Checks

The backend exposes a Spring Boot Actuator health endpoint at `/actuator/health`.

- **Railway**: Set the health check path to `/actuator/health` in Service Settings → Networking → Health Check Path.
- **Render**: Set the health check path to `/actuator/health` in Service Settings.

This endpoint returns `{"status":"UP"}` when the app is healthy, which keeps the service alive on free-tier platforms that auto-sleep after idle.

---

## Verify the Deployed App

1. Open the Vercel URL in a fresh incognito browser.
2. The dashboard should load with stat cards populating from `/api/metrics`.
3. Click **"Run Batch ▶"** — the batch should complete and all charts/tables should update.
4. Check browser DevTools → **Network** tab: no CORS errors, all API calls return 200.
5. Check browser DevTools → **Console** tab: zero errors.
6. Navigate through all 8 sidebar sections — each should show real data.

### Common Issues

| Problem | Fix |
|---|---|
| "Backend not reachable" banner | Check `VITE_API_BASE` env var in Vercel matches your Railway URL exactly |
| CORS errors in console | Set `FRONTEND_ORIGIN` in Railway to your Vercel URL |
| CORS still failing after setting origin | Make sure the Railway URL has re-deployed after changing the env var |
| Stat cards show 0 | Run a batch first — data is seeded on backend startup, but metrics need a batch run |
| Railway app sleeps on free tier | First request after idle may take 30-60s to wake up |

---

## Local Development (No Deploy Needed)

```bash
# Backend
cd backend
mvn spring-boot:run
# → http://localhost:8080

# Frontend
cd frontend
npm install
npm run dev
# → http://localhost:5173
```

No environment variables needed — defaults to H2 in-memory, engine-decided recovery (LLM explanation off), CORS `*`.
