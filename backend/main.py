"""
Virality Clipper backend.

Pipeline: YouTube URL -> transcript (youtube-transcript-api, yt-dlp fallback)
-> AI viral-moment detection (OpenAI / Gemini / Anthropic, heuristic fallback)
-> yt-dlp source download -> ffmpeg 9:16/16:9/1:1 render + ASS caption burn-in
-> thumbnails + streaming MP4 downloads.

API:
  GET  /api/health
  POST /api/jobs
  GET  /api/jobs/{job_id}
  GET  /api/clips/{clip_id}.mp4   (Range-capable via FileResponse)
  GET  /api/clips/{clip_id}.jpg
"""

import logging
import os
import re
import shutil
import threading
import time
import uuid
from pathlib import Path
from typing import Any, Dict, List, Optional

from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel, Field, field_validator

import ai_analyzer
from ai_analyzer import TranscriptSegment  # noqa: F401  (kept for type clarity)
from video_processor import (
    ASPECT_TARGETS,
    download_source_video,
    extract_video_id,
    fetch_metadata,
    fetch_transcript,
    make_thumbnail,
    render_clip,
    validate_youtube_url,
    build_ass_captions,
)

logger = logging.getLogger("virality")
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")

BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = Path(os.environ.get("DATA_DIR", str(BASE_DIR / "data"))).resolve()
CLIPS_DIR = DATA_DIR / "clips"
JOBS_DIR = DATA_DIR / "jobs"
CLIPS_DIR.mkdir(parents=True, exist_ok=True)
JOBS_DIR.mkdir(parents=True, exist_ok=True)

APP_VERSION = "1.0.0"
MAX_CLIP_COUNT = 5
MIN_CLIP_COUNT = 3
MIN_TARGET_SEC = 30
MAX_TARGET_SEC = 60
ASPECT_RATIOS = ("9:16", "16:9", "1:1")

# ---------------------------------------------------------------------------
# In-process job store (single-process; swap for Redis for multi-instance)
# ---------------------------------------------------------------------------

_JOBS: Dict[str, Dict[str, Any]] = {}
_JOB_LOCK = threading.Lock()


def _update_job(job_id: str, **changes) -> Optional[Dict[str, Any]]:
    with _JOB_LOCK:
        job = _JOBS.get(job_id)
        if job is None:
            return None
        job.update(changes)
        return job


def _get_job(job_id: str) -> Optional[Dict[str, Any]]:
    with _JOB_LOCK:
        job = _JOBS.get(job_id)
        return dict(job) if job else None


# Clip status transitions are always under _JOB_LOCK via _update_job.


# ---------------------------------------------------------------------------
# FastAPI app
# ---------------------------------------------------------------------------

app = FastAPI(
    title="Virality Clipper",
    description="AI YouTube-to-Shorts Viral Clipping backend.",
    version=APP_VERSION,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ---------------------------------------------------------------------------
# Schemas
# ---------------------------------------------------------------------------


class JobRequest(BaseModel):
    youtube_url: str = Field(..., min_length=1, max_length=2000)
    clip_count: int = Field(default=3, ge=MIN_CLIP_COUNT, le=MAX_CLIP_COUNT)
    target_duration_sec: int = Field(default=45, ge=MIN_TARGET_SEC, le=MAX_TARGET_SEC)
    aspect_ratio: str = Field(default="9:16")
    burn_captions: bool = True
    api_key: Optional[str] = Field(default=None, max_length=400)
    provider: Optional[str] = Field(default=None, max_length=20)

    @field_validator("aspect_ratio")
    @classmethod
    def _check_ratio(cls, v: str) -> str:
        v = (v or "9:16").strip()
        if v not in ASPECT_RATIOS:
            raise ValueError("aspect_ratio must be one of '9:16', '16:9', '1:1'")
        return v

    @field_validator("youtube_url")
    @classmethod
    def _check_url(cls, v: str) -> str:
        v = v.strip()
        if not validate_youtube_url(v):
            raise ValueError("Not a recognizable YouTube URL")
        return v


class JobResponse(BaseModel):
    job_id: str


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _clip_to_json(clip: Dict[str, Any]) -> Dict[str, Any]:
    clip_id = clip.get("clip_id") or ""
    return {
        "clip_id": clip_id,
        "title": clip.get("title", ""),
        "hook_caption": clip.get("hook_caption", ""),
        "virality_score": clip.get("virality_score", 0),
        "start_sec": clip.get("start_sec", 0.0),
        "end_sec": clip.get("end_sec", 0.0),
        "duration_sec": clip.get("duration_sec", 0.0),
        "download_url": f"/api/clips/{clip_id}.mp4" if clip_id else None,
        "thumbnail_url": f"/api/clips/{clip_id}.jpg" if clip_id else None,
        "status": clip.get("status", "pending"),
        "error": clip.get("error"),
    }


def _job_to_json(job: Dict[str, Any]) -> Dict[str, Any]:
    clips = [_clip_to_json(c) for c in job.get("clips", [])]
    return {
        "id": job.get("id"),
        "status": job.get("status", "queued"),
        "progress_pct": job.get("progress_pct", 0),
        "youtube_url": job.get("youtube_url"),
        "video_title": job.get("video_title"),
        "video_duration": job.get("video_duration"),
        "clip_count": job.get("clip_count"),
        "target_duration_sec": job.get("target_duration_sec"),
        "aspect_ratio": job.get("aspect_ratio"),
        "burn_captions": job.get("burn_captions"),
        "provider": job.get("provider"),
        "ai_mode": job.get("ai_mode"),
        "clips": clips,
        "error": job.get("error"),
        "created_at": job.get("created_at"),
        "updated_at": job.get("updated_at"),
        "elapsed_sec": (time.time() - job["created_at_ts"]) if job.get("created_at_ts") else None,
    }


# ---------------------------------------------------------------------------
# Background job processing
# ---------------------------------------------------------------------------


def _clip_id() -> str:
    return f"clip_{uuid.uuid4().hex[:12]}"


def _update_clip(job_id: str, clip_id: str, **changes) -> None:
    with _JOB_LOCK:
        job = _JOBS.get(job_id)
        if job:
            for c in job.get("clips", []):
                if c.get("clip_id") == clip_id:
                    c.update(changes)
                    break
            job["updated_at"] = time.time()


def process_job(job_id: str) -> None:
    job = _JOBS.get(job_id)
    if not job:
        return

    tmp_dir: Optional[Path] = None
    try:
        tmp_dir = JOBS_DIR / job_id
        tmp_dir.mkdir(parents=True, exist_ok=True)
        _update_job(job_id, status="transcript", progress_pct=10, tmp_dir=str(tmp_dir), updated_at=time.time())

        # ---- transcript ----------------------------------------------------
        url = job["youtube_url"]
        video_id = extract_video_id(url)
        if not video_id:
            raise RuntimeError("Could not extract video ID from URL")

        meta = fetch_metadata(url)
        duration = meta.get("duration")
        _update_job(job_id, video_title=meta.get("title"), video_duration=duration, updated_at=time.time())

        transcript = fetch_transcript(video_id, tmp_dir / "subs", url)
        if not transcript:
            raise RuntimeError("Transcript came back empty")

        # ---- AI analysis ---------------------------------------------------
        _update_job(job_id, status="ai_analyzing", progress_pct=35, updated_at=time.time())
        analysis = ai_analyzer.analyze(
            segments=transcript,
            video_duration=duration,
            clip_count=job["clip_count"],
            target_sec=job["target_duration_sec"],
            provider=job.get("provider"),
            api_key=job.get("api_key"),
        )
        clips = analysis.get("clips", [])
        ai_mode = analysis.get("ai_mode", "heuristic")
        provider_used = analysis.get("provider", "heuristic")

        if not clips:
            raise RuntimeError("No viral moments could be detected in this video")

        _update_job(
            job_id,
            provider=provider_used,
            ai_mode=ai_mode,
            clips=[
                {
                    "clip_id": _clip_id(),
                    "title": c.title,
                    "hook_caption": c.hook_caption,
                    "virality_score": c.virality_score,
                    "start_sec": c.start_sec,
                    "end_sec": c.end_sec,
                    "duration_sec": c.duration_sec,
                    "status": "pending",
                    "error": None,
                }
                for c in clips
            ],
            updated_at=time.time(),
        )

        # ---- download source ----------------------------------------------
        _update_job(job_id, status="rendering", progress_pct=40, updated_at=time.time())
        source = download_source_video(url, tmp_dir)

        # ---- render clips --------------------------------------------------
        stored = [dict(c) for c in _JOBS.get(job_id)["clips"]]
        for idx, clip in enumerate(stored):
            clip_id = clip["clip_id"]
            clip_url = CLIPS_DIR / f"{clip_id}.mp4"
            thumb_url = CLIPS_DIR / f"{clip_id}.jpg"
            ass_path = tmp_dir / f"ass_{idx}.ass"
            try:
                if job["burn_captions"]:
                    aspect_w, aspect_h = ASPECT_TARGETS.get(job["aspect_ratio"], ASPECT_TARGETS["9:16"])
                    build_ass_captions(
                        transcript,
                        clip["start_sec"],
                        clip["end_sec"],
                        clip["hook_caption"],
                        ass_path,
                        aspect_w,
                        aspect_h,
                    )
                render_clip(
                    source_path=source,
                    start=clip["start_sec"],
                    end=clip["end_sec"],
                    aspect_ratio=job["aspect_ratio"],
                    burn_captions=bool(job["burn_captions"]),
                    ass_path=ass_path if job["burn_captions"] else None,
                    output_path=clip_url,
                )
                make_thumbnail(clip_url, thumb_url)
                _update_clip(job_id, clip_id, status="done", error=None)
            except Exception as clip_err:
                logger.warning("Clip %s failed: %s", clip_id, clip_err)
                _update_clip(job_id, clip_id, status="failed", error=str(clip_err)[:400])
                # cleanup partial outputs
                try:
                    if clip_url.exists():
                        clip_url.unlink()
                except OSError:
                    pass

            progress = 40 + int((55 * (idx + 1)) / max(1, len(stored)))
            _update_job(job_id, progress_pct=progress, updated_at=time.time())

        # ---- finalize ------------------------------------------------------
        final_job = _JOBS.get(job_id)
        ok = sum(1 for c in final_job.get("clips", []) if c.get("status") == "done")
        if ok == 0:
            raise RuntimeError("All clips failed to render — check ffmpeg availability and source download")

        _update_job(job_id, status="completed", progress_pct=100, updated_at=time.time())

    except Exception as e:
        logger.error("Job %s failed: %s", job_id, e)
        _update_job(
            job_id,
            status="failed",
            error=str(e)[:1000],
            progress_pct=_JOBS.get(job_id, {}).get("progress_pct", 0),
            updated_at=time.time(),
        )
    finally:
        try:
            if tmp_dir and tmp_dir.exists():
                shutil.rmtree(tmp_dir, ignore_errors=True)
        except Exception:
            pass


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------


@app.get("/api/health")
def health() -> Dict[str, Any]:
    return {
        "status": "ok",
        "version": APP_VERSION,
        "jobs_running": sum(1 for j in _JOBS.values() if j.get("status") not in ("completed", "failed")),
        "timestamp": time.time(),
    }


@app.get("/")
def root() -> Dict[str, Any]:
    return {
        "name": "Virality Clipper",
        "version": APP_VERSION,
        "endpoints": ["POST /api/jobs", "GET /api/jobs/{job_id}", "GET /api/clips/{clip_id}.mp4", "GET /api/clips/{clip_id}.jpg", "GET /api/health"],
    }


@app.post("/api/jobs", status_code=201)
def create_job(payload: JobRequest) -> Dict[str, Any]:
    job_id = uuid.uuid4().hex[:16]
    job = {
        "id": job_id,
        "status": "queued",
        "progress_pct": 0,
        "youtube_url": payload.youtube_url,
        "clips": [],
        "error": None,
        "created_at": time.time(),
        "created_at_ts": time.time(),
        "updated_at": time.time(),
        "clip_count": payload.clip_count,
        "target_duration_sec": payload.target_duration_sec,
        "aspect_ratio": payload.aspect_ratio,
        "burn_captions": payload.burn_captions,
        "api_key": payload.api_key,
        "provider": payload.provider,
        "ai_mode": None,
        "video_title": None,
        "video_duration": None,
        "tmp_dir": None,
    }
    with _JOB_LOCK:
        _JOBS[job_id] = job

    thread = threading.Thread(target=process_job, args=(job_id,), daemon=True)
    thread.start()

    return {"job_id": job_id}


@app.get("/api/jobs/{job_id}")
def get_job(job_id: str) -> Dict[str, Any]:
    job = _get_job(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Job not found")
    return _job_to_json(job)


@app.get("/api/clips/{clip_id}.mp4")
def get_clip_mp4(clip_id: str, request: Request) -> FileResponse:
    clip_id = _sanitize_clip_id(clip_id)
    path = CLIPS_DIR / f"{clip_id}.mp4"
    if not path.exists():
        raise HTTPException(status_code=404, detail="Clip not found")
    return FileResponse(str(path), media_type="video/mp4", filename=f"{clip_id}.mp4", content_disposition_type="inline")


@app.get("/api/clips/{clip_id}.jpg")
def get_clip_thumbnail(clip_id: str, request: Request) -> FileResponse:
    clip_id = _sanitize_clip_id(clip_id)
    path = CLIPS_DIR / f"{clip_id}.jpg"
    if not path.exists():
        raise HTTPException(status_code=404, detail="Thumbnail not found")
    return FileResponse(str(path), media_type="image/jpeg", filename=f"{clip_id}.jpg", content_disposition_type="inline")


_CLIP_ID_RE = re.compile(r"^[a-zA-Z0-9_]{1,64}$")


def _sanitize_clip_id(clip_id: str) -> str:
    clip_id = Path(clip_id).name  # strip any path components
    if not _CLIP_ID_RE.match(clip_id):
        raise HTTPException(status_code=400, detail="Invalid clip id")
    return clip_id
