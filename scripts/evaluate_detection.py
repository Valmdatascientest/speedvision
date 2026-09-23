#!/usr/bin/env python3
"""Evaluate exported detections against independently annotated pixel boxes (no model needed)."""
import argparse
import json
import math
from pathlib import Path

KINDS = ("vehicle", "plate")

def box_valid(box):
    return len(box) == 4 and all(math.isfinite(v) for v in box) and 0 <= box[0] < box[2] and 0 <= box[1] < box[3]

def iou(a, b):
    intersection = max(0, min(a[2], b[2]) - max(a[0], b[0])) * max(0, min(a[3], b[3]) - max(a[1], b[1]))
    union = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - intersection
    return intersection / union if union else 0

def size_bin(box):
    width = box[2] - box[0]
    return "lt32px" if width < 32 else "32to63px" if width < 64 else "ge64px"

def evaluate(frames, threshold=0.5):
    if not 0 < threshold <= 1:
        raise ValueError("IoU threshold must be in (0,1]")
    counts = {kind: {"tp": 0, "fp": 0, "fn": 0} for kind in KINDS}
    bins = {name: {"found": 0, "total": 0} for name in ("lt32px", "32to63px", "ge64px")}
    seen = set()
    for frame in frames:
        if frame["id"] in seen:
            raise ValueError("Duplicate frame id")
        seen.add(frame["id"])
        for key in ("truth", "predictions"):
            for item in frame[key]:
                if item["kind"] not in KINDS or not box_valid(item["box"]):
                    raise ValueError("Invalid annotation")
                if key == "predictions" and not 0 <= item["score"] <= 1:
                    raise ValueError("Invalid score")
        for kind in KINDS:
            truth = [g for g in frame["truth"] if g["kind"] == kind]
            predictions = sorted((p for p in frame["predictions"] if p["kind"] == kind), key=lambda p: p["score"], reverse=True)
            matched = set()
            for prediction in predictions:
                candidates = [(iou(prediction["box"], g["box"]), i) for i, g in enumerate(truth) if i not in matched]
                overlap, index = max(candidates, default=(0, -1))
                if overlap >= threshold:
                    matched.add(index)
                    counts[kind]["tp"] += 1
                else:
                    counts[kind]["fp"] += 1
            counts[kind]["fn"] += len(truth) - len(matched)
            if kind == "plate":
                for index, target in enumerate(truth):
                    group = bins[size_bin(target["box"])]
                    group["total"] += 1
                    group["found"] += index in matched
    for stats in counts.values():
        stats["precision"] = stats["tp"] / (stats["tp"] + stats["fp"]) if stats["tp"] + stats["fp"] else None
        stats["recall"] = stats["tp"] / (stats["tp"] + stats["fn"]) if stats["tp"] + stats["fn"] else None
    for stats in bins.values():
        stats["recall"] = stats["found"] / stats["total"] if stats["total"] else None
    return {"frames": len(seen), "iou_threshold": threshold, "metrics": counts, "plate_recall_by_native_width": bins}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--iou", type=float, default=0.5)
    args = parser.parse_args()
    data = json.loads(args.manifest.read_text())
    if data.get("schema") != 1 or not data.get("frames"):
        parser.error("Expected schema 1 and a nonempty independently annotated frame list")
    result = evaluate(data["frames"], args.iou)
    result["dataset_note"] = data.get("dataset_note", "Provenance not supplied: not an independent validation claim")
    args.output.write_text(json.dumps(result, indent=2, allow_nan=False) + "\n")
    print(json.dumps(result, indent=2, allow_nan=False))

if __name__ == "__main__":
    main()
