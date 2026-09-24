#!/usr/bin/env python3
"""Combine explicit single-identity depth CSV exports, sorted by source timestamp. No interpolation."""
import argparse
import csv
from pathlib import Path

HEADER = ["sequence_id", "track_id", "calibration_id", "timestamp_us", "depth_m", "quality", "geometry_valid", "camera_fixed", "track_observed"]


def merge(paths):
    rows = []
    identity = None
    timestamps = set()
    for path in paths:
        if Path(path).stat().st_size > 524288:
            raise ValueError("Input too large")
        with open(path, newline="", encoding="utf-8-sig") as stream:
            reader = csv.DictReader(stream)
            if reader.fieldnames != HEADER:
                raise ValueError("Incompatible observation CSV header")
            for row in reader:
                if None in row or any(value is None for value in row.values()):
                    raise ValueError("Invalid column count")
                key = tuple(row[name] for name in ("sequence_id", "track_id", "calibration_id"))
                if identity is not None and key != identity:
                    raise ValueError("Different source/track/calibration: never merge into one window")
                identity = key
                timestamp = int(row["timestamp_us"])
                if timestamp < 0 or timestamp in timestamps:
                    raise ValueError("Negative or duplicate source timestamp")
                timestamps.add(timestamp)
                rows.append(row)
                if len(rows) > 5000:
                    raise ValueError("Too many observations")
    if not rows:
        raise ValueError("No observations")
    return sorted(rows, key=lambda row: int(row["timestamp_us"]))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True)
    parser.add_argument("inputs", nargs="+")
    args = parser.parse_args()
    rows = merge(args.inputs)
    with open(args.output, "x", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=HEADER)
        writer.writeheader()
        writer.writerows(rows)
    print(f"{len(rows)} observations; timestamps and gaps preserved")
