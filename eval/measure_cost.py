"""Measure what it costs to translate the evaluation windows with Claude Haiku 5.5.

Runs every (video, 5-minute chunk, effort, repeat) once, saves each raw output and
its usage, then prints tokens and dollars per video hour.

Usage: ANTHROPIC_API_KEY=... python eval/measure_cost.py [repeats]
"""

import json
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import anthropic

MODEL = "claude-haiku-5-5"
PRICE_IN, PRICE_OUT = 0.10, 0.50  # USD per million tokens, prompts up to 100K tokens
PROMPT_VERSION = "v0"
CHUNK_SEC = 300
# Thinking tokens are billed as output and dominate the cost, so each condition
# varies only how much the model thinks. "medium" is this model's default.
CONDITIONS = {
    "medium": {"output_config": {"effort": "medium"}},
    "low": {"output_config": {"effort": "low"}},
    "low-nothink": {"output_config": {"effort": "low"}, "thinking": {"type": "disabled"}},
}
EFFORTS = list(CONDITIONS)

ROOT = Path(__file__).parent
SYSTEM = (ROOT / "prompts" / f"{PROMPT_VERSION}_system.txt").read_text()
OUT_DIR = ROOT / "raw" / "runs" / PROMPT_VERSION

CONTEXT = {
    "v1": (
        "Channel: Gigi Murin. Solo streamer talking to her chat.\n"
        "Register: casual Korean 반말.\n"
        "Glossary:\n"
        "- Gigi Murin = 지지 무린 (the streamer)\n"
        "- Deadlock = 데드락 (a hero shooter game). Heroes: Infernus = 인페르누스, Haze = 헤이즈, Ivy = 아이비\n"
        "- Guardian = 가디언, lane = 라인, ranked = 랭크, feeding = 피딩, M1 = 좌클릭\n"
        "- Worlds = 롤드컵 (League of Legends World Championship)\n"
    ),
    "v2": (
        "Channel: Dokibird. Title: WILDCARD CLUB HANGOUT, Meet Team Dokibird!\n"
        "Several people talking casually in a voice call.\n"
        "Register: casual Korean 반말 for everyone.\n"
        "Glossary: none.\n"
    ),
    "v3": (
        "Channel: Koseki Bijou. Title: The Legend of Zelda: Ocarina of Time Remake.\n"
        "A solo streamer plays the game. Captions mix voiced in-game dialogue with her own reactions.\n"
        "Register: casual Korean 반말 for the streamer, a fitting register for game characters.\n"
        "Glossary: none. Use the official Korean names of this series where you know them.\n"
    ),
}


def chunks(video):
    """Split a video's caption events into consecutive CHUNK_SEC windows."""
    start, end = video["window_sec"]
    events = (ROOT / video["words"]).read_text().splitlines()
    for lo in range(start, end, CHUNK_SEC):
        in_window = [e for e in events if lo * 1000 <= int(e.split(" ", 1)[0]) < (lo + CHUNK_SEC) * 1000]
        before = [e for e in events if int(e.split(" ", 1)[0]) < lo * 1000][-3:]
        yield lo, before, in_window


def run_one(client, video_id, lo, before, events, effort, rep):
    user = CONTEXT[video_id]
    if before:
        user += "\nPrevious context:\n" + "\n".join(before) + "\n"
    user += "\nCaptions:\n" + "\n".join(events)
    t0 = time.time()
    # Streamed so the large max_tokens (room for thinking) cannot hit an HTTP timeout.
    with client.messages.stream(
        model=MODEL,
        max_tokens=48000,
        system=SYSTEM,
        messages=[{"role": "user", "content": user}],
        **CONDITIONS[effort],
    ) as stream:
        response = stream.get_final_message()
    text = "".join(b.text for b in response.content if b.type == "text")
    name = f"{video_id}_{lo}_{effort}_{rep}"
    (OUT_DIR / f"{name}.txt").write_text(text)
    return {
        "video": video_id,
        "chunk_start": lo,
        "effort": effort,
        "rep": rep,
        "stop_reason": response.stop_reason,
        "seconds": round(time.time() - t0, 1),
        "subtitles": len([line for line in text.splitlines() if "|" in line]),
        "usage": response.usage.model_dump(),
    }


def main():
    repeats = int(sys.argv[1]) if len(sys.argv) > 1 else 3
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((ROOT / "manifest.json").read_text())
    client = anthropic.Anthropic()

    jobs = [
        (video["id"], lo, before, events, effort, rep)
        for video in manifest["videos"]
        for lo, before, events in chunks(video)
        for effort in EFFORTS
        for rep in range(1, repeats + 1)
    ]
    with ThreadPoolExecutor(max_workers=6) as pool:
        futures = [pool.submit(run_one, client, *job) for job in jobs]
        records = []
        for job, future in zip(jobs, futures):
            try:
                records.append(future.result())
            except anthropic.APIError as error:
                records.append({"video": job[0], "chunk_start": job[1], "effort": job[4], "rep": job[5], "error": repr(error)})

    with (OUT_DIR / "usage.jsonl").open("w") as f:
        for record in records:
            f.write(json.dumps(record, ensure_ascii=False) + "\n")

    ok = [r for r in records if "usage" in r]
    print(f"calls {len(records)}, failed {len(records) - len(ok)}, stop reasons {sorted({r['stop_reason'] for r in ok})}")
    print("video condition   rep  in_tok out_tok thinking subtitles  usd_per_video_hour")
    windows_per_hour = 3600 / (CHUNK_SEC * 2)  # each video has two chunks
    total = 0.0
    for video_id in CONTEXT:
        for effort in EFFORTS:
            for rep in range(1, repeats + 1):
                rows = [r for r in ok if (r["video"], r["effort"], r["rep"]) == (video_id, effort, rep)]
                if len(rows) != 2:
                    continue
                tin = sum(r["usage"]["input_tokens"] for r in rows)
                tout = sum(r["usage"]["output_tokens"] for r in rows)
                usd = tin / 1e6 * PRICE_IN + tout / 1e6 * PRICE_OUT
                total += usd
                subs = sum(r["subtitles"] for r in rows)
                think = sum((r["usage"].get("output_tokens_details") or {}).get("thinking_tokens") or 0 for r in rows)
                print(f"{video_id} {effort:11} {rep}  {tin:6} {tout:7} {think:8} {subs:9}  {usd * windows_per_hour:.4f}")
    print(f"spent on this measurement: about ${total:.3f}")


if __name__ == "__main__":
    main()
