"""Count defect candidates in saved model outputs with the baseline's rules.

Each subtitle covers the raw caption words from its start time to the next
subtitle's start time. The break and speaker rules are then the same ones
count_baseline.py applies to the subtitles the user watches now.

Usage: python eval/score_runs.py [prompt_version]
"""

import json
import re
import sys
from collections import defaultdict

from count_baseline import ROOT, SENTENCE_END, load_tokens, register

LINE = re.compile(r"^(\d+)\|(\S.*)$")
MAX_CHARS = 45  # the prompt asks for about this many Korean characters per subtitle


def score_chunk(text, tokens, line_starts, chunk_end_ms):
    """Count defects in one chunk's output against the raw caption tokens."""
    lines = [line for line in text.splitlines() if line.strip()]
    subs = [(int(m.group(1)), m.group(2)) for m in map(LINE.match, lines) if m]
    word_starts = {start for _, _, start in tokens}
    counts = defaultdict(int)
    counts["subtitles"] = len(subs)
    counts["bad_format"] = len(lines) - len(subs)
    previous = None
    for i, (start, korean) in enumerate(subs):
        end = subs[i + 1][0] if i + 1 < len(subs) else chunk_end_ms
        covered = [t for t in tokens if start <= t[2] < end]
        counts["out_of_order"] += end < start
        counts["off_word_start"] += start not in word_starts
        counts["empty"] += not covered
        broken = bool(covered) and not SENTENCE_END.search(covered[-1][0])
        counts["break"] += broken
        # A break whose next subtitle starts exactly where a caption line starts points at
        # a start time copied from the line instead of taken from the first word.
        counts["break_on_line"] += broken and end in line_starts
        counts["speakers"] += any(t[1] for t in covered[1:])
        counts["too_long"] += len(korean) > MAX_CHARS
        style = register(korean)
        counts["register"] += bool(style and previous and style != previous)
        previous = style or previous
    return counts


def main():
    version = sys.argv[1] if len(sys.argv) > 1 else "v0"
    run_dir = ROOT / "raw" / "runs" / version
    manifest = json.loads((ROOT / "manifest.json").read_text())
    keys = ["subtitles", "bad_format", "break", "break_on_line", "speakers", "register", "too_long", "off_word_start", "empty", "out_of_order"]
    print("video condition    rep " + " ".join(f"{k:>14}" for k in keys))
    for video in manifest["videos"]:
        tokens = load_tokens(video["words"])
        line_starts = {int(line.split(" ", 1)[0]) for line in (ROOT / video["words"]).read_text().splitlines()}
        totals = defaultdict(lambda: defaultdict(int))
        for path in sorted(run_dir.glob(f"{video['id']}_*.txt")):
            _, chunk_start, condition, rep = path.stem.split("_")
            counts = score_chunk(path.read_text(), tokens, line_starts, (int(chunk_start) + 300) * 1000)
            for key, value in counts.items():
                totals[(condition, rep)][key] += value
        for (condition, rep), counts in sorted(totals.items()):
            print(f"{video['id']}    {condition:12} {rep}   " + " ".join(f"{counts[k]:14}" for k in keys))


if __name__ == "__main__":
    main()
