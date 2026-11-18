"""Measure how long a viewer waits for subtitles at the place they are watching.

Only the 10-minute evaluation windows are saved, so a long video is made by repeating one
window end to end. Its captions are posted with a playback position, and the server is polled
until it returns a subtitle within the minute after that position, then until the video is done.
Repeats run one after another so they do not compete for the model.

Usage: python eval/measure_wait.py <base_url> <label> [repeats]
"""

import base64
import json
import os
import sys
import time

from count_baseline import ROOT
from run_server import call, events

VIDEO = "v1"
COPIES = 6  # 6 windows of 10 minutes make a 1-hour video
POSITION_SEC = 1800  # the viewer starts watching in the middle
POLL_SEC = 0.5
OUT_DIR = ROOT / "raw" / "runs" / "wait"


def long_video(video):
    start, end = video["window_sec"]
    window = events(video)
    return [
        {**event, "tStartMs": event["tStartMs"] + (copy * (end - start) - start) * 1000}
        for copy in range(COPIES)
        for event in window
    ]


def run_one(base_url, video, captions):
    fake_id = base64.urlsafe_b64encode(os.urandom(9)).decode()[:11]
    url = f"{base_url}/api/videos/{fake_id}"
    position = POSITION_SEC * 1000
    t0 = time.time()
    call(f"{url}/translation?positionMs={position}",
         {"title": video["title"], "channel": video["channel"], "events": captions})
    first = None
    while True:
        stored = call(f"{url}/subtitles?positionMs={position}")
        elapsed = round(time.time() - t0, 1)
        if first is None and any(position <= s["startMs"] < position + 60_000 for s in stored["subtitles"]):
            first = elapsed
        if stored["status"] != "PENDING" or elapsed > 1800:
            break
        time.sleep(POLL_SEC)
    return {
        "server_id": fake_id,
        "status": stored["status"],
        "first_subtitle_sec": first,
        "done_sec": elapsed,
        "subtitles": len(stored["subtitles"]),
    }


def main():
    base_url, label = sys.argv[1:3]
    repeats = int(sys.argv[3]) if len(sys.argv) > 3 else 3
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((ROOT / "manifest.json").read_text())
    video = next(v for v in manifest["videos"] if v["id"] == VIDEO)
    captions = long_video(video)

    print("label        rep status  first_subtitle_sec  done_sec  subtitles")
    with (OUT_DIR / f"{label}.jsonl").open("w") as f:
        for rep in range(1, repeats + 1):
            record = {"label": label, "rep": rep, **run_one(base_url, video, captions)}
            f.write(json.dumps(record) + "\n")
            f.flush()
            print(f"{label:12} {rep}   {record['status']:7} {record['first_subtitle_sec']!s:>18} "
                  f"{record['done_sec']:9} {record['subtitles']:10}", flush=True)


if __name__ == "__main__":
    main()
