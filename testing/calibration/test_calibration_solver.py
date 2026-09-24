"""Tests the solve stage with independent analytic projections, not field validation."""
import importlib.util
from pathlib import Path
import unittest

import numpy as np

spec = importlib.util.spec_from_file_location("calibrate", Path(__file__).parents[2] / "scripts/calibrate_camera.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class CalibrationSolverTest(unittest.TestCase):
    def test_recovers_known_intrinsics_with_separate_views(self):
        objects = np.zeros((54, 3), np.float32)
        objects[:, :2] = np.mgrid[:9, :6].T.reshape(-1, 2) * .025
        views = []
        for i in range(18):
            ax, ay = -.35 + i * .04, .28 * (-1 if i % 2 else 1)
            rx = np.array([[1, 0, 0], [0, np.cos(ax), -np.sin(ax)], [0, np.sin(ax), np.cos(ax)]])
            ry = np.array([[np.cos(ay), 0, np.sin(ay)], [0, 1, 0], [-np.sin(ay), 0, np.cos(ay)]])
            camera = objects @ (rx @ ry).T + [-.12 + .02 * (i % 4), -.09 + .015 * (i % 3), .65 + .025 * i]
            pixels = camera[:, :2] / camera[:, 2:]
            pixels = pixels * [900, 920] + [640, 480]
            views.append(pixels.astype(np.float32).reshape(-1, 1, 2))
        k, d, rms, held_out, errors = module.solve(objects, views[:14], views[14:], (1280, 960))
        np.testing.assert_allclose([k[0, 0], k[1, 1], k[0, 2], k[1, 2]], [900, 920, 640, 480], atol=.05)
        self.assertLess(held_out, .001)
        self.assertEqual(len(errors), 4)
        with self.assertRaises(ValueError):
            module.solve(objects, views[:5], views[14:], (1280, 960))


if __name__ == "__main__":
    unittest.main()
