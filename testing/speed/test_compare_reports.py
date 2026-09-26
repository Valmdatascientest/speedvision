import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('compare', Path(__file__).resolve().parents[2] / 'scripts/compare_speed_reports.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


def report():
    return dict(schema_version=1, reference_count=10, matched_count=10, coverage=1,
                algorithm='baseline', input_sha256={'reference': 'a'*64},
                errors_mps=dict(mae=2, rmse=3, bias=-1, population_std=2))


class ComparisonTest(unittest.TestCase):
    def test_error_improvement_preserves_coverage_loss(self):
        candidate = report()
        candidate.update(algorithm='candidate', matched_count=5, coverage=.5)
        candidate['errors_mps']['mae'] = 1
        delta = m.compare(report(), candidate)
        self.assertEqual(delta['error_delta_mps']['mae'], -1)
        self.assertEqual(delta['coverage_delta'], -.5)
        self.assertFalse(delta['paired_error_comparison'])

    def test_different_reference_or_inconsistent_counts_refused(self):
        for patch in [dict(input_sha256={'reference': 'b'*64}), dict(matched_count=11), dict(coverage=.3)]:
            candidate = dict(report(), **patch)
            with self.assertRaises(ValueError):
                m.compare(report(), candidate)

    def test_no_comparisons_never_looks_like_zero_error(self):
        candidate = report()
        candidate.update(matched_count=0, coverage=0)
        candidate['errors_mps'] = dict.fromkeys(candidate['errors_mps'])
        self.assertIsNone(m.compare(report(), candidate)['error_delta_mps']['mae'])

    def test_nonfinite_or_negative_metrics_refused(self):
        for value in [float('nan'), float('inf'), -1]:
            candidate = copy.deepcopy(report())
            candidate['errors_mps']['mae'] = value
            with self.assertRaises(ValueError):
                m.compare(report(), candidate)
