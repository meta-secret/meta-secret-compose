import XCTest

@MainActor
final class CaseTwoIosInitiatorUITest: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchEnvironment["METASECRET_UI_TEST_MODE"] = "true"
        app.launch()
    }

    func testCreateVaultThenApproveAndroidAndWeb() throws {
        let vaultName = env("E2E_VAULT_NAME", defaultValue: "test@test.ru")
        let secretName = env("E2E_SECRET_NAME", defaultValue: "test-secret")
        let secretValue = env("E2E_SECRET_VALUE", defaultValue: "test-secret-value")

        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(vaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        tap("add-secret-fab", timeout: 180)
        typeSecretNameAndValue(name: secretName, value: secretValue)
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.47)).tap()
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        tap("secret-row-\(secretName)")
        tap("show-secret-button", timeout: 60)
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "revealed-secret-value", timeout: 90)
        XCTAssertTrue(app.descendants(matching: .any)[secretValue].waitForExistence(timeout: 10), "iOS revealed secret value was not visible")
        tap("show-secret-close")
        XCTAssertFalse(app.descendants(matching: .any)[secretValue].waitForExistence(timeout: 5), "iOS secret value stayed visible after closing")
        print("E2E: IOS_INITIATOR_READY")

        approvePendingJoin(marker: "IOS_ANDROID_JOIN_APPROVED")
        approvePendingJoin(marker: "IOS_WEB_JOIN_APPROVED")

        // iOS is the recovery sender in Test #2. Web and Android receive the
        // requests and the orchestrator selects which one approves each cycle.
        tap("tab-secrets")
        let recoveryCycles = Int(env("E2E_RECOVERY_CYCLES", defaultValue: "18")) ?? 18
        for cycle in 1...recoveryCycles {
            // The orchestrator owns the sequence. Do not send the next request
            // before the previous recovery has been fully observed and closed.
            waitForApproval(platform: "ios-sender", cycle: cycle)
            tap("secret-row-\(secretName)")
            tap("show-secret-button")
            enterSimulatorPasscodeIfNeeded()
            print("E2E: IOS_RECOVERY_REQUEST_SENT_\(cycle)")
            waitForVisible(identifier: "revealed-secret-value", timeout: 180)
            XCTAssertTrue(app.descendants(matching: .any)[secretValue].waitForExistence(timeout: 10))
            print("E2E: IOS_RECOVERY_SECRET_VISIBLE_\(cycle)")
            tap("show-secret-close")
            print("E2E: IOS_RECOVERY_CLOSED_\(cycle)")
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

    private func typeSecretNameAndValue(name: String, value: String) {
        let nameField = app.descendants(matching: .any)["secret-name-input"]
        XCTAssertTrue(nameField.waitForExistence(timeout: 30), "secret-name-input was not visible")
        nameField.typeText(name)

        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.40)).tap()
        let valueField = app.descendants(matching: .any)["secret-value-input"]
        XCTAssertTrue(valueField.waitForExistence(timeout: 5), "secret-value-input was not visible")
        valueField.typeText(value)
    }

    private func approvePendingJoin(marker: String) {
        print("E2E: IOS_OPENING_DEVICES_FOR_JOIN_\(marker)")
        tap("tab-devices")
        print("E2E: IOS_DEVICES_TAB_OPENED_\(marker)")
        waitForVisible(identifier: "pending-device-row", timeout: 180)
        tap("pending-device-row")
        print("E2E: IOS_PENDING_DEVICE_OPENED_\(marker)")
        tap("alert-join-request-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "alert-join-request", timeout: 120)
        print("E2E: \(marker)")
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
