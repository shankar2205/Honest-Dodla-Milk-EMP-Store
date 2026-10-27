import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "compare_catalog_fingerprints.py"
sys.path.insert(0, str(SCRIPT.parent))


class CatalogFingerprintComparisonTests(unittest.TestCase):
    def test_identical_snapshots_match(self):
        from compare_catalog_fingerprints import compare_json

        self.assertEqual(compare_json({"functions": [{"name": "f", "secure": True}]},
                                      {"functions": [{"name": "f", "secure": True}]}), [])

    def test_object_fields_are_reported_at_stable_paths(self):
        from compare_catalog_fingerprints import compare_json

        diffs = compare_json(
            {"functions": {"public.f()": {"security_definer": True}}},
            {"functions": {"public.f()": {"security_definer": False}}},
        )
        self.assertEqual(len(diffs), 1)
        self.assertEqual(diffs[0].path, "/functions/public.f()/security_definer")
        self.assertEqual(diffs[0].kind, "changed")
        self.assertTrue(diffs[0].baseline)
        self.assertFalse(diffs[0].candidate)

    def test_array_addition_and_removal_are_reported(self):
        from compare_catalog_fingerprints import compare_json

        diffs = compare_json(
            {"policies": [{"name": "old"}]},
            {"policies": [{"name": "new"}]},
        )
        self.assertEqual({item.kind for item in diffs}, {"added", "removed"})
        self.assertEqual(len(diffs), 2)

    def test_cli_fail_on_diff_returns_nonzero(self):
        with tempfile.TemporaryDirectory() as directory:
            baseline = Path(directory) / "baseline.json"
            candidate = Path(directory) / "candidate.json"
            baseline.write_text(json.dumps({"version": 1}), encoding="utf-8")
            candidate.write_text(json.dumps({"version": 2}), encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(SCRIPT), str(baseline), str(candidate), "--fail-on-diff"],
                check=False, capture_output=True, text=True,
            )
            self.assertEqual(result.returncode, 1)
            self.assertIn("Catalog drift detected", result.stdout)
            self.assertIn("/version", result.stdout)

    def test_invalid_json_is_an_input_error(self):
        with tempfile.TemporaryDirectory() as directory:
            baseline = Path(directory) / "baseline.json"
            candidate = Path(directory) / "candidate.json"
            baseline.write_text("{", encoding="utf-8")
            candidate.write_text("{}", encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(SCRIPT), str(baseline), str(candidate)],
                check=False, capture_output=True, text=True,
            )
            self.assertEqual(result.returncode, 2)
            self.assertIn("cannot read valid JSON", result.stderr)


if __name__ == "__main__":
    unittest.main()
