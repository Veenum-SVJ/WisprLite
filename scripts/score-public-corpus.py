"""Score opt-in public-corpus results; never reads app settings or credentials."""
import argparse
import json
import unicodedata
from pathlib import Path


def normalized(text, fold_marks=False):
    text = unicodedata.normalize("NFC", text).lower()
    if fold_marks:
        text = "".join(c for c in unicodedata.normalize("NFD", text)
                       if not unicodedata.category(c).startswith("M"))
    return " ".join("".join(c if unicodedata.category(c)[0] in "LMN" else " "
                            for c in text).split())


def edits(expected, actual):
    previous = list(range(len(actual) + 1))
    for index, reference in enumerate(expected, 1):
        current = [index]
        for offset, recognized in enumerate(actual, 1):
            current.append(min(current[-1] + 1, previous[offset] + 1,
                               previous[offset - 1] + (reference != recognized)))
        previous = current
    return previous[-1]


def score(references, results):
    measurements = []
    for row in results["results"]:
        reference = references[row["fixture"]]
        report = {key: row[key] for key in ("fixture", "sha256", "language", "model", "elapsed_ms", "error")}
        if row["error"] is None and isinstance(row.get("text"), str) and row["text"].strip():
            expected, actual = normalized(reference), normalized(row["text"])
            assert expected, "Reference must contain words"
            expected_words, actual_words = expected.split(), actual.split()
            report.update(reference_words=len(expected_words),
                          word_edits=edits(expected_words, actual_words),
                          wer=edits(expected_words, actual_words) / len(expected_words),
                          cer=edits(expected, actual) / len(expected))
            folded_reference = normalized(reference, True).split()
            report["mark_folded_wer"] = edits(folded_reference, normalized(row["text"], True).split()) / len(folded_reference)
        else:
            report["scored"] = False
        measurements.append(report)
    return {"provider": results["provider"], "measurements": measurements,
            "scope": "Two fixed public clips; not a representative benchmark or personal-voice acceptance"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("references", type=Path, help="JSON mapping fixture filenames to reference text")
    parser.add_argument("results", type=Path, help="PublicCorpusEvaluationTest results JSON")
    args = parser.parse_args()
    print(json.dumps(score(json.loads(args.references.read_text(encoding="utf-8")),
                           json.loads(args.results.read_text(encoding="utf-8"))),
                     ensure_ascii=False, indent=2))
