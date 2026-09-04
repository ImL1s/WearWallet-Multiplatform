#!/usr/bin/env python3
"""Validate local file targets in maintained, tracked Markdown documents."""

from __future__ import annotations

import argparse
import pathlib
import re
import subprocess
import sys
import urllib.parse


REFERENCE_LINK_RE = re.compile(r"^\s*\[[^\]]+\]:\s*(.*)$")
HTML_LINK_RE = re.compile(r"<(?:a|img)\b[^>]*\b(?:href|src)=[\"']([^\"']+)[\"']", re.I)
SCHEME_RE = re.compile(r"^[A-Za-z][A-Za-z0-9+.-]*:")
FENCE_RE = re.compile(r"^ {0,3}(`{3,}|~{3,})(.*)$")

DEFAULT_EXCLUDED_PREFIXES = (
    ".claude/",
    ".kiro/",
    ".serena/",
    "coreKmp/native/archived/",
    "docs/archive/",
    "docs/evidence/",
    "scripts/public-export/overlay/",
    "watchos/Pods/",
)


def markdown_candidates(root: pathlib.Path) -> list[pathlib.Path]:
    output = subprocess.check_output(
        [
            "git",
            "ls-files",
            "--cached",
            "--others",
            "--exclude-standard",
            "-z",
            "--",
            "*.md",
        ],
        cwd=root,
    )
    paths: set[pathlib.Path] = set()
    for raw_path in output.split(b"\0"):
        if not raw_path:
            continue
        relative = raw_path.decode()
        if relative.startswith(DEFAULT_EXCLUDED_PREFIXES):
            continue
        paths.add(root / relative)
    return sorted(paths)


def parse_destination(text: str) -> str | None:
    """Return one Markdown destination, preserving balanced parentheses."""
    text = text.lstrip()
    if not text:
        return None

    if text.startswith("<"):
        escaped = False
        for index, char in enumerate(text[1:], 1):
            if char == ">" and not escaped:
                return text[1:index]
            escaped = char == "\\" and not escaped
            if char != "\\":
                escaped = False
        return None

    destination: list[str] = []
    depth = 0
    escaped = False
    for char in text:
        if escaped:
            destination.append(char)
            escaped = False
            continue
        if char == "\\":
            escaped = True
            continue
        if char == "(":
            depth += 1
            destination.append(char)
            continue
        if char == ")":
            if depth == 0:
                break
            depth -= 1
            destination.append(char)
            continue
        if char.isspace() and depth == 0:
            break
        destination.append(char)

    return "".join(destination) or None


def inline_markdown_targets(line: str) -> list[str]:
    targets: list[str] = []
    search_from = 0
    while True:
        marker = line.find("](", search_from)
        if marker == -1:
            return targets
        destination = parse_destination(line[marker + 2 :])
        if destination:
            targets.append(destination)
        search_from = marker + 2


def extract_targets(line: str) -> list[str]:
    targets = inline_markdown_targets(line)
    reference_match = REFERENCE_LINK_RE.match(line)
    if reference_match:
        destination = parse_destination(reference_match.group(1))
        if destination:
            targets.append(destination)
    targets.extend(match.group(1) for match in HTML_LINK_RE.finditer(line))
    return targets


def mask_html_comments(line: str, in_comment: bool) -> tuple[str, bool]:
    """Mask Markdown HTML comments while preserving line length."""
    chars = list(line)
    cursor = 0
    while cursor < len(line):
        if in_comment:
            end = line.find("-->", cursor)
            if end == -1:
                return " " * len(line), True
            for index in range(cursor, end + 3):
                chars[index] = " "
            cursor = end + 3
            in_comment = False
            continue

        start = line.find("<!--", cursor)
        if start == -1:
            break
        end = line.find("-->", start + 4)
        if end == -1:
            for index in range(start, len(line)):
                chars[index] = " "
            in_comment = True
            break
        for index in range(start, end + 3):
            chars[index] = " "
        cursor = end + 3

    return "".join(chars), in_comment


def mask_inline_code(line: str) -> str:
    """Mask matched backtick code spans so example links are not validated."""
    chars = list(line)
    cursor = 0
    while cursor < len(line):
        if line[cursor] != "`":
            cursor += 1
            continue

        run_end = cursor
        while run_end < len(line) and line[run_end] == "`":
            run_end += 1
        delimiter = line[cursor:run_end]
        closing = line.find(delimiter, run_end)
        if closing == -1:
            cursor = run_end
            continue
        for index in range(cursor, closing + len(delimiter)):
            chars[index] = " "
        cursor = closing + len(delimiter)

    return "".join(chars)


def fence_marker(line: str) -> tuple[str, int, str] | None:
    """Return marker details for a fence indented by at most three spaces."""
    match = FENCE_RE.match(line)
    if match is None:
        return None
    marker = match.group(1)
    return marker[0], len(marker), match.group(2)


def normalize_target(raw_target: str) -> str | None:
    target = raw_target.strip()
    if not target or target.startswith(("#", "//")) or SCHEME_RE.match(target):
        return None

    target = urllib.parse.unquote(target.split("#", 1)[0].split("?", 1)[0])
    return target or None


def resolve_target(
    root: pathlib.Path, source: pathlib.Path, target: str
) -> pathlib.Path:
    if target.startswith("/"):
        return root / target.lstrip("/")
    return source.parent / target


def relative_display(root: pathlib.Path, path: pathlib.Path) -> str:
    try:
        return str(path.resolve().relative_to(root))
    except ValueError:
        return str(path.resolve())


def find_missing_targets(root: pathlib.Path) -> list[tuple[pathlib.Path, int, str, pathlib.Path]]:
    failures: list[tuple[pathlib.Path, int, str, pathlib.Path]] = []
    for source in markdown_candidates(root):
        # A tracked file can be removed in the current worktree before the
        # deletion is staged. Deleted documents have no links left to validate.
        if not source.exists():
            continue
        fence_character: str | None = None
        fence_length = 0
        in_html_comment = False
        for line_number, line in enumerate(
            source.read_text(encoding="utf-8", errors="replace").splitlines(), 1
        ):
            marker = fence_marker(line)
            if fence_character is None and marker is not None:
                fence_character, fence_length = marker[:2]
                continue
            if fence_character is not None:
                if (
                    marker is not None
                    and marker[0] == fence_character
                    and marker[1] >= fence_length
                    and not marker[2].strip()
                ):
                    fence_character = None
                    fence_length = 0
                continue

            visible_line, in_html_comment = mask_html_comments(line, in_html_comment)
            visible_line = mask_inline_code(visible_line)
            for raw_target in extract_targets(visible_line):
                target = normalize_target(raw_target)
                if target is None:
                    continue
                resolved = resolve_target(root, source, target)
                if not resolved.exists():
                    failures.append((source, line_number, raw_target, resolved))
    return failures


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "root",
        nargs="?",
        type=pathlib.Path,
        default=pathlib.Path.cwd(),
        help="repository root (default: current directory)",
    )
    args = parser.parse_args()
    root = args.root.resolve()

    failures = find_missing_targets(root)
    for source, line_number, raw_target, resolved in failures:
        print(
            f"{source.relative_to(root)}:{line_number}: "
            f"{raw_target} -> {relative_display(root, resolved)}"
        )

    if failures:
        print(f"\nFound {len(failures)} missing local Markdown target(s).")
        return 1

    print("All maintained Markdown local targets exist.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
