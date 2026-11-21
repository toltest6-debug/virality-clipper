"""
AI viral-moment detection.

Chunks a timestamped transcript, asks an LLM (OpenAI / Gemini / Anthropic, or a
deterministic heuristic fallback when no API key is configured) for the most
engaging self-contained segments, validates the result, dedupes overlaps and
returns the top N clips.
"""

import json
import logging
import os
import re
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)

SUPPORTED_PROVIDERS = ("auto", "openai", "gemini", "anthropic")
ASPECT_RATIOS = ("9:16", "16:9", "1:1")
MIN_CLIP_SECONDS = 20.0
MAX_CLIP_SECONDS = 90.0

# ---------------------------------------------------------------------------
# Data model
# ---------------------------------------------------------------------------


@dataclass
class TranscriptSegment:
    start: float
    duration: float
    text: str

    @property
    def end(self) -> float:
        return self.start + self.duration

    def to_dict(self) -> Dict[str, Any]:
        return {"start": self.start, "duration": self.duration, "text": self.text}


@dataclass
class ViralClipDraft:
    start_sec: float
    end_sec: float
    title: str
    hook_caption: str
    virality_score: int
    source: str = "ai"  # 'ai' | 'heuristic'

    @property
    def duration_sec(self) -> float:
        return max(0.0, self.end_sec - self.start_sec)


def _clean_text(text: Any) -> str:
    if not text:
        return ""
    text = str(text)
    text = re.sub(r"<[^>]+>", "", text)  # strip html-ish tags
    text = re.sub(r"\[(?:\d+(?:\.\d+)?)\]", "", text)  # drop loose numeric ids
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def _coerce_segment(raw: Any) -> Optional[TranscriptSegment]:
    if raw is None:
        return None

    # FetchedTranscriptSnippet (object) or dict from raw data
    text = None
    start = None
    duration = None
    if isinstance(raw, dict):
        text = raw.get("text")
        start = raw.get("start")
        duration = raw.get("duration")
    else:
        text = getattr(raw, "text", None)
        start = getattr(raw, "start", None)
        duration = getattr(raw, "duration", None)

    if text is None:
        return None

    try:
        start = float(start or 0.0)
    except (TypeError, ValueError):
        start = 0.0
    try:
        duration = float(duration or 0.0)
    except (TypeError, ValueError):
        duration = 0.0

    text = _clean_text(text)
    if not text:
        return None
    return TranscriptSegment(start=start, duration=max(0.1, duration), text=text)


# ---------------------------------------------------------------------------
# Chunking + prompt building
# ---------------------------------------------------------------------------

CHUNK_MAX_CHARS = 6000
CHUNK_MAX_SEGMENTS = 45


def chunk_segments(segments: List[TranscriptSegment]) -> List[List[TranscriptSegment]]:
    chunks: List[List[TranscriptSegment]] = []
    current: List[TranscriptSegment] = []
    current_len = 0

    def flush():
        nonlocal current, current_len
        if current:
            chunks.append(current)
        current = []
        current_len = 0

    for seg in segments:
        seg_len = len(seg.text)
        if current and (current_len + seg_len > CHUNK_MAX_CHARS or len(current) >= CHUNK_MAX_SEGMENTS):
            flush()
        current.append(seg)
        current_len += seg_len

    flush()
    return chunks


def _fmt_ts(seconds: float) -> str:
    m = int(seconds // 60)
    s = seconds - m * 60
    return f"{m}:{s:04.1f}"


def _fmt_chunk(chunk: List[TranscriptSegment]) -> str:
    lines = []
    for seg in chunk:
        lines.append(f"[{_fmt_ts(seg.start)}] {seg.text}")
    return "\n".join(lines)


def build_prompt(total_duration: Optional[float], target_sec: int, chunk: List[TranscriptSegment]) -> str:
    total = f"{total_duration:.0f}s" if total_duration else "unknown"
    return f"""You are a viral-clip editor. Select the most engaging, self-contained "viral moments" for short-form vertical video (YouTube Shorts / Reels / TikTok) from a transcript chunk of a YouTube video.

Video metadata:
- total duration: {total}
- target clip length: ~{target_sec} seconds (acceptable 20-60s)

Each candidate clip MUST:
- be self-contained: it must work for viewers who know nothing about the video
- have a strong hook in the first 3 seconds
- end with an emotional, funny, surprising, or educational payoff
- be between 20 and 60 seconds
- NEVER invent content: only use text/facts from the transcript

Return STRICT JSON (no prose, no markdown fences, no trailing commentary) with this exact schema:
{{"clips": [{{"start_sec": 12.5, "end_sec": 38.0, "title": "Punchy title, max 8 words, no emojis", "hook_caption": "<= 25 word overlay quote that hooks the viewer", "virality_score": 1-100}}]}}

A clip's start_sec must align to a natural sentence/beat boundary in the chunk.
Pick 1-3 clips that best fit the criteria from THIS chunk.

Transcript chunk:
{_fmt_chunk(chunk)}"""


# ---------------------------------------------------------------------------
# LLM providers
# ---------------------------------------------------------------------------

_ENV_KEYS = {
    "openai": ("OPENAI_API_KEY",),
    "anthropic": ("ANTHROPIC_API_KEY",),
    "gemini": ("GEMINI_API_KEY", "GOOGLE_API_KEY", "GOOGLE_GENERATIVE_AI_API_KEY"),
}

_PROVIDER_MODELS = {
    "openai": "OPENAI_MODEL",
    "anthropic": "ANTHROPIC_MODEL",
    "gemini": "GEMINI_MODEL",
}

_PROVIDER_DEFAULT_MODELS = {
    "openai": "gpt-4o-mini",
    "anthropic": "claude-sonnet-4-5",
    "gemini": "gemini-2.5-flash",
}


def detect_provider(explicit: Optional[str], api_key: Optional[str]) -> Optional[str]:
    """Resolve provider from explicit arg + env keys. Returns None for heuristic mode."""
    provider = (explicit or os.environ.get("AI_PROVIDER") or "auto").strip().lower()
    if provider not in SUPPORTED_PROVIDERS:
        provider = "auto"

    if provider == "auto":
        if api_key:
            # Infer from key prefix (OpenAI / Anthropic / Gemini conventions)
            if api_key.startswith("sk-ant"):
                return "anthropic"
            if api_key.startswith("AIza"):
                return "gemini"
            return "openai"
        for p in ("openai", "anthropic", "gemini"):
            if any(os.environ.get(k) for k in _ENV_KEYS[p]):
                return p
        return None

    # explicit provider: check for key
    if api_key or any(os.environ.get(k) for k in _ENV_KEYS[provider]):
        return provider
    return None


def _get_key(provider: str, api_key: Optional[str]) -> str:
    if api_key:
        return api_key
    for env in _ENV_KEYS[provider]:
        value = os.environ.get(env)
        if value:
            return value
    raise RuntimeError(
        f"No API key for provider '{provider}'. Provide one in the job request (api_key) "
        f"or set {'|'.join(_ENV_KEYS[provider])} in the environment."
    )


def _model_name(provider: str) -> str:
    return os.environ.get(_PROVIDER_MODELS[provider]) or _PROVIDER_DEFAULT_MODELS[provider]


def _extract_json(text: str) -> Any:
    text = (text or "").strip()
    if not text:
        raise ValueError("empty model response")
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass
    # isolate the first balanced { ... } block
    start = text.find("{")
    if start == -1:
        raise ValueError("no JSON object found in response")
    depth = 0
    for i in range(start, len(text)):
        ch = text[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return json.loads(text[start : i + 1])
    raise ValueError("unbalanced JSON in response")


def _call_openai(prompt: str, api_key: Optional[str]) -> str:
    from openai import OpenAI

    client = OpenAI(api_key=_get_key("openai", api_key))
    response = client.chat.completions.create(
        model=_model_name("openai"),
        messages=[
            {"role": "system", "content": "You are a viral-clip editor. Return ONLY strict JSON."},
            {"role": "user", "content": prompt},
        ],
        temperature=0.4,
        response_format={"type": "json_object"},
    )
    return response.choices[0].message.content or ""


def _call_anthropic(prompt: str, api_key: Optional[str]) -> str:
    from anthropic import Anthropic

    client = Anthropic(api_key=_get_key("anthropic", api_key))
    response = client.messages.create(
        model=_model_name("anthropic"),
        max_tokens=2500,
        temperature=0.4,
        messages=[{"role": "user", "content": prompt}],
    )
    for block in response.content or []:
        if getattr(block, "type", None) == "text" or hasattr(block, "text"):
            return getattr(block, "text", "") or ""
    return ""


def _call_gemini(prompt: str, api_key: Optional[str]) -> str:
    import google.generativeai as genai

    genai.configure(api_key=_get_key("gemini", api_key))
    model = genai.GenerativeModel(_model_name("gemini"))
    response = model.generate_content(
        prompt,
        generation_config={"response_mime_type": "application/json", "temperature": 0.4},
    )
    return getattr(response, "text", "") or ""


_PROVIDER_CALLERS = {
    "openai": _call_openai,
    "anthropic": _call_anthropic,
    "gemini": _call_gemini,
}


def _candidates_from_chunk(chunk: List[TranscriptSegment], total_duration, target_sec, provider, api_key) -> List[ViralClipDraft]:
    prompt = build_prompt(total_duration, target_sec, chunk)
    try:
        raw_text = _PROVIDER_CALLERS[provider](prompt, api_key)
    except Exception as e:
        logger.warning("Provider call failed (%s): %s", provider, e)
        return []
    try:
        payload = _extract_json(raw_text)
    except Exception as e:
        logger.warning("Could not parse model output as JSON: %s", e)
        return []
    clips = payload.get("clips") if isinstance(payload, dict) else None
    if not isinstance(clips, list):
        return []

    drafts: List[ViralClipDraft] = []
    for item in clips:
        try:
            start = float(item["start_sec"])
            end = float(item["end_sec"])
            title = _clean_text(item.get("title") or "").strip()
            hook = _clean_text(item.get("hook_caption") or "").strip()
            score = int(float(item.get("virality_score", 50)))
        except Exception:
            continue
        if not title:
            title = "Viral moment"
        if not hook:
            hook = title
        if end <= start:
            continue
        score = max(1, min(100, score))
        drafts.append(ViralClipDraft(start, end, title, hook, score, "ai"))
    return drafts


# ---------------------------------------------------------------------------
# Heuristic fallback (no LLM key configured)
# ---------------------------------------------------------------------------

_HOT_KEYWORDS = (
    "wow", "amazing", "incredible", "unbelievable", "shocking", "insane", "crazy",
    "genius", "never", "wait", "but", "honestly", "secret", "biggest", "ultimate",
    "mind", "never", "sure", "literally", "so crazy", "can't believe", "how", "why",
    "struggling", "failed", "won", "shut", "drama", "love", "hate", "disgusted",
)


def heuristic_analyze(
    segments: List[TranscriptSegment],
    video_duration: Optional[float],
    clip_count: int,
    target_sec: int,
) -> List[ViralClipDraft]:
    """Deterministic keyword/punctuation-based fallback."""
    if not segments:
        return []

    limit = video_duration if video_duration and video_duration > 0 else segments[-1].end
    if limit <= 0:
        return []

    window_dur = float(target_sec)
    stride = max(8.0, window_dur / 3.0)
    candidates: List[Dict[str, Any]] = []

    t = 0.0
    while t < limit - 12:
        end = min(t + window_dur, limit)
        segs = [s for s in segments if s.start < end and s.end > t]
        if len(segs) < 2:
            t += stride
            continue
        text = " ".join(s.text for s in segs)
        lower = text.lower()
        energy = 0.0
        energy += lower.count("!") * 2.0
        energy += lower.count("?") * 1.2
        energy += lower.count("??") * 1.0
        for kw in _HOT_KEYWORDS:
            if kw in lower:
                energy += 2.5
        energy += (len(text) / max(1.0, end - t)) * 0.02  # density
        # prefer windows near the middle of the video (less fluff) but not biased too hard
        middle_bias = 1.0 - min(0.5, abs(t + window_dur / 2 - limit / 2) / max(1.0, limit))
        energy *= middle_bias + 0.4
        candidates.append(
            {
                "start": t,
                "end": end,
                "energy": energy,
                "segments": segs,
                "text": text,
            }
        )
        t += stride

    candidates.sort(key=lambda c: c["energy"], reverse=True)

    accepted: List[Dict[str, Any]] = []
    for c in candidates:
        overlap = any(not (c["end"] < a["start"] or c["start"] > a["end"]) for a in accepted)
        if overlap:
            continue
        accepted.append(c)
        if len(accepted) >= clip_count:
            break

    if not accepted and candidates:
        accepted = [candidates[0]]

    drafts: List[ViralClipDraft] = []
    for i, c in enumerate(accepted):
        segs = c["segments"]
        # pick the punchiest line in the window for hook/title
        best_sentence = max(
            (s.text for s in segs),
            key=lambda s: (s.count("!") * 3 + s.count("?") * 2 + sum(1 for kw in _HOT_KEYWORDS if kw in s.lower())),
            default=c["text"],
        )
        hook = _clean_text(best_sentence)
        hook = hook[:140].strip()
        title_words = re.findall(r"[A-Za-z']+", hook)
        title = " ".join(title_words[:6]) or f"Viral moment #{i + 1}"
        score = int(min(95, 55 + c["energy"] * 1.1))
        drafts.append(
            ViralClipDraft(
                start_sec=float(c["start"]),
                end_sec=float(c["end"]),
                title=title,
                hook_caption=hook,
                virality_score=max(1, min(100, score)),
                source="heuristic",
            )
        )
    return drafts


# ---------------------------------------------------------------------------
# Validation / merge / selection
# ---------------------------------------------------------------------------


def _normalize_clip(draft: ViralClipDraft, video_duration: Optional[float], target_sec: int) -> Optional[ViralClipDraft]:
    start = max(0.0, draft.start_sec)
    end = draft.end_sec

    if video_duration and video_duration > 0:
        end = min(end, video_duration)

    # clamp duration to target (spec asks 25-60s, app choice 30/45/60)
    max_dur = min(MAX_CLIP_SECONDS, target_sec + 10)
    if end - start > max_dur:
        end = start + max_dur

    if end - start < MIN_CLIP_SECONDS:
        return None

    title = draft.title.strip() or "Viral moment"
    hook = draft.hook_caption.strip() or title
    score = max(1, min(100, int(draft.virality_score)))
    return ViralClipDraft(start, end, title, hook, score, draft.source)


def merge_candidates(
    candidates: List[ViralClipDraft],
    video_duration: Optional[float],
    target_sec: int,
    clip_count: int,
) -> List[ViralClipDraft]:
    cleaned: List[ViralClipDraft] = []
    for c in candidates:
        norm = _normalize_clip(c, video_duration, target_sec)
        if norm:
            cleaned.append(norm)

    cleaned.sort(key=lambda c: c.virality_score, reverse=True)

    accepted: List[ViralClipDraft] = []
    for c in cleaned:
        too_close = False
        for a in accepted:
            overlap = min(c.end_sec, a.end_sec) - max(c.start_sec, a.start_sec)
            min_dur = min(c.duration_sec, a.duration_sec)
            if min_dur > 0 and overlap / min_dur > 0.35:
                too_close = True
                break
        if not too_close:
            accepted.append(c)
        if len(accepted) >= clip_count:
            break

    accepted.sort(key=lambda c: c.virality_score, reverse=True)
    return accepted


# ---------------------------------------------------------------------------
# Public entry point
# ---------------------------------------------------------------------------


def analyze(
    segments: List[Any],
    video_duration: Optional[float],
    clip_count: int,
    target_sec: int,
    provider: Optional[str],
    api_key: Optional[str],
) -> Dict[str, Any]:
    """
    Returns {"provider": provider_or_heuristic, "clips": [ViralClipDraft, ...], "ai_mode": "ai"|"heuristic"}.
    """
    normalized = [s for s in (_coerce_segment(x) for x in segments or []) if s]
    if not normalized:
        return {"provider": "heuristic", "ai_mode": "heuristic", "clips": []}

    provider_name = detect_provider(provider, api_key)
    clip_count = max(3, min(5, int(clip_count)))
    target_sec = max(20, min(120, int(target_sec)))

    candidates: List[ViralClipDraft] = []
    ai_mode = "heuristic"
    provider_used = "heuristic"

    if provider_name:
        chunks = chunk_segments(normalized)
        for idx, chunk in enumerate(chunks):
            try:
                batch = _candidates_from_chunk(chunk, video_duration, target_sec, provider_name, api_key)
                if batch:
                    ai_mode = "ai"
                    provider_used = provider_name
                    candidates.extend(batch)
            except Exception as e:
                logger.warning("Chunk %d failed (%s): %s", idx, provider_name, e)
        # If the LLM returned nothing usable, fall through to heuristic.
        if not candidates:
            logger.warning("LLM provider returned no usable clips; falling back to heuristic selection")

    if not candidates:
        candidates = heuristic_analyze(normalized, video_duration, clip_count, target_sec)
        ai_mode = "heuristic"
        provider_used = provider_name or "heuristic"

    # Final selection with overlap dedupe
    result = merge_candidates(candidates, video_duration, target_sec, clip_count)

    return {
        "provider": provider_used,
        "ai_mode": ai_mode if any(c.source == "ai" for c in result) else "heuristic",
        "clips": result,
    }
