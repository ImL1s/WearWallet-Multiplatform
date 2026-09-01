# kotlin-blockchain-client

<p align="center">
  <img src="./docs/images/hero.png" alt="kotlin-blockchain-client Hero" width="100%">
</p>

<p align="center">
  <a href="https://jitpack.io/#ImL1s/kotlin-blockchain-client"><img src="https://jitpack.io/v/ImL1s/kotlin-blockchain-client.svg" alt="JitPack"></a>
  <a href="#"><img src="https://img.shields.io/badge/kotlin-2.1.0-blue.svg?logo=kotlin" alt="Kotlin"></a>
  <a href="#"><img src="https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20watchOS%20%7C%20JVM-orange" alt="Platform"></a>
  <a href="#"><img src="https://img.shields.io/badge/WatchOS-Supported-green?style=for-the-badge&logo=apple" alt="WatchOS Supported"></a>
</p>

<p align="center">
  <strong>📡 Lightweight Koin-friendly Blockchain RPC Client for Kotlin Multiplatform.</strong>
</p>

---

## 🏗️ Architecture

```mermaid
graph TD
    subgraph "Client Interface"
        A[App Logic] --> B[BlockchainProvider]
    end

    subgraph "Network Layer (Ktor)"
        B --> C{RPC Connector}
        C -->|HTTP| D[EVM RPC / Infura]
        C -->|HTTP| E[Solana RPC]
        C -->|WebSocket| F[Real-time Events]
    end

    subgraph "Data Mapping"
        D --> G[Response Parser]
        E --> G
        G --> H[Type-Safe Models]
    end
```

---

## ✨ Features

- **Unified Provider API**: Single interface to interact with multiple chains.
- **Ktor Powered**: Built on top of Ktor for efficient, asynchronous networking.
- **Type-Safe RPC**: Automatic serialization/deserialization of RPC requests and responses.
- **Multilingual Logging**: Integrated with Kermit for cross-platform debugging.
- **Koin Integration**: Ready to be injected into your DI tree.

---

## 📦 Installation

```kotlin
// build.gradle.kts
implementation("com.github.ImL1s:kotlin-blockchain-client:0.4.0-watchos")
```

---

## 🚀 Quick Start

### Initialize Client
```kotlin
val client = EthereumClient(
    rpcUrl = "https://mainnet.infura.io/v3/...",
    chainId = 1
)

val balance = client.getBalance("0x...")
println("Balance: $balance")
```

---

## 📄 License
MIT License
