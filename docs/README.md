# Docs index

This public repo (`ImL1s/WearWallet-Multiplatform`) is the canonical development
tree. When documentation disagrees with the repository, prefer
`settings.gradle.kts`, module build files, current source, and CI.

Do **not** rename or restore this repository identity to `WearWallet-public`.
Historical archive, 1Password flows, and private evidence packs are **not**
shipped in this tree.

## Languages

- [English project overview](../README.md)
- [繁體中文專案概覽](./zh-TW/README.md)

## Start here

| Need | Document |
| --- | --- |
| Clone, credentials, CI, releases | [Public build notes](./PUBLIC_BUILD.md) · [繁體中文](./PUBLIC_BUILD.zh-TW.md) |
| Local keys, `sdk.dir`, `publicSnapshot`, signing | [API configuration](./API_CONFIGURATION.md) · [繁體中文](./API_CONFIGURATION.zh-TW.md) |
| Public-safe development notes | [Development guide](./DEVELOPMENT_GUIDE.md) · [繁體中文](./DEVELOPMENT_GUIDE.zh-TW.md) |
| Install Wear debug APK (emulator / sideload) | [Wear OS install](./WEAR_OS_INSTALL.md) · [繁體中文](./WEAR_OS_INSTALL.zh-TW.md) |
| Wear debug emulator QA overlays | [Wear QA harness](./WEAR_QA_HARNESS.md) · [繁體中文](./WEAR_QA_HARNESS.zh-TW.md) |
| Select and run checks | [Testing guide](./TESTING_GUIDE.md) · [繁體中文](./TESTING_GUIDE.zh-TW.md) |
| Understand current modules and data flow | [Architecture](./ARCHITECTURE.md) · [繁體中文](./ARCHITECTURE.zh-TW.md) |
| Review security boundaries (design) | [Security design](./SECURITY.md) · [繁體中文](./SECURITY.zh-TW.md) |
| Report a vulnerability | [Vulnerability policy](../SECURITY.md) · [GitHub path](../.github/SECURITY.md) |
| Work on the shared core | [`coreKmp` README](../coreKmp/README.md) · [API map](./COREKMP_API_OVERVIEW.md) · [繁體中文](./COREKMP_API_OVERVIEW.zh-TW.md) |
| Contribute a change | [Contributing](./CONTRIBUTING.md) · [繁體中文](./CONTRIBUTING.zh-TW.md) |
| See current priorities | [Roadmap](./ROADMAP.md) · [繁體中文](./ROADMAP.zh-TW.md) |
| Feature claims | [Feature status](./FEATURE_STATUS.md) · [繁體中文](./FEATURE_STATUS.zh-TW.md) |
| Provenance of this public tree | [Public snapshot](./PUBLIC_SNAPSHOT.md) · [繁體中文](./PUBLIC_SNAPSHOT.zh-TW.md) |

## Platform guides

- [Wear OS install (debug APK)](./WEAR_OS_INSTALL.md) · [繁體中文](./WEAR_OS_INSTALL.zh-TW.md)
- [`wear/` module](../wear/README.md) · [`mobile/` module](../mobile/README.md)
- [watchOS development](./WATCHOS_DEVELOPMENT_GUIDE.md) · [繁體中文](./WATCHOS_DEVELOPMENT_GUIDE.zh-TW.md) · [`watchos/` README](../watchos/README.md)
- [Keystone integration](./KEYSTONE_INTEGRATION_GUIDE.md) · [繁體中文](./KEYSTONE_INTEGRATION_GUIDE.zh-TW.md)
- [`iosApp/` leftovers (unsupported)](../iosApp/README.md)
- [`TrustWalletBridge/` experimental iOS pod](../TrustWalletBridge/README.md)

## Configuration and licensing

- [API configuration](./API_CONFIGURATION.md) · [繁體中文](./API_CONFIGURATION.zh-TW.md)
- Templates: [`local.properties.template`](../local.properties.template), [`.env.example`](../.env.example), [`gradle.properties.example`](../gradle.properties.example)
- [Third-party inventory](./THIRD_PARTY.md) · [繁體中文](./THIRD_PARTY.zh-TW.md)
- [Vendored `modules/`](../modules/README.md)
- [Licensing](./LICENSING.md) · [繁體中文](./LICENSING.zh-TW.md)
- [Screenshots](./SCREENSHOTS.md) · [繁體中文](./SCREENSHOTS.zh-TW.md)
- [WebView contract](./WEBVIEW_CONTRACT.md) · [繁體中文](./WEBVIEW_CONTRACT.zh-TW.md)
- [Store asset workspace (drafts)](../store-assets/README.md)
- [Scripts](../scripts/README.md)

## Status and evidence boundaries

Documents named `analysis`, `assessment`, `design`, `migration`, `progress`,
`report`, or `roadmap` may describe a proposal or a point-in-time snapshot.
They are useful context, but they do not independently prove that a feature is
implemented, merged, released, or verified on physical hardware.

Capability claims are only those in [FEATURE_STATUS.md](./FEATURE_STATUS.md)
([繁體中文](./FEATURE_STATUS.zh-TW.md)).

[`examples/`](./examples/) holds sample Kotlin, not a supported SDK surface.

## Documentation checks

Local (dependency-free):

```bash
./scripts/check_markdown_links.py
```

Public CI uses `npx markdown-link-check` on a curated file list in
[`.github/workflows/ci.yml`](../.github/workflows/ci.yml). That list must include
this index and the maintained guides it links.
