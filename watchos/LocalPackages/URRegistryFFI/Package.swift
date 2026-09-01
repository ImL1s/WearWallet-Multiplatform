// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "URRegistryFFILocal",
    platforms: [
        .iOS(.v15),
        .watchOS(.v7),
        .macOS(.v13)
    ],
    products: [
        .library(
            name: "URRegistryFFILocal",
            targets: ["URRegistryFFIBinary"]
        ),
    ],
    targets: [
        .binaryTarget(
            name: "URRegistryFFIBinary",
            path: "../../.build/artifacts/keystone-sdk-ios/URRegistryFFI/URRegistryFFI.xcframework"
        )
    ]
)