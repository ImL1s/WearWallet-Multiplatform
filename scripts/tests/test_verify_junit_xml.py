#!/usr/bin/env python3
"""
Unit tests for scripts/verify_junit_xml.py.

Verifies:
1. Fail-closed behavior on 0 tests executed (total_tests == 0 -> exit 1)
2. Fail-closed behavior on no XML files found (exit 1)
3. Fail-closed behavior on XML parse error (exit 1)
4. Fail-closed behavior on invariant violation (total != passed + failed + skipped, or negative -> exit 1)
5. Fail-closed behavior on test failures or errors (total_failed > 0 -> exit 1)
6. Correct accounting for passed, failed, and skipped/disabled tests
7. Time parsing robustness (floats with or without commas)
8. CLI subprocess execution
"""

import os
import subprocess
import sys
import tempfile
import unittest

# Add scripts directory to sys.path
SCRIPTS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if SCRIPTS_DIR not in sys.path:
    sys.path.insert(0, SCRIPTS_DIR)

import verify_junit_xml


class TestVerifyJUnitXml(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()

    def tearDown(self):
        self.temp_dir.cleanup()

    def _create_xml(self, filename: str, content: str) -> str:
        filepath = os.path.join(self.temp_dir.name, filename)
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content)
        return filepath

    def test_no_files_found_exits_1(self):
        non_existent_pattern = os.path.join(self.temp_dir.name, "nonexistent", "*.xml")
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([non_existent_pattern])
        self.assertEqual(cm.exception.code, 1)

    def test_malformed_xml_exits_1(self):
        xml_path = self._create_xml("malformed.xml", "<testsuite tests='5'")
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml_path])
        self.assertEqual(cm.exception.code, 1)

    def test_zero_tests_single_suite_fails_closed_exits_1(self):
        xml_path = self._create_xml(
            "zero_tests.xml",
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<testsuite name="com.cbstudio.EmptyTest" tests="0" failures="0" errors="0" skipped="0" time="0.001">\n'
            '</testsuite>'
        )
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml_path])
        self.assertEqual(cm.exception.code, 1)

    def test_zero_tests_multiple_suites_fails_closed_exits_1(self):
        xml_path = self._create_xml(
            "zero_multiple.xml",
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<testsuites>\n'
            '  <testsuite name="com.cbstudio.Suite1" tests="0" failures="0" errors="0" skipped="0" time="0.0"/>\n'
            '  <testsuite name="com.cbstudio.Suite2" tests="0" failures="0" errors="0" skipped="0" time="0.0"/>\n'
            '</testsuites>'
        )
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml_path])
        self.assertEqual(cm.exception.code, 1)

    def test_zero_tests_in_empty_testsuites_root_exits_1(self):
        xml_path = self._create_xml(
            "empty_suites.xml",
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<testsuites id="1" name="EmptySuites">\n'
            '</testsuites>'
        )
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml_path])
        self.assertEqual(cm.exception.code, 1)

    def test_test_failures_detected_exits_1(self):
        xml_path = self._create_xml(
            "failing_tests.xml",
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<testsuite name="com.cbstudio.FailTest" tests="5" failures="2" errors="0" skipped="0" time="0.100">\n'
            '  <testcase name="test1" classname="com.cbstudio.FailTest" time="0.01"/>\n'
            '  <testcase name="test2" classname="com.cbstudio.FailTest" time="0.01">\n'
            '    <failure message="Assertion error">Stacktrace</failure>\n'
            '  </testcase>\n'
            '</testsuite>'
        )
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml_path])
        self.assertEqual(cm.exception.code, 1)

    def test_test_errors_detected_exits_1(self):
        xml_path = self._create_xml(
            "error_tests.xml",
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<testsuite name="com.cbstudio.ErrorTest" tests="4" failures="0" errors="1" skipped="0" time="0.050">\n'
            '  <testcase name="test1" classname="com.cbstudio.ErrorTest" time="0.01">\n'
            '    <error message="Null pointer">Stacktrace</error>\n'
            '  </testcase>\n'
            '</testsuite>'
        )
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml_path])
        self.assertEqual(cm.exception.code, 1)

    def test_invariant_violation_negative_passed_exits_1(self):
        # tests=2, failures=3 -> passed = 2 - 3 = -1 -> invariant failure
        xml_path = self._create_xml(
            "invalid_counts.xml",
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<testsuite name="com.cbstudio.BadCountTest" tests="2" failures="3" errors="0" skipped="0" time="0.010">\n'
            '</testsuite>'
        )
        with self.assertRaises(SystemExit) as cm:
            verify_junit_xml.verify_junit_xml([xml_path])
        self.assertEqual(cm.exception.code, 1)

    def test_valid_suite_success_returns_0(self):
        xml_path = self._create_xml(
            "valid_suite.xml",
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<testsuite name="com.cbstudio.ValidTest" tests="10" failures="0" errors="0" skipped="2" time="0.350">\n'
            '  <testcase name="test1" classname="com.cbstudio.ValidTest" time="0.05"/>\n'
            '  <testcase name="test2" classname="com.cbstudio.ValidTest" time="0.00"><skipped/></testcase>\n'
            '</testsuite>'
        )
        ret = verify_junit_xml.verify_junit_xml([xml_path])
        self.assertEqual(ret, 0)

    def test_valid_nested_testsuites_success_returns_0(self):
        xml_path = self._create_xml(
            "valid_nested.xml",
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<testsuites>\n'
            '  <testsuite name="com.cbstudio.SuiteA" tests="5" failures="0" errors="0" skipped="0" time="0.100"/>\n'
            '  <testsuite name="com.cbstudio.SuiteB" tests="8" failures="0" errors="0" skipped="1" time="0.200"/>\n'
            '</testsuites>'
        )
        ret = verify_junit_xml.verify_junit_xml([xml_path])
        self.assertEqual(ret, 0)

    def test_disabled_attribute_counted_as_skipped(self):
        xml_path = self._create_xml(
            "disabled_tests.xml",
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<testsuite name="com.cbstudio.DisabledTest" tests="6" failures="0" errors="0" skipped="1" disabled="2" time="0.080">\n'
            '</testsuite>'
        )
        ret = verify_junit_xml.verify_junit_xml([xml_path])
        self.assertEqual(ret, 0)

    def test_time_parsing_robustness(self):
        xml_path = self._create_xml(
            "time_test.xml",
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<testsuite name="com.cbstudio.TimeTest" tests="3" failures="0" errors="0" skipped="0" time="1,234.56">\n'
            '</testsuite>'
        )
        ret = verify_junit_xml.verify_junit_xml([xml_path])
        self.assertEqual(ret, 0)

    def test_cli_subprocess_zero_tests_fails(self):
        xml_path = self._create_xml(
            "cli_zero.xml",
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<testsuite name="com.cbstudio.CliZeroTest" tests="0" failures="0" errors="0" skipped="0" time="0.0">\n'
            '</testsuite>'
        )
        script_path = os.path.join(SCRIPTS_DIR, "verify_junit_xml.py")
        res = subprocess.run(
            [sys.executable, script_path, xml_path],
            capture_output=True,
            text=True
        )
        self.assertEqual(res.returncode, 1)
        self.assertIn("0 tests were executed", res.stderr)

    def test_cli_subprocess_success(self):
        xml_path = self._create_xml(
            "cli_success.xml",
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<testsuite name="com.cbstudio.CliSuccessTest" tests="4" failures="0" errors="0" skipped="0" time="0.05">\n'
            '</testsuite>'
        )
        script_path = os.path.join(SCRIPTS_DIR, "verify_junit_xml.py")
        res = subprocess.run(
            [sys.executable, script_path, xml_path],
            capture_output=True,
            text=True
        )
        self.assertEqual(res.returncode, 0)
        self.assertIn("All JUnit XML test count invariants", res.stdout)


if __name__ == "__main__":
    unittest.main()
