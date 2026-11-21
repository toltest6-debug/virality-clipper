# Virality Clipper

AI-powered YouTube-to-Shorts viral clipping, in one codebase:

- **Backend** (FastAPI + yt-dlp + ffmpeg): downloads a YouTube video, extracts the timed transcript, uses an LLM (OpenAI / Gemini / Anthropic — or an offline heuristic fallback) to pick the 3–5 most viral self-contained moments, then renders each as a 9:16 / 16:9 / 1:1 MP4 with burned-in ASS captions, a thumbnail, and streaming download endpoints.
- **Android app** (native Kotlin + Jetpack Compose, Material 3 dark "Creator Studio" theme): paste a YouTube URL or use YouTube's **Share → Virality Clipper**, watch the job progress live, play each short inline with ExoPlayer, and download/share each Short with one tap.

---

## Repository layout

```
ViralityClipper/
├── app/                          # Android application (Kotlin + Compose)
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml   # ACTION_SEND Share intent + MAIN/LAUNCHER
│       ├── java/com/viralityclipper/app/
│       │   ├── ViralityClipperApp.kt     # Application + manual DI container
│       │   ├── MainActivity.kt
│       │   ├── data/             # ApiClient, SettingsPreferences, MediaStoreSaver, …
│       │   ├── ui/               # Screens, components, theme, viewmodels
│       │   └── ui/viewmodels/    # ViewModelFactory + Home/Job/Saved/Settings
│       └── res/                  # Vector drawables (all icons), adaptive launcher icons
├── backend/
│   ├── main.py                   # FastAPI app (jobs, clips, health)
│   ├── ai_analyzer.py            # Transcripts → viral-moment candidates (LLM or heuristic)
│   ├── video_processor.py        # Transcript fetch, source download, ffmpeg render
│   ├── requirements.txt
│   ├── Dockerfile
│   ├── .dockerignore
│   └── .env.example
├── .github/workflows/build-apk.yml
├── build.gradle.kts              # Root build (apply-false plugins)
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/               # gradle-8.10.2 wrapper
├── gradlew / gradlew.bat
└── README.md
```

---

## Android app

**Stack**: Kotlin 2.0.21, Gradle 8.10.2, AGP 8.7.2, Jetpack Compose (BOM 2024.10.01), Material 3, Navigation Compose, OkHttp 4.12.0, DataStore Preferences 1.1.1, Media3 ExoPlayer 1.4.1, minSdk 29 / targetSdk 35.

### Features

1. **Link input + share sheet** — YouTube URL input with paste-from-clipboard, clip count (3/5), target length (30/45/60s), aspect ratio, burn-captions toggle, and a gradient **Generate Viral Clips** CTA. The manifest registers `ACTION_SEND` (`text/plain`) so tapping **Share** on any YouTube video hands the URL straight to the app, fills the field and auto-starts the job with a small "Shared link detected" banner.
2. **Live job tracker + results feed** — animated 4-step progress card (Transcript → AI analysis → Render → Ready), live progress bar, virality score badge (`94/100 · Viral`), AI hook title, duration, inline ExoPlayer preview, and one-tap **Save to Gallery** (MediaStore → `Movies/ViralityClipper`) and **Share** (Intent.ACTION_SEND) buttons per clip.
3. **Saved Shorts vault** — grid-style list of every saved short (name, duration, size), delete with confirmation, share, and a refresh button.
4. **Settings** — backend URL with **Test Connection**, AI provider + API key (optional, never stored on the backend, only on-device in DataStore), defaults for clip count / length / aspect ratio / captions, and the app version.

### Building the APK locally

You need JDK 17+ and an Android SDK with API 35 (e.g. `ANDROID_HOME=/path/to/sdk`, or run with Android Studio and open the repo):

```bash
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

### Building the APK with GitHub Actions

The committed workflow `.github/workflows/build-apk.yml` builds the APK on every push/PR using the pre-installed Android SDK on `ubuntu-latest` (no setup-android action needed) and uploads it as a `virality-clipper-debug-apk` artifact:

1. Push this repository to GitHub.
2. Open the **Actions** tab → the **Build APK** run appears automatically.
3. Download the artifact (`app-debug.apk`) from the run.

---

## Backend API

Base URL: whatever you deploy it at (e.g. `https://virality-clipper.onrender.com`).

| Endpoint | Description |
|---|---|
| `GET /api/health` | Liveness check `{status, version, jobs_running}` |
| `POST /api/jobs` | Create a job: `{youtube_url, clip_count (3-5), target_duration_sec (30-60), aspect_ratio ("9:16"|"16:9"|"1:1"), burn_captions, api_key?, provider?}` → `{job_id}` |
| `GET /api/jobs/{job_id}` | Job status + clips list: `status` (`queued`/`transcript`/`ai_analyzing`/`rendering`/`completed`/`failed`), `progress_pct`, `clips[]` (`clip_id, title, hook_caption, virality_score, start_sec, end_sec, duration_sec, download_url, thumbnail_url, status, error`), `error` |
| `GET /api/clips/{clip_id}.mp4` | Streams the rendered MP4 (Range-capable, ExoPlayer-ready) |
| `GET /api/clips/{clip_id}.jpg` | Poster-frame thumbnail |

### Pipeline

1. `youtube-transcript-api` (falls back to yt-dlp auto-subtitles) pulls the timed transcript.
2. New LLM client call per transcript chunk (structured JSON prompt) asks for the top N self-contained moments; results are validated, clamped, de-overlapped, and scored. **If no LLM key is configured** (or the provider fails), the job degrades to a deterministic heuristic scorer and says so (`ai_mode: "heuristic"`).
3. Source video is downloaded once (≤1080p, best mp4 + best audio, merged), and each clip is cut with ffmpeg: cover-fit scale + center-crop to the target aspect ratio + optional ASS caption burn-in + thumbnail extraction.
4. Source files are deleted right after rendering; only clips + thumbnails remain.

---

## Deploying the backend (1 click)

The backend is a plain Docker image; the same Dockerfile works on Render / Railway / Fly.io.

### Render (one-click via Blueprint)

1. In Render, click **New → Blueprint**, point it at this repo (or **New → Web Service** with a Docker build).
2. Environment variables (Settings → Environment):
   - `AI_PROVIDER` (optional): `openai` / `gemini` / `anthropic` / blank for auto
   - `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `GEMINI_API_KEY` (pick the one you use)
   - `OPENAI_MODEL`, `ANTHROPIC_MODEL`, `GEMINI_MODEL` (optional overrides)
   - Leave `PORT` unset — Render sets it for you
3. Deploy → health check: `GET /api/health` returns 200.

### Railway

1. New project → **Deploy from GitHub repo** → root directory `backend/`.
2. Railway detects `backend/Dockerfile` and builds it.
3. Add the same env vars under the service's **Variables** tab and redeploy.

### Fly.io

```bash
fly launch --dockerfile backend/Dockerfile --copy-config
fly secrets set GEMINI_API_KEY=...           # or OPENAI_API_KEY / ANTHROPIC_API_KEY
fly deploy
```

Then point the Android app at your deployed URL (Settings → Backend Server URL).

**Important:** the backend runs in a **single-process** in-memory job store. For multiple replicas, replace `_JOBS` (`backend/main.py`) with Redis/Postgres-backed storage and a shared results bucket.

---

## Android app — backend settings

After deploying:

1. Open the app → **Settings**.
2. **Backend Server URL** — your Render/Railway/Fly URL (e.g. `https://virality-clipper.onrender.com`).
3. **Test Connection** — should turn green (health check).
4. Optionally add an AI provider + API key for smarter clip selection. If omitted, the app still works with the heuristic fallback.

---

## CI / CD

`.github/workflows/build-apk.yml` — verify APK build:

```
.github/workflows/build-apk.yml
```

See the Actions tab in GitHub for live status, artifacts and logs.

---

## Security & privacy notes

- An optional `api_key` passed from the app is used only for that job request and is never persisted or written anywhere except the in-memory job state (only in the job JSON for the requesting client). The backend logs never print it.
- The APK stores the backend URL, AI provider and API key in on-device DataStore only — nothing leaves the phone except through the API calls described above.
- TikTok / Instagram Reels / YouTube Shorts are uploaded via the system share sheet with `Intent.ACTION_SEND`; the app does not upload directly to those platforms (no API keys required).
- Media files stay on-device (`Movies/ViralityClipper`).
- Shared links are handled via `ACTION_SEND` with `text/plain`; nothing is read beyond that intent.
- `usesCleartextTraffic=true` is required for LAN development backends (e.g. `http://192.168.x.x:8000`). For production, deploy the backend behind HTTPS and remove that flag.
