"""Count how often a term is not written the agreed way in saved server outputs.

terms.json lists, per evaluation video, the forms the recognizer wrote for a term and the
written forms counted as right. The term was said as often as a recognizer form appears in
the window's captions, and a run misses it as often as that exceeds the right forms found.
A form in Latin letters is matched as a whole word, so "Hazel" does not count as "Haze".

Usage: python eval/count_terms.py <label>
"""

import json
import re
import sys
from collections import defaultdict

from count_baseline import ROOT, load_tokens


def count(form, text):
    if form.isascii():
        return len(re.findall(rf"(?<![A-Za-z]){re.escape(form)}(?![A-Za-z])", text))
    return text.count(form)


def main():
    label = sys.argv[1]
    manifest = json.loads((ROOT / "manifest.json").read_text())
    terms = json.loads((ROOT / "terms.json").read_text())["videos"]
    print("video label        rep  said  missed  missed_terms")
    for video in manifest["videos"]:
        source = " ".join(word for word, _, _ in load_tokens(video["words"]))
        said = {
            term["right"][0]: sum(len(re.findall(rf"\b{re.escape(form)}\b", source, re.I)) for form in term["heard"])
            for term in terms[video["id"]]
        }
        outputs = defaultdict(str)
        for path in sorted((ROOT / "raw" / "runs" / "server").glob(f"{video['id']}_*_{label}_*.txt")):
            outputs[path.stem.split("_")[3]] += path.read_text()
        for rep, korean in sorted(outputs.items()):
            missed = {}
            for term in terms[video["id"]]:
                found = sum(count(form, korean) for form in term["right"])
                if said[term["right"][0]] > found:
                    missed[term["right"][0]] = said[term["right"][0]] - found
            print(f"{video['id']}    {label:12} {rep} {sum(said.values()):5} {sum(missed.values()):7}  "
                  + ", ".join(f"{name} {count}" for name, count in missed.items()))


if __name__ == "__main__":
    main()
