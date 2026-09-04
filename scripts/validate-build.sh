#!/bin/bash

# Validate the credential inputs used by a WearWallet Gradle build.
# This script reports only presence and format; it never prints secret values.

set -euo pipefail

printf '%s\n' 'WearWallet build environment validation'
printf '%s\n' '======================================='

missing=0

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "$repo_root/.gitmodules" ]]; then
    empty_submodules=0
    while IFS= read -r line; do
        [[ -n "$line" ]] || continue
        status_char="${line:0:1}"
        path="$(awk '{print $2}' <<<"$line")"
        [[ -n "$path" ]] || continue

        if [[ "$status_char" == "-" ]]; then
            echo "ERROR: required submodule is not initialized: $path"
            echo "This public tree has no .gitmodules; inspect the missing path."
            empty_submodules=1
            continue
        fi

        if [[ ! -d "$repo_root/$path" ]]; then
            echo "ERROR: required submodule path is missing: $path"
            echo "This public tree has no .gitmodules; inspect the missing path."
            empty_submodules=1
            continue
        fi

        # Use -quit so find exits 0 itself (avoid SIGPIPE under pipefail from `head`).
        if ! find "$repo_root/$path" -mindepth 1 ! -name '.git' -print -quit 2>/dev/null | grep -q .; then
            echo "ERROR: required submodule is missing or empty: $path"
            echo "This public tree has no .gitmodules; inspect the missing path."
            empty_submodules=1
        fi
    done < <(git -C "$repo_root" submodule status --recursive)

    if [[ "$empty_submodules" -eq 0 ]]; then
        echo 'OK: git submodules are initialized'
    else
        missing=1
    fi
fi

if [[ ! -f "$repo_root/local.properties" ]]; then
    echo 'WARNING: local.properties is missing; Android builds need sdk.dir.'
    echo 'Copy local.properties.template to local.properties and set sdk.dir.'
fi

read_user_gradle_property() {
    local key="$1"
    local properties_file="${HOME:-}/.gradle/gradle.properties"

    [[ -n "${HOME:-}" && -f "$properties_file" ]] || return 0
    awk -v key="$key" '
        /^[[:space:]]*[#!]/ { next }
        {
            line = $0
            sub(/^[[:space:]]*/, "", line)
            if (index(line, key) != 1) next
            rest = substr(line, length(key) + 1)
            if (rest != "" && rest !~ /^([[:space:]]|[:=])/) next
            if (rest ~ /^[[:space:]]/) {
                sub(/^[[:space:]]*/, "", rest)
                sub(/^[:=][[:space:]]*/, "", rest)
            } else {
                sub(/^[:=][[:space:]]*/, "", rest)
            }
            sub(/[[:space:]]*$/, "", rest)
            value = rest
            found = 1
        }
        END { if (found) print value }
    ' "$properties_file"
}

github_source='environment'

if [[ "${GITHUB_ACTOR+x}" = x ]]; then
    github_actor="$GITHUB_ACTOR"
else
    github_actor=$(read_user_gradle_property 'github.actor')
    github_source='environment or user-level Gradle properties'
fi

if [[ "${GITHUB_TOKEN+x}" = x ]]; then
    github_token="$GITHUB_TOKEN"
else
    github_token=$(read_user_gradle_property 'github.token')
    github_source='environment or user-level Gradle properties'
fi

if [[ -n "$github_actor" && -n "$github_token" ]]; then
    echo "OK: GitHub Packages credentials are present via $github_source"
else
    echo 'ERROR: GitHub Packages credentials are required for package resolution'
    echo 'Set GITHUB_ACTOR and GITHUB_TOKEN, or github.actor and github.token in ~/.gradle/gradle.properties.'
    missing=1
fi

infura_value="${INFURA_PROJECT_ID:-}"
infura_source='environment'

if [[ -z "$infura_value" && -f local.properties ]]; then
    infura_value=$(awk -F= '
        /^infura\.project\.id=/ {
            sub(/^[^=]*=/, "")
            print
            exit
        }
    ' local.properties)
    infura_source='local.properties'
fi

if [[ -z "$infura_value" ]]; then
    echo 'WARNING: no Infura project ID was found; service-connected Infura features may be unavailable.'
    echo 'Set INFURA_PROJECT_ID, or use an ignored local.properties file when those features are needed.'
else
    echo "OK: Infura configuration is present via $infura_source"
    if [[ ! "$infura_value" =~ ^[A-Za-z0-9_-]{20,}$ ]]; then
        echo 'WARNING: Infura value has an unexpected shape; verify it without logging it.'
    fi
fi

if [[ "$missing" -ne 0 ]]; then
    exit 1
fi

printf '%s\n' 'Environment validation passed.'
