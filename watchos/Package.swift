// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "WearWalletWatch",
    platforms: [
        .watchOS(.v10),
        .iOS(.v16),
        .macOS(.v13)
    ],
    products: [
        .library(
            name: "WearWalletWatch",
            targets: ["WearWalletWatch"]
        )
    ],
    dependencies: [
        // Keystone SDK for hardware wallet integration
        .package(url: "https://github.com/KeystoneHQ/keystone-sdk-ios.git", from: "0.0.1"),
        // URKit for UR encoding/decoding
        .package(url: "https://github.com/BlockchainCommons/URKit.git", from: "15.0.0"),
        // Web3.swift for Ethereum interactions (optional, for enhanced functionality)
        .package(url: "https://github.com/chainnodesorg/Web3.swift.git", branch: "master"),
        // P256K for secp256k1 key operations (watchOS compatible)
        .package(url: "https://github.com/21-DOT-DEV/swift-secp256k1.git", exact: "0.21.0")
    ],
    targets: [
        .target(
            name: "WearWalletWatch",
            dependencies: [
                .product(name: "KeystoneSDK", package: "keystone-sdk-ios"),
                .product(name: "URKit", package: "URKit"),
                .product(name: "Web3", package: "Web3.swift"),
                .product(name: "Web3PromiseKit", package: "Web3.swift"),
                .product(name: "secp256k1", package: "swift-secp256k1")
            ],
            path: "WearWalletWatch",
            linkerSettings: [
                .unsafeFlags([
                    "-F", "../coreKmp/build/bin/watchosSimulatorArm64/debugFramework",
                    "-framework", "coreKmp"
                ])
            ]
        )
    ]
)