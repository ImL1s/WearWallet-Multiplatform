# Scripts in this public tree

These helpers are optional. Public CI runs the Python guards and Gradle tasks
directly. None of this is 1Password, Play Console, or private-vault export
tooling.

## Documentation and CI guards

| Script | Role |
| --- | --- |
| [`check_markdown_links.py`](./check_markdown_links.py) | Local Markdown target existence |
| [`check_ci_pat_fallback.py`](./check_ci_pat_fallback.py) | CI must fall back when `GH_TOKEN_PACKAGES` is empty |
| [`check_release_manifest_attack_surface.py`](./check_release_manifest_attack_surface.py) | Release-manifest job input |
| [`generate_release_checksums.py`](./generate_release_checksums.py) | `SHA256SUMS.txt` for prereleases |
| [`verify_junit_xml.py`](./verify_junit_xml.py) | JUnit XML presence check |

Matching unit tests live under [`tests/`](./tests/).

## Wear / Apple helpers

| Script | Role |
| --- | --- |
| [`build-watchos.sh`](./build-watchos.sh) | Link the watchOS simulator `coreKmp` framework |
| [`capture-wear-screenshots.py`](./capture-wear-screenshots.py) | AVD capture after a debug install (`--serial` required) |
| [`auto-screenshot.sh`](./auto-screenshot.sh) | Shell helper around adb screencap (serial as first arg) |

Wear serial must come from `adb devices -l` and be passed through. See
[`docs/WEAR_OS_INSTALL.md`](../docs/WEAR_OS_INSTALL.md) and
[`docs/SCREENSHOTS.md`](../docs/SCREENSHOTS.md).

## Optional env wrapper

[`build-with-validation.sh`](./build-with-validation.sh) sources ignored
`.env` (`set -a; source .env; set +a`) then runs `./gradlew`. Gradle itself
does **not** load `.env`. Prefer that explicit `source` in your shell, or
user-level `~/.gradle/gradle.properties`. See
[`docs/API_CONFIGURATION.md`](../docs/API_CONFIGURATION.md).

[`validate-build.sh`](./validate-build.sh) reports presence/format of
credential inputs and does not print secret values. This public tree has no
`.gitmodules`; submodule checks in that script are skipped when the file is
absent.

## Not a public setup path

Scripts that mention 1Password, store listing automation, or private export
are leftovers or local conveniences. They are **not** the supported clone
path. Do not reconstruct private credential-management here.
