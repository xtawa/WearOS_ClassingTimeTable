// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "ClassingCore", platforms: [.iOS(.v17), .watchOS(.v10), .macOS(.v13)], products: [.library(name: "ClassingCore", targets: ["ClassingCore"])], targets: [.target(name: "ClassingCore"), .testTarget(name: "ClassingCoreTests", dependencies: ["ClassingCore"])])
