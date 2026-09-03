#!/usr/bin/env python3
"""
Unit tests for scripts/generate_release_checksums.py.

Verifies:
1. Commit SHA resolution order (override > env > git rev-parse > GITHUB_SHA > unknown)
2. Fail-closed validation when 0 artifacts match (exit 1)
3. Fail-closed validation when APK is missing (exit 1)
4. Fail-closed validation when AAB is missing (exit 1)
5. Success case when both APK and AAB are present (exit 0)
6. Checksum and size accuracy
7. CLI argument parsing and subprocess execution
"""

import hashlib
import os
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

# Add scripts directory to sys.path
SCRIPTS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if SCRIPTS_DIR not in sys.path:
    sys.path.insert(0, SCRIPTS_DIR)

import generate_release_checksums


class TestGenerateReleaseChecksums(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()

    def tearDown(self):
        self.temp_dir.cleanup()

    def _create_dummy_file(self, filename: str, content: bytes = b"dummy binary data") -> str:
        filepath = os.path.join(self.temp_dir.name, filename)
        os.makedirs(os.path.dirname(filepath), exist_ok=True)
        with open(filepath, "wb") as f:
            f.write(content)
        return filepath

    # --- Commit SHA Tests ---

    def test_explicit_override_argument(self):
        sha = generate_release_checksums.get_commit_sha(override_sha="1111222233334444555566667777888899990000")
        self.assertEqual(sha, "1111222233334444555566667777888899990000")

    def test_explicit_env_var_override(self):
        with patch.dict(os.environ, {"RELEASE_COMMIT_SHA": "abcdef1234567890abcdef1234567890abcdef12"}):
            sha = generate_release_checksums.get_commit_sha()
            self.assertEqual(sha, "abcdef1234567890abcdef1234567890abcdef12")

    def test_git_rev_parse_preferred_over_github_sha(self):
        # Even when GITHUB_SHA points to a PR synthetic merge commit, git rev-parse HEAD must be preferred
        fake_github_sha = "merge_commit_sha_from_github_synthetic_pr_event"
        with patch.dict(os.environ, {"GITHUB_SHA": fake_github_sha}, clear=False):
            # Remove any explicit env overrides
            for k in ("RELEASE_COMMIT_SHA", "AUDITED_COMMIT_SHA", "PR_HEAD_SHA"):
                if k in os.environ:
                    del os.environ[k]

            sha = generate_release_checksums.get_commit_sha()
            # If in a valid git repo (which WearWallet is), sha must NOT equal fake_github_sha
            self.assertNotEqual(sha, fake_github_sha)
            self.assertEqual(len(sha), 40)

    def test_github_sha_fallback_when_git_fails(self):
        with patch.dict(os.environ, {"GITHUB_SHA": "fallback_github_sha_12345"}, clear=True):
            with patch("subprocess.run", side_effect=Exception("Git command failed")):
                sha = generate_release_checksums.get_commit_sha()
                self.assertEqual(sha, "fallback_github_sha_12345")

    def test_unknown_fallback_when_all_fail(self):
        with patch.dict(os.environ, {}, clear=True):
            with patch("subprocess.run", side_effect=Exception("Git command failed")):
                sha = generate_release_checksums.get_commit_sha()
                self.assertEqual(sha, "unknown-commit-sha")

    # --- Artifact Validation Tests ---

    def test_zero_artifacts_fail_closed(self):
        is_valid, err_msg, apks, aabs = generate_release_checksums.validate_artifacts([])
        self.assertFalse(is_valid)
        self.assertIn("No release artifacts found", err_msg)
        self.assertEqual(len(apks), 0)
        self.assertEqual(len(aabs), 0)

    def test_missing_apk_fail_closed(self):
        aab_path = self._create_dummy_file("app.aab")
        is_valid, err_msg, apks, aabs = generate_release_checksums.validate_artifacts([aab_path])
        self.assertFalse(is_valid)
        self.assertIn("Missing required APK", err_msg)
        self.assertEqual(len(apks), 0)
        self.assertEqual(len(aabs), 1)

    def test_missing_aab_fail_closed(self):
        apk_path = self._create_dummy_file("app.apk")
        is_valid, err_msg, apks, aabs = generate_release_checksums.validate_artifacts([apk_path])
        self.assertFalse(is_valid)
        self.assertIn("Missing required AAB", err_msg)
        self.assertEqual(len(apks), 1)
        self.assertEqual(len(aabs), 0)

    def test_both_apk_and_aab_present_success(self):
        apk_path = self._create_dummy_file("app.apk")
        aab_path = self._create_dummy_file("app.aab")
        is_valid, err_msg, apks, aabs = generate_release_checksums.validate_artifacts([apk_path, aab_path])
        self.assertTrue(is_valid)
        self.assertEqual(err_msg, "")
        self.assertEqual(len(apks), 1)
        self.assertEqual(len(aabs), 1)

    # --- End-to-End Manifest Generation Tests ---

    def test_generate_manifest_zero_artifacts_exits_1(self):
        output_txt = os.path.join(self.temp_dir.name, "checksums.txt")
        custom_patterns = [os.path.join(self.temp_dir.name, "nonexistent", "*.apk")]

        with self.assertRaises(SystemExit) as cm:
            generate_release_checksums.generate_manifest(
                output_path=output_txt,
                custom_patterns=custom_patterns
            )
        self.assertEqual(cm.exception.code, 1)
        self.assertFalse(os.path.exists(output_txt))

    def test_generate_manifest_only_apk_exits_1(self):
        output_txt = os.path.join(self.temp_dir.name, "checksums.txt")
        self._create_dummy_file("release/wear-release.apk")
        custom_patterns = [os.path.join(self.temp_dir.name, "release", "*")]

        with self.assertRaises(SystemExit) as cm:
            generate_release_checksums.generate_manifest(
                output_path=output_txt,
                custom_patterns=custom_patterns
            )
        self.assertEqual(cm.exception.code, 1)
        self.assertFalse(os.path.exists(output_txt))

    def test_generate_manifest_only_aab_exits_1(self):
        output_txt = os.path.join(self.temp_dir.name, "checksums.txt")
        self._create_dummy_file("release/wear-release.aab")
        custom_patterns = [os.path.join(self.temp_dir.name, "release", "*")]

        with self.assertRaises(SystemExit) as cm:
            generate_release_checksums.generate_manifest(
                output_path=output_txt,
                custom_patterns=custom_patterns
            )
        self.assertEqual(cm.exception.code, 1)
        self.assertFalse(os.path.exists(output_txt))

    def test_generate_manifest_success_both_apk_and_aab(self):
        output_txt = os.path.join(self.temp_dir.name, "checksums.txt")
        apk_content = b"wear-os-apk-content-sample"
        aab_content = b"wear-os-aab-content-sample"
        apk_path = self._create_dummy_file("release/wear-release.apk", apk_content)
        aab_path = self._create_dummy_file("release/wear-release.aab", aab_content)

        custom_patterns = [os.path.join(self.temp_dir.name, "release", "*")]

        count = generate_release_checksums.generate_manifest(
            output_path=output_txt,
            custom_patterns=custom_patterns,
            commit_sha_override="fedcba9876543210fedcba9876543210fedcba98"
        )
        self.assertEqual(count, 2)
        self.assertTrue(os.path.exists(output_txt))

        with open(output_txt, "r", encoding="utf-8") as f:
            content = f.read()

        apk_sha256 = hashlib.sha256(apk_content).hexdigest()
        aab_sha256 = hashlib.sha256(aab_content).hexdigest()

        self.assertIn("Audited Commit SHA : fedcba9876543210fedcba9876543210fedcba98", content)
        self.assertIn(apk_sha256, content)
        self.assertIn(aab_sha256, content)
        self.assertIn("wear-release.apk", content)
        self.assertIn("wear-release.aab", content)

    # --- Subprocess CLI Tests ---

    def test_cli_subprocess_zero_artifacts_fails(self):
        script_path = os.path.join(SCRIPTS_DIR, "generate_release_checksums.py")
        out_txt = os.path.join(self.temp_dir.name, "out.txt")
        res = subprocess.run(
            [sys.executable, script_path, out_txt, os.path.join(self.temp_dir.name, "empty", "*.apk")],
            capture_output=True,
            text=True
        )
        self.assertEqual(res.returncode, 1)
        self.assertIn("No release artifacts found", res.stderr)

    def test_cli_subprocess_success_with_both(self):
        script_path = os.path.join(SCRIPTS_DIR, "generate_release_checksums.py")
        out_txt = os.path.join(self.temp_dir.name, "out.txt")
        self._create_dummy_file("bin/app.apk")
        self._create_dummy_file("bin/app.aab")
        search_pattern = os.path.join(self.temp_dir.name, "bin", "*")

        res = subprocess.run(
            [sys.executable, script_path, out_txt, search_pattern, "--commit-sha", "1234567890123456789012345678901234567890"],
            capture_output=True,
            text=True
        )
        self.assertEqual(res.returncode, 0)
        self.assertTrue(os.path.exists(out_txt))
        self.assertIn("Generated", res.stdout)


if __name__ == "__main__":
    unittest.main()
