import XCTest

// Failure-only consumed UI samples; this diagnostic does not prove a Core failure.
private struct LiveboardRequireObservation {
    let role: String
    var count = "UNKNOWN"
    var exists = "UNKNOWN"
    var visible = "UNKNOWN"
    var target = "UNKNOWN"
    var app = "UNKNOWN"

    static func geometry(_ bounds: CGRect?) -> String {
        guard let bounds else { return "UNKNOWN" }
        if bounds.isNull { return "NULL" }
        return bounds.isEmpty ? "EMPTY" : "NONEMPTY"
    }

    var row: String {
        "E2E_REQUIRE_FAILURE_V1 ROLE=\(role) C=\(count) X=\(exists) V=\(visible) T=\(target) A=\(app)"
    }
}

@MainActor
extension XCUIApplication {
    func lbRequire(_ identifier: String, timeout: TimeInterval = 30, observedCount: ((Int) -> Void)? = nil,
                   observedPredicate: ((Int, Bool?) -> Void)? = nil) -> XCUIElement {
        let element = descendants(matching: .any)[identifier]
        let role = identifier.hasPrefix("lb-approve-") ? "APPROVE" : identifier.hasPrefix("lb-decline-") ? "DECLINE" : "OTHER"
        var sample = LiveboardRequireObservation(role: role)
        lbWait({
            sample = LiveboardRequireObservation(role: role)
            let count = self.descendants(matching: .any).matching(identifier: identifier).count
            observedCount?(count)
            sample.count = count == 0 ? "ZERO" : count == 1 ? "ONE" : count > 1 ? "MANY" : "UNKNOWN"
            XCTAssertLessThanOrEqual(count, 1, "Actual action target is ambiguous")
            guard count == 1 else { observedPredicate?(count, nil); return false }
            let visible = self.lbVisible(element, observed: { exists, target, app in
                sample.exists = exists ? "TRUE" : "FALSE"
                sample.target = LiveboardRequireObservation.geometry(target)
                sample.app = LiveboardRequireObservation.geometry(app)
            })
            sample.visible = visible ? "TRUE" : "FALSE"
            observedPredicate?(count, visible)
            return visible
        }, timeout: timeout, failureDiagnostic: { print(sample.row) })
        return element
    }

    /// Same action deadline; measured viewport reveal never retries a business decision.
    func lbRequireExpandedApprove(_ captured: LiveboardIncoming) -> XCUIElement {
        let deadline = Date().addingTimeInterval(30)
        let identifier = "lb-approve-\(captured.requestId)"
        let element = descendants(matching: .any)[identifier]
        var reveal = LiveboardIncomingRevealState()
        var sample = LiveboardRequireObservation(role: "APPROVE")
        var refused = false
        func sameHead() -> Bool {
            guard let current = lbReadIncoming() else { return false }
            return current.identifier == captured.identifier && current.kind == captured.kind &&
                current.sender == captured.sender && current.subject == captured.subject && current.count == captured.count
        }
        lbWait({
            sample = LiveboardRequireObservation(role: "APPROVE")
            guard !refused, Date() < deadline else { return false }
            guard sameHead() else { refused = true; return false }
            let clip = self.lbRevealIncomingInbox(&reveal, deadline: deadline)
            guard Date() < deadline else { return false }
            guard sameHead() else { refused = true; return false }
            guard clip != nil else { return false }
            let count = self.descendants(matching: .any).matching(identifier: identifier).count
            sample.count = count == 0 ? "ZERO" : count == 1 ? "ONE" : count > 1 ? "MANY" : "UNKNOWN"
            XCTAssertLessThanOrEqual(count, 1, "Actual action target is ambiguous")
            guard count == 1 else { return false }
            let visible = self.lbVisible(element, observed: { exists, target, app in
                sample.exists = exists ? "TRUE" : "FALSE"
                sample.target = LiveboardRequireObservation.geometry(target)
                sample.app = LiveboardRequireObservation.geometry(app)
            })
            sample.visible = visible ? "TRUE" : "FALSE"
            guard Date() < deadline else { return false }
            guard sameHead() else { refused = true; return false }
            return visible && Date() < deadline
        }, timeout: max(0, deadline.timeIntervalSinceNow), failureDiagnostic: { print(sample.row) })
        return element
    }

}
