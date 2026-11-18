"""Count how often an agreed Korean term is missing from saved server outputs.

terms.json lists, per evaluation video, the forms the recognizer wrote for a term and the
Korean forms counted as right. The term was said as often as a recognizer form appears in
the window's captions, and a run misses it as often as that exceeds the Korean forms found.

Usage: python eval/count_terms.py <label>
"""

import json
import re
import sys
from collections import defaultdict

from count_baseline import ROOT, load_tokens


def main():
    label = sys.argv[1]
    manifest = json.loads((ROOT / "manifest.json").read_text())
    terms = json.loads((ROOT / "terms.json").read_text())["videos"]
    print("video label        rep  said  missed  missed_terms")
    for video in manifest["videos"]:
        source = " ".join(word for word, _, _ in load_tokens(video["words"]))
        said = {
            term["ko"][0]: sum(len(re.findall(rf"\b{re.escape(form)}\b", source, re.I)) for form in term["heard"])
            for term in terms[video["id"]]
        }
        outputs = defaultdict(str)
        for path in sorted((ROOT / "raw" / "runs" / "server").glob(f"{video['id']}_*_{label}_*.txt")):
            outputs[path.stem.split("_")[3]] += path.read_text()
        for rep, korean in sorted(outputs.items()):
            missed = {}
            for term in terms[video["id"]]:
                found = sum(korean.count(form) for form in term["ko"])
                if said[term["ko"][0]] > found:
                    missed[term["ko"][0]] = said[term["ko"][0]] - found
            print(f"{video['id']}    {label:12} {rep} {sum(said.values()):5} {sum(missed.values()):7}  "
                  + ", ".join(f"{name} {count}" for name, count in missed.items()))


if __name__ == "__main__":
    main()
