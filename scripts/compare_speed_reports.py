#!/usr/bin/env python3
"""Compare two speed evaluations on exactly the same reference, without hiding coverage."""
import argparse
import json
import math
from pathlib import Path


def compare(baseline, candidate):
    for report in (baseline, candidate):
        if report['schema_version'] != 1:
            raise ValueError('Unsupported evaluation schema')
        count, matched = report['reference_count'], report['matched_count']
        if type(count) is not int or type(matched) is not int or count <= 0 or not 0 <= matched <= count:
            raise ValueError('Invalid comparison counts')
        if not math.isclose(report['coverage'], matched/count, abs_tol=1e-12):
            raise ValueError('Inconsistent coverage')
        digest = report['input_sha256']['reference']
        if len(digest) != 64 or any(c not in '0123456789abcdef' for c in digest):
            raise ValueError('Missing reference fingerprint')
        for key in ('mae', 'rmse', 'bias', 'population_std'):
            value = report['errors_mps'][key]
            if matched == 0:
                if value is not None:
                    raise ValueError('Empty comparison must not claim an error metric')
            elif not isinstance(value, (int, float)) or not math.isfinite(value) or (key != 'bias' and value < 0):
                raise ValueError('Invalid error metric')
    if (baseline['input_sha256']['reference'] != candidate['input_sha256']['reference'] or
            baseline['reference_count'] != candidate['reference_count']):
        raise ValueError('Reports must use the exact same reference CSV')
    deltas = {}
    for key in ('mae', 'rmse', 'bias', 'population_std'):
        a, b = baseline['errors_mps'][key], candidate['errors_mps'][key]
        deltas[key] = None if a is None or b is None else b-a
    return dict(schema=1, baseline=baseline['algorithm'], candidate=candidate['algorithm'],
                reference_sha256=baseline['input_sha256']['reference'],
                error_delta_mps=deltas, coverage_delta=candidate['coverage']-baseline['coverage'],
                baseline_matched=baseline['matched_count'], candidate_matched=candidate['matched_count'],
                paired_error_comparison=False,
                interpretation='Aggregate errors may cover different accepted samples; no automatic winner or significance claim')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('baseline', type=Path)
    parser.add_argument('candidate', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        report = compare(json.loads(args.baseline.read_text()), json.loads(args.candidate.read_text()))
        with args.output.open('x', encoding='utf-8') as stream:
            stream.write(json.dumps(report, indent=2, allow_nan=False) + '\n')
    except (ValueError, KeyError, TypeError, OSError) as error:
        parser.exit(2, f'Comparison refused: {error}\n')


if __name__ == '__main__':
    main()
