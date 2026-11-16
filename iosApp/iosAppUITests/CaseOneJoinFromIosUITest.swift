import XCTest

/// Shared UI-only registration actions for all native XCTest actors.
/// This source is already included in the UI test target; business markers stay
/// in the scenario methods, after their observable completion conditions.
@MainActor
extension XCUIApplication {
    // Failure-only closed UI diagnostic; no private values or Core failure inference.
    private func orbitLandingTimeoutDiagnostic() {
        let tags = [
            ("SKIP", "onboarding-skip"), ("ENTER", "orbit-enter"),
            ("INPUT", "orbit-email-input"), ("OK", "orbit-ok"),
            ("BACK", "orbit-back"), ("CREATE", "orbit-create"),
            ("JOIN", "orbit-join"), ("REQUEST", "orbit-request"),
            ("ERROR", "orbit-phase-error"), ("CHECKING", "orbit-phase-checking"),
            ("CREATE_ERROR", "orbit-phase-createerror"),
            ("READY", "orbit-transition-ready"), ("TRANSITION", "orbit-transition-running"),
        ]
        var row = ["WAIT": "FAILED"]
        for (key, tag) in tags {
            let count = descendants(matching: .any).matching(identifier: tag).count
            row[key] = count == 0 ? "0" : count == 1 ? "1" : "MANY"
        }
        let status = descendants(matching: .any).matching(identifier: "orbit-status")
        let count = status.count
        if count == 1 {
            switch status.element(boundBy: 0).label {
            case "Could not reset. Try again.": row["STATUS"] = "RESET_FAILED"
            case "Network error. Try again.": row["STATUS"] = "NETWORK"
            case "This vault already has the maximum of 3 devices.": row["STATUS"] = "MAX3"
            default: row["STATUS"] = "OTHER"
            }
        } else { row["STATUS"] = count == 0 ? "NONE" : "AMBIGUOUS" }
        let unavailable = "E2E_ORBIT_LANDING_DIAG_V1 {\"AVAILABILITY\":\"UNAVAILABLE\",\"WAIT\":\"FAILED\"}"
        guard let data = try? JSONSerialization.data(withJSONObject: row, options: [.sortedKeys]),
              let json = String(data: data, encoding: .utf8) else { print(unavailable); return }
        let line = "E2E_ORBIT_LANDING_DIAG_V1 " + json
        print(line.utf8.count + 1 <= 512 ? line : unavailable)
    }

    func orbitEnterVaultName(_ name: String) {
        let skip = descendants(matching: .any)["onboarding-skip"]
        let enter = descendants(matching: .any)["orbit-enter"]
        let landing = NSPredicate { _, _ in skip.exists || enter.exists }
        let result = XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: landing, object: nil)], timeout: 30)
        if result != .completed { orbitLandingTimeoutDiagnostic() }
        XCTAssertEqual(result, .completed,
                       "Registration landing did not expose onboarding Skip or Orbit Enter")
        if skip.exists { orbitTap("onboarding-skip") }
        orbitTap("orbit-enter")
        let field = descendants(matching: .any)["orbit-email-input"]
        orbitWaitForTag("orbit-email-input")
        field.tap()
        // Back → Enter keeps the previous name; replace it instead of appending.
        let existing = field.value as? String ?? ""
        if !existing.isEmpty && existing != field.placeholderValue {
            field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: existing.count))
        }
        field.typeText(name)
        orbitTap("orbit-ok")
        orbitWaitForTag("orbit-phase-idle", timeout: 60)
    }

    func orbitCreateVault() {
        orbitWaitForTag("orbit-transition-ready")
        let create = descendants(matching: .any)["orbit-create"]
        XCTAssertTrue(create.isEnabled, "Create requires a canonically available Vault name")
        orbitTap("orbit-create")
        orbitWaitForTag("lb-home", timeout: 180)
    }

    func orbitRequestJoin(awaitPending: Bool = true) {
        orbitTap("orbit-join")
        orbitTap("orbit-request")
        if awaitPending { orbitWaitForTag("orbit-phase-pending", timeout: 180) }
    }

    func orbitTap(_ identifier: String) {
        let element = descendants(matching: .any)[identifier]
        let ready = descendants(matching: .any)["orbit-transition-ready"]
        let actionable = NSPredicate { _, _ in
            (identifier == "onboarding-skip" || ready.exists) && element.exists && element.isEnabled && element.isHittable
        }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: actionable, object: nil)], timeout: 60), .completed,
                       "Orbit control \(identifier) did not become actionable")
        element.tap()
    }

    func orbitWaitForTag(_ identifier: String, timeout: TimeInterval = 30) {
        XCTAssertTrue(descendants(matching: .any)[identifier].waitForExistence(timeout: timeout),
                      "Orbit state/control \(identifier) was not observable")
    }
}

@MainActor
final class CaseOneJoinFromIosUITest: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.lbApplyTestIdentity()
        app.lbResetSelectors()
        app.launchEnvironment["METASECRET_UI_TEST_MODE"] = "true"
        app.launch()
    }

    func testJoinApprovedVaultAndShowSecret() throws {
        let vaultName = env("E2E_VAULT_NAME", defaultValue: "test@test.ru")
        let secretName = env("E2E_SECRET_NAME", defaultValue: "test-secret")
        let secretValue = env("E2E_SECRET_VALUE", defaultValue: "test-secret-value")

        app.orbitEnterVaultName(vaultName)
        app.orbitRequestJoin()

        // The Compose click handler launches signUp() asynchronously. Wait for
        // its terminal pending state before notifying the orchestrator; emitting
        // the marker immediately after tap creates a race with the server sync.
        waitForVisible(identifier: "orbit-phase-pending", timeout: 180)
        print("E2E: IOS_JOIN_REQUEST_SENT")

        waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        print("E2E: IOS_MAIN_AFTER_APPROVE")
        print("E2E: IOS_SECRET_VISIBLE")

        tap("secret-primary-action-\(secretName)")
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "revealed-secret-value", timeout: 90)
        app.lbAssertValue(secretValue)
        print("E2E: IOS_SHOW_SECRET_SUCCESS")
        tap("show-secret-close")
        app.lbAssertNoPrivateContent(secretValue)
        print("E2E: IOS_CLOSE_SECRET_SUCCESS")

        _ = app.lbIncoming(LiveboardIncomingExpected(kind: "Join"), timeout: 300)
        print("E2E: IOS_ANDROID_JOIN_NOTIFICATION_SUCCESS")

        let recoveryCycles = Int(env("E2E_RECOVERY_CYCLES", defaultValue: "18")) ?? 18
        let approvalCycles = Set(
            env("E2E_IOS_RECOVERY_APPROVALS", defaultValue: "1,2,3,4,5,6,13,15,17")
                .split(separator: ",")
                .compactMap { Int($0) }
        )

        for cycle in 1...recoveryCycles {
            print("E2E: IOS_RECOVERY_REQUEST_WAITING_\(cycle)")
            waitForVisible(
                identifier: "recovery-request-badge-\(secretName)",
                timeout: 45,
                failureMessage: "recovery cycle \(cycle): incoming recovery badge was not visible"
            )
            print("E2E: IOS_RECOVERY_REQUEST_ALERT_\(cycle)")
            if approvalCycles.contains(cycle) {
                waitForApproval(platform: "ios", cycle: cycle)
                tapRecoveryRequest(secretName: secretName)
                waitForVisible(identifier: "alert-recovery-request", timeout: 30)
                tap("alert-recovery-request-accept")
                enterSimulatorPasscodeIfNeeded()
                waitForHidden(identifier: "alert-recovery-request", timeout: 120)
                print("E2E: IOS_RECOVERY_APPROVE_SUCCESS_\(cycle)")
            }
            waitForHidden(identifier: "recovery-request-badge-\(secretName)", timeout: 180)
            print("E2E: IOS_RECOVERY_REQUEST_CLOSED_\(cycle)")
        }
    }

    private func skipOnboardingIfNeeded() {
        let skip = app.lbElement("onboarding-skip")
        if skip.waitForExistence(timeout: 10) {
            skip.tap()
        }
    }

    private func waitForApproval(platform: String, cycle: Int) {
        let baseUrl = env("E2E_APPROVAL_COORDINATOR_URL", defaultValue: "http://127.0.0.1:5180")
        let deadline = Date().addingTimeInterval(120)
        while Date() < deadline {
            if let url = URL(string: "\(baseUrl)/approval?platform=\(platform)&cycle=\(cycle)"),
               let result = try? String(contentsOf: url, encoding: .utf8),
               result == "allowed" {
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        XCTFail("Timed out waiting for orchestrator approval for iOS recovery cycle \(cycle)")
    }

    private func tap(_ identifier: String, timeout: TimeInterval = 30) {
        app.lbTapIntent(identifier, timeout: timeout)
    }
    private func tapRecoveryRequest(secretName: String) {
        _ = app.lbIncoming(LiveboardIncomingExpected(kind: "Recover", subject: secretName))
    }
    private func waitForVisible(
        identifier: String,
        timeout: TimeInterval,
        failureMessage: String? = nil
    ) {
        app.lbWaitIntent(identifier, timeout: timeout)
    }
    private func waitForHidden(identifier: String, timeout: TimeInterval) {
        app.lbWaitIntent(identifier, timeout: timeout, visible: false)
    }
    private func waitForText(_ text: String, timeout: TimeInterval) {
        let element = app.lbElement(text)
        XCTAssertTrue(element.waitForExistence(timeout: timeout), "\(text) was not visible")
    }

    private func waitUntilHittable(_ element: XCUIElement, timeout: TimeInterval) {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if element.exists && element.isHittable {
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        XCTFail("\(element) did not become hittable")
    }

    private func dismissKeyboardIfNeeded() {
        app.lbDismissKeyboard()
    }
    private func enterSimulatorPasscodeIfNeeded() {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let passcodeFields = springboard.secureTextFields
        let firstPasscodeField = passcodeFields.element(boundBy: 0)

        if firstPasscodeField.waitForExistence(timeout: 2) {
            firstPasscodeField.tap()
            firstPasscodeField.typeText("1111")
            return
        }

        let digitOne = springboard.buttons["1"]
        if digitOne.waitForExistence(timeout: 1) {
            digitOne.tap()
            digitOne.tap()
            digitOne.tap()
            digitOne.tap()
        }
    }

    private func env(_ key: String, defaultValue: String) -> String {
        ProcessInfo.processInfo.environment[key] ?? defaultValue
    }
}

private extension XCUIElement {
    func tapIfExists() {
        if exists && isHittable {
            tap()
        }
    }
}
