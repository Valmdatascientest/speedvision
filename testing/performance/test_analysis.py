import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('performance', Path(__file__).resolve().parents[2] / 'scripts/analyze_performance.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


def fixture():
    return dict(schema=1, workload='repeated-bus-fixture', samples=[
        dict(index=i, start_ns=i*20_000_000, end_ns=i*20_000_000+10_000_000,
             vehicle_ms=6, plate_ms=2, total_ms=8, rois_processed=1, rois_omitted=2, thermal_status=None)
        for i in range(5)])


class PerformanceTest(unittest.TestCase):
    def test_wall_throughput_includes_gaps_and_percentiles_exclude_cold_start(self):
        data = fixture()
        data['cold_call_ms'] = 9000
        report = m.analyze(data)
        self.assertAlmostEqual(report['completed_calls_per_second'], 5/.09)
        self.assertEqual(report['timings']['call_ms']['p95'], 10)
        self.assertEqual(report['rois_omitted'], 10)
        self.assertEqual(report['thermal_status_sample_counts'], {'unavailable': 5})

    def test_nearest_rank_and_thermal_counts(self):
        data = fixture()
        data['samples'][0]['thermal_status'] = 3
        self.assertEqual(m.analyze(data)['thermal_status_sample_counts'], {'3': 1, 'unavailable': 4})
        self.assertEqual(m.percentile(list(range(1, 21)), .95), 19)

    def test_corrupt_measurements_refused(self):
        for patch in [dict(start_ns=0), dict(end_ns=0), dict(total_ms=20),
                      dict(vehicle_ms=float('nan')), dict(thermal_status=7), dict(rois_processed=5),
                      dict(rois_omitted=-1), dict(index=0)]:
            data = copy.deepcopy(fixture())
            data['samples'][1].update(patch)
            with self.subTest(patch=patch), self.assertRaises(ValueError):
                m.analyze(data)

    def test_short_or_unknown_capture_refused(self):
        for data in [dict(fixture(), schema=2), dict(fixture(), samples=[])]:
            with self.assertRaises(ValueError):
                m.analyze(data)
