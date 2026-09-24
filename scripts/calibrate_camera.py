#!/usr/bin/env python3
"""Offline native-image checkerboard calibration. No downloads or automatic captures."""
import argparse
import hashlib
import json
import math
from pathlib import Path

import cv2
import numpy as np


def solve(objects, training, validation, size):
    if len(training) < 12 or len(validation) < 4:
        raise ValueError("At least 12 training and 4 held-out views required")
    rms, k, d, _, _ = cv2.calibrateCamera(
        [objects] * len(training), training, size, None, None
    )
    errors = []
    for corners in validation:
        ok, rotation, translation = cv2.solvePnP(objects, corners, k, d)
        if not ok:
            raise ValueError("Held-out pose could not be solved")
        projected, _ = cv2.projectPoints(objects, rotation, translation, k, d)
        errors.append(float(np.sqrt(np.mean(np.sum((projected - corners) ** 2, axis=2)))))
    held_out = math.sqrt(sum(e * e for e in errors) / len(errors))
    values = np.concatenate((k.ravel(), d.ravel(), [rms, held_out]))
    if not np.isfinite(values).all() or max(errors) > 2 or held_out > 2:
        raise ValueError("Non-finite calibration or held-out reprojection above 2 px")
    return k, d.ravel(), float(rms), held_out, errors


def run(args):
    manifest_path = Path(args.manifest)
    manifest = json.loads(manifest_path.read_text())
    binding = json.loads(Path(args.source).read_text())["binding"]
    size = (binding["nativeWidth"], binding["nativeHeight"])
    columns, rows = manifest["innerCorners"]
    square = float(manifest["squareMeters"])
    if not (3 <= columns <= 30 and 3 <= rows <= 30 and math.isfinite(square) and 0 < square <= 1):
        raise ValueError("Invalid measured board geometry")
    objects = np.zeros((columns * rows, 3), np.float32)
    objects[:, :2] = np.mgrid[:columns, :rows].T.reshape(-1, 2) * square
    seen = set()
    splits = {}
    for split in ("training", "validation"):
        corners_list = []
        for name in manifest[split]:
            path = manifest_path.parent / name
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            if digest in seen:
                raise ValueError("Duplicate image or leakage between splits")
            seen.add(digest)
            gray = cv2.imread(str(path), cv2.IMREAD_GRAYSCALE | cv2.IMREAD_IGNORE_ORIENTATION)
            if gray is None or gray.shape[::-1] != size:
                raise ValueError(f"{name}: native resolution mismatch")
            ok, corners = cv2.findChessboardCornersSB(gray, (columns, rows))
            if not ok:
                raise ValueError(f"{name}: board not found (no silent skipping)")
            corners_list.append(corners)
        splits[split] = corners_list
    k, d, rms, held_out, errors = solve(objects, splits["training"], splits["validation"], size)
    plate = manifest["plate"]
    if not (0.05 <= plate["widthMeters"] <= 2 and 0.02 <= plate["heightMeters"] <= 1
            and plate["widthMeters"] > plate["heightMeters"] and plate["name"]):
        raise ValueError("Invalid known plate dimensions")
    if not (1 <= k[0, 0] <= 100000 and 1 <= k[1, 1] <= 100000
            and 0 <= k[0, 2] < size[0] and 0 <= k[1, 2] < size[1] and np.abs(d).max() <= 10):
        raise ValueError("Unusable intrinsics; improve coverage and pose diversity")
    if not manifest["provenance"].strip():
        raise ValueError("Document source, mode, date and acquisition")
    profile = dict(schemaVersion=1, model="brown5-native", binding=binding,
                   fx=float(k[0, 0]), fy=float(k[1, 1]), cx=float(k[0, 2]), cy=float(k[1, 2]),
                   distortion=d.tolist(), provenance=manifest["provenance"],
                   validationRmsPx=held_out, plate=plate)
    # Exclusive creation protects an existing calibration from accidental replacement.
    with open(args.output, "x") as output:
        json.dump(profile, output, indent=2, allow_nan=False)
    print(json.dumps(dict(opencv=cv2.__version__, trainingRmsPx=rms,
                          validationRmsPx=held_out, validationPerViewPx=errors,
                          note="Reprojection does not validate metric accuracy or acquisition mode")))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--source", required=True, help="Source geometry JSON exported by the app")
    parser.add_argument("--output", required=True)
    run(parser.parse_args())
