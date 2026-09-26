#!/usr/bin/env python3
"""Evaluate exported historical speeds against an independent, exact-time reference."""
import argparse
import csv
import hashlib
import json
import math
from collections import Counter
from pathlib import Path

REFERENCE = ['sequence_id', 'track_id', 'calibration_id', 'reference_us', 'speed_mps']
RESULT = 'algorithm,sequence_id,track_id,calibration_id,timestamp_us,input_depth_m,status,rejection,speed_mps,speed_kmh,quality_score,samples,inliers,reference_us,window_us,residual_m,compute_ms'.split(',')


def number(value):
    result = float(value)
    if not math.isfinite(result):
        raise ValueError('Non-finite number')
    return result


def integer(value):
    result = int(value)
    if result < 0:
        raise ValueError('Negative timestamp or track')
    return result


def identity(row):
    if not row['sequence_id'] or not row['calibration_id']:
        raise ValueError('Empty identity')
    return row['sequence_id'], integer(row['track_id']), row['calibration_id']


def read(path, header):
    with Path(path).open(encoding='utf-8-sig', newline='') as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != header:
            raise ValueError('Unexpected CSV header')
        rows = list(reader)
    if not rows or any(None in row or None in row.values() for row in rows):
        raise ValueError('Empty or malformed CSV')
    return rows


def percentile(values, fraction):
    values = sorted(values)
    position = (len(values) - 1) * fraction
    lo = math.floor(position)
    hi = math.ceil(position)
    return values[lo] + (values[hi] - values[lo]) * (position - lo)


def evaluate(results, references):
    truth = {}
    for row in references:
        key = (*identity(row), integer(row['reference_us']))
        if key in truth:
            raise ValueError('Duplicate reference identity/time')
        truth[key] = number(row['speed_mps'])
    if not truth or not results:
        raise ValueError('Empty evaluation')
    algorithms = {row['algorithm'] for row in results}
    if len(algorithms) != 1 or not next(iter(algorithms)):
        raise ValueError('Evaluate one named algorithm at a time')
    seen, predictions, errors, durations = set(), set(), [], []
    rejections = Counter()
    unmatched = 0
    for row in results:
        ident = identity(row)
        key = (*ident, integer(row['timestamp_us']))
        if key in seen:
            raise ValueError('Duplicate result identity/input time')
        seen.add(key)
        duration = number(row['compute_ms'])
        if duration < 0:
            raise ValueError('Negative compute time')
        durations.append(duration)
        if row['status'] == 'rejected':
            if not row['rejection'] or any(row[k] for k in ('speed_mps', 'speed_kmh', 'reference_us')):
                raise ValueError('Invalid rejected result')
            rejections[row['rejection']] += 1
            continue
        if row['status'] != 'accepted' or row['rejection']:
            raise ValueError('Invalid status')
        speed = number(row['speed_mps'])
        if not math.isclose(number(row['speed_kmh']), speed * 3.6, abs_tol=1e-6):
            raise ValueError('Inconsistent speed units')
        reference_time = integer(row['reference_us'])
        if reference_time > key[-1]:
            raise ValueError('Reference time after input time')
        target = (*ident, reference_time)
        if target in predictions:
            raise ValueError('Duplicate prediction reference time')
        predictions.add(target)
        if target in truth:
            errors.append(speed - truth[target])
        else:
            unmatched += 1
    bias = sum(errors) / len(errors) if errors else None
    return {
        'schema_version': 1, 'algorithm': next(iter(algorithms)),
        'reference_count': len(truth), 'result_count': len(results),
        'matched_count': len(errors), 'coverage': len(errors) / len(truth),
        'missing_reference_predictions': len(truth) - len(errors),
        'accepted_without_reference': unmatched, 'rejections': dict(sorted(rejections.items())),
        'errors_mps': {
            'mae': sum(abs(e) for e in errors) / len(errors) if errors else None,
            'rmse': math.sqrt(sum(e * e for e in errors) / len(errors)) if errors else None,
            'bias': bias,
            'population_std': math.sqrt(sum((e - bias) ** 2 for e in errors) / len(errors)) if errors else None,
        },
        'replay_compute_ms': {'p50': percentile(durations, .5), 'p95': percentile(durations, .95)},
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--results', type=Path, required=True)
    parser.add_argument('--reference', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        report = evaluate(read(args.results, RESULT), read(args.reference, REFERENCE))
        report['input_sha256'] = {name: hashlib.sha256(path.read_bytes()).hexdigest()
                                  for name, path in [('results', args.results), ('reference', args.reference)]}
        with args.output.open('x', encoding='utf-8') as stream:
            stream.write(json.dumps(report, indent=2, allow_nan=False) + '\n')
    except (ValueError, OSError) as error:
        parser.exit(2, f'Evaluation refused: {error}\n')


if __name__ == '__main__':
    main()
