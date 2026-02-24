// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "PttDictation",
    platforms: [.macOS(.v13)],
    targets: [
        .executableTarget(
            name: "PttDictation",
            dependencies: [],
            path: "Sources/App",
            resources: [.copy("Resources")]
        ),
        .testTarget(
            name: "PttDictationTests",
            dependencies: ["PttDictation"],
            path: "Tests"
        ),
    ]
)
