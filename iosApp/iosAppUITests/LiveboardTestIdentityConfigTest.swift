import Foundation
import XCTest

final class LiveboardTestIdentityConfigTest: XCTestCase {
    func testActualResolverTypedConfigurationBoundary() throws {
        let firstToken = "f2-a6c3e8f0-0000-4000-8000-000000000001"
        let secondToken = "f2-b6c3e8f0-0000-4000-8000-000000000002"
        let unmarked: [Any?] = [nil, "NO"]

        for marker in unmarked {
            for simulator in [true, false] {
                let resolved = try resolveE2ETestIdentity(
                    marker: marker, namespace: nil, isSimulator: simulator
                )
                XCTAssertTrue(resolved == nil, "Default identity must remain unscoped")
                for supplied in ["", firstToken] {
                    expectFailure(marker: marker, namespace: supplied, simulator: simulator,
                                  expected: .namespaceOnUnmarkedBuild)
                }
            }
        }

        let invalidMarkers: [Any] = ["", "yes", "NO ", "invalid", 1, false, NSNull()]
        for marker in invalidMarkers {
            expectFailure(marker: marker, namespace: firstToken, simulator: false,
                          expected: .invalidBuildMarker)
        }

        let first = try resolveE2ETestIdentity(marker: "YES", namespace: firstToken, isSimulator: true)
        let repeated = try resolveE2ETestIdentity(marker: "YES", namespace: firstToken, isSimulator: true)
        let second = try resolveE2ETestIdentity(marker: "YES", namespace: secondToken, isSimulator: true)
        XCTAssertTrue(first == "MetaSecret.E2E." + firstToken, "Canonical scope resolution mismatch")
        XCTAssertTrue(first == repeated, "Scope must be stable")
        XCTAssertTrue(first != second && second == "MetaSecret.E2E." + secondToken,
                      "Distinct canonical inputs must have distinct scopes")

        let malformed: [String?] = [
            nil, "", "f2-", "f2-not-a-uuid", firstToken.uppercased(),
            "f2-" + String(firstToken.dropFirst(3)).uppercased(),
            String(firstToken.dropFirst(3)), firstToken + " ", " " + firstToken,
            "f2-" + firstToken.dropFirst(3).replacingOccurrences(of: "-", with: ""),
        ]
        for supplied in malformed {
            expectFailure(marker: "YES", namespace: supplied, simulator: true,
                          expected: .invalidNamespace)
        }
        for supplied in [nil, "", firstToken] as [String?] {
            expectFailure(marker: "YES", namespace: supplied, simulator: false,
                          expected: .simulatorRequired)
        }
        // Marker precedes namespace/Simulator checks; unmarked namespace precedes Simulator.
        expectFailure(marker: true, namespace: nil, simulator: false, expected: .invalidBuildMarker)
        expectFailure(marker: "NO", namespace: "", simulator: false, expected: .namespaceOnUnmarkedBuild)
    }

    private func expectFailure(
        marker: Any?, namespace: String?, simulator: Bool,
        expected: E2ETestIdentityConfigurationError,
        location: (file: StaticString, line: UInt) = (#filePath, #line)
    ) {
        do {
            _ = try resolveE2ETestIdentity(marker: marker, namespace: namespace, isSimulator: simulator)
            XCTFail("Expected typed configuration refusal", file: location.file, line: location.line)
        } catch {
            XCTAssertTrue((error as? E2ETestIdentityConfigurationError) == expected,
                          "Unexpected refusal type or precedence", file: location.file, line: location.line)
        }
    }
}
