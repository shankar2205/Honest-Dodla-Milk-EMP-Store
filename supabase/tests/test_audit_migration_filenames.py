"""Regression tests for the migration filename audit script."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "audit_migration_filenames.py"


class MigrationFilenameAuditTests(unittest.TestCase):
    def run_audit(self, names):
        with tempfile.TemporaryDirectory() as directory:
            migrations = Path(directory) / "supabase" / "migrations"
            migrations.mkdir(parents=True)
            for name in names:
                (migrations / name).write_text("-- test migration\n", encoding="utf-8")
            return subprocess.run(
                [sys.executable, str(SCRIPT)],
                cwd=directory,
                capture_output=True,
                text=True,
                check=False,
            )

    def test_unique_timestamped_migrations_pass(self):
        result = self.run_audit([
            "20261023000000_atomic_batch.sql",
            "20261023010000_policy_tuning.sql",
        ])
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("Filename audit passed", result.stdout)

    def test_legacy_numeric_collisions_warn_without_failing(self):
        result = self.run_audit([
            "0002_first_change.sql",
            "0002_second_change.sql",
        ])
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("Legacy numeric prefix collisions", result.stdout)

    def test_invalid_calendar_timestamp_fails(self):
        for name in ("20261340000000_invalid_month_day.sql", "20261023256000_invalid_time.sql"):
            with self.subTest(name=name):
                result = self.run_audit([name])
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("Invalid timestamped migration version", result.stdout)

    def test_valid_leap_day_timestamp_passes(self):
        result = self.run_audit(["20240229010203_leap_day.sql"])
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_duplicate_timestamp_versions_fail(self):
        result = self.run_audit([
            "20261023000000_first.sql",
            "20261023000000_second.sql",
        ])
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Duplicate timestamped migration version", result.stdout)

    def test_unrecognized_prefix_fails(self):
        result = self.run_audit(["initial_schema.sql"])
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Unrecognized migration filename prefix", result.stdout)

    def test_timestamped_file_requires_descriptive_suffix(self):
        result = self.run_audit(["20261023000000_.sql"])
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("descriptive suffix", result.stdout)


if __name__ == "__main__":
    unittest.main()
