package io.github.ttypic.swiftklib.gradle.templates

// MetaSecret fork: add swiftSettings so SwiftPM sees UniFFI Clang module (mobile_uniffiFFI.modulemap).
// Upstream: https://github.com/ttypic/swift-klib-plugin — sync when bumping the plugin.

internal fun createPackageSwiftContents(
    cinteropName: String,
): String = """
    // swift-tools-version:5.5
    import PackageDescription

    let package = Package(
        name: "$cinteropName",
        products: [
            .library(
                name: "$cinteropName",
                type: .static,
                targets: ["$cinteropName"])
        ],
        dependencies: [],
        targets: [
            .target(
                name: "$cinteropName",
                dependencies: [],
                path: "$cinteropName",
                swiftSettings: [
                    .unsafeFlags([
                        "-Xcc", "-fmodule-map-file=$cinteropName/UniffiGenerated/mobile_uniffiFFI.modulemap"
                    ])
                ])
        ]
    )
""".trimIndent()
