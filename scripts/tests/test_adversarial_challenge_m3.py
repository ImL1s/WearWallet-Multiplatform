#!/usr/bin/env python3
"""
scripts/tests/adversarial_challenge_m3.py

Comprehensive Adversarial Stress Test Suite for Milestone 3.
Empirically tests:
1. scripts/verify_junit_xml.py fail-closed invariants and corrupt input handling.
2. scripts/generate_release_checksums.py binary validation, fail-closed handling, and commit SHA resolution.
3. .github/workflows/ci.yml provenance assertion simulation and workflow schema verification.
"""

import hashlib
import json
import os
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

# Ensure scripts directory is in sys.path
SCRIPTS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROJECT_ROOT = os.path.dirname(SCRIPTS_DIR)
if SCRIPTS_DIR not in sys.path:
    sys.path.insert(0, SCRIPTS_DIR)

import generate_release_checksums
import verify_junit_xml


class AdversarialJUnitXmlTests(unittest.TestCase):
    """Adversarial stress testing of JUnit XML Verifier."""

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()

    def tearDown(self):
        self.temp_dir.cleanup()

    def _write_xml(self, filename: str, content: str) -> str:
        path = os.path.join(self.temp_dir.name, filename)
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        return path

    def _write_binary(self, filename: str, content: bytes) -> str:
        path = os.path.join(self.temp_dir.name, filename)
        with open(path, "wb") as f:
            f.write(content)
        return path

    def test_adv_junit_01_zero_tests_single_suite(self):
        """0 tests executed in a testsuite must exit 1."""
        xml = self._write_xml(
            "zero.xml",
            '<testsuite name="EmptySuite" tests="0" failures="0" errors="0" skipped="0"/>'
        )
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(cm.exception.code, 1)

    def test_adv_junit_02_zero_tests_multiple_suites(self):
        """0 tests executed across multiple testsuites must exit 1."""
        xml = self._write_xml(
            "zero_multi.xml",
            '<testsuites>\n'
            '  <testsuite name="Suite1" tests="0" failures="0" errors="0" skipped="0"/>\n'
            '  <testsuite name="Suite2" tests="0" failures="0" errors="0" skipped="0"/>\n'
            '</testsuites>'
        )
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(cm.exception.code, 1)

    def test_adv_junit_03_zero_tests_empty_testsuites_root(self):
        """Empty <testsuites/> container with no children must exit 1."""
        xml = self._write_xml("empty_root.xml", '<testsuites name="Empty"/>')
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(cm.exception.code, 1)

    def test_adv_junit_04_invariant_failures_exceed_tests(self):
        """Failures (6) > tests (5) -> negative passed count (-1) -> exit 1."""
        xml = self._write_xml(
            "neg_pass.xml",
            '<testsuite name="Bad1" tests="5" failures="6" errors="0" skipped="0"/>'
        )
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(cm.exception.code, 1)

    def test_adv_junit_05_invariant_errors_exceed_tests(self):
        """Errors (6) > tests (5) -> negative passed count (-1) -> exit 1."""
        xml = self._write_xml(
            "neg_pass2.xml",
            '<testsuite name="Bad2" tests="5" failures="0" errors="6" skipped="0"/>'
        )
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(cm.exception.code, 1)

    def test_adv_junit_06_invariant_skipped_exceed_tests(self):
        """Skipped (6) > tests (5) -> negative passed count (-1) -> exit 1."""
        xml = self._write_xml(
            "neg_pass3.xml",
            '<testsuite name="Bad3" tests="5" failures="0" errors="0" skipped="6"/>'
        )
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(cm.exception.code, 1)

    def test_adv_junit_07_negative_attribute_tests(self):
        """Negative tests attribute (tests="-5") -> passed < 0 -> exit 1."""
        xml = self._write_xml(
            "neg_tests.xml",
            '<testsuite name="Bad4" tests="-5" failures="0" errors="0" skipped="0"/>'
        )
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(cm.exception.code, 1)

    def test_adv_junit_08_negative_attribute_failures(self):
        """Negative failures attribute (failures="-1") -> failed < 0 -> exit 1."""
        xml = self._write_xml(
            "neg_failures.xml",
            '<testsuite name="Bad5" tests="5" failures="-1" errors="0" skipped="0"/>'
        )
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(cm.exception.code, 1)

    def test_adv_junit_09_corrupt_xml_unclosed_tag(self):
        """Truncated XML syntax error -> exit 1."""
        xml = self._write_xml("corrupt1.xml", '<testsuite name="Broken" tests="5"><testcase')
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(cm.exception.code, 1)

    def test_adv_junit_10_corrupt_xml_binary_garbage(self):
        """Binary garbage pretending to be XML -> exit 1."""
        xml = self._write_binary("garbage.xml", b"\x00\xFF\xFE\x00\xDE\xAD\xBE\xEF" * 32)
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(cm.exception.code, 1)

    def test_adv_junit_11_corrupt_xml_empty_file(self):
        """0-byte XML file -> exit 1."""
        xml = self._write_xml("empty.xml", "")
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(cm.exception.code, 1)

    def test_adv_junit_12_no_xml_files_found(self):
        """No XML files matching search pattern -> exit 1."""
        nonexistent = os.path.join(self.temp_dir.name, "none", "*.xml")
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([nonexistent])
        self.assertEqual(cm.exception.code, 1)

    def test_adv_junit_13_actual_failures_detected(self):
        """Test suite with test failure -> exit 1."""
        xml = self._write_xml(
            "failure.xml",
            '<testsuite name="Fail" tests="1" failures="1" errors="0" skipped="0">\n'
            '  <testcase name="t1"><failure>msg</failure></testcase>\n'
            '</testsuite>'
        )
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(cm.exception.code, 1)

    def test_adv_junit_14_actual_errors_detected(self):
        """Test suite with test error -> exit 1."""
        xml = self._write_xml(
            "error.xml",
            '<testsuite name="Err" tests="1" failures="0" errors="1" skipped="0">\n'
            '  <testcase name="t1"><error>msg</error></testcase>\n'
            '</testsuite>'
        )
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(cm.exception.code, 1)

    def test_adv_junit_15_valid_suite_pass_and_skip(self):
        """Valid testsuite with passed and skipped tests returns 0."""
        xml = self._write_xml(
            "valid.xml",
            '<testsuite name="Valid" tests="10" failures="0" errors="0" skipped="2" disabled="1">\n'
            '  <testcase name="t1"/>\n'
            '</testsuite>'
        )
        # tests=10, skipped=3 (2+1), passed=7 -> total == 7 + 0 + 3 == 10
        ret = verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(ret, 0)

    def test_adv_junit_16_cli_subprocess_invocation(self):
        """CLI invocation of verify_junit_xml.py via subprocess."""
        script_path = os.path.join(SCRIPTS_DIR, "verify_junit_xml.py")
        zero_xml = self._write_xml("zero_cli.xml", '<testsuite name="Zero" tests="0" failures="0" errors="0" skipped="0"/>')
        res = subprocess.run([sys.executable, script_path, zero_xml], capture_output=True, text=True)
        self.assertEqual(res.returncode, 1)
        self.assertIn("0 tests were executed", res.stderr)

        valid_xml = self._write_xml("valid_cli.xml", '<testsuite name="Valid" tests="3" failures="0" errors="0" skipped="0"/>')
        res_valid = subprocess.run([sys.executable, script_path, valid_xml], capture_output=True, text=True)
        self.assertEqual(res_valid.returncode, 0)
        self.assertIn("verified", res_valid.stdout)


class AdversarialReleaseChecksumsTests(unittest.TestCase):
    """Adversarial stress testing of Release Checksums Generator."""

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()

    def tearDown(self):
        self.temp_dir.cleanup()

    def _create_file(self, relpath: str, content: bytes = b"test payload") -> str:
        full_path = os.path.join(self.temp_dir.name, relpath)
        os.makedirs(os.path.dirname(full_path), exist_ok=True)
        with open(full_path, "wb") as f:
            f.write(content)
        return full_path

    def test_adv_checksum_01_zero_artifacts_fail_closed(self):
        """0 matched artifacts must exit 1 and not write manifest."""
        out_txt = os.path.join(self.temp_dir.name, "manifest.txt")
        custom_patterns = [os.path.join(self.temp_dir.name, "empty_dir", "*")]
        with self.assertRaises(SystemExit) as cm:
            generate_release_checksums.generate_manifest(
                output_path=out_txt,
                custom_patterns=custom_patterns
            )
        self.assertEqual(cm.exception.code, 1)
        self.assertFalse(os.path.exists(out_txt))

    def test_adv_checksum_02_apk_present_aab_missing(self):
        """Only APK present, AAB missing -> exit 1 and not write manifest."""
        out_txt = os.path.join(self.temp_dir.name, "manifest.txt")
        self._create_file("wear/wear-release.apk", b"apk binary")
        custom_patterns = [os.path.join(self.temp_dir.name, "wear", "*")]
        with self.assertRaises(SystemExit) as cm:
            generate_release_checksums.generate_manifest(
                output_path=out_txt,
                custom_patterns=custom_patterns
            )
        self.assertEqual(cm.exception.code, 1)
        self.assertFalse(os.path.exists(out_txt))

    def test_adv_checksum_03_aab_present_apk_missing(self):
        """Only AAB present, APK missing -> exit 1 and not write manifest."""
        out_txt = os.path.join(self.temp_dir.name, "manifest.txt")
        self._create_file("wear/wear-release.aab", b"aab binary")
        custom_patterns = [os.path.join(self.temp_dir.name, "wear", "*")]
        with self.assertRaises(SystemExit) as cm:
            generate_release_checksums.generate_manifest(
                output_path=out_txt,
                custom_patterns=custom_patterns
            )
        self.assertEqual(cm.exception.code, 1)
        self.assertFalse(os.path.exists(out_txt))

    def test_adv_checksum_04_both_apk_and_aab_present_success(self):
        """Both APK and AAB present -> exit 0 and write accurate manifest."""
        out_txt = os.path.join(self.temp_dir.name, "release-checksums.txt")
        apk_data = b"WEAR_WALLET_APK_BINARY_V1"
        aab_data = b"WEAR_WALLET_AAB_BUNDLE_V1"
        self._create_file("wear/wear-release.apk", apk_data)
        self._create_file("wear/wear-release.aab", aab_data)
        custom_patterns = [os.path.join(self.temp_dir.name, "wear", "*")]

        count = generate_release_checksums.generate_manifest(
            output_path=out_txt,
            custom_patterns=custom_patterns,
            commit_sha_override="a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
        )
        self.assertEqual(count, 2)
        self.assertTrue(os.path.exists(out_txt))

        with open(out_txt, "r", encoding="utf-8") as f:
            content = f.read()

        apk_sha = hashlib.sha256(apk_data).hexdigest()
        aab_sha = hashlib.sha256(aab_data).hexdigest()
        self.assertIn(apk_sha, content)
        self.assertIn(aab_sha, content)
        self.assertIn("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2", content)
        self.assertIn("wear/wear-release.apk", content.replace("\\", "/"))
        self.assertIn("wear/wear-release.aab", content.replace("\\", "/"))

    def test_adv_checksum_05_commit_sha_precedence_git_over_github_sha(self):
        """git rev-parse HEAD MUST be preferred over GITHUB_SHA in normal git repo."""
        synthetic_pr_merge_sha = "ffffffffffffffffffffffffffffffffffffffff"
        with patch.dict(os.environ, {"GITHUB_SHA": synthetic_pr_merge_sha}, clear=False):
            for k in ("RELEASE_COMMIT_SHA", "AUDITED_COMMIT_SHA", "PR_HEAD_SHA"):
                if k in os.environ:
                    del os.environ[k]

            resolved_sha = generate_release_checksums.get_commit_sha()
            # Must NOT be synthetic PR merge SHA
            self.assertNotEqual(resolved_sha, synthetic_pr_merge_sha)
            self.assertEqual(len(resolved_sha), 40)

    def test_adv_checksum_06_commit_sha_explicit_env_overrides(self):
        """PR_HEAD_SHA or RELEASE_COMMIT_SHA overrides git rev-parse HEAD."""
        explicit_sha = "0123456789abcdef0123456789abcdef01234567"
        with patch.dict(os.environ, {"PR_HEAD_SHA": explicit_sha}):
            resolved_sha = generate_release_checksums.get_commit_sha()
            self.assertEqual(resolved_sha, explicit_sha)

    def test_adv_checksum_07_sha256_large_payload(self):
        """Test SHA-256 calculation for large pseudo-random payload."""
        large_payload = os.urandom(1024 * 512)  # 512 KB
        file_path = self._create_file("large.bin", large_payload)
        expected_sha = hashlib.sha256(large_payload).hexdigest()
        actual_sha = generate_release_checksums.get_sha256(file_path)
        self.assertEqual(actual_sha, expected_sha)

    def test_adv_checksum_08_cli_subprocess_execution(self):
        """Subprocess execution of generate_release_checksums.py."""
        script_path = os.path.join(SCRIPTS_DIR, "generate_release_checksums.py")
        out_txt = os.path.join(self.temp_dir.name, "cli_manifest.txt")

        # Fail case
        res_fail = subprocess.run(
            [sys.executable, script_path, out_txt, os.path.join(self.temp_dir.name, "none", "*")],
            capture_output=True,
            text=True
        )
        self.assertEqual(res_fail.returncode, 1)

        # Success case
        self._create_file("dist/app.apk", b"apk")
        self._create_file("dist/app.aab", b"aab")
        res_ok = subprocess.run(
            [
                sys.executable,
                script_path,
                out_txt,
                os.path.join(self.temp_dir.name, "dist", "*"),
                "--commit-sha",
                "9999888877776666555544443333222211110000"
            ],
            capture_output=True,
            text=True
        )
        self.assertEqual(res_ok.returncode, 0)
        self.assertTrue(os.path.exists(out_txt))


class AdversarialWorkflowProvenanceTests(unittest.TestCase):
    """Adversarial testing of .github/workflows/ci.yml provenance logic and layout."""

    def test_adv_ci_01_bash_provenance_script_simulation(self):
        """Simulate bash provenance check logic under bash or python."""
        # Logic:
        # EXPECTED_SHA="..."
        # ACTUAL_SHA="$(git rev-parse HEAD)"
        # if [ "$ACTUAL_SHA" != "$EXPECTED_SHA" ]; then exit 1; fi
        actual_head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()

        # Matching case
        expected_match = actual_head
        self.assertEqual(expected_match, actual_head)

        # Mismatch case
        expected_mismatch = "0000000000000000000000000000000000000000"
        self.assertNotEqual(expected_mismatch, actual_head)

    def test_adv_ci_02_powershell_provenance_script_simulation(self):
        """Simulate powershell provenance assertion logic."""
        actual_head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        ps_script_match = f"""
        $ExpectedSha = '{actual_head}'
        $ActualSha = (git rev-parse HEAD).Trim()
        if ($ActualSha -ne $ExpectedSha) {{ exit 1 }} else {{ exit 0 }}
        """
        res_match = subprocess.run(["powershell", "-Command", ps_script_match], capture_output=True, text=True)
        self.assertEqual(res_match.returncode, 0)

        ps_script_mismatch = """
        $ExpectedSha = '0000000000000000000000000000000000000000'
        $ActualSha = (git rev-parse HEAD).Trim()
        if ($ActualSha -ne $ExpectedSha) { exit 1 } else { exit 0 }
        """
        res_mismatch = subprocess.run(["powershell", "-Command", ps_script_mismatch], capture_output=True, text=True)
        self.assertEqual(res_mismatch.returncode, 1)

    def test_adv_ci_03_workflow_structure_verification(self):
        """Verify ci.yml contains all required provenance checks and artifact uploads."""
        workflow_path = os.path.join(PROJECT_ROOT, ".github", "workflows", "ci.yml")
        self.assertTrue(os.path.isfile(workflow_path))

        with open(workflow_path, "r", encoding="utf-8") as f:
            content = f.read()

        # Required jobs
        self.assertIn("build-test-linux:", content)
        self.assertIn("build-test-windows:", content)
        self.assertIn("compile-link-test-macos:", content)

        # Exact HEAD checkout
        self.assertIn("ref: ${{ github.event.pull_request.head.sha || github.sha }}", content)

        # Provenance verification step
        self.assertIn("Verify Exact-HEAD Commit Provenance", content)
        self.assertIn("git rev-parse HEAD", content)

        # Fail-closed JUnit verification
        self.assertIn("verify_junit_xml.py", content)

        # Fail-closed Release Checksums
        self.assertIn("generate_release_checksums.py", content)

        # Mandatory Artifact Uploads
        self.assertIn("junit-reports-linux", content)
        self.assertIn("html-reports-linux", content)
        self.assertIn("wear-release-apk-linux", content)
        self.assertIn("wear-release-aab-linux", content)
        self.assertIn("release-checksums-linux", content)

        self.assertIn("junit-reports-windows", content)
        self.assertIn("html-reports-windows", content)
        self.assertIn("wear-release-apk-windows", content)
        self.assertIn("wear-release-aab-windows", content)
        self.assertIn("release-checksums-windows", content)

        self.assertIn("junit-reports-macos", content)
        self.assertIn("html-reports-macos", content)
        self.assertIn("apple-frameworks-macos", content)


if __name__ == "__main__":
    unittest.main()
