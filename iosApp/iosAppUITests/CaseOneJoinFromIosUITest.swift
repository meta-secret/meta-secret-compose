import XCTest

/// Shared UI-only registration actions for all native XCTest actors.
/// This source is already included in the UI test target; business markers stay
/// in the scenario methods, after their observable completion conditions.
@MainActor
extension XCUIApplication {
    func orbitEnterVaultName(_ name: String) {
        let skip = descendants(matching: .any)["onboarding-skip"]
        let enter = descendants(matching: .any)["orbit-enter"]
        let landing = NSPredicate { _, _ in skip.exists || enter.exists }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: landing, object: nil)], timeout: 30), .completed,
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
        orbitWaitForTag("tab-secrets", timeout: 180)
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
        XCTAssertTrue(app.descendants(matching: .any)[secretValue].waitForExistence(timeout: 10), "iOS revealed secret value was not visible")
        print("E2E: IOS_SHOW_SECRET_SUCCESS")
        tap("show-secret-close")
        XCTAssertFalse(app.descendants(matching: .any)[secretValue].waitForExistence(timeout: 5), "iOS secret value stayed visible after closing")
        print("E2E: IOS_CLOSE_SECRET_SUCCESS")

        waitForText("Go to the Devices tab and add the necessary ones to your network", timeout: 300)
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
        let skip = app.descendants(matching: .any)["onboarding-skip"]
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
        let element = app.descendants(matching: .any)[identifier]
        XCTAssertTrue(element.waitForExistence(timeout: timeout), "\(identifier) was not visible")
        if !element.isHittable {
            dismissKeyboardIfNeeded()
            waitUntilHittable(element, timeout: 10)
        }
        element.tap()
    }

    private func tapRecoveryRequest(secretName: String) {
        let tagged = app.descendants(matching: .any)["open-recovery-request-\(secretName)"]
        if tagged.waitForExistence(timeout: 30) {
            if !tagged.isHittable {
                dismissKeyboardIfNeeded()
                waitUntilHittable(tagged, timeout: 10)
            }
            tagged.tap()
            return
        }

        // Compose's iOS accessibility bridge can expose the Material button
        // by its localized label while omitting the testTag from the merged
        // semantics tree. Keep the E2E action equivalent and print the tree
        // so a future bridge regression is diagnosable from CI logs.
        let localized = ["Open request", "Открыть запрос"]
        for label in localized {
            let button = app.buttons[label]
            if button.waitForExistence(timeout: 2) {
                if !button.isHittable {
                    dismissKeyboardIfNeeded()
                    waitUntilHittable(button, timeout: 10)
                }
                button.tap()
                return
            }
        }

        print("E2E: iOS accessibility tree while opening recovery request:\n\(app.debugDescription)")
        XCTFail("open-recovery-request-\(secretName) was not visible")
    }

    private func waitForVisible(
        identifier: String,
        timeout: TimeInterval,
        failureMessage: String? = nil
    ) {
        let element = app.descendants(matching: .any)[identifier]
        XCTAssertTrue(
            element.waitForExistence(timeout: timeout),
            failureMessage ?? "\(identifier) was not visible"
        )
    }

    private func waitForHidden(identifier: String, timeout: TimeInterval) {
        let element = app.descendants(matching: .any)[identifier]
        XCTAssertTrue(element.waitForNonExistence(timeout: timeout), "\(identifier) did not disappear")
    }

    private func waitForText(_ text: String, timeout: TimeInterval) {
        let element = app.descendants(matching: .any)[text]
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
        if app.keyboards.element.exists {
            app.keyboards.buttons["Done"].tapIfExists()
            app.keyboards.buttons["Return"].tapIfExists()
            app.tap()
        }
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
