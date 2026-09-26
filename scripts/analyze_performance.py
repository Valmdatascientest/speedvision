#!/usr/bin/env python3
"""Summarize an explicitly collected repeated-fixture Android benchmark."""
import argparse
import hashlib
import json
import math
from pathlib import Path


def finite(value):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or value < 0:
        raise ValueError('Expected a finite nonnegative number')
    return value


def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * fraction) - 1)]


def analyze(data):
    if data.get('schema') != 1 or data.get('workload') != 'repeated-bus-fixture':
        raise ValueError('Unsupported benchmark schema/workload')
    rows = data['samples']
    if len(rows) < 5:
        raise ValueError('At least five measured samples required')
    previous = -1
    timings = {key: [] for key in ['vehicle_ms', 'plate_ms', 'total_ms', 'call_ms']}
    thermal = {}
    omitted = 0
    for i, row in enumerate(rows):
        start, end = finite(row['start_ns']), finite(row['end_ns'])
        if row['index'] != i or start < previous or end <= start:
            raise ValueError('Unordered, overlapping or zero-length samples')
        previous = end
        call_ms = (end - start) / 1e6
        for key in ['vehicle_ms', 'plate_ms', 'total_ms']:
            timings[key].append(finite(row[key]))
        if not math.isclose(row['total_ms'], row['vehicle_ms'] + row['plate_ms'], abs_tol=1e-5):
            raise ValueError('Inconsistent stage durations')
        if row['total_ms'] > call_ms + 1e-5:
            raise ValueError('Inner duration exceeds call duration')
        timings['call_ms'].append(call_ms)
        for key in ['rois_processed', 'rois_omitted']:
            if type(row[key]) is not int or row[key] < 0:
                raise ValueError('Invalid ROI count')
        if row['rois_processed'] > 4:
            raise ValueError('ROI budget exceeded')
        omitted += row['rois_omitted']
        status = row['thermal_status']
        if status is not None and (type(status) is not int or status not in range(7)):
            raise ValueError('Invalid thermal status')
        label = 'unavailable' if status is None else str(status)
        thermal[label] = thermal.get(label, 0) + 1
    elapsed = (rows[-1]['end_ns'] - rows[0]['start_ns']) / 1e9
    return {
        'schema': 1, 'sample_count': len(rows), 'measured_seconds': elapsed,
        'completed_calls_per_second': len(rows) / elapsed,
        'percentile_method': 'nearest-rank',
        'timings': {key: {'p50': percentile(values, .5), 'p95': percentile(values, .95), 'max': max(values)}
                    for key, values in timings.items()},
        'thermal_status_sample_counts': thermal, 'rois_omitted': omitted,
        'first_quarter_call_p50_ms': percentile(timings['call_ms'][:max(1, len(rows)//4)], .5),
        'last_quarter_call_p50_ms': percentile(timings['call_ms'][-max(1, len(rows)//4):], .5),
        'scope': 'Repeated decoded fixture; excludes camera, decoding, tracking, rendering and energy measurement',
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('benchmark', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    try:
        raw = args.benchmark.read_bytes()
        report = analyze(json.loads(raw))
        report['input_sha256'] = hashlib.sha256(raw).hexdigest()
        with args.output.open('x', encoding='utf-8') as stream:
            stream.write(json.dumps(report, indent=2, allow_nan=False) + '\n')
    except (ValueError, KeyError, TypeError, OSError) as error:
        parser.exit(2, f'Benchmark refused: {error}\n')


if __name__ == '__main__':
    main()
