#!/usr/bin/env python3
"""
scripts/generate_release_checksums.py

Generates the release checksums and provenance manifest (release-checksums.txt)
for WearWallet release artifacts.

Strict Fail-Closed Invariants:
1. Audited Git Commit SHA is retrieved via `git rev-parse HEAD` (or explicit CLI/env override)
   to ensure exact checked-out commit provenance, avoiding GITHUB_SHA PR synthetic merge commits.
2. Binary Validation: Enforces that matched release artifacts contain at least 1 APK (*.apk)
   and at least 1 AAB (*.aab) binary.
3. Fail-Closed Error Handling: If 0 artifacts match, or if either required binary type is missing,
   logs descriptive error to sys.stderr and exits with code 1 immediately without writing a partial manifest.
4. Produces deterministic SHA-256 hashes, file sizes, and forward-slash normalized relative paths.
"""

import argparse
import datetime
import glob
import hashlib
import os
import platform
import subprocess
import sys


def get_sha256(filepath: str) -> str:
    """Calculates SHA-256 hex digest for the specified file."""
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()


def get_commit_sha(override_sha: str = None) -> str:
    """
    Resolves the exact audited commit SHA.

    Priority Order:
    1. Explicit override passed as argument
    2. Explicit environment variables (RELEASE_COMMIT_SHA, AUDITED_COMMIT_SHA, PR_HEAD_SHA)
    3. `git rev-parse HEAD` (checked-out working tree HEAD)
    4. Fallback to `GITHUB_SHA` only if git fails (avoids PR synthetic merge commit issue)
    5. 'unknown-commit-sha'
    """
    if override_sha and str(override_sha).strip():
        return str(override_sha).strip()

    for env_var in ("RELEASE_COMMIT_SHA", "AUDITED_COMMIT_SHA", "PR_HEAD_SHA"):
        val = os.environ.get(env_var, "").strip()
        if val:
            return val

    # 3. Direct git query: must precede GITHUB_SHA
    try:
        res = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            capture_output=True,
            text=True,
            check=True
        )
        sha = res.stdout.strip()
        if sha:
            return sha
    except Exception:
        pass

    # 4. GITHUB_SHA fallback
    if "GITHUB_SHA" in os.environ and os.environ["GITHUB_SHA"].strip():
        return os.environ["GITHUB_SHA"].strip()

    return "unknown-commit-sha"


def get_jdk_version() -> str:
    java_home = os.environ.get("JAVA_HOME", "")
    if java_home:
        basename = os.path.basename(os.path.normpath(java_home))
        if basename:
            return basename
    try:
        res = subprocess.run(
            ["java", "-version"],
            capture_output=True,
            text=True
        )
        output = res.stderr or res.stdout
        lines = output.strip().splitlines()
        if lines:
            return lines[0].strip()
    except Exception:
        pass
    return "Temurin 17.0.12 / 21.0.9.10"


def get_version_from_toml(key: str, toml_path: str = "gradle/libs.versions.toml") -> str:
    if os.path.isfile(toml_path):
        try:
            with open(toml_path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line.startswith(f"{key} =") or line.startswith(f"{key}="):
                        parts = line.split("=", 1)
                        if len(parts) == 2:
                            return parts[1].strip().strip('"').strip("'")
        except Exception:
            pass
    return "unknown"


def get_gradle_version(props_path: str = "gradle/wrapper/gradle-wrapper.properties") -> str:
    if os.path.isfile(props_path):
        try:
            with open(props_path, "r", encoding="utf-8") as f:
                for line in f:
                    if "distributionUrl" in line:
                        for part in line.split("/"):
                            if "gradle-" in part:
                                return part.split("-all")[0].split("-bin")[0].replace("gradle-", "Gradle ")
        except Exception:
            pass
    return "Gradle 8.13"


def find_release_artifacts(extra_patterns=None, custom_patterns=None):
    """Discovers release artifacts based on glob patterns."""
    if custom_patterns:
        artifact_patterns = list(custom_patterns)
    else:
        artifact_patterns = [
            "wear/build/outputs/apk/release/*.apk",
            "wear/build/outputs/bundle/release/*.aab",
            "mobile/build/outputs/apk/release/*.apk",
            "mobile/build/outputs/bundle/release/*.aab",
        ]
        if extra_patterns:
            artifact_patterns.extend(extra_patterns)

    matched_files = []
    for pattern in artifact_patterns:
        for f in glob.glob(pattern, recursive=True):
            if os.path.isfile(f) and f not in matched_files:
                matched_files.append(f)

    return matched_files, artifact_patterns


def validate_artifacts(matched_files, require_apk: bool = True, require_aab: bool = True):
    """
    Strict fail-closed validation of discovered artifacts.
    Returns (is_valid: bool, error_message: str, apks: list, aabs: list)
    """
    apk_files = [f for f in matched_files if f.lower().endswith(".apk")]
    aab_files = [f for f in matched_files if f.lower().endswith(".aab")]

    if len(matched_files) == 0:
        return False, "No release artifacts found matching patterns.", apk_files, aab_files

    if require_apk and len(apk_files) == 0:
        return False, f"Missing required APK binary (*.apk). Found {len(apk_files)} APKs and {len(aab_files)} AABs.", apk_files, aab_files

    if require_aab and len(aab_files) == 0:
        return False, f"Missing required AAB binary (*.aab). Found {len(apk_files)} APKs and {len(aab_files)} AABs.", apk_files, aab_files

    return True, "", apk_files, aab_files


def generate_manifest(
    output_path: str = "release-checksums.txt",
    extra_patterns=None,
    custom_patterns=None,
    commit_sha_override: str = None,
    require_apk: bool = True,
    require_aab: bool = True,
    raise_on_error: bool = False
) -> int:
    """
    Generates release checksum manifest with strict fail-closed validation.

    Exits with code 1 (or raises RuntimeError if raise_on_error=True) when:
    - 0 release artifacts matched
    - Missing required APK binary (when require_apk=True)
    - Missing required AAB binary (when require_aab=True)
    """
    matched_files, artifact_patterns = find_release_artifacts(extra_patterns, custom_patterns)
    is_valid, err_msg, apk_files, aab_files = validate_artifacts(
        matched_files,
        require_apk=require_apk,
        require_aab=require_aab
    )

    if not is_valid:
        error_output = (
            f"❌ ERROR: {err_msg}\n"
            f"Searched patterns: {artifact_patterns}\n"
            f"Total matched: {len(matched_files)} (APKs: {len(apk_files)}, AABs: {len(aab_files)})"
        )
        print(error_output, file=sys.stderr)
        if raise_on_error:
            raise RuntimeError(err_msg)
        sys.exit(1)

    commit_sha = get_commit_sha(commit_sha_override)
    run_id = os.environ.get("GITHUB_RUN_ID", "local-build")
    run_attempt = os.environ.get("GITHUB_RUN_ATTEMPT", "1")
    workflow_name = os.environ.get("GITHUB_WORKFLOW", "CI Pipeline")
    timestamp_utc = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    jdk_version = get_jdk_version()
    gradle_version = get_gradle_version()
    kotlin_version = get_version_from_toml("kotlin")
    agp_version = get_version_from_toml("agp")
    host_env = f"{platform.system()} {platform.release()} ({platform.machine()})"

    lines = [
        "# ==============================================================================",
        "# WearWallet Release Artifact Manifest & Checksums",
        "# ==============================================================================",
        f"# Audited Commit SHA : {commit_sha}",
        f"# CI Run ID          : {run_id}",
        f"# CI Run Attempt     : {run_attempt}",
        f"# CI Workflow        : {workflow_name}",
        f"# Build Timestamp UTC: {timestamp_utc}",
        f"# JDK Toolchain      : {jdk_version}",
        f"# Gradle Version     : {gradle_version}",
        f"# Kotlin Version     : Kotlin {kotlin_version}",
        f"# AGP Version        : AGP {agp_version}",
        f"# Host Environment   : {host_env}",
        "# ==============================================================================",
        f"# {'SHA-256 Checksum':<64}  {'Size (bytes)':<12}  {'Artifact Relative Path'}",
        f"# {'-' * 64}  {'-' * 12}  {'-' * 32}"
    ]

    for f in sorted(matched_files):
        size = os.path.getsize(f)
        sha256 = get_sha256(f)
        try:
            rel_path = os.path.relpath(f, start=os.getcwd()).replace("\\", "/")
        except ValueError:
            rel_path = os.path.normpath(f).replace("\\", "/")
        lines.append(f"{sha256}  {size:<12}  {rel_path}")

    lines.append("# ==============================================================================")

    manifest_content = "\n".join(lines) + "\n"

    # Ensure parent directories exist
    output_dir = os.path.dirname(output_path)
    if output_dir:
        os.makedirs(output_dir, exist_ok=True)

    with open(output_path, "w", encoding="utf-8") as f:
        f.write(manifest_content)

    print(f"✅ Generated {output_path} with {len(matched_files)} artifacts ({len(apk_files)} APKs, {len(aab_files)} AABs) for commit {commit_sha[:8] if len(commit_sha) >= 8 else commit_sha}.")
    print(manifest_content)
    return len(matched_files)


def main():
    parser = argparse.ArgumentParser(
        description="Generate WearWallet release checksums manifest with fail-closed validation."
    )
    parser.add_argument(
        "output_file",
        nargs="?",
        default="release-checksums.txt",
        help="Output manifest file path (default: release-checksums.txt)"
    )
    parser.add_argument(
        "extra_patterns",
        nargs="*",
        default=[],
        help="Additional glob patterns to match release artifacts"
    )
    parser.add_argument(
        "--commit-sha",
        dest="commit_sha",
        default=None,
        help="Explicit commit SHA override"
    )
    parser.add_argument(
        "--no-require-apk",
        dest="require_apk",
        action="store_false",
        default=True,
        help="Disable mandatory APK requirement"
    )
    parser.add_argument(
        "--no-require-aab",
        dest="require_aab",
        action="store_false",
        default=True,
        help="Disable mandatory AAB requirement"
    )

    args = parser.parse_args()

    generate_manifest(
        output_path=args.output_file,
        extra_patterns=args.extra_patterns,
        commit_sha_override=args.commit_sha,
        require_apk=args.require_apk,
        require_aab=args.require_aab
    )


if __name__ == "__main__":
    main()
