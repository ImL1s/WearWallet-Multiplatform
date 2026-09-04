#!/usr/bin/env python3
"""Generate export-manifest.json for the WearWallet public export tree.

Records every shipped file's path/size/sha256 plus scanner metadata and the
guard ledger, so a maintainer (or CI) can audit exactly what was published and
which guards ran. The exporter refuses to publish if any required guard is
missing from the ledger (see scripts/export-public.sh).
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    ap.add_argument("--source-sha", required=True)
    ap.add_argument("--source-branch", required=True)
    ap.add_argument("--export-utc", required=True)
    ap.add_argument("--gitleaks-version", required=True)
    ap.add_argument("--bip39-report", type=Path, default=None)
    ap.add_argument("--guard-log", type=Path, required=True)
    args = ap.parse_args()

    root = args.root.resolve()
    files = []
    total_bytes = 0
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        if ".git" in path.parts:
            continue
        rel = str(path.relative_to(root))
        if rel in ("export-manifest.json", ".guard-log.tsv"):
            continue
        size = path.stat().st_size
        total_bytes += size
        files.append({"path": rel, "size": size, "sha256": sha256_of(path)})

    bip39_summary = None
    if args.bip39_report and args.bip39_report.exists():
        bip39_data = json.loads(args.bip39_report.read_text(encoding="utf-8"))
        bip39_summary = {
            "distinct_phrases_found": bip39_data.get("distinct_phrases_found"),
            "reviewed_allowlisted_count": len(bip39_data.get("reviewed_allowlisted", [])),
            "unreviewed_blocking_count": len(bip39_data.get("unreviewed_blocking", [])),
            "skip_dirs": bip39_data.get("skip_dirs", []),
        }

    guards = []
    if args.guard_log.exists():
        for line in args.guard_log.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            name, status = (line.split("\t", 1) + ["UNKNOWN"])[:2]
            guards.append({"name": name, "status": status})

    manifest = {
        "schema_version": 1,
        "source_repo": "ImL1s/WearWallet",
        "source_sha": args.source_sha,
        "source_branch": args.source_branch,
        "export_utc": args.export_utc,
        "file_count": len(files),
        "total_bytes": total_bytes,
        "scanners": {
            "gitleaks_version": args.gitleaks_version,
            "bip39_scanner": bip39_summary,
        },
        "guards": guards,
        "files": files,
    }
    args.out.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(f"wrote manifest: {args.out} ({len(files)} files, {total_bytes} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
