#!/usr/bin/env python3
"""Fail if the Wear/mobile *release* manifest graph exposes debug or backup surface.

Inspects committed ``src/main`` + ``src/release`` overlays by default.
With ``--merged``, inspects AGP merged release manifests instead (after
``:wear:processReleaseMainManifest`` / ``:mobile:processReleaseMainManifest``).

``src/debug`` is intentionally ignored: VoiceCommandActivity may live there.
"""

from __future__ import annotations

import argparse
import pathlib
import sys
import xml.etree.ElementTree as ET

ANDROID_NS = "http://schemas.android.com/apk/res/android"
TOOLS_NS = "http://schemas.android.com/tools"

SOURCE_MANIFESTS = (
    "wear/src/main/AndroidManifest.xml",
    "wear/src/release/AndroidManifest.xml",
    "mobile/src/main/AndroidManifest.xml",
    "mobile/src/release/AndroidManifest.xml",
)

MERGED_GLOBS = (
    "wear/build/intermediates/merged_manifest/release/**/AndroidManifest.xml",
    "wear/build/intermediates/merged_manifests/release/**/AndroidManifest.xml",
    "wear/build/intermediates/packaged_manifests/release/**/AndroidManifest.xml",
    "mobile/build/intermediates/merged_manifest/release/**/AndroidManifest.xml",
    "mobile/build/intermediates/merged_manifests/release/**/AndroidManifest.xml",
    "mobile/build/intermediates/packaged_manifests/release/**/AndroidManifest.xml",
)


def local_tag(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def attr(elem: ET.Element, name: str, namespace: str = ANDROID_NS) -> str:
    return elem.attrib.get(f"{{{namespace}}}{name}", "") or ""


def is_removed(elem: ET.Element) -> bool:
    node = attr(elem, "node", TOOLS_NS).lower()
    return node in {"remove", "removeall"}


def collect_source_manifests(root: pathlib.Path) -> list[pathlib.Path]:
    found: list[pathlib.Path] = []
    missing_required: list[str] = []
    for relative in SOURCE_MANIFESTS:
        path = root / relative
        if path.is_file():
            found.append(path)
        elif "/src/main/" in relative:
            missing_required.append(relative)
    if missing_required:
        raise SystemExit(
            "missing required release source manifests:\n  " + "\n  ".join(missing_required)
        )
    return found


def collect_merged_manifests(root: pathlib.Path) -> list[pathlib.Path]:
    found: list[pathlib.Path] = []
    seen: set[pathlib.Path] = set()
    for pattern in MERGED_GLOBS:
        for path in root.glob(pattern):
            if path.is_file() and path not in seen:
                seen.add(path)
                found.append(path)
    wear = [p for p in found if "wear/build/" in str(p).replace("\\", "/")]
    mobile = [p for p in found if "mobile/build/" in str(p).replace("\\", "/")]
    if not wear or not mobile:
        raise SystemExit(
            "merged release manifests not found for wear and mobile. "
            "Run :wear:processReleaseMainManifest and "
            ":mobile:processReleaseMainManifest first.\n"
            f"searched under {root}"
        )
    return found


def scan_manifest(path: pathlib.Path) -> list[str]:
    violations: list[str] = []
    try:
        tree = ET.parse(path)
    except ET.ParseError as exc:
        return [f"{path}: XML parse error: {exc}"]

    for elem in tree.iter():
        if is_removed(elem):
            continue
        android_name = attr(elem, "name")
        tag = local_tag(elem.tag)

        if "MoneroDeviceTest" in android_name or "MoneroDeviceTest" in attr(elem, "label"):
            violations.append(f"{path}: {tag} declares MoneroDeviceTest ({android_name})")
        if "VoiceCommandActivity" in android_name:
            violations.append(f"{path}: {tag} declares VoiceCommandActivity ({android_name})")
        if ".presentation.debug." in android_name:
            violations.append(
                f"{path}: {tag} android:name contains .presentation.debug. ({android_name})"
            )
        if "SYSTEM_ALERT_WINDOW" in android_name:
            violations.append(f"{path}: {tag} declares SYSTEM_ALERT_WINDOW")
        if tag == "application" and attr(elem, "allowBackup") == "true":
            violations.append(f'{path}: application android:allowBackup="true"')
    return violations


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--root",
        type=pathlib.Path,
        default=None,
        help="Repository root (default: parent of scripts/)",
    )
    parser.add_argument(
        "--merged",
        action="store_true",
        help="Inspect AGP merged release manifests instead of source overlays",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = (args.root or pathlib.Path(__file__).resolve().parents[1]).resolve()
    manifests = collect_merged_manifests(root) if args.merged else collect_source_manifests(root)
    violations: list[str] = []
    for path in manifests:
        violations.extend(scan_manifest(path))
    if violations:
        print("release manifest attack-surface check FAILED:", file=sys.stderr)
        for item in violations:
            print(f"  - {item}", file=sys.stderr)
        return 1
    print("release manifest attack-surface check passed:")
    for path in manifests:
        print(f"  {path.relative_to(root)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
