# kotlin-caip-standards

<p align="center">
  <img src="../docs/images/kmp_crypto_banner.png" alt="kotlin-caip-standards Banner" width="100%">
</p>

<p align="center">
  <a href="https://jitpack.io/#ImL1s/kotlin-caip-standards"><img src="https://jitpack.io/v/ImL1s/kotlin-caip-standards.svg" alt="JitPack"></a>
  <a href="#"><img src="https://img.shields.io/badge/kotlin-2.1.0-blue.svg?logo=kotlin" alt="Kotlin"></a>
  <a href="#"><img src="https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20watchOS%20%7C%20JVM-orange" alt="Platform"></a>
  <a href="#"><img src="https://img.shields.io/badge/WatchOS-Supported-green?style=for-the-badge&logo=apple" alt="WatchOS Supported"></a>
</p>

<p align="center">
  <strong>📏 Chain Agnostic Standards (CAIP) for Kotlin Multiplatform.</strong>
</p>

---

## 🏗️ Architecture

```mermaid
graph LR
    A[Chain ID] --> B{CAIP-2 Parser}
    B -->|EIP-155| C[Ethereum ID]
    B -->|BIP-122| D[Bitcoin ID]

    E[Account Addr] --> F{CAIP-10 Parser}
    F -->|Chain + Addr| G[Unified Account ID]
    
    H[Asset ID] --> I{CAIP-19 Parser}
    I -->|Namespace + Ref| J[NFT / Token ID]
```

---

## ✨ Features

- **CAIP-2**: Chain Identifiers (e.g., `eip155:1`).
- **CAIP-10**: Account Identifiers (e.g., `eip155:1:0x...`).
- **CAIP-19**: Asset Identifiers (NFTs, Tokens).
- **CAIP-25**: JSON-RPC Provider Request/Response schemas.
- **Type-safe serialization**: Fully compatible with `kotlinx.serialization`.

---

## 📦 Installation

```kotlin
// build.gradle.kts
implementation("com.github.ImL1s:kotlin-caip-standards:0.2.0-watchos")
```

---

## 🚀 Usage

### Parse Chain ID
```kotlin
val chainId = Caip2.parse("eip155:1")
println("Namespace: ${chainId.namespace}") // eip155
println("Reference: ${chainId.reference}") // 1
```

---

## 📄 License
MIT License
