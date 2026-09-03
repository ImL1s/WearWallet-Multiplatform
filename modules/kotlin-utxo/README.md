# kotlin-utxo

<p align="center">
  <img src="../kotlin-blockchain-client/docs/images/hero.png" alt="kotlin-utxo Hero" width="100%">
</p>

<p align="center">
  <a href="https://jitpack.io/#ImL1s/kotlin-utxo"><img src="https://jitpack.io/v/ImL1s/kotlin-utxo.svg" alt="JitPack"></a>
  <a href="#"><img src="https://img.shields.io/badge/kotlin-2.1.0-blue.svg?logo=kotlin" alt="Kotlin"></a>
  <a href="#"><img src="https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20watchOS%20%7C%20JVM-orange" alt="Platform"></a>
  <a href="#"><img src="https://img.shields.io/badge/WatchOS-Supported-green?style=for-the-badge&logo=apple" alt="WatchOS Supported"></a>
</p>

<p align="center">
  <strong>₿ Pure Kotlin UTXO management for Bitcoin and UTXO-based blockchains.</strong>
</p>

<p align="center">
  Optimal coin selection, fee estimation, and transaction building<br>
  designed for <strong>wearables</strong> and mobile.
</p>

---

## 🪙 What is UTXO?

Unlike account-based systems (Ethereum), Bitcoin uses the **UTXO (Unspent Transaction Output)** model:

```mermaid
graph LR
    subgraph "Your Wallet"
        A["UTXO 1<br/>0.5 BTC"] 
        B["UTXO 2<br/>0.3 BTC"]
        C["UTXO 3<br/>1.0 BTC"]
    end
    
    subgraph "Transaction"
        D[Select UTXOs]
        E[Create Outputs]
    end
    
    subgraph "Outputs"
        F["Recipient<br/>0.7 BTC"]
        G["Change<br/>~0.09 BTC"]
        H["Fee<br/>~0.01 BTC"]
    end
    
    A --> D
    B --> D
    D --> E
    E --> F
    E --> G
    E --> H
```

This library handles the complexity of UTXO selection automatically.

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| **5 Selection Strategies** | Largest-first, smallest-first, branch-and-bound, random, FIFO |
| **Accurate Fee Estimation** | Virtual byte calculation for SegWit |
| **Dust Protection** | Automatic filtering of uneconomical UTXOs |
| **Confirmation Control** | Filter by confirmation count |
| **Pure Kotlin** | No native dependencies |
| **Wearable-Ready** | Optimized for watchOS and Wear OS |

---

## 🏗️ Architecture

```mermaid
graph TB
    subgraph "UTXO Selection Flow"
        A[Available UTXOs] --> B{Selection Strategy}
        
        B -->|Largest First| C[Minimize Inputs]
        B -->|Smallest First| D[Consolidate Dust]
        B -->|Branch & Bound| E[Optimal Selection]
        B -->|Random| F[Better Privacy]
        B -->|FIFO| G[Oldest First]
        
        C --> H[UTXOSelection Result]
        D --> H
        E --> H
        F --> H
        G --> H
        
        H --> I[Fee Estimation]
        I --> J[Change Calculation]
        J --> K[Final Transaction]
    end
```

---

## 🎯 Supported Platforms

| Platform | Target | Status |
|----------|--------|--------|
| **Android** | `androidTarget` | ✅ |
| **iOS** | `iosArm64`, `iosSimulatorArm64`, `iosX64` | ✅ |
| **watchOS** | `watchosArm64`, `watchosSimulatorArm64` | ✅ |
| **JVM** | `jvm` | ✅ |

---

## 📦 Installation

```kotlin
// build.gradle.kts
implementation("com.github.ImL1s:kotlin-utxo:0.4.0-watchos")
```

---

## 🚀 Quick Start

### Basic UTXO Selection

```kotlin
import io.github.iml1s.utxo.*

// Create available UTXOs
val utxos = listOf(
    UTXO(txid = "abc123...", vout = 0, value = 50_000, confirmed = true),
    UTXO(txid = "def456...", vout = 1, value = 30_000, confirmed = true),
    UTXO(txid = "ghi789...", vout = 0, value = 100_000, confirmed = true)
)

// Select UTXOs for transaction
val selector = UTXOSelector()
val selection = selector.select(
    utxos = utxos,
    targetAmount = 70_000,  // satoshis
    feeRate = 10            // sat/vB
)

println("Selected: ${selection.inputCount} UTXOs")
```

---

## 📄 License
MIT License
