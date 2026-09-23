import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("evaluation", Path(__file__).resolve().parents[2] / "scripts/evaluate_detection.py")
evaluation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evaluation)

class DetectionMetricsTest(unittest.TestCase):
    def test_duplicate_prediction_is_false_positive_and_miss_counts(self):
        box = [0, 0, 20, 10]
        frames = [{"id": "synthetic", "truth": [{"kind": "plate", "box": box}, {"kind": "plate", "box": [40, 0, 120, 10]}],
                   "predictions": [{"kind": "plate", "box": box, "score": 0.9}, {"kind": "plate", "box": box, "score": 0.8}]}]
        result = evaluation.evaluate(frames)
        self.assertEqual(result["metrics"]["plate"], {"tp": 1, "fp": 1, "fn": 1, "precision": 0.5, "recall": 0.5})
        self.assertEqual(result["plate_recall_by_native_width"]["lt32px"]["recall"], 1)
        self.assertEqual(result["plate_recall_by_native_width"]["ge64px"]["recall"], 0)
    def test_empty_truth_does_not_invent_perfect_recall(self):
        result = evaluation.evaluate([{"id": "negative", "truth": [], "predictions": []}])
        self.assertIsNone(result["metrics"]["plate"]["recall"])
    def test_invalid_annotation_and_duplicate_id_rejected(self):
        frame = {"id": "x", "truth": [], "predictions": []}
        with self.assertRaises(ValueError):
            evaluation.evaluate([frame, frame])
        with self.assertRaises(ValueError):
            evaluation.evaluate([{"id": "invalid", "truth": [{"kind": "plate", "box": [0, 0, 0, 5]}], "predictions": []}])

if __name__ == "__main__":
    unittest.main()
