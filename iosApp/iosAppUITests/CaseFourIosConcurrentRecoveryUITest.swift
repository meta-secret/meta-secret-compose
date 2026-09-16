import XCTest

@MainActor
final class CaseFourIosConcurrentRecoveryUITest: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchEnvironment["METASECRET_UI_TEST_MODE"] = "true"
        app.launch()
    }

    func testJoinAndroidInitiatedVaultAndHandleConcurrentRecovery() throws {
        let recoveryCycles = Int(env("E2E_RECOVERY_CYCLES", defaultValue: "18")) ?? 18
        let senderCycles = cycleSet(
            "E2E_IOS_SENDER_CYCLES",
            defaultValue: "7,8,9,10,11,12,13,14,15,16,17,18"
        )
        let approvalSteps = approvalStepSet(
            defaultValue: "1:1,1:2,2:1,2:2,9:2,10:1,11:2,12:1,15:2,16:1,17:2,18:1"
        )
        XCTAssertEqual(recoveryCycles, 18, "Full Test #4 must receive all 18 recovery cycles")
        try joinAndroidInitiatedVaultAndRunRecovery(
            recoveryCycles: recoveryCycles,
            senderCycles: senderCycles,
            approvalSteps: approvalSteps,
        )
    }

    func testJoinAndroidInitiatedVaultAndHandleConcurrentRecoveryBlock1() throws {
        try joinAndroidInitiatedVaultAndRunRecovery(
            recoveryCycles: 6,
            senderCycles: [],
            approvalSteps: ["1:1", "1:2", "2:1", "2:2"],
        )
    }

    func testJoinAndroidInitiatedVaultAndHandleConcurrentRecoveryBlock2() throws {
        try joinAndroidInitiatedVaultAndRunRecovery(
            recoveryCycles: 6,
            senderCycles: [1, 2, 3, 4, 5, 6],
            approvalSteps: ["3:2", "4:1", "5:2", "6:1"],
        )
    }

    func testJoinAndroidInitiatedVaultAndHandleConcurrentRecoveryBlock3() throws {
        try joinAndroidInitiatedVaultAndRunRecovery(
            recoveryCycles: 6,
            senderCycles: [1, 2, 3, 4, 5, 6],
            approvalSteps: ["3:2", "4:1", "5:2", "6:1"],
        )
    }

    private func joinAndroidInitiatedVaultAndRunRecovery(
        recoveryCycles: Int,
        senderCycles: Set<Int>,
        approvalSteps: Set<String>,
    ) throws {
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

        for cycle in 1...recoveryCycles {
            if senderCycles.contains(cycle) {
                waitForApproval(platform: "ios-sender", cycle: cycle)
                requestRecovery(secretName, cycle: cycle)
                print("E2E: IOS_RECOVERY_REQUEST_SENT_\(cycle)")
                // Preserve the sender dialog until both approvals finish. A
                // receiver-only iOS sender must not reopen it, because that
                // would issue a second recovery request rather than reveal
                // the accepted claim. Close early only when iOS also needs
                // to approve its peer's request in this cycle.
                if approvalSteps.contains("\(cycle):1") || approvalSteps.contains("\(cycle):2") {
                    closeShowSecretDialog()
                }
            }

            for step in 1...2 where approvalSteps.contains("\(cycle):\(step)") {
                waitForApproval(platform: "ios-approve-\(step)", cycle: cycle)
                approveIncomingRecovery(secretName, cycle: cycle, request: step)
                print("E2E: IOS_APPROVED_INCOMING_\(cycle)_\(step)")
            }

            if senderCycles.contains(cycle) {
                waitForApproval(platform: "ios-show", cycle: cycle)
                if approvalSteps.contains("\(cycle):1") || approvalSteps.contains("\(cycle):2") {
                    showAcceptedSecret(secretName, cycle: cycle)
                }
                waitForVisible(identifier: "revealed-secret-value", timeout: 180)
                print("E2E: IOS_RECOVERY_SECRET_VISIBLE_\(cycle)")
                closeShowSecretDialog()
                waitForHidden(identifier: "revealed-secret-value", timeout: 30)
                print("E2E: IOS_RECOVERY_CLOSED_\(cycle)")
            }
        }
    }

    private func requestRecovery(_ secretName: String, cycle: Int) {
        logPrimaryAction(secretName, cycle: cycle, phase: "recover")
        tap("secret-primary-action-\(secretName)")
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(
            identifier: "show-secret-dialog",
            timeout: 30,
            failureMessage: "recovery cycle \(cycle): Recover action did not open the secret dialog"
        )
        // The sender dialog stays open while the orchestrator coordinates the
        // approvals. The Web-side incoming-claim badge is the authoritative
        // synchronization point for the native recover() request; a success
        // notification can be hidden behind this modal and is not a stable
        // XCUI assertion.
        print("E2E: IOS_RECOVERY_DIALOG_OPEN_\(cycle)")
    }

    private func showAcceptedSecret(_ secretName: String, cycle: Int) {
        logPrimaryAction(secretName, cycle: cycle, phase: "show")
        tap("secret-primary-action-\(secretName)")
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(
            identifier: "show-secret-dialog",
            timeout: 30,
            failureMessage: "recovery cycle \(cycle): Show action did not open the secret dialog"
        )
    }

    private func logPrimaryAction(_ secretName: String, cycle: Int, phase: String) {
        let action = app.descendants(matching: .any)["secret-primary-action-\(secretName)"]
        XCTAssertTrue(
            action.waitForExistence(timeout: 30),
            "recovery cycle \(cycle): primary action was not visible before \(phase)"
        )
        print(
            "E2E: IOS_PRIMARY_ACTION_\(cycle)_\(phase) " +
                "label=\(action.label) value=\(action.value ?? "nil") " +
                "enabled=\(action.isEnabled) hittable=\(action.isHittable)"
        )
    }

    private func closeShowSecretDialog() {
        let dialog = app.descendants(matching: .any)["show-secret-dialog"]
        let close = app.descendants(matching: .any)["show-secret-close"]
        let deadline = Date().addingTimeInterval(30)
        var tapAttempts = 0

        // A state refresh can race the Compose dialog's exit transition. Treat
        // the dialog itself as the source of truth, wait for the close control
        // to become hittable, and retry only while the modal is still present.
        // This is a semantic UI wait, not a fixed delay.
        while Date() < deadline {
            if !dialog.exists {
                print("E2E: IOS_SHOW_SECRET_DIALOG_ALREADY_CLOSED")
                return
            }

            if close.exists && close.isHittable {
                tapAttempts += 1
                print("E2E: IOS_SHOW_SECRET_CLOSE_ATTEMPT_\(tapAttempts)")
                close.tap()
                if dialog.waitForNonExistence(timeout: 5) {
                    return
                }
            } else {
                RunLoop.current.run(until: Date().addingTimeInterval(0.2))
            }
        }

        print(
            "E2E: IOS_SHOW_SECRET_CLOSE_TIMEOUT " +
                "dialogExists=\(dialog.exists) closeExists=\(close.exists) " +
                "closeHittable=\(close.isHittable) attempts=\(tapAttempts)"
        )
        XCTFail("show-secret-dialog did not disappear")
    }

    private func cycleSet(_ key: String, defaultValue: String) -> Set<Int> {
        Set(env(key, defaultValue: defaultValue).split(separator: ",").compactMap { Int($0) })
    }

    private func approvalStepSet(defaultValue: String) -> Set<String> {
        Set(env("E2E_IOS_APPROVAL_STEPS", defaultValue: defaultValue)
            .split(separator: ",")
            .map(String.init))
    }

    private func approveIncomingRecovery(_ secretName: String, cycle: Int, request: Int) {
        tap("open-recovery-request-\(secretName)")
        waitForVisible(
            identifier: "alert-recovery-request",
            timeout: 30,
            failureMessage: "recovery cycle \(cycle), request \(request): approval alert was not visible"
        )
        tap("alert-recovery-request-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "alert-recovery-request", timeout: 120)
        // The visible approval alert is replaced by a processing overlay while
        // the native accept call is still running. Do not announce approval or
        // tap the next Show action until that overlay is gone, otherwise the
        // next tap is consumed by the overlay.
        waitForHidden(identifier: "alert-recovery-request-processing", timeout: 120)
        print("E2E: IOS_RECOVERY_APPROVE_SUCCESS_\(cycle)_\(request)")
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
               let result = readApprovalResponse(url),
               result == "allowed" {
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        XCTFail("Timed out waiting for orchestrator approval for iOS recovery cycle \(cycle)")
    }

    private func readApprovalResponse(_ url: URL) -> String? {
        // Keep the test runner's main actor responsive. String(contentsOf:)
        // performs synchronous URL loading and makes XCTest report a UI
        // unresponsiveness warning on every poll.
        var response: String?
        let semaphore = DispatchSemaphore(value: 0)
        URLSession.shared.dataTask(with: url) { data, _, _ in
            if let data {
                response = String(data: data, encoding: .utf8)
            }
            semaphore.signal()
        }.resume()
        _ = semaphore.wait(timeout: .now() + 2)
        return response
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
