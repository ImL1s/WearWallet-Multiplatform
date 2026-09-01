<div align="center">

**English** | **[繁體中文](./ROADMAP.zh-TW.md)**

</div>

# WearWallet roadmap

This roadmap records priorities without promising release dates. An item is not
complete until the relevant implementation, tests, exact-head CI, review, and
required device or hardware evidence exist.

## 1. Security and correctness

- Keep production signing paths fail closed across every platform and backend.
- Replace remaining placeholders, permissive fallbacks, and unverified chain
  implementations.
- Expand fixed-vector and negative testing for wallet derivation, signing, and
  transaction encoding.
- Keep secret storage, deletion, logging, and analytics boundaries explicit.

## 2. `coreKmp` capability clarity

- Reduce the gap between declared adapter APIs and verified implementation.
- Publish a generated API reference only after its tasks are reproducible.
- Track support by platform, network, wallet type, signer, and backend rather
  than using a single chain-support label.
- Remove stale `shared` and `sharedKmp` references from maintained tooling and
  documentation.

## 3. Platform verification

- Maintain Android/Wear OS unit and build coverage.
- Keep iOS and watchOS Kotlin/Native compile and framework-link checks green.
- Add repeatable simulator checks for platform integration.
- Record physical phone, watch, and Keystone checks separately from automated
  evidence.

## 4. Product and release readiness

- Stabilize wallet creation/import, persistence, receive, and transaction flows.
- Validate accessibility, small-screen layout, offline behavior, and error
  recovery on supported devices.
- Keep debug, sideload, store-testing, and production evidence separate.
- Require signed-artifact provenance and rollback notes for releases.

## 5. Developer experience and documentation

- Keep one maintained documentation index and dependency-free local link check.
- Shorten historical status reports or move them to an archive/evidence area.
- Make setup and validation commands reproducible from a clean clone.
- Remove tracked generated outputs, logs, and platform dependency artifacts in
  a dedicated cleanup after ownership and regeneration are verified.

## Status definitions

| Status | Meaning |
| --- | --- |
| Proposed | Problem and acceptance criteria are documented |
| Implemented | Code exists on a review branch |
| Locally verified | Relevant commands passed in a clean worktree |
| CI verified | Required checks passed on the exact head commit |
| Device verified | Required physical device or hardware check is recorded |
| Released | Signed artifact and store/release state are confirmed |

The previous long-form 2025 roadmap is retained as a
[historical snapshot](./archive/ROADMAP-legacy-2025.md), not current status.
