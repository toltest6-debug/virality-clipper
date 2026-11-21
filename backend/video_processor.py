"""
Video acquisition + rendering.

- transcript extraction (youtube-transcript-api, with a yt-dlp subtitle fallback)
- source video download (single merged stream, ~1080p cap)
- ffmpeg crop/scale + burned-in ASS captions + thumbnail extraction
"""

import logging
import os
import re
import shutil
import subprocess
from pathlib import Path
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)

ASPECT_TARGETS = {
    "9:16": (1080, 1920),
    "16:9": (1920, 1080),
    "1:1": (1080, 1080),
}

_YT_URL_PATTERNS = (
    r"(?:https?:\/\/)?(?:www\.)?(?:youtube\.com\/watch\?(?:[^#&?]+&)*v=|youtube\.com\/shorts\/|youtube\.com\/embed\/|youtube\.com\/live\/|youtube\.com\/@[^/]+\/videos\/|youtu\.be\/)([a-zA-Z0-9_-]{11})",
    r"(?:https?:\/\/)?(?:www\.)?youtube\.com\/watch\?v=([a-zA-Z0-9_-]{11})",
)


# ---------------------------------------------------------------------------
# URL / metadata
# ---------------------------------------------------------------------------


def extract_video_id(url: str) -> Optional[str]:
    if not url:
        return None
    url = url.strip()
    for pattern in _YT_URL_PATTERNS:
        m = re.search(pattern, url)
        if m:
            return m.group(1)
    if re.fullmatch(r"[a-zA-Z0-9_-]{11}", url):
        return url
    return None


def validate_youtube_url(url: str) -> bool:
    return extract_video_id(url) is not None


def fetch_metadata(youtube_url: str) -> Dict[str, Any]:
    from yt_dlp import YoutubeDL

    opts = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "noplaylist": True,
        "no_progress": True,
    }
    with YoutubeDL(opts) as ydl:
        info = ydl.extract_info(youtube_url, download=False)
    duration = info.get("duration")
    try:
        duration = float(duration) if duration is not None else None
    except (TypeError, ValueError):
        duration = None
    return {
        "title": str(info.get("title") or "Untitled"),
        "duration": duration,
        "webpage_url": info.get("webpage_url") or youtube_url,
    }


# ---------------------------------------------------------------------------
# Transcript fetching (youtube-transcript-api with yt-dlp subtitle fallback)
# ---------------------------------------------------------------------------


def fetch_transcript(video_id: str, out_dir: Path, youtube_url: Optional[str] = None, languages: Optional[List[str]] = None) -> List[Dict[str, Any]]:
    languages = languages or ["en", "en-US", "en-GB"]
    logger.info("Fetching transcript via youtube-transcript-api for %s", video_id)
    try:
        from youtube_transcript_api import YouTubeTranscriptApi

        api = YouTubeTranscriptApi()
        snippets = api.fetch(video_id, languages=languages)
        segments: List[Dict[str, Any]] = []
        for snippet in snippets:
            text = getattr(snippet, "text", None) or (snippet.get("text") if isinstance(snippet, dict) else None)
            start = getattr(snippet, "start", None) if not isinstance(snippet, dict) else snippet.get("start")
            duration = getattr(snippet, "duration", None) if not isinstance(snippet, dict) else snippet.get("duration")
            if not text:
                try:
                    text = snippet[0]
                    start = snippet[1]
                    duration = snippet[2]
                except Exception:
                    continue
            try:
                start = float(start or 0.0)
            except (TypeError, ValueError):
                start = 0.0
            try:
                duration = float(duration or 0.0)
            except (TypeError, ValueError):
                duration = 0.0
            text = re.sub(r"\s+", " ", str(text)).strip()
            if not text:
                continue
            segments.append({"start": start, "duration": max(0.1, duration), "text": text})
        if segments:
            logger.info("Transcript via youtube-transcript-api: %d segments", len(segments))
            return segments
    except Exception as e:
        logger.warning("youtube-transcript-api failed (%s); falling back to yt-dlp subtitles", e)

    # Fallback: yt-dlp auto-subtitles (works for manually disabled transcripts)
    if not youtube_url:
        raise RuntimeError("Transcript is unavailable (YouTube API error + no fallback URL)")
    logger.info("Fetching transcript via yt-dlp auto-subtitles for %s", video_id)
    return _fetch_subtitles_with_ytdlp(youtube_url, out_dir, languages)


def _fetch_subtitles_with_ytdlp(youtube_url: str, out_dir: Path, languages: List[str]) -> List[Dict[str, Any]]:
    from yt_dlp import YoutubeDL

    out_dir.mkdir(parents=True, exist_ok=True)
    outtmpl = str(out_dir / "subs.%(ext)s")
    opts = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "noplaylist": True,
        "no_progress": True,
        "writeautomaticsub": True,
        "subtitleslangs": ",".join(languages),
        "outtmpl": outtmpl,
    }
    try:
        with YoutubeDL(opts) as ydl:
            ydl.download([youtube_url])
    except Exception as e:
        raise RuntimeError(f"yt-dlp subtitle fallback failed: {e}") from e

    for ext in (".vtt", ".srt"):
        candidates = sorted(out_dir.glob(f"*{ext}"))
        if not candidates:
            continue
        for file_path in candidates:
            text = file_path.read_text(encoding="utf-8", errors="replace")
            segments = parse_vtt(text) if ext == ".vtt" else parse_srt(text)
            if segments:
                logger.info("Transcript via yt-dlp subtitles: %d segments from %s", len(segments), file_path.name)
                return segments

    raise RuntimeError("Could not retrieve a transcript (YouTube blocked or no subtitles available)")


def _vtt_time_to_sec(t: str) -> float:
    t = t.strip().replace(",", ".")
    m = re.match(r"(?:(\d+):)?(\d+):(\d{2})\.(\d{3})", t)
    if m:
        h = int(m.group(1) or 0)
        mi = int(m.group(2))
        sec = int(m.group(3))
        ms = int(m.group(4))
        return h * 3600 + mi * 60 + sec + ms / 1000.0
    # plain seconds fallback
    try:
        return float(t)
    except ValueError:
        return 0.0


def parse_vtt(text: str) -> List[Dict[str, Any]]:
    segments: List[Dict[str, Any]] = []
    for block in re.split(r"\n\s*\n", text):
        lines = [ln.strip() for ln in block.splitlines() if ln.strip()]
        if not lines:
            continue
        if lines and lines[0].upper().startswith("NOTE"):
            continue
        ts_idx = next((i for i, ln in enumerate(lines) if "-->" in ln), None)
        if ts_idx is None:
            continue
        m = re.match(r"([\d:.,]+)\s*-->\s*([\d:.,]+)", lines[ts_idx])
        if not m:
            continue
        start = _vtt_time_to_sec(m.group(1))
        end = _vtt_time_to_sec(m.group(2))
        text_lines = [ln for ln in lines[ts_idx + 1 :] if "-->" not in ln]
        body = " ".join(text_lines)
        body = re.sub(r"<[^>]+>", "", body)
        body = body.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", '"').replace("&#39;", "'")
        body = re.sub(r"\s+", " ", body).strip()
        if not body:
            continue
        segments.append({"start": start, "duration": max(0.1, end - start), "text": body})
    return segments


def parse_srt(text: str) -> List[Dict[str, Any]]:
    segments: List[Dict[str, Any]] = []
    for block in re.split(r"\n\s*\n", text):
        lines = [ln.strip() for ln in block.splitlines() if ln.strip()]
        if not lines:
            continue
        i = 0
        if re.fullmatch(r"\d+", lines[0]):
            i = 1
        m = re.match(r"(\d{2}:\d{2}:\d{2}[,.]\d{3})\s*-->\s*(\d{2}:\d{2}:\d{2}[,.]\d{3})", lines[i] if i < len(lines) else "")
        if not m:
            continue
        start = _vtt_time_to_sec(m.group(1))
        end = _vtt_time_to_sec(m.group(2))
        body = " ".join(lines[i + 1 :])
        body = re.sub(r"<[^>]+>", "", body)
        body = body.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", '"').replace("&#39;", "'")
        body = re.sub(r"\s+", " ", body).strip()
        if not body:
            continue
        segments.append({"start": start, "duration": max(0.1, end - start), "text": body})
    return segments


# ---------------------------------------------------------------------------
# Source download
# ---------------------------------------------------------------------------


def download_source_video(youtube_url: str, out_dir: Path, max_height: int = 1080) -> Path:
    from yt_dlp import YoutubeDL

    out_dir.mkdir(parents=True, exist_ok=True)
    outtmpl = str(out_dir / "src.%(ext)s")

    def _cleanup_src():
        for f in out_dir.glob("src.*"):
            try:
                f.unlink()
            except OSError:
                pass

    # Attempt a 1080p-capped merged download. If ffmpeg is unavailable (e.g. a
    # bare container), fall back to a single best-file download which does
    # not require merging.
    merged_opts = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "no_progress": True,
        "format": f"bestvideo[height<={max_height}][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<={max_height}][ext=mp4]/best[ext=mp4]/best",
        "merge_output_format": "mp4",
        "outtmpl": outtmpl,
        "retries": 4,
        "fragment_retries": 4,
    }
    try:
        with YoutubeDL(merged_opts) as ydl:
            ydl.download([youtube_url])
        if not list(out_dir.glob("src.*")):
            raise RuntimeError("download completed but no source file produced")
        return _find_source(out_dir)
    except Exception as e:
        logger.warning("Merged download failed (%s); falling back to single-file best format", e)
        _cleanup_src()

    single_opts = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "no_progress": True,
        "format": f"best[height<={max_height}][ext=mp4]/best[ext=mp4]/best",
        "outtmpl": outtmpl,
        "retries": 4,
        "fragment_retries": 4,
    }
    with YoutubeDL(single_opts) as ydl:
        ydl.download([youtube_url])
    if not list(out_dir.glob("src.*")):
        raise RuntimeError("Source video file not found after download")
    return _find_source(out_dir)


def _find_source(out_dir: Path) -> Path:
    candidates = sorted(out_dir.glob("src.*"))
    mp4 = [p for p in candidates if p.suffix.lower() == ".mp4"]
    if mp4:
        return mp4[0]
    if candidates:
        return candidates[0]
    raise RuntimeError("Source video file not found after download")


# ---------------------------------------------------------------------------
# ASS caption generation
# ---------------------------------------------------------------------------


def _ass_time(seconds: float) -> str:
    seconds = max(0.0, float(seconds))
    h = int(seconds // 3600)
    m = int((seconds % 3600) // 60)
    s = int(seconds % 60)
    cs = int(round((seconds - int(seconds)) * 100))
    if cs >= 100:
        s += 1
        cs = 0
        if s >= 60:
            m += 1
            s = 0
        if m >= 60:
            h += 1
            m = 0
    return f"{h}:{m:02d}:{s:02d}.{cs:02d}"


def _ass_color(hex_value: str) -> str:
    """Convert #RRGGBB to ASS &HAABBGGRR."""
    hex_value = hex_value.lstrip("#")
    r, g, b = hex_value[0:2], hex_value[2:4], hex_value[4:6]
    return f"&H00{b}{g}{r}"


def _wrap_text(text: str, max_chars: int = 42, max_lines: int = 3) -> List[List[str]]:
    words = text.split()
    lines: List[str] = []
    current = ""
    for word in words:
        if len(word) > max_chars:
            # very long word (URL, hashtag): allow it to fill its own line
            current = current + " " + word if current else word
            if len(current) > max_chars:
                lines.append(current)
                current = ""
            continue
        candidate = f"{current} {word}".strip()
        if len(candidate) <= max_chars:
            current = candidate
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    # chunk lines into groups of max_lines
    return [lines[i : i + max_lines] for i in range(0, len(lines), max_lines)] if lines else []


def build_ass_captions(
    segments: List[Dict[str, Any]],
    clip_start: float,
    clip_end: float,
    hook_caption: str,
    ass_path: Path,
    target_w: int,
    target_h: int,
) -> None:
    """Burn styled ASS captions for a single clip. `segments` is the full transcript."""
    clip_dur = max(0.2, clip_end - clip_start)
    hook = _clean_caption_text(hook_caption)
    # colors (ASS &HAABBGGRR)
    cyan = _ass_color("00E5FF")
    white = _ass_color("FFFFFF")
    black = _ass_color("000000")

    header = f"""[Script Info]
ScriptType: v4.00+
PlayResX: {target_w}
PlayResY: {target_h}
WrapStyle: 2
ScaledBorderAndShadow: yes
YCbCr Matrix: TV.709

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Hook, Arial Bold, 52, {cyan}, {white}, {black}, {black}, -1, 0, 1, 3, 0, 8, 80, 80, 220, 1
Style: Caption, Arial Bold, 28, {white}, {white}, {black}, {black}, -1, 0, 1, 3, 1, 2, 70, 70, 80, 1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""

    events: List[str] = []

    # Hook up top (first 4s)
    if hook:
        hook_line = " ".join(hook.split()).replace("\\", "/")
        events.append(
            f"Dialogue: 0, {_ass_time(0.0)}, {_ass_time(min(4.0, clip_dur))}, Hook, , 0,0,0, ,{{\\pos({target_w//2}, 210)}}{hook_line}"
        )

    # Caption events from transcript segments
    for seg in segments:
        seg_start = float(seg.get("start", 0.0))
        seg_end = seg_start + float(seg.get("duration", 1.0))
        if seg_end <= clip_start or seg_start >= clip_end:
            continue
        rel_start = max(0.0, seg_start - clip_start)
        rel_end = min(clip_dur, seg_end - clip_start)
        if rel_end - rel_start <= 0.15:
            continue
        text = _clean_caption_text(seg.get("text", ""))
        if not text:
            continue
        groups = _wrap_text(text, max_chars=44, max_lines=3)
        if not groups:
            continue
        seg_dur = rel_end - rel_start
        per = seg_dur / max(1, len(groups))
        for gi, group in enumerate(groups):
            start = rel_start + per * gi
            end = rel_start + per * (gi + 1)
            if end - start < 0.2:
                continue
            caption_text = "\\N".join(group)
            events.append(
                f"Dialogue: 1, {_ass_time(start)}, {_ass_time(end)}, Caption, , 0,0,0, ,{caption_text}"
            )

    ass_path.parent.mkdir(parents=True, exist_ok=True)
    with open(ass_path, "w", encoding="utf-8") as fh:
        fh.write(header)
        fh.write("\n".join(events))
        fh.write("\n")


def _clean_caption_text(text: str) -> str:
    text = re.sub(r"<[^>]+>", "", str(text))
    text = text.replace("\\N", " ")  # avoid stray ASS newlines inside text
    text = re.sub(r"\s+", " ", text).strip()
    return text


# ---------------------------------------------------------------------------
# Rendering
# ---------------------------------------------------------------------------


def _run_ffmpeg(cmd: List[str], timeout: int = 1200) -> None:
    proc = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    if proc.returncode != 0:
        tail = (proc.stderr or "").strip().splitlines()
        raise RuntimeError(f"ffmpeg failed (rc={proc.returncode}): {tail[-1] if tail else 'unknown error'}")


def render_clip(
    source_path: Path,
    start: float,
    end: float,
    aspect_ratio: str,
    burn_captions: bool,
    ass_path: Optional[Path],
    output_path: Path,
) -> Path:
    target_w, target_h = ASPECT_TARGETS.get(aspect_ratio, ASPECT_TARGETS["9:16"])
    duration = max(0.1, end - start)

    vf_parts = [
        f"scale={target_w}:{target_h}:force_original_aspect_ratio=increase",
        f"crop={target_w}:{target_h}",
        "setsar=1",
    ]
    if burn_captions and ass_path and Path(ass_path).exists():
        vf_parts.append(f"subtitles='{Path(ass_path).resolve()}'")

    cmd = [
        "ffmpeg", "-hide_banner", "-y",
        "-ss", f"{start:.3f}",
        "-t", f"{duration:.3f}",
        "-avoid_negative_ts", "make_zero",
        "-i", str(source_path),
        "-vf", ",".join(vf_parts),
        "-map", "0:v:0",
        "-map", "0:a?",
        "-c:v", "libx264",
        "-preset", "veryfast",
        "-crf", "23",
        "-c:a", "aac",
        "-b:a", "128k",
        "-movflags", "+faststart",
        str(output_path),
    ]
    try:
        _run_ffmpeg(cmd)
    except RuntimeError:
        # Fallback for very old ffmpeg without optional `0:a?` map syntax: pick audio stream 0 by index.
        cmd2 = []
        skip_next = False
        for c in cmd:
            if skip_next:
                skip_next = False
                continue
            if c == "0:a?":
                skip_next = True  # drop the optional map; -map 0:v:0 remains
                continue
            cmd2.append(c)
        try:
            _run_ffmpeg(cmd2)
        except RuntimeError:
            raise

    if not Path(output_path).exists() or Path(output_path).stat().st_size < 1024:
        raise RuntimeError("Rendered clip is missing or suspiciously small")
    return Path(output_path)


def make_thumbnail(clip_path: Path, thumb_path: Path) -> Optional[Path]:
    try:
        cmd = [
            "ffmpeg", "-hide_banner", "-y",
            "-ss", "0.4",
            "-i", str(clip_path),
            "-frames:v", "1",
            "-q:v", "2",
            str(thumb_path),
        ]
        _run_ffmpeg(cmd, timeout=120)
        if Path(thumb_path).exists() and Path(thumb_path).stat().st_size > 1024:
            return Path(thumb_path)
    except Exception as e:
        logger.warning("Thumbnail extraction failed: %s", e)
    return None
