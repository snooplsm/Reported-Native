// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "LiteRTLM",
    platforms: [
        .iOS(.v15),
        .macOS(.v12)
    ],
    products: [
        .library(
            name: "LiteRTLM",
            targets: ["LiteRTLM"]
        )
    ],
    targets: [
        .binaryTarget(
            name: "CLiteRTLM",
            url: "https://github.com/google-ai-edge/LiteRT-LM/releases/download/v0.13.0/CLiteRTLM.xcframework.zip",
            checksum: "af23c77b8eae3f1888fc0348c133af8a13f1e8a89f5788de7e38457f512e768a"
        ),
        .binaryTarget(
            name: "CLiteRTLM_mac",
            url: "https://github.com/google-ai-edge/LiteRT-LM/releases/download/v0.13.0/CLiteRTLM_mac.xcframework.zip",
            checksum: "5b5ca1d15763924247cc27931e2ab099f39fb06a12376df01d1f8f6242f1cec3"
        ),
        .target(
            name: "LiteRTLM",
            dependencies: [
                .target(name: "CLiteRTLM", condition: .when(platforms: [.iOS])),
                .target(name: "CLiteRTLM_mac", condition: .when(platforms: [.macOS]))
            ],
            path: "swift"
        )
    ]
)
