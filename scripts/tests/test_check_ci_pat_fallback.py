#!/usr/bin/env python3
"""TDD tests for scripts/check_ci_pat_fallback.py."""

from __future__ import annotations

import importlib.util
import pathlib
import unittest

SCRIPT = pathlib.Path(__file__).parents[1] / "check_ci_pat_fallback.py"
SPEC = importlib.util.spec_from_file_location("check_ci_pat_fallback", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
checker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checker)

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
CI_YML = REPO_ROOT / ".github" / "workflows" / "ci.yml"

REQUIRES_PAT_NO_FALLBACK = """\
name: CI
jobs:
  build-test:
    steps:
      - name: Create local gradle.properties
        run: |
          echo "github.actor=${{ github.actor }}" >> gradle.properties
          echo "github.token=${{ secrets.GH_TOKEN_PACKAGES }}" >> gradle.properties
      - name: Assemble Wear debug APK
        env:
          GITHUB_TOKEN: ${{ secrets.GH_TOKEN_PACKAGES }}
        run: ./gradlew :wear:assembleDebug -PpublicSnapshot=true
"""

FALLBACK_BUT_FORK_INJECTS_TOKEN = """\
name: CI
jobs:
  build-test:
    steps:
      - name: Create local gradle.properties
        run: |
          TOKEN='${{ secrets.GH_TOKEN_PACKAGES }}'
          if [ -z "$TOKEN" ]; then
            TOKEN='${{ secrets.GITHUB_TOKEN }}'
          fi
          echo "github.token=$TOKEN" >> gradle.properties
      - name: Assemble Wear debug APK
        env:
          GITHUB_TOKEN: ${{ secrets.GH_TOKEN_PACKAGES != '' && secrets.GH_TOKEN_PACKAGES || secrets.GITHUB_TOKEN }}
        run: ./gradlew :wear:assembleDebug -PpublicSnapshot=true
"""

FORK_SAFE_WITH_FALLBACK = """\
name: CI
jobs:
  build-test:
    steps:
      - name: Create local gradle.properties
        run: |
          TOKEN='${{ secrets.GH_TOKEN_PACKAGES }}'
          if [ -z "$TOKEN" ]; then
            TOKEN='${{ secrets.GITHUB_TOKEN }}'
          fi
          if [ "${{ github.event.pull_request.head.repo.fork }}" != "true" ] && [ -n "$TOKEN" ]; then
            echo "github.actor=${ACTOR:-${{ github.actor }}}" >> gradle.properties
            echo "github.token=$TOKEN" >> gradle.properties
          fi
      - name: Assemble Wear debug APK
        env:
          GITHUB_TOKEN: ${{ secrets.GH_TOKEN_PACKAGES != '' && secrets.GH_TOKEN_PACKAGES || secrets.GITHUB_TOKEN }}
        run: ./gradlew :wear:assembleDebug -PpublicSnapshot=true
"""


class CiPatFallbackTest(unittest.TestCase):
    def test_requires_pat_without_fallback_fails(self) -> None:
        violations = checker.analyze_workflow(REQUIRES_PAT_NO_FALLBACK)
        joined = "\n".join(violations)
        self.assertTrue(
            any("fallback" in item.lower() for item in violations),
            joined,
        )

    def test_injects_github_token_on_fork_without_guard_fails(self) -> None:
        violations = checker.analyze_workflow(FALLBACK_BUT_FORK_INJECTS_TOKEN)
        joined = "\n".join(violations)
        self.assertTrue(
            any("fork" in item.lower() for item in violations),
            joined,
        )

    def test_fork_safe_fallback_fixture_passes(self) -> None:
        self.assertEqual(checker.analyze_workflow(FORK_SAFE_WITH_FALLBACK), [])

    def test_committed_ci_yml_passes(self) -> None:
        self.assertTrue(CI_YML.is_file(), f"missing {CI_YML}")
        violations = checker.analyze_workflow(CI_YML.read_text(encoding="utf-8"))
        self.assertEqual(violations, [])


if __name__ == "__main__":
    unittest.main()
