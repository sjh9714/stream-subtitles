"""Count defect candidates in the subtitles the user currently watches.

Three of the five evaluation items can be counted by rule, so this script counts
them and writes one row per cue for a person to confirm. Wrong terms and
misaligned lines need reading and are not counted here.

Usage: python eval/count_baseline.py
"""

import json
import re
from pathlib import Path

ROOT = Path(__file__).parent
CUE_HEAD = re.compile(r"^\[(\d+):(\d+)(?::(\d+))?\] (.*)$")
SENTENCE_END = re.compile(r"[.?!][\"'”’)]*$")


def load_cues(paths):
    """Read (source, translation) cues in order, dropping the overlap between captures."""
    cues, seen = [], set()
    for path in paths:
        lines = (ROOT / path).read_text().splitlines()
        for i, line in enumerate(lines):
            head = CUE_HEAD.match(line)
            if not head:
                continue
            source = head.group(4).strip()
            following = lines[i + 1] if i + 1 < len(lines) else ""
            target = "" if CUE_HEAD.match(following) else following.strip()
            if source not in seen:
                seen.add(source)
                cues.append({"at": line[: line.index("]") + 1], "source": source, "target": target})
    return cues


def load_tokens(path):
    """Flatten the raw caption words into (word, starts_new_speaker, start_ms) triples."""
    tokens, pending = [], False
    for line in (ROOT / path).read_text().splitlines():
        line_start, *raws = line.split(" ")
        for raw in raws:
            word, _, offset = raw.partition("@") if re.search(r"@\d+$", raw) else (raw, "", "0")
            if word == ">>":
                pending = True
            elif word and not re.match(r"^[\[\]_]", word) and not word.endswith("]"):
                tokens.append((word, pending, int(line_start) + int(offset)))
                pending = False
    return tokens


def speaker_changes_inside(cue_words, tokens, cursor):
    """Count speaker marks that fall inside a cue, not at its first word."""
    if not cue_words:
        return 0, cursor
    start = next((i for i in range(cursor, min(cursor + 80, len(tokens))) if tokens[i][0] == cue_words[0]), None)
    if start is None:
        return 0, cursor
    end = min(start + len(cue_words), len(tokens))
    return sum(1 for token in tokens[start + 1 : end] if token[1]), end


def register(text):
    """Classify the sentence ending of a Korean line, or None when it has none."""
    t = re.sub(r"[\s\"'”’)\]….,!?~]+$", "", text)
    if len(t) < 2:
        return None
    if re.search(r"(습니다|ㅂ니다|니다|니까|십시오)$", t):
        return "합쇼체"
    if t.endswith("요"):
        return "해요체"
    if re.search(r"(한다|는다|ㄴ다|었다|았다|였다|이다|겠다|했다|된다|있다|없다)$", t):
        return "문어체"
    if re.search(r"(어|아|야|지|네|해|래|게|걸|군|나|까|냐|니|자|줘|돼|봐|대)$", t):
        return "반말"
    return None


def main():
    manifest = json.loads((ROOT / "manifest.json").read_text())
    print("video cues  mid-sentence-break  register-change  cues-mixing-speakers")
    for video in manifest["videos"]:
        cues = load_cues(video["current"])
        tokens = load_tokens(video["words"])
        cursor, previous, rows = 0, None, []
        totals = {"break": 0, "register": 0, "speakers": 0}
        for cue in cues:
            broken = not SENTENCE_END.search(cue["source"])
            mixed, cursor = speaker_changes_inside(cue["source"].split(), tokens, cursor)
            style = register(cue["target"])
            changed = bool(style and previous and style != previous)
            previous = style or previous
            totals["break"] += broken
            totals["register"] += changed
            totals["speakers"] += mixed > 0
            flags = [name for name, on in (("끊김", broken), ("말투", changed), ("화자", mixed > 0)) if on]
            rows.append("\t".join([cue["at"], ",".join(flags), style or "", cue["source"], cue["target"]]))
        out = ROOT / "raw" / f"{video['id']}.baseline_candidates.tsv"
        out.write_text("시간\t후보\t말투\t영어\t지금 자막\n" + "\n".join(rows) + "\n")
        print(f"{video['id']}    {len(cues):4}  {totals['break']:18}  {totals['register']:15}  {totals['speakers']:20}")


if __name__ == "__main__":
    main()
