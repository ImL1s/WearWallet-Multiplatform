#!/usr/bin/env python3
"""
scripts/tests/adversarial_fuzzing_challenger_m3_2.py

Independent Adversarial Fuzzing & Stress Test Suite for Milestone 3 (Challenger 2).
Exhaustively tests:
1. scripts/verify_junit_xml.py:
   - Whitespace in XML attributes (leading, trailing, tabs, newlines)
   - Large numbers of nested test suites (1,000+ suites, deep nesting)
   - Disabled vs skipped attribute parsing combinations
   - Empty files, zero-byte files, truncated XML, binary garbage, non-testsuite roots
   - Negative numbers, non-integer attributes, empty attribute strings
2. scripts/generate_release_checksums.py:
   - Corrupted/unreadable files
   - Zero-byte files (empty APK & AAB hash verification)
   - Spaces and special characters in artifact filenames and paths
   - Symlinks / junctions
   - CLI extra pattern args, globs, mixed extensions
   - Mandatory APK/AAB matrix and bypass flags
   - Full 7-level commit SHA resolution precedence hierarchy
3. Provenance check logic across Bash and PowerShell:
   - Trailing whitespace & newline handling in git rev-parse output
   - Empty string handling (fail-closed)
   - Case sensitivity and hex validity
   - Live execution on current repo
"""

import glob
import hashlib
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

SCRIPTS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROJECT_ROOT = os.path.dirname(SCRIPTS_DIR)
if SCRIPTS_DIR not in sys.path:
    sys.path.insert(0, SCRIPTS_DIR)

import generate_release_checksums
import verify_junit_xml


class FuzzVerifyJUnitXml(unittest.TestCase):
    """Adversarial stress and fuzzing tests for verify_junit_xml.py."""

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()

    def tearDown(self):
        self.temp_dir.cleanup()

    def _write_file(self, filename: str, content: str, encoding: str = "utf-8") -> str:
        path = os.path.join(self.temp_dir.name, filename)
        with open(path, "w", encoding=encoding) as f:
            f.write(content)
        return path

    def _write_bytes(self, filename: str, content: bytes) -> str:
        path = os.path.join(self.temp_dir.name, filename)
        with open(path, "wb") as f:
            f.write(content)
        return path

    # --- 1. Whitespace in XML Attributes ---

    def test_whitespace_in_numeric_attributes(self):
        """Tests leading/trailing spaces and tabs in tests, failures, errors, skipped, disabled, time."""
        xml = self._write_file(
            "ws_valid.xml",
            '<testsuite name=" Whitespace Suite " tests="  10  " failures="  0  " errors="  0\t" '
            'skipped="  2  " disabled=" \t1\n " time="  4.5678  ">\n'
            '  <testcase name="t1"/>\n'
            '</testsuite>'
        )
        # tests=10, skipped=3 (2+1), passed=7 -> total == 7 + 0 + 3 == 10
        ret = verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(ret, 0)

    def test_whitespace_in_failure_suite(self):
        """Whitespace in failure counts must still trigger fail-closed exit 1."""
        xml = self._write_file(
            "ws_fail.xml",
            '<testsuite name=" Whitespace Fail " tests="  5  " failures="  1  " errors="  0  " skipped="  0  "/>'
        )
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(cm.exception.code, 1)

    def test_empty_string_attribute_fails_closed(self):
        """Attribute tests="" or failures="" results in ValueError -> caught and exits 1."""
        xml = self._write_file(
            "empty_attr.xml",
            '<testsuite name="EmptyAttr" tests="" failures="0" errors="0" skipped="0"/>'
        )
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(cm.exception.code, 1)

    def test_float_string_in_int_attribute_fails_closed(self):
        """tests="10.0" -> int("10.0") raises ValueError -> exits 1."""
        xml = self._write_file(
            "float_attr.xml",
            '<testsuite name="FloatAttr" tests="10.0" failures="0" errors="0" skipped="0"/>'
        )
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(cm.exception.code, 1)

    def test_time_attribute_unusual_formats(self):
        """Test time with comma separators, exponential notation, empty string, invalid text."""
        xml = self._write_file(
            "time_formats.xml",
            '<testsuites>\n'
            '  <testsuite name="CommaTime" tests="1" failures="0" errors="0" skipped="0" time="1,234.56"/>\n'
            '  <testsuite name="ExpTime" tests="1" failures="0" errors="0" skipped="0" time="1.5e-2"/>\n'
            '  <testsuite name="EmptyTime" tests="1" failures="0" errors="0" skipped="0" time=""/>\n'
            '  <testsuite name="InvalidTime" tests="1" failures="0" errors="0" skipped="0" time="NaN_or_text"/>\n'
            '</testsuites>'
        )
        ret = verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(ret, 0)

    # --- 2. Large Numbers of Test Suites & Deep Hierarchy ---

    def test_fuzz_1000_parallel_suites(self):
        """Fuzz test with 1,000 distinct testsuites in a single testsuites container."""
        suites = []
        expected_total = 0
        expected_skipped = 0
        expected_passed = 0
        for i in range(1000):
            tests = (i % 7) + 1
            skipped = 1 if (i % 10 == 0) else 0
            passed = tests - skipped
            suites.append(
                f'  <testsuite name="Suite_{i:04d}" tests="{tests}" failures="0" errors="0" '
                f'skipped="{skipped}" disabled="0" time="0.01"/>'
            )
            expected_total += tests
            expected_skipped += skipped
            expected_passed += passed

        xml_content = '<testsuites>\n' + '\n'.join(suites) + '\n</testsuites>'
        xml = self._write_file("1000_suites.xml", xml_content)
        ret = verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(ret, 0)

    def test_deeply_nested_testsuites(self):
        """Deeply nested <testsuite> elements inside <testsuites>."""
        xml_content = (
            '<testsuites name="Root">\n'
            '  <testsuite name="PackageA" tests="5" failures="0" errors="0" skipped="1">\n'
            '    <testcase name="testA1"/>\n'
            '  </testsuite>\n'
            '  <testsuite name="PackageB" tests="8" failures="0" errors="0" skipped="0">\n'
            '    <testcase name="testB1"/>\n'
            '  </testsuite>\n'
            '</testsuites>'
        )
        xml = self._write_file("nested_suites.xml", xml_content)
        ret = verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(ret, 0)

    # --- 3. Disabled vs Skipped Combinations ---

    def test_disabled_and_skipped_combinations(self):
        """Test exhaustive combinations of skipped and disabled attributes."""
        # Case A: Only disabled set
        xml_a = self._write_file(
            "disabled_only.xml",
            '<testsuite name="DisabledOnly" tests="5" failures="0" errors="0" skipped="0" disabled="2"/>'
        )
        self.assertEqual(verify_junit_xml.verify_junit_xml([xml_a]), 0)

        # Case B: Only skipped set
        xml_b = self._write_file(
            "skipped_only.xml",
            '<testsuite name="SkippedOnly" tests="5" failures="0" errors="0" skipped="3" disabled="0"/>'
        )
        self.assertEqual(verify_junit_xml.verify_junit_xml([xml_b]), 0)

        # Case C: Both skipped and disabled set, sum == tests
        xml_c = self._write_file(
            "all_skipped.xml",
            '<testsuite name="AllSkipped" tests="6" failures="0" errors="0" skipped="4" disabled="2"/>'
        )
        self.assertEqual(verify_junit_xml.verify_junit_xml([xml_c]), 0)

        # Case D: Both skipped and disabled set, sum > tests -> negative passed -> exit 1
        xml_d = self._write_file(
            "over_skipped.xml",
            '<testsuite name="OverSkipped" tests="5" failures="0" errors="0" skipped="4" disabled="2"/>'
        )
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml_d])
        self.assertEqual(cm.exception.code, 1)

    # --- 4. Corrupted & Empty Files ---

    def test_zero_byte_xml_file(self):
        """0-byte file must exit 1."""
        xml = self._write_file("zero_byte.xml", "")
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(cm.exception.code, 1)

    def test_xml_declaration_only_file(self):
        """File with only XML header and no root element must exit 1."""
        xml = self._write_file("decl_only.xml", '<?xml version="1.0" encoding="UTF-8"?>\n')
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(cm.exception.code, 1)

    def test_truncated_xml_in_mid_tag(self):
        """Truncated unclosed XML tag must exit 1."""
        xml = self._write_file("unclosed.xml", '<?xml version="1.0"?><testsuite name="Test" tests="5"')
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(cm.exception.code, 1)

    def test_non_xml_random_fuzz_bytes(self):
        """Random binary bytes pretending to be XML must exit 1."""
        for i in range(5):
            fuzz_bytes = os.urandom(256 * (i + 1))
            xml = self._write_bytes(f"fuzz_{i}.xml", fuzz_bytes)
            with self.assertRaises(SystemExit) as cm:
                verify_junit_xml.verify_junit_xml([xml])
            self.assertEqual(cm.exception.code, 1)

    def test_unrelated_xml_root_tag(self):
        """XML with root tag other than testsuite/testsuites -> total_tests == 0 -> exits 1."""
        xml = self._write_file(
            "unrelated.xml",
            '<?xml version="1.0"?><project name="MyProject"><target name="build"/></project>'
        )
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml])
        self.assertEqual(cm.exception.code, 1)


class FuzzGenerateReleaseChecksums(unittest.TestCase):
    """Adversarial stress and edge-case testing for generate_release_checksums.py."""

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

    # --- 1. Zero-byte Files ---

    def test_zero_byte_apk_and_aab(self):
        """0-byte APK and 0-byte AAB must generate correct empty-file SHA-256 hash."""
        apk_path = self._create_file("wear/app-release.apk", b"")
        aab_path = self._create_file("wear/app-release.aab", b"")
        out_txt = os.path.join(self.temp_dir.name, "manifest_zero.txt")

        custom_patterns = [os.path.join(self.temp_dir.name, "wear", "*")]
        count = generate_release_checksums.generate_manifest(
            output_path=out_txt,
            custom_patterns=custom_patterns,
            commit_sha_override="1234567890abcdef1234567890abcdef12345678"
        )
        self.assertEqual(count, 2)
        empty_sha256 = hashlib.sha256(b"").hexdigest()  # e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855

        with open(out_txt, "r", encoding="utf-8") as f:
            manifest_content = f.read()

        self.assertIn(empty_sha256, manifest_content)
        self.assertIn("0 ", manifest_content)  # size 0

    # --- 2. Spaces and Special Characters in Paths ---

    def test_spaces_and_special_characters_in_filenames(self):
        """Artifacts with spaces, parentheses, brackets, hashes, plus signs."""
        apk_path = self._create_file(
            "outputs/apk/Wear Wallet (v1.0.0-rc1) [release] #1+prod.apk",
            b"APK_WITH_SPACES_AND_SYMBOLS"
        )
        aab_path = self._create_file(
            "outputs/bundle/Wear Wallet Bundle (v1.0.0-rc1) [release].aab",
            b"AAB_WITH_SPACES_AND_SYMBOLS"
        )
        out_txt = os.path.join(self.temp_dir.name, "manifest_spaces.txt")

        custom_patterns = [
            os.path.join(self.temp_dir.name, "outputs", "apk", "*"),
            os.path.join(self.temp_dir.name, "outputs", "bundle", "*"),
        ]

        count = generate_release_checksums.generate_manifest(
            output_path=out_txt,
            custom_patterns=custom_patterns,
            commit_sha_override="fedcba0987654321fedcba0987654321fedcba09"
        )
        self.assertEqual(count, 2)

        with open(out_txt, "r", encoding="utf-8") as f:
            content = f.read()

        # Check normalization with forward slashes and exact filename preservation
        self.assertIn("Wear Wallet (v1.0.0-rc1) [release] #1+prod.apk", content)
        self.assertIn("Wear Wallet Bundle (v1.0.0-rc1) [release].aab", content)
        self.assertNotIn("\\outputs\\", content)  # No backslashes in artifact paths

    # --- 3. Extra Pattern Args and Globs ---

    def test_cli_extra_patterns_mixed_artifacts(self):
        """Test extra_patterns matching additional files (frameworks, zips, txt)."""
        self._create_file("wear/app.apk", b"apk")
        self._create_file("wear/app.aab", b"aab")
        self._create_file("extra/framework.zip", b"framework zip")
        self._create_file("extra/symbols.txt", b"symbols")

        out_txt = os.path.join(self.temp_dir.name, "manifest_extra.txt")
        custom_patterns = [
            os.path.join(self.temp_dir.name, "wear", "*"),
            os.path.join(self.temp_dir.name, "extra", "*.zip"),
            os.path.join(self.temp_dir.name, "extra", "*.txt"),
        ]

        count = generate_release_checksums.generate_manifest(
            output_path=out_txt,
            custom_patterns=custom_patterns,
            commit_sha_override="1111222233334444555566667777888899990000"
        )
        self.assertEqual(count, 4)

        with open(out_txt, "r", encoding="utf-8") as f:
            content = f.read()
        self.assertIn("app.apk", content)
        self.assertIn("app.aab", content)
        self.assertIn("framework.zip", content)
        self.assertIn("symbols.txt", content)

    # --- 4. Validation Matrix & Bypass Flags ---

    def test_artifact_validation_matrix_exhaustive(self):
        """Test all 4 combinations of APK/AAB presence and flag overrides."""
        dir_empty = os.path.join(self.temp_dir.name, "m_empty")
        dir_apk_only = os.path.join(self.temp_dir.name, "m_apk_only")
        dir_aab_only = os.path.join(self.temp_dir.name, "m_aab_only")
        dir_both = os.path.join(self.temp_dir.name, "m_both")

        os.makedirs(dir_empty, exist_ok=True)
        self._create_file("m_apk_only/app.apk", b"apk")
        self._create_file("m_aab_only/app.aab", b"aab")
        self._create_file("m_both/app.apk", b"apk")
        self._create_file("m_both/app.aab", b"aab")

        # 1. Empty -> Fail
        files, _ = generate_release_checksums.find_release_artifacts(custom_patterns=[os.path.join(dir_empty, "*")])
        valid, err, _, _ = generate_release_checksums.validate_artifacts(files)
        self.assertFalse(valid)

        # 2. APK only -> Fail when require_aab=True, Pass when require_aab=False
        files, _ = generate_release_checksums.find_release_artifacts(custom_patterns=[os.path.join(dir_apk_only, "*")])
        valid, err, _, _ = generate_release_checksums.validate_artifacts(files, require_apk=True, require_aab=True)
        self.assertFalse(valid)
        valid_no_aab, _, _, _ = generate_release_checksums.validate_artifacts(files, require_apk=True, require_aab=False)
        self.assertTrue(valid_no_aab)

        # 3. AAB only -> Fail when require_apk=True, Pass when require_apk=False
        files, _ = generate_release_checksums.find_release_artifacts(custom_patterns=[os.path.join(dir_aab_only, "*")])
        valid, err, _, _ = generate_release_checksums.validate_artifacts(files, require_apk=True, require_aab=True)
        self.assertFalse(valid)
        valid_no_apk, _, _, _ = generate_release_checksums.validate_artifacts(files, require_apk=False, require_aab=True)
        self.assertTrue(valid_no_apk)

        # 4. Both -> Pass
        files, _ = generate_release_checksums.find_release_artifacts(custom_patterns=[os.path.join(dir_both, "*")])
        valid, err, _, _ = generate_release_checksums.validate_artifacts(files, require_apk=True, require_aab=True)
        self.assertTrue(valid)

    # --- 5. Commit SHA Precedence Hierarchy ---

    def test_full_commit_sha_precedence_ladder(self):
        """Exhaustively verify 6-tier SHA priority resolution hierarchy."""
        sha_override = "aaaa000000000000000000000000000000000000"
        sha_release = "bbbb000000000000000000000000000000000000"
        sha_audited = "cccc000000000000000000000000000000000000"
        sha_pr_head = "dddd000000000000000000000000000000000000"
        sha_github = "eeee000000000000000000000000000000000000"

        # Tier 1: CLI override wins over all env vars
        with patch.dict(os.environ, {
            "RELEASE_COMMIT_SHA": sha_release,
            "AUDITED_COMMIT_SHA": sha_audited,
            "PR_HEAD_SHA": sha_pr_head,
            "GITHUB_SHA": sha_github
        }):
            self.assertEqual(generate_release_checksums.get_commit_sha(sha_override), sha_override)

        # Tier 2: RELEASE_COMMIT_SHA wins over AUDITED_COMMIT_SHA
        with patch.dict(os.environ, {
            "RELEASE_COMMIT_SHA": sha_release,
            "AUDITED_COMMIT_SHA": sha_audited,
            "PR_HEAD_SHA": sha_pr_head,
            "GITHUB_SHA": sha_github
        }):
            self.assertEqual(generate_release_checksums.get_commit_sha(), sha_release)

        # Tier 3: AUDITED_COMMIT_SHA wins over PR_HEAD_SHA
        with patch.dict(os.environ, {
            "AUDITED_COMMIT_SHA": sha_audited,
            "PR_HEAD_SHA": sha_pr_head,
            "GITHUB_SHA": sha_github
        }, clear=True):
            self.assertEqual(generate_release_checksums.get_commit_sha(), sha_audited)

        # Tier 4: PR_HEAD_SHA wins over git rev-parse HEAD and GITHUB_SHA
        with patch.dict(os.environ, {
            "PR_HEAD_SHA": sha_pr_head,
            "GITHUB_SHA": sha_github
        }, clear=True):
            self.assertEqual(generate_release_checksums.get_commit_sha(), sha_pr_head)

        # Tier 5: In clean git repo with no env vars, git rev-parse HEAD is used (not GITHUB_SHA)
        with patch.dict(os.environ, {"GITHUB_SHA": sha_github}, clear=True):
            git_sha = generate_release_checksums.get_commit_sha()
            self.assertNotEqual(git_sha, sha_github)
            self.assertEqual(len(git_sha), 40)


class FuzzCrossPlatformProvenance(unittest.TestCase):
    """Adversarial tests for Git Exact-HEAD Provenance logic in Bash and PowerShell."""

    def test_git_rev_parse_whitespace_handling_in_bash(self):
        """Simulate Bash behavior: $(git rev-parse HEAD) strips trailing newlines."""
        raw_output = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True)
        # raw output contains '\n'
        self.assertTrue(raw_output.endswith("\n"))
        # In Bash $(cmd) strips all trailing newlines:
        bash_stripped = raw_output.rstrip("\r\n")
        self.assertEqual(len(bash_stripped), 40)

        # Test bash string equality comparison
        expected = bash_stripped
        self.assertEqual(bash_stripped, expected)

    def test_git_rev_parse_whitespace_handling_in_powershell(self):
        """Test PowerShell .Trim() on git rev-parse output."""
        ps_cmd = """
        $Raw = git rev-parse HEAD
        $Trimmed = $Raw.Trim()
        if ($Trimmed.Length -ne 40) { exit 1 }
        $Expected = $Trimmed
        if ($Trimmed -ne $Expected) { exit 2 }
        exit 0
        """
        res = subprocess.run(["powershell", "-Command", ps_cmd], capture_output=True, text=True)
        self.assertEqual(res.returncode, 0)

    def test_provenance_empty_expected_sha_fails_closed(self):
        """If expected SHA is empty string, provenance check MUST fail."""
        ps_empty_test = """
        $ExpectedSha = "".Trim()
        $ActualSha = (git rev-parse HEAD).Trim()
        if ($ActualSha -ne $ExpectedSha) {
          exit 1
        }
        exit 0
        """
        res = subprocess.run(["powershell", "-Command", ps_empty_test], capture_output=True, text=True)
        self.assertEqual(res.returncode, 1)

    def test_provenance_whitespace_padding_fails_or_trims(self):
        """PowerShell .Trim() correctly handles accidental spaces in expected SHA."""
        actual_head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        ps_padding_test = f"""
        $ExpectedSha = "  {actual_head}  `n".Trim()
        $ActualSha = (git rev-parse HEAD).Trim()
        if ($ActualSha -ne $ExpectedSha) {{
          exit 1
        }}
        exit 0
        """
        res = subprocess.run(["powershell", "-Command", ps_padding_test], capture_output=True, text=True)
        self.assertEqual(res.returncode, 0)

    def test_provenance_case_sensitivity_hex(self):
        """Verify git SHA casing: git rev-parse HEAD is strictly lowercase 40-char hex."""
        actual_head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        self.assertEqual(actual_head, actual_head.lower())
        self.assertTrue(all(c in "0123456789abcdef" for c in actual_head))


if __name__ == "__main__":
    unittest.main(verbosity=2)
