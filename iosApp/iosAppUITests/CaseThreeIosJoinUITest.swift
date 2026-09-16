import XCTest

@MainActor
final class CaseThreeIosJoinUITest: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchEnvironment["METASECRET_UI_TEST_MODE"] = "true"
        app.launch()
    }

    func testJoinAndroidInitiatedVaultAndHandleRecovery() throws {
        let vaultName = env("E2E_VAULT_NAME", defaultValue: "test@test.ru")
        let secretName = env("E2E_SECRET_NAME", defaultValue: "test-secret")

        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(vaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        tap("email-confirmation-join", timeout: 60)
        enterSimulatorPasscodeIfNeeded()

        print("E2E: IOS_JOIN_REQUEST_SENT")

        // Approval happens on Android after this test has sent the join
        // request. iOS may present its device PIN only at that later point;
        // keep handling it while waiting for the vault to become available.
        waitForSecretAfterJoin(secretName, timeout: 180)
        print("E2E: IOS_MAIN_AFTER_APPROVE")
        print("E2E: IOS_SECRET_VISIBLE")

        // In Test #3 Android is the sole recovery sender. iOS joins only to
        // receive and approve incoming recovery requests; revealing this
        // secret here could itself create a recovery request from iOS.
        let recoveryCycles = Int(env("E2E_RECOVERY_CYCLES", defaultValue: "18")) ?? 18
        let approvalCycles = Set(
            // xcodebuild does not reliably pass arbitrary shell variables to
            // the XCTest runner. Keep this fallback aligned with Test #3:
            // Web approves 1...6, iOS approves 7...12, then the last six
            // alternate beginning with Web at 13 — so iOS is 14, 16 and 18.
            env("E2E_IOS_RECOVERY_APPROVALS", defaultValue: "7,8,9,10,11,12,14,16,18")
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
                tap("open-recovery-request-\(secretName)")
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
            // A just-launched Compose screen can report the control before it
            // is actually hittable. Go through the common resilient tap path.
            tap("onboarding-skip")
            print("E2E: IOS_ONBOARDING_SKIPPED")
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

    private func openManualEmailSignIn() {
        let manual = app.descendants(matching: .any)["signin-email-manual"]
        if manual.waitForExistence(timeout: 30) {
            manual.tap()
            return
        }

        let manualByText = app.buttons["Enter email manually"]
        XCTAssertTrue(manualByText.waitForExistence(timeout: 10), "Manual email sign-in button was not visible")
        manualByText.tap()
    }

    private func typeEmail(_ email: String) {
        let field = app.descendants(matching: .any)["email-input"]
        XCTAssertTrue(field.waitForExistence(timeout: 30), "Email input was not visible")
        field.tap()
        field.typeText(email)
        dismissKeyboardIfNeeded()
        waitUntilHittable(app.descendants(matching: .any)["manual-signin-continue"], timeout: 10)
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

    private func waitForSecretAfterJoin(_ secretName: String, timeout: TimeInterval) {
        let secret = app.descendants(matching: .any)["secret-row-\(secretName)"]
        let deadline = Date().addingTimeInterval(timeout)
        print("E2E: IOS_WAITING_FOR_JOIN_APPROVAL")

        while Date() < deadline {
            if secret.exists {
                return
            }

            if enterSimulatorPasscodeIfNeeded() {
                print("E2E: IOS_LATE_JOIN_PASSCODE_ENTERED")
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }

        XCTFail("secret-row-\(secretName) was not visible after join approval")
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

    @discardableResult
    private func enterSimulatorPasscodeIfNeeded() -> Bool {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let passcodeFields = springboard.secureTextFields
        let firstPasscodeField = passcodeFields.element(boundBy: 0)

        if firstPasscodeField.waitForExistence(timeout: 2) {
            firstPasscodeField.tap()
            firstPasscodeField.typeText("1111")
            return true
        }

        let digitOne = springboard.buttons["1"]
        if digitOne.waitForExistence(timeout: 1) {
            digitOne.tap()
            digitOne.tap()
            digitOne.tap()
            digitOne.tap()
            return true
        }

        return false
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
