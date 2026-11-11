// swift-tools-version:5.9
import PackageDescription

// Everything that is not a screen lives here: wire models, the API client and the pure logic that the Android app
// also has (formatting, route replay, crash and driving detection). It builds and is tested without a simulator.
let package = Package(
    name: "LocanaKit",
    platforms: [.iOS(.v17), .macOS(.v13)],
    products: [.library(name: "LocanaKit", targets: ["LocanaKit"])],
    targets: [
        .target(name: "LocanaKit"),
        .testTarget(name: "LocanaKitTests", dependencies: ["LocanaKit"]),
    ]
)
