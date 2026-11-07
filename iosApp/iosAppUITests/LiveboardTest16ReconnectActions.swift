import XCTest

@MainActor
extension XCUIApplication {
    /// Test16 only. No generic resume change, repeated dismissal or approval loop.
    func lbTest16ReconnectApprove(_ before: LiveboardIncoming) -> Bool {
        guard before.kind == "Recover" else { XCTFail("Test16 expected Recover"); return false }
        let deadline = ProcessInfo.processInfo.systemUptime + 120
        func remaining() -> TimeInterval { max(0, deadline - ProcessInfo.processInfo.systemUptime) }
        func fresh() -> Bool { remaining() > 0 }
        func same(_ now: LiveboardIncoming) -> Bool {
            now.identifier == before.identifier && now.kind == before.kind && now.sender == before.sender &&
                now.subject == before.subject && now.count == before.count
        }
        func terminalCandidate() -> Bool {
            guard fresh(), !lbInboxError().exists, fresh() else { return false }
            let now = lbReadIncoming()
            guard fresh() else { return false }
            return now?.identifier != before.identifier && lbIncomingCount() == before.count - 1 && fresh()
        }
        var dismissed = false, initialError = false, initialTerminal = false
        lbWait({
            guard fresh() else { return false }
            initialError = self.lbInboxError().exists
            guard fresh() else { return false }
            initialTerminal = !initialError && terminalCandidate()
            return initialError || initialTerminal
        }, timeout: remaining(), message: "Test16 neither actual error nor terminal was observed")
        guard fresh(), initialError || initialTerminal else { XCTFail("Test16 initial outcome unresolved"); return false }
        if initialError {
            guard fresh() else { XCTFail("Test16 reconnect expired"); return false }
            let tile = descendants(matching: .any).matching(identifier: "lb-inbox")
            guard tile.count == 1, tile.firstMatch.isEnabled, tile.firstMatch.isHittable else {
                XCTFail("Test16 actual error tile unavailable"); return false
            }
            let errorControl = tile.firstMatch
            guard fresh() else { XCTFail("Test16 error click deadline expired"); return false }
            errorControl.tap()
            guard fresh() else { XCTFail("Test16 error acknowledgement expired"); return false }
            dismissed = true
            lbWait({ fresh() && !self.lbInboxError().exists && fresh() }, timeout: remaining())
        }
        var ended = false, actionable = false
        guard fresh() else { XCTFail("Test16 reconnect expired"); return false }
        lbWait({
            guard fresh(), !self.lbInboxError().exists, fresh() else { return false }
            ended = terminalCandidate()
            if ended { return true }
            guard dismissed, let current = self.lbReadIncoming(), fresh(), same(current) else { return false }
            let approve = self.descendants(matching: .any).matching(identifier: "lb-approve-\(before.requestId)")
            actionable = approve.count == 1 && approve.firstMatch.isEnabled && approve.firstMatch.isHittable
            return fresh() && actionable
        }, timeout: remaining(), message: "Test16 SAME request did not safely re-enable or terminate")
        guard fresh(), ended || actionable else { XCTFail("Test16 reconnect expired or unresolved"); return false }
        if !ended && !terminalCandidate() {
            guard dismissed, !lbInboxError().exists, let current = lbReadIncoming(), same(current), fresh() else {
                XCTFail("Test16 request changed before retry"); return false
            }
            let approve = descendants(matching: .any).matching(identifier: "lb-approve-\(before.requestId)")
            guard approve.count == 1, approve.firstMatch.isEnabled, approve.firstMatch.isHittable,
                  remaining() > 3 else { XCTFail("Test16 retry unavailable within original budget"); return false }
            lbSelection.incoming = before
            let approveControl = approve.firstMatch
            guard remaining() > 3 else { XCTFail("Test16 approval authentication reserve expired"); return false }
            approveControl.tap()
            guard remaining() > 3 else { XCTFail("Test16 authentication budget expired"); return false }
            // Existing helper has2+1s waits; opaque AX calls cannot be preempted, so recheck after return.
            _ = lbConfirmSystemIfPresent()
            guard fresh() else { XCTFail("Test16 authentication exceeded original budget"); return false }
        }
        guard fresh(), lbIncomingTerminal(before, timeout: remaining(), deadline: deadline), fresh() else {
            XCTFail("Test16 strict original terminal not proved within original budget"); return false
        }
        return true
    }
}
