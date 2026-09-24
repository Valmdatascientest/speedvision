import csv
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("merge", Path(__file__).parents[2] / "scripts/merge_depth_observations.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class MergeTest(unittest.TestCase):
    def test_orders_explicit_exports_and_refuses_duplicates_or_identity_change(self):
        with tempfile.TemporaryDirectory() as directory:
            paths = []
            for i, t in enumerate([200000, 0, 100000]):
                path = Path(directory) / f"{i}.csv"
                with path.open("w", newline="") as stream:
                    writer = csv.writer(stream)
                    writer.writerow(module.HEADER)
                    writer.writerow(["source", 1, "calibration", t, 20, .9, "true", "true", "true"])
                paths.append(path)
            self.assertEqual([0, 100000, 200000], [int(r["timestamp_us"]) for r in module.merge(paths)])
            with self.assertRaises(ValueError):
                module.merge(paths + [paths[0]])
            paths[0].write_text(paths[0].read_text().replace("source,", "other,"))
            with self.assertRaises(ValueError):
                module.merge(paths)
            with self.assertRaises(ValueError):
                module.merge([])


if __name__ == "__main__":
    unittest.main()
