#!/usr/bin/env python3
"""Fail if public CI regresses to requiring a maintainer PAT with no fallback.

Public assemble jobs may prefer ``secrets.GH_TOKEN_PACKAGES`` when a maintainer
sets it, but they must fall back to the job ``GITHUB_TOKEN`` when that secret is
empty. Fork PRs must not receive maintainer ``github.token`` in
``gradle.properties``.
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys

DEFAULT_WORKFLOW = pathlib.Path(".github/workflows/ci.yml")

BARE_PAT_ENV = re.compile(
    r"GITHUB_TOKEN:\s*\$\{\{\s*secrets\.GH_TOKEN_PACKAGES\s*\}\}"
)
EXPR_FALLBACK = (
    "secrets.GH_TOKEN_PACKAGES != '' && secrets.GH_TOKEN_PACKAGES || secrets.GITHUB_TOKEN"
)
EMPTY_TOKEN_FALLBACK = re.compile(
    r"""if\s+\[\s+-z\s+"\$TOKEN"\s*\]\s*;\s*then\s+TOKEN='\$\{\{\s*secrets\.GITHUB_TOKEN\s*\}\}'""",
    re.DOTALL,
)
FORK_THEN_TOKEN = re.compile(
    r"pull_request\.head\.repo\.fork[\s\S]{0,800}github\.token=",
    re.IGNORECASE,
)


def analyze_workflow(text: str) -> list[str]:
    """Return human-readable violations; empty means the workflow is acceptable."""
    violations: list[str] = []
    if not text.strip():
        return ["workflow is empty"]

    if BARE_PAT_ENV.search(text):
        violations.append(
            "GITHUB_TOKEN env uses secrets.GH_TOKEN_PACKAGES with no "
            "secrets.GITHUB_TOKEN fallback"
        )

    has_pat = "secrets.GH_TOKEN_PACKAGES" in text
    has_empty_fallback = bool(EMPTY_TOKEN_FALLBACK.search(text))
    has_expr_fallback = EXPR_FALLBACK in text
    if has_pat and not (has_empty_fallback or has_expr_fallback):
        violations.append(
            "secrets.GH_TOKEN_PACKAGES is used without a GITHUB_TOKEN fallback"
        )

    writes_gradle_token = bool(re.search(r"github\.token=", text))
    if writes_gradle_token:
        if "github.event.pull_request.head.repo.fork" not in text:
            violations.append(
                "github.token is written to gradle.properties without a fork-PR guard"
            )
        elif not FORK_THEN_TOKEN.search(text):
            violations.append(
                "fork guard does not precede github.token gradle.properties injection"
            )

    return violations


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "workflow",
        nargs="?",
        default=str(DEFAULT_WORKFLOW),
        help="Path to ci.yml (default: .github/workflows/ci.yml)",
    )
    args = parser.parse_args(argv)
    path = pathlib.Path(args.workflow)
    if not path.is_file():
        print(f"missing workflow: {path}", file=sys.stderr)
        return 1
    violations = analyze_workflow(path.read_text(encoding="utf-8"))
    if violations:
        print(f"{path}: CI PAT fallback guard failed:")
        for item in violations:
            print(f"  - {item}")
        return 1
    print(f"{path}: GITHUB_TOKEN fallback present; fork PRs do not get github.token")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
