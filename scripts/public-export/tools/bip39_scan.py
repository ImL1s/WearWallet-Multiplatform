#!/usr/bin/env python3
"""BIP39-aware mnemonic scanner for the WearWallet public export pipeline.

Scans a directory tree for text runs that are valid BIP39 mnemonics (12, 15,
18, 21, or 24 words, every word in the English wordlist, checksum valid) and
fails closed unless the exact phrase has been reviewed and recorded in the
allowlist by its sha256 fingerprint.

This is intentionally stricter than a naive "N lowercase words in a row"
heuristic: word-list membership + checksum validation makes accidental false
positives astronomically unlikely, so any hit is either a real mnemonic
(BLOCK) or a previously-reviewed, explicitly allowlisted fixture (PASS).

Usage:
    bip39_scan.py --root DIR --wordlist FILE --allowlist FILE --report FILE
    bip39_scan.py --root DIR --wordlist FILE --summary-only   # local triage,
                                                                # never prints
                                                                # phrases

Exit codes:
    0  no unreviewed findings (or --summary-only)
    1  one or more unreviewed BIP39 mnemonics found
    2  usage / IO error
"""
from __future__ import annotations

import argparse
import fnmatch
import hashlib
import json
import re
import sys
from pathlib import Path

VALID_LENGTHS = (24, 21, 18, 15, 12)  # longest first so we report the widest match
WORD_RUN_RE = re.compile(r"\b[a-z]+(?:[ \t]+[a-z]+){11,}\b")

# Directories we never need to scan (VCS metadata, not part of a text export).
SKIP_DIR_NAMES = {".git"}

# Binary/media extensions worth a fast skip before attempting a UTF-8 decode.
SKIP_EXTENSIONS = {
    ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".icns", ".bmp",
    ".zip", ".jar", ".aar", ".so", ".dylib", ".dll", ".a", ".o", ".class",
    ".ttf", ".otf", ".woff", ".woff2", ".keystore", ".jks", ".p12",
    ".mp3", ".mp4", ".mov", ".avi", ".pdf", ".xcframework",
}

MAX_FILE_BYTES = 25 * 1024 * 1024  # skip pathologically large files


def load_wordlist(path: Path) -> dict[str, int]:
    words = [w.strip() for w in path.read_text(encoding="utf-8").splitlines() if w.strip()]
    if len(words) != 2048:
        raise ValueError(f"expected 2048 BIP39 words, got {len(words)} from {path}")
    if len(set(words)) != 2048:
        raise ValueError(f"wordlist {path} contains duplicate entries")
    return {w: i for i, w in enumerate(words)}


def load_allowlist(path: Path | None) -> dict[str, dict]:
    if path is None or not path.exists():
        return {}
    data = json.loads(path.read_text(encoding="utf-8"))
    entries = data.get("entries", [])
    out: dict[str, dict] = {}
    for entry in entries:
        sha = entry.get("sha256")
        if not sha:
            continue
        out[sha.lower()] = entry
    return out


def checksum_valid(words: list[str], index: dict[str, int]) -> bool:
    n = len(words)
    total_bits = 11 * n
    checksum_bits = total_bits // 33
    entropy_bits = total_bits - checksum_bits
    if entropy_bits % 8 != 0:
        return False
    bits = "".join(format(index[w], "011b") for w in words)
    entropy_bin = bits[:entropy_bits]
    checksum_bin = bits[entropy_bits:]
    entropy_bytes = int(entropy_bin, 2).to_bytes(entropy_bits // 8, byteorder="big")
    digest = hashlib.sha256(entropy_bytes).digest()
    hash_bits = "".join(format(b, "08b") for b in digest)
    return hash_bits[:checksum_bits] == checksum_bin


def find_mnemonics_in_text(text: str, index: dict[str, int]) -> list[tuple[str, int]]:
    """Return list of (phrase, start_offset) for valid BIP39 mnemonics in text."""
    found: list[tuple[str, int]] = []
    for m in WORD_RUN_RE.finditer(text):
        run = m.group(0)
        words = run.split()
        n = len(words)
        # Slide every valid window length across this run; longest first avoids
        # reporting both a 24-word phrase and an internal 12-word sub-slice twice
        # when they overlap completely (rare, but keeps output tidy).
        used_ranges: list[tuple[int, int]] = []
        for length in VALID_LENGTHS:
            if length > n:
                continue
            for start in range(0, n - length + 1):
                if any(start < e and start + length > s for s, e in used_ranges):
                    continue
                window = words[start:start + length]
                if not all(w in index for w in window):
                    continue
                if checksum_valid(window, index):
                    phrase = " ".join(window)
                    found.append((phrase, m.start()))
                    used_ranges.append((start, start + length))
    return found


def is_probably_binary(data: bytes) -> bool:
    if b"\x00" in data:
        return True
    try:
        data.decode("utf-8")
    except UnicodeDecodeError:
        return True
    return False


def iter_text_files(root: Path, skip_dirs: set[str]):
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        rel_parts = path.relative_to(root).parts
        if any(part in SKIP_DIR_NAMES for part in path.parts):
            continue
        if rel_parts and rel_parts[0] in skip_dirs:
            continue
        if path.suffix.lower() in SKIP_EXTENSIONS:
            continue
        try:
            size = path.stat().st_size
        except OSError:
            continue
        if size == 0 or size > MAX_FILE_BYTES:
            continue
        yield path


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--root", required=True, type=Path)
    ap.add_argument("--wordlist", required=True, type=Path)
    ap.add_argument("--allowlist", type=Path, default=None)
    ap.add_argument("--report", type=Path, default=None, help="write JSON report here")
    ap.add_argument("--summary-only", action="store_true",
                     help="triage mode: never print phrases, only counts+hashes")
    ap.add_argument("--skip-dir", action="append", default=[],
                     help="top-level dir (relative to --root) to exclude from scanning, "
                          "e.g. --skip-dir modules (vendored from already-public upstream "
                          "repos; repeatable)")
    args = ap.parse_args()

    index = load_wordlist(args.wordlist)
    allow = {} if args.summary_only else load_allowlist(args.allowlist)
    skip_dirs = set(args.skip_dir)

    root = args.root.resolve()
    findings = []  # for report / unreviewed listing
    by_hash: dict[str, dict] = {}

    for path in iter_text_files(root, skip_dirs):
        try:
            data = path.read_bytes()
        except OSError:
            continue
        if is_probably_binary(data):
            continue
        try:
            text = data.decode("utf-8")
        except UnicodeDecodeError:
            continue
        hits = find_mnemonics_in_text(text, index)
        if not hits:
            continue
        rel = str(path.relative_to(root))
        for phrase, offset in hits:
            sha = hashlib.sha256(phrase.encode("utf-8")).hexdigest()
            line_no = text.count("\n", 0, offset) + 1
            entry = by_hash.setdefault(sha, {
                "sha256": sha,
                "word_count": len(phrase.split()),
                "files": [],
            })
            entry["files"].append({"path": rel, "line": line_no})

    unreviewed = []
    reviewed = []
    for sha, entry in by_hash.items():
        if sha in allow:
            reviewed.append({**entry, "allowlist_note": allow[sha].get("note", "")})
        else:
            unreviewed.append(entry)

    if args.summary_only:
        print(f"Distinct valid BIP39 phrases found: {len(by_hash)}")
        for sha, entry in sorted(by_hash.items()):
            print(f"  sha256={sha} words={entry['word_count']} files={len(entry['files'])}")
            for f in entry["files"][:5]:
                print(f"      {f['path']}:{f['line']}")
            if len(entry["files"]) > 5:
                print(f"      ... and {len(entry['files']) - 5} more")
        return 0

    report = {
        "root": str(root),
        "wordlist_words": len(index),
        "skip_dirs": sorted(skip_dirs),
        "distinct_phrases_found": len(by_hash),
        "reviewed_allowlisted": reviewed,
        "unreviewed_blocking": unreviewed,
    }
    if args.report:
        args.report.write_text(json.dumps(report, indent=2), encoding="utf-8")

    if unreviewed:
        print(f"BIP39 scanner: {len(unreviewed)} unreviewed mnemonic-like phrase(s) found "
              f"(not on allowlist by sha256). See report for file:line locations "
              f"(phrases themselves are not printed).", file=sys.stderr)
        for entry in unreviewed:
            locs = ", ".join(f"{f['path']}:{f['line']}" for f in entry["files"][:3])
            more = f" (+{len(entry['files']) - 3} more)" if len(entry["files"]) > 3 else ""
            print(f"  sha256={entry['sha256']} words={entry['word_count']} at {locs}{more}",
                  file=sys.stderr)
        return 1

    print(f"BIP39 scanner: {len(by_hash)} distinct phrase(s) found, all reviewed/allowlisted.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
