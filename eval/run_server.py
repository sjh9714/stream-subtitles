"""Send the evaluation windows through a running server and save what it stores.

Each (video, repeat) is posted under a made-up video id, because the server
translates a given id only once. The stored subtitles are written per 5-minute
chunk as "<start_ms>|<Korean>" lines, the format score_runs.py reads, and the
token usage the server recorded is read back from its database container.

Usage: python eval/run_server.py <base_url> <label> <db_container> [repeats]
  then: python eval/score_runs.py server
"""

import base64
import json
import os
import re
import subprocess
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

from count_baseline import ROOT

PRICE_IN, PRICE_OUT = 0.10, 0.50  # USD per million tokens, Claude Haiku 5.5
CHUNK_SEC = 300
OUT_DIR = ROOT / "raw" / "runs" / "server"


def events(video):
    """Turn the saved word file back into the json3 events the extension would send."""
    result = []
    for line in (ROOT / video["words"]).read_text().splitlines():
        start, *raws = line.split(" ")
        segs = []
        for raw in raws:
            word, _, offset = raw.partition("@") if re.search(r"@\d+$", raw) else (raw, "", "")
            segs.append({"utf8": " " + word, **({"tOffsetMs": int(offset)} if offset else {})})
        result.append({"tStartMs": int(start), "segs": segs})
    return result


def call(url, body=None):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read())


def run_one(base_url, label, video, rep):
    fake_id = base64.urlsafe_b64encode(os.urandom(9)).decode()[:11]
    url = f"{base_url}/api/videos/{fake_id}"
    t0 = time.time()
    call(f"{url}/translation", {"title": video["title"], "channel": video["channel"], "events": events(video)})
    while True:
        stored = call(f"{url}/subtitles")
        if stored["status"] != "PENDING":
            break
        if time.time() - t0 > 900:
            stored = {"status": "TIMEOUT", "subtitles": []}
            break
        time.sleep(2)
    start, end = video["window_sec"]
    for lo in range(start, end, CHUNK_SEC):
        lines = [
            f"{s['startMs']}|{s['text']}"
            for s in stored["subtitles"]
            if lo * 1000 <= s["startMs"] < (lo + CHUNK_SEC) * 1000
        ]
        (OUT_DIR / f"{video['id']}_{lo}_{label}_{rep}.txt").write_text("\n".join(lines) + "\n")
    return {
        "video": video["id"],
        "label": label,
        "rep": rep,
        "server_id": fake_id,
        "status": stored["status"],
        "seconds": round(time.time() - t0, 1),
        "subtitles": len(stored["subtitles"]),
    }


def usage(db_container):
    """Read (video id -> list of (input tokens, output tokens, millis)) from the server's database."""
    sql = "select youtube_id, input_tokens, output_tokens, millis from translation_chunk order by 1, chunk_index"
    out = subprocess.run(
        ["docker", "exec", db_container, "psql", "-U", "subtitles", "-d", "subtitles", "-At", "-F", " ", "-c", sql],
        check=True, capture_output=True, text=True,
    ).stdout
    rows = {}
    for line in out.splitlines():
        video_id, *numbers = line.split(" ")
        rows.setdefault(video_id, []).append(tuple(map(int, numbers)))
    return rows


def main():
    base_url, label, db_container = sys.argv[1:4]
    repeats = int(sys.argv[4]) if len(sys.argv) > 4 else 3
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((ROOT / "manifest.json").read_text())

    records = []
    for video in manifest["videos"]:  # one video at a time, its repeats together
        with ThreadPoolExecutor(max_workers=repeats) as pool:
            records += pool.map(lambda rep: run_one(base_url, label, video, rep), range(1, repeats + 1))

    chunks = usage(db_container)
    windows_per_hour = 3600 / (CHUNK_SEC * 2)  # each window is two chunks
    print("video label        rep status  subtitles  in_tok out_tok  model_sec  usd_per_video_hour")
    for record in records:
        rows = chunks.get(record["server_id"], [])
        tin, tout = sum(r[0] for r in rows), sum(r[1] for r in rows)
        record.update(input_tokens=tin, output_tokens=tout, model_seconds=round(sum(r[2] for r in rows) / 1000, 1))
        usd = (tin * PRICE_IN + tout * PRICE_OUT) / 1e6 * windows_per_hour
        print(
            f"{record['video']}    {label:12} {record['rep']}   {record['status']:7} {record['subtitles']:9} "
            f"{tin:7} {tout:7} {record['model_seconds']:10}  {usd:.4f}"
        )
    with (OUT_DIR / f"{label}.jsonl").open("w") as f:
        for record in records:
            f.write(json.dumps(record, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    main()
