# kotlin-address

<p align="center">
  <img src="./docs/images/hero.png" alt="kotlin-address Hero" width="100%">
</p>

<p align="center">
  <a href="https://jitpack.io/#ImL1s/kotlin-address"><img src="https://jitpack.io/v/ImL1s/kotlin-address.svg" alt="JitPack"></a>
  <a href="#"><img src="https://img.shields.io/badge/kotlin-2.1.0-blue.svg?logo=kotlin" alt="Kotlin"></a>
  <a href="#"><img src="https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20watchOS%20%7C%20JVM-orange" alt="Platform"></a>
  <a href="#"><img src="https://img.shields.io/badge/WatchOS-Supported-green?style=for-the-badge&logo=apple" alt="WatchOS Supported"></a>
</p>

<p align="center">
  <strong>🌐 Unified Blockchain Address Handling for Kotlin Multiplatform.</strong>
</p>

Pure Kotlin library for robust blockchain address parsing, validation, and conversion across multiple ecosystems.

---

## 🏗️ Architecture

```mermaid
graph TD
    subgraph "Address Parsing Component"
        A[Raw String Input] --> B{Address Parser}
        B -->|Identify| C[Address Type]
    end

    subgraph "Validation & Conversion"
        C --> D{Validator}
        D -->|Valid| E[Address Object]
        D -->|Invalid| F[Error Handling]
        
        E --> G[Bech32 / Base58 Decoding]
        G --> H[Internal Byte Data]
    end

    subgraph "Target Encoding"
        H --> I{Encoder}
        I -->|Bitcoin| J[P2WPKH / P2TR]
        I -->|Ethereum| K[Checksum Address]
        I -->|Solana| L[Base58 Address]
    end
```

---

## ✨ Features

- **Multi-Chain Support**: Bitcoin (SegWit, Taproot), Ethereum (EIP-55), Solana, and more.
- **Deep Validation**: Checksum verification, prefix matching, and length validation.
- **Bech32/Bech32m**: Native support for BIP173 and BIP350.
- **Base58Check**: Legacy address support with robust checksumming.
- **Pure Kotlin**: 100% Kotlin code, perfect for KMP and WatchOS.

---

## 📦 Installation

```kotlin
// build.gradle.kts
implementation("com.github.ImL1s:kotlin-address:0.3.0-watchos")
```

## 🚀 Usage

### Parse and Validate Bitcoin Address
```kotlin
val address = BitcoinAddress.from("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh")
if (address.isValid) {
    println("Type: ${address.type}") // P2WPKH
    println("PubKey Hash: ${address.hash.toHex()}")
}
```

### Ethereum Checksum Address
```kotlin
val ethAddr = EthereumAddress("0x742d35cc6634c0532925a3b844bc454e4438f44e")
println(ethAddr.toChecksumAddress())
```

---

## 📄 License
MIT License
