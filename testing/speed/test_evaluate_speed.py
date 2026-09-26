import importlib.util
from pathlib import Path
import unittest
import csv
import json
import subprocess
import sys
import tempfile

spec = importlib.util.spec_from_file_location('evaluate_speed', Path(__file__).resolve().parents[2] / 'scripts/evaluate_speed.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


def reference(t, speed):
    return dict(sequence_id='a', track_id='1', calibration_id='c', reference_us=str(t), speed_mps=str(speed))


def result(t, speed):
    return dict(reference(t, speed), algorithm='test', timestamp_us=str(t + 600000),
                speed_kmh=str(speed * 3.6), status='accepted', rejection='', compute_ms='2')


class EvaluationTest(unittest.TestCase):
    def test_known_signed_errors_and_missing_coverage(self):
        report = m.evaluate([result(0, 3), result(1, -1)],
                            [reference(0, 2), reference(1, 2), reference(2, 0)])
        self.assertEqual(report['coverage'], 2 / 3)
        self.assertEqual(report['errors_mps'], dict(mae=2, rmse=5 ** .5, bias=-1, population_std=2))

    def test_exact_reference_time_and_identity_not_arrival(self):
        self.assertEqual(m.evaluate([result(1, 2)], [reference(600001, 2)])['matched_count'], 0)
        other = reference(1, 2)
        other['calibration_id'] = 'different'
        report = m.evaluate([result(1, 2)], [other])
        self.assertIsNone(report['errors_mps']['mae'])
        self.assertEqual(report['accepted_without_reference'], 1)

    def test_rejections_count_without_inventing_speed(self):
        row = result(1, 0)
        row.update(status='rejected', rejection='WARMUP', speed_mps='', speed_kmh='', reference_us='')
        report = m.evaluate([row], [reference(1, 0)])
        self.assertEqual(report['rejections'], {'WARMUP': 1})
        self.assertEqual(report['coverage'], 0)

    def test_duplicates_nonfinite_and_unit_errors_refused(self):
        for rows, refs in [([result(1, 2)] * 2, [reference(1, 2)]),
                           ([result(1, 2)], [reference(1, 2)] * 2),
                           ([result(1, float('nan'))], [reference(1, 2)]),
                           ([dict(result(1, 2), speed_kmh='2')], [reference(1, 2)])]:
            with self.subTest(rows=rows), self.assertRaises(ValueError):
                m.evaluate(rows, refs)

    def test_percentile_interpolates(self):
        self.assertEqual(m.percentile([1, 3], .5), 2)
        self.assertAlmostEqual(m.percentile([1, 3], .95), 2.9)

    def test_cli_writes_hashes_and_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, header, row in [('results', m.RESULT, result(1, 2)),
                                      ('reference', m.REFERENCE, reference(1, 1))]:
                with (root / name).open('w', newline='') as stream:
                    writer = csv.DictWriter(stream, fieldnames=header)
                    writer.writeheader()
                    writer.writerow(row)
            command = [sys.executable, m.__file__, '--results', str(root / 'results'),
                       '--reference', str(root / 'reference'), '--output', str(root / 'report')]
            subprocess.run(command, check=True, capture_output=True)
            report = json.loads((root / 'report').read_text())
            self.assertEqual(report['errors_mps']['mae'], 1)
            self.assertEqual(len(report['input_sha256']['results']), 64)
            original = (root / 'report').read_bytes()
            self.assertNotEqual(subprocess.run(command, capture_output=True).returncode, 0)
            self.assertEqual((root / 'report').read_bytes(), original)
