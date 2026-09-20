import XCTest
import Foundation

@MainActor
final class CaseEightIosBothOfflineUITest: XCTestCase {
    private struct StepConfig: Decodable {
        let role: String?
        let cycle: String?
        let step: String?
        let approvalPlatform: String?
        let sender: String?
        let secretName: String?
        let action: String?
        let expectedOutcome: String?
        let repeatApprove: Bool?
        let duplicateRecovery: Bool?
        let networkRole: String?
        let networkCycles: String?
    }

    private static let stepConfigPath = "/tmp/metasecret-e2e-ios-step.json"
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchEnvironment["METASECRET_UI_TEST_MODE"] = "true"
        let e2eServerUrl = (ProcessInfo.processInfo.environment["E2E_CORE_SERVER_URL"] ?? "")
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if !e2eServerUrl.isEmpty {
            app.launchEnvironment["METASECRET_E2E_SERVER_URL"] = e2eServerUrl
        }
        app.launch()
    }

    func joinWebInitiatedVault() throws {
        let vaultName = coordinatorVaultName(defaultValue: "test8@test.ru")

        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(vaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        tap("email-confirmation-join", timeout: 60)
        enterSimulatorPasscodeIfNeeded()
        print("E2E: IOS_JOIN_REQUEST_SENT")

        for name in configuredSecretNames() {
            waitForVisible(identifier: "secret-row-\(name)", timeout: 180)
        }
        print("E2E: IOS_JOIN_READY")
        print("E2E: IOS_SECRETS_READY")
    }

    func runNetworkLossCycles() throws {
        let config = stepConfig()
        let role = config.networkRole ?? config.role ?? env("E2E_NETWORK_ROLE", defaultValue: "")
        let cycles = (config.networkCycles ?? env("E2E_NETWORK_CYCLES", defaultValue: ""))
            .split(separator: ",")
            .map(String.init)
            .filter { !$0.isEmpty }
        let secretName = env("E2E_SECRET_NAME", defaultValue: "test-secret")
        XCTAssertTrue(
            ["sender", "offline-receiver", "observer"].contains(role),
            "Unsupported network-loss role: \(role)"
        )
        XCTAssertFalse(cycles.isEmpty, "No network-loss cycles configured")
        print("E2E: IOS_NETWORK_CONFIG role=\(role) cycles=\(cycles.joined(separator: ","))")
        waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        // Signal readiness only after the native UI has loaded the secret row;
        // the orchestrator uses this marker instead of racing the first request
        // against simulator startup.
        print("E2E: IOS_NETWORK_READY")

        for cycle in cycles {
            switch role {
            case "sender":
                waitForApproval(platform: "ios-sender", cycle: cycle, step: "1")
                showAcceptedSecretIfNeeded(secretName, cycle: cycle, step: "1")
                waitForPrimaryAction(secretName, expected: "Recover", timeout: 180)
                tap("secret-primary-action-\(secretName)")
                enterSimulatorPasscodeIfNeeded()
                waitForVisible(identifier: "show-secret-dialog", timeout: 30)
                print("E2E: IOS_RECOVERY_REQUEST_SENT_\(cycle)_1")
                // Keep the request dialog separate from the later Show step.
                // If it remains open, the socket's RECOVER_ACCEPTED event can
                // reveal the secret immediately, before the coordinator
                // releases the explicit show phase.
                closeShowSecretDialog()
                waitForApproval(platform: "ios-show", cycle: cycle, step: "1")
                revealAcceptedSecret(secretName, cycle: cycle, step: "1")
                print("E2E: IOS_RECOVERY_SECRET_VISIBLE_\(cycle)_1")
                tap("show-secret-close")
                waitForHidden(identifier: "revealed-secret-value", timeout: 30)
                waitForPrimaryAction(secretName, expected: "Recover", timeout: 30)
                print("E2E: IOS_RECOVERY_CLOSED_\(cycle)_1")

            case "offline-receiver":
                waitForVisible(identifier: "recovery-request-badge-\(secretName)", timeout: 180)
                tap("open-recovery-request-\(secretName)")
                waitForVisible(identifier: "alert-recovery-request", timeout: 30)
                print("E2E: IOS_INCOMING_VISIBLE_\(cycle)_1")
                print("E2E: IOS_NETWORK_LOSS_READY_\(cycle)")
                waitForApproval(platform: "ios-offline-attempt", cycle: cycle, step: "1")
                tap("alert-recovery-request-accept")
                enterSimulatorPasscodeIfNeeded()
                print("E2E: IOS_OFFLINE_APPROVE_CLICKED_\(cycle)")
                waitForApproval(platform: "ios-offline-online", cycle: cycle, step: "1")
                // Reconnect can flush the failed approval automatically. Do
                // not create a second response when the alert has already
                // disappeared; tap only if the action is still present.
                if waitForRecoveryResolutionOrApprove(timeout: 120) {
                    tap("alert-recovery-request-accept")
                    enterSimulatorPasscodeIfNeeded()
                }
                waitForHidden(identifier: "alert-recovery-request", timeout: 120)
                waitForHidden(identifier: "alert-recovery-request-processing", timeout: 120)
                print("E2E: IOS_APPROVED_AFTER_RECONNECT_\(cycle)_1")

            case "observer":
                waitForVisible(identifier: "recovery-request-badge-\(secretName)", timeout: 180)
                waitForVisible(identifier: "open-recovery-request-\(secretName)", timeout: 180)
                print("E2E: IOS_INCOMING_VISIBLE_\(cycle)_1")
                waitForApproval(platform: "ios-observer-finish", cycle: cycle, step: "1")
                waitForHidden(identifier: "recovery-request-badge-\(secretName)", timeout: 120)
                waitForHidden(identifier: "open-recovery-request-\(secretName)", timeout: 120)
                print("E2E: IOS_OBSERVER_CLOSED_\(cycle)_1")

            default:
                XCTFail("Unsupported network-loss role: \(role)")
            }
        }
        print("E2E: IOS_NETWORK_LOSS_DONE")
    }

    func sendRecoveryRequestAndExit() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test-secret")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "1")
        waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        waitForPrimaryAction(secretName, expected: "Recover", timeout: 180)
        waitForApproval(platform: "ios-sender", cycle: cycle, step: "1")
        tap("secret-primary-action-\(secretName)")
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "show-secret-dialog", timeout: 30)
        print("E2E: IOS_RECOVERY_REQUEST_SENT_\(cycle)_1")
        print("E2E: IOS_SENDER_OFFLINE_BOUNDARY_\(cycle)")
    }

    func showAcceptedRecoveryAfterOffline() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test-secret")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "1")
        waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        waitForPrimaryAction(secretName, expected: "Show", timeout: 180)
        waitForApproval(platform: "ios-show", cycle: cycle, step: "1")
        tap("secret-primary-action-\(secretName)")
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "revealed-secret-value", timeout: 180)
        print("E2E: IOS_RECOVERY_SECRET_VISIBLE_\(cycle)_1")
        tap("show-secret-close")
        waitForHidden(identifier: "revealed-secret-value", timeout: 30)
        waitForHidden(identifier: "show-secret-dialog", timeout: 30)
        waitForPrimaryAction(secretName, expected: "Recover", timeout: 30)
        print("E2E: IOS_RECOVERY_CLOSED_\(cycle)_1")
    }

    func handleRecoveryStep() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test-secret")
        let secretValue = env("E2E_SECRET_VALUE", defaultValue: "test-secret-value")
        let role = config.role ?? env("E2E_ROLE", defaultValue: "receiver")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "1")
        let step = config.step ?? env("E2E_STEP", defaultValue: "1")
        let approvalPlatform = config.approvalPlatform ?? env("E2E_APPROVAL_PLATFORM", defaultValue: "")

        print("E2E: IOS_STEP_CONFIG role=\(role) cycle=\(cycle) step=\(step) approval=\(approvalPlatform)")

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)

        if role == "sender" {
            waitForApproval(platform: "ios-sender", cycle: cycle, step: step)
            // A receiver that approved an earlier cycle legitimately has a
            // Show action. Consume that accepted claim before creating the
            // next recovery request; otherwise waiting for Recover would hang
            // forever even though the application is in the correct state.
            showAcceptedSecretIfNeeded(secretName, cycle: cycle, step: step)
            waitForPrimaryAction(secretName, expected: "Recover", timeout: 180)
            tap("secret-primary-action-\(secretName)")
            enterSimulatorPasscodeIfNeeded()
            waitForVisible(identifier: "show-secret-dialog", timeout: 30)
            print("E2E: IOS_RECOVERY_REQUEST_SENT_\(cycle)_\(step)")
            if approvalPlatform == "cli" {
                closeShowSecretDialog()
                waitForApproval(platform: "ios-invalidated", cycle: cycle, step: step)
                waitForPrimaryAction(secretName, expected: "Recover", timeout: 180)
                print("E2E: IOS_RECOVERY_INVALIDATED_\(cycle)_\(step)")
                return
            }
            waitForApproval(platform: "ios-show", cycle: cycle, step: step)
            revealAcceptedSecret(secretName, cycle: cycle, step: step)
            XCTAssertTrue(
                app.descendants(matching: .any)[secretValue].waitForExistence(timeout: 10),
                "iOS sender did not reveal the expected secret"
            )
            print("E2E: IOS_RECOVERY_SECRET_VISIBLE_\(cycle)_\(step)")
            tap("show-secret-close")
            waitForHidden(identifier: "revealed-secret-value", timeout: 30)
            print("E2E: IOS_RECOVERY_CLOSED_\(cycle)_\(step)")
            return
        }

        waitForVisible(identifier: "recovery-request-badge-\(secretName)", timeout: 180)
        // The first responder can close this receiver's request before the
        // second runner reaches the open button. Wait for either the button
        // or the terminal badge disappearance instead of asserting the
        // button at a fixed instant.
        let openBeforeDecision = app.descendants(matching: .any)["open-recovery-request-\(secretName)"]
        let badgeBeforeDecision = app.descendants(matching: .any)["recovery-request-badge-\(secretName)"]
        let openOrTerminal = NSPredicate { _, _ in openBeforeDecision.exists || !badgeBeforeDecision.exists }
        let openWait = XCTNSPredicateExpectation(predicate: openOrTerminal, object: nil)
        _ = XCTWaiter.wait(for: [openWait], timeout: 30)
        if !openBeforeDecision.exists {
            if !badgeBeforeDecision.exists {
                print("E2E: IOS_ACTION_SKIPPED_AFTER_TERMINAL_\(cycle)_\(step)")
                return
            }
            XCTFail("iOS recovery request button did not appear while the request remained pending")
            return
        }
        print("E2E: IOS_INCOMING_VISIBLE_\(cycle)_\(step)")
        if approvalPlatform == "ios" {
            waitForApproval(platform: "ios-approve", cycle: cycle, step: step)
            tap("open-recovery-request-\(secretName)")
            waitForVisible(identifier: "alert-recovery-request", timeout: 30)
            tap("alert-recovery-request-accept")
            enterSimulatorPasscodeIfNeeded()
            waitForHidden(identifier: "alert-recovery-request", timeout: 120)
            waitForHidden(identifier: "alert-recovery-request-processing", timeout: 120)
            print("E2E: IOS_APPROVED_INCOMING_\(cycle)_\(step)")
        } else {
            waitForApproval(platform: "ios-dismiss", cycle: cycle, step: step)
            waitForHidden(identifier: "recovery-request-badge-\(secretName)", timeout: 120)
            waitForHidden(identifier: "open-recovery-request-\(secretName)", timeout: 120)
            print("E2E: IOS_DISMISSED_INCOMING_\(cycle)_\(step)")
        }
    }

    func handleApproveDeclineStep() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test-secret")
        let role = config.role ?? env("E2E_ROLE", defaultValue: "receiver")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "1")
        let step = config.step ?? env("E2E_STEP", defaultValue: "1")
        let action = config.action ?? env("E2E_ACTION", defaultValue: "approve")
        let expectedOutcome = config.expectedOutcome ?? env("E2E_EXPECTED_OUTCOME", defaultValue: "approved")
        let repeatApprove = config.repeatApprove ?? (env("E2E_REPEAT_APPROVE", defaultValue: "0") == "1")
        let duplicateRecovery = config.duplicateRecovery ?? (env("E2E_DUPLICATE_RECOVERY", defaultValue: "0") == "1")

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)

        if role == "sender" {
            waitForApproval(platform: "ios-sender", cycle: cycle, step: step)
            showAcceptedSecretIfNeeded(secretName, cycle: cycle, step: step)
            waitForPrimaryAction(secretName, expected: "Recover", timeout: 180)
            tap("secret-primary-action-\(secretName)")
            enterSimulatorPasscodeIfNeeded()
            waitForVisible(identifier: "show-secret-dialog", timeout: 30)
            print("E2E: IOS_RECOVERY_REQUEST_SENT_\(cycle)_\(step)")
            if duplicateRecovery {
                let duplicateAction = app.descendants(matching: .any)["secret-primary-action-\(secretName)"]
                if duplicateAction.exists && duplicateAction.isHittable {
                    duplicateAction.tap()
                    print("E2E: IOS_DUPLICATE_RECOVERY_SENT_\(cycle)_\(step)")
                } else {
                    print("E2E: IOS_DUPLICATE_RECOVERY_SKIPPED_AFTER_GUARD_\(cycle)_\(step)")
                }
                closeShowSecretDialog()
                waitForApproval(platform: "ios-duplicate-finish", cycle: cycle, step: step)
                revealAcceptedSecret(secretName, cycle: cycle, step: step)
                XCTAssertTrue(app.descendants(matching: .any)[env("E2E_SECRET_VALUE", defaultValue: "test-secret-value")].exists)
                print("E2E: IOS_RECOVERY_SECRET_VISIBLE_\(cycle)_\(step)")
                closeShowSecretDialog()
                waitForPrimaryAction(secretName, expected: "Recover", timeout: 30)
                print("E2E: IOS_RECOVERY_CLOSED_\(cycle)_\(step)")
                return
            }
            // Keep the sender from auto-completing the claim when the first
            // receiver approves. The race test must process both receiver
            // decisions before the explicit Show gate is released.
            closeShowSecretDialog()
            if expectedOutcome == "approved" {
                waitForApproval(platform: "ios-show", cycle: cycle, step: step)
                revealAcceptedSecret(secretName, cycle: cycle, step: step)
                XCTAssertTrue(app.descendants(matching: .any)[env("E2E_SECRET_VALUE", defaultValue: "test-secret-value")].exists)
                print("E2E: IOS_RECOVERY_SECRET_VISIBLE_\(cycle)_\(step)")
                closeShowSecretDialog()
                waitForPrimaryAction(secretName, expected: "Recover", timeout: 30)
                print("E2E: IOS_RECOVERY_CLOSED_\(cycle)_\(step)")
            } else {
                waitForApproval(platform: "ios-declined", cycle: cycle, step: step)
                closeShowSecretDialog()
                waitForHidden(identifier: "revealed-secret-value", timeout: 30)
                waitForPrimaryAction(secretName, expected: "Recover", timeout: 30)
                print("E2E: IOS_RECOVERY_NOT_VISIBLE_\(cycle)_\(step)")
                print("E2E: IOS_RECOVERY_CLOSED_\(cycle)_\(step)")
            }
            return
        }

        waitForVisible(identifier: "recovery-request-badge-\(secretName)", timeout: 180)
        if duplicateRecovery {
            waitForSingleActiveClaim(secretName, cycle: cycle, step: step)
        }
        // The first responder can close this receiver's request before the
        // second runner reaches the open button. Wait for either the button
        // or the terminal badge disappearance instead of asserting the
        // button at a fixed instant.
        let openBeforeDecision = app.descendants(matching: .any)["open-recovery-request-\(secretName)"]
        let badgeBeforeDecision = app.descendants(matching: .any)["recovery-request-badge-\(secretName)"]
        let openOrTerminal = NSPredicate { _, _ in openBeforeDecision.exists || !badgeBeforeDecision.exists }
        let openWait = XCTNSPredicateExpectation(predicate: openOrTerminal, object: nil)
        _ = XCTWaiter.wait(for: [openWait], timeout: 30)
        if !openBeforeDecision.exists {
            if !badgeBeforeDecision.exists {
                print("E2E: IOS_ACTION_SKIPPED_AFTER_TERMINAL_\(cycle)_\(step)")
                return
            }
            XCTFail("iOS recovery request button did not appear while the request remained pending")
            return
        }
        print("E2E: IOS_INCOMING_VISIBLE_\(cycle)_\(step)")
        waitForApproval(platform: "ios-\(action)", cycle: cycle, step: step)
        let open = app.descendants(matching: .any)["open-recovery-request-\(secretName)"]
        let badgeAfterGate = app.descendants(matching: .any)["recovery-request-badge-\(secretName)"]
        // The first response is committed before this gate is released. Give
        // the local accessibility tree a bounded, state-based opportunity to
        // observe that terminal transition; if it remains pending, this
        // runner is the first responder and may open the request.
        let terminalAfterGate = NSPredicate { _, _ in !badgeAfterGate.exists || !open.exists }
        let terminalWait = XCTNSPredicateExpectation(predicate: terminalAfterGate, object: nil)
        let terminalResult = XCTWaiter.wait(for: [terminalWait], timeout: 5)
        if terminalResult == .completed || !badgeAfterGate.exists || !open.exists || !open.isHittable {
            print("E2E: IOS_ACTION_SKIPPED_AFTER_TERMINAL_\(cycle)_\(step)")
            return
        }
        open.tap()
        // The first responder may have already made the recovery terminal
        // before this runner opens its local sheet. In that case the badge is
        // removed and there is no alert to act on; record a skipped late
        // response instead of failing while waiting for a dialog that should
        // no longer exist.
        let alert = app.descendants(matching: .any)["alert-recovery-request"]
        let badge = app.descendants(matching: .any)["recovery-request-badge-\(secretName)"]
        let terminalOrAlert = NSPredicate { _, _ in alert.exists || !badge.exists }
        let alertWait = XCTNSPredicateExpectation(predicate: terminalOrAlert, object: nil)
        _ = XCTWaiter.wait(for: [alertWait], timeout: 30)
        if !alert.exists {
            if !badge.exists {
                print("E2E: IOS_ACTION_SKIPPED_AFTER_TERMINAL_\(cycle)_\(step)")
                return
            }
            XCTFail("iOS recovery alert did not appear while the request remained pending")
            return
        }
        print("E2E: IOS_ACTION_STARTED_\(cycle)_\(step)")
        // The shared scenario calls the positive decision `approve`, while
        // the iOS accessibility identifier follows the product label `accept`.
        let actionIdentifier = action == "approve" ? "accept" : action
        tap("alert-recovery-request-\(actionIdentifier)")
        enterSimulatorPasscodeIfNeeded()
        if repeatApprove && action == "approve" {
            let secondApprove = app.descendants(matching: .any)["alert-recovery-request-accept"]
            if secondApprove.exists && secondApprove.isHittable {
                print("E2E: IOS_REPEAT_APPROVE_SECOND_TAP_SENT_\(cycle)_\(step)")
                secondApprove.tap()
            } else {
                print("E2E: IOS_REPEAT_APPROVE_SECOND_TAP_SKIPPED_AFTER_DISMISS_\(cycle)_\(step)")
            }
        }
        waitForHidden(identifier: "alert-recovery-request", timeout: 120)
        waitForHidden(identifier: "alert-recovery-request-processing", timeout: 120)
        print("E2E: IOS_\(action == "decline" ? "DECLINED" : "APPROVED")_INCOMING_\(cycle)_\(step)")
    }

    private func revealAcceptedSecret(_ secretName: String, cycle: String, step: String) {
        let action = app.descendants(matching: .any)["secret-primary-action-\(secretName)"]
        let dialog = app.descendants(matching: .any)["show-secret-dialog"]
        let revealed = app.descendants(matching: .any)["revealed-secret-value"]
        let deadline = Date().addingTimeInterval(180)
        while Date() < deadline {
            if revealed.exists { return }
            if action.exists && action.label.localizedCaseInsensitiveContains("Show") {
                print("E2E: IOS_SHOW_ACTION_AFTER_APPROVAL_\(cycle)_\(step)")
                if dialog.exists {
                    tap("show-secret-close")
                    _ = dialog.waitForNonExistence(timeout: 30)
                }
                action.tap()
                enterSimulatorPasscodeIfNeeded()
                waitForVisible(identifier: "revealed-secret-value", timeout: 180)
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        XCTFail("iOS sender did not reveal accepted secret in cycle \(cycle), step \(step)")
    }

    private func waitForPrimaryAction(_ secretName: String, expected: String, timeout: TimeInterval) {
        let action = app.descendants(matching: .any)["secret-primary-action-\(secretName)"]
        let deadline = Date().addingTimeInterval(timeout)
        var lastObservedLabel = "<missing>"
        while Date() < deadline {
            let observedLabel = action.exists ? action.label : "<missing>"
            if observedLabel != lastObservedLabel {
                print(
                    "E2E: IOS_PRIMARY_ACTION_STATE secret=\(secretName) "
                        + "expected=\(expected) label=\(observedLabel)"
                )
                lastObservedLabel = observedLabel
            }
            if action.exists, action.label.localizedCaseInsensitiveContains(expected) {
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        print(
            "E2E: IOS_PRIMARY_ACTION_TIMEOUT secret=\(secretName) expected=\(expected) "
                + "exists=\(action.exists) label=\(action.label)"
        )
        XCTFail("Primary action for \(secretName) did not become \(expected)")
    }

    private func waitForSingleActiveClaim(_ secretName: String, cycle: String, step: String) {
        let badge = app.descendants(matching: .any)["recovery-request-badge-\(secretName)"]
        let deadline = Date().addingTimeInterval(180)
        while Date() < deadline {
            if badge.exists {
                let label = badge.label.trimmingCharacters(in: .whitespacesAndNewlines)
                let count = label.split(whereSeparator: { $0 == " " || $0 == "\u{00a0}" }).last.map(String.init)
                if count == "1" {
                    print("E2E: IOS_SINGLE_ACTIVE_CLAIM_\(cycle)_\(step)")
                    return
                }
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        XCTFail("iOS expected exactly one active recovery claim, badge=\(badge.label)")
    }

    private func showAcceptedSecretIfNeeded(_ secretName: String, cycle: String, step: String) {
        let action = app.descendants(matching: .any)["secret-primary-action-\(secretName)"]
        let deadline = Date().addingTimeInterval(180)
        while Date() < deadline {
            if action.exists {
                let label = action.label
                if label.localizedCaseInsensitiveContains("Recover") { return }
                if label.localizedCaseInsensitiveContains("Show") {
                    print("E2E: IOS_STALE_SHOW_ACTION_\(cycle)_\(step) consuming previously accepted claim")
                    action.tap()
                    enterSimulatorPasscodeIfNeeded()
                    waitForVisible(identifier: "revealed-secret-value", timeout: 180)
                    tap("show-secret-close")
                    waitForHidden(identifier: "revealed-secret-value", timeout: 30)
                    waitForHidden(identifier: "show-secret-dialog", timeout: 30)
                    return
                }
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        XCTFail("Primary action for \(secretName) did not become Recover or Show")
    }

    private func waitForApproval(platform: String, cycle: String, step: String) {
        let baseUrl = env("E2E_APPROVAL_COORDINATOR_URL", defaultValue: "http://127.0.0.1:5180")
        let deadline = Date().addingTimeInterval(240)
        while Date() < deadline {
            if let url = URL(string: "\(baseUrl)/approval?platform=\(platform)-\(step)&cycle=\(cycle)"),
               let result = try? String(contentsOf: url, encoding: .utf8),
               result == "allowed" {
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        XCTFail("Timed out waiting for \(platform) step \(step), cycle \(cycle)")
    }

    private func skipOnboardingIfNeeded() {
        let skip = app.descendants(matching: .any)["onboarding-skip"]
        if skip.waitForExistence(timeout: 10) { skip.tap() }
    }

    private func openManualEmailSignIn() {
        let manual = app.descendants(matching: .any)["signin-email-manual"]
        if manual.waitForExistence(timeout: 30) { manual.tap(); return }
        let manualByText = app.buttons["Enter email manually"]
        XCTAssertTrue(manualByText.waitForExistence(timeout: 10))
        manualByText.tap()
    }

    private func typeEmail(_ email: String) {
        let field = app.descendants(matching: .any)["email-input"]
        XCTAssertTrue(field.waitForExistence(timeout: 30))
        field.tap()
        field.typeText(email)
        dismissKeyboardIfNeeded()
    }

    private func tap(_ identifier: String, timeout: TimeInterval = 30) {
        let element = app.descendants(matching: .any)[identifier]
        XCTAssertTrue(element.waitForExistence(timeout: timeout), "\(identifier) was not visible")
        if !element.isHittable { dismissKeyboardIfNeeded() }
        element.tap()
    }

    private func waitForVisible(identifier: String, timeout: TimeInterval) {
        XCTAssertTrue(
            app.descendants(matching: .any)[identifier].waitForExistence(timeout: timeout),
            "\(identifier) was not visible"
        )
    }

    private func waitForHidden(identifier: String, timeout: TimeInterval) {
        XCTAssertTrue(
            app.descendants(matching: .any)[identifier].waitForNonExistence(timeout: timeout),
            "\(identifier) did not disappear"
        )
    }

    private func waitForRecoveryResolutionOrApprove(timeout: TimeInterval) -> Bool {
        let accept = app.descendants(matching: .any)["alert-recovery-request-accept"]
        let alert = app.descendants(matching: .any)["alert-recovery-request"]
        let processing = app.descendants(matching: .any)["alert-recovery-request-processing"]
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if accept.exists { return true }
            if !alert.exists && !processing.exists { return false }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        XCTFail("Recovery alert did not resolve after reconnect")
        return false
    }

    private func closeShowSecretDialog() {
        let dialog = app.descendants(matching: .any)["show-secret-dialog"]
        let close = app.descendants(matching: .any)["show-secret-close"]
        let deadline = Date().addingTimeInterval(30)
        var tapAttempts = 0

        // Compose can recreate the close control during the exit transition.
        // Retry only while the dialog is present and the control is hittable;
        // this is a state-based wait, not an artificial sleep.
        while Date() < deadline {
            if !dialog.exists {
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

        XCTFail("show-secret-dialog did not disappear")
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
        let secureField = springboard.secureTextFields.element(boundBy: 0)
        if secureField.waitForExistence(timeout: 2) {
            secureField.tap()
            secureField.typeText("1111")
            return
        }
        let one = springboard.buttons["1"]
        if one.waitForExistence(timeout: 1) {
            one.tap(); one.tap(); one.tap(); one.tap()
        }
    }

    private func env(_ key: String, defaultValue: String) -> String {
        ProcessInfo.processInfo.environment[key] ?? defaultValue
    }

    private func coordinatorVaultName(defaultValue: String) -> String {
        let baseUrl = env("E2E_APPROVAL_COORDINATOR_URL", defaultValue: "http://127.0.0.1:5180")
        guard let url = URL(string: "\(baseUrl)/scenario") else { return env("E2E_VAULT_NAME", defaultValue: defaultValue) }
        let deadline = Date().addingTimeInterval(30)
        while Date() < deadline {
            if let data = try? Data(contentsOf: url),
               let raw = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let vaultName = raw["vaultName"] as? String {
                return vaultName
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        return env("E2E_VAULT_NAME", defaultValue: defaultValue)
    }

    private func configuredSecretNames() -> [String] {
        let fallback = env("E2E_SECRET_NAME", defaultValue: "test-secret")
        let baseUrl = env("E2E_APPROVAL_COORDINATOR_URL", defaultValue: "http://127.0.0.1:5180")
        if let url = URL(string: "\(baseUrl)/scenario"),
           let data = try? Data(contentsOf: url),
           let scenario = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let raw = scenario["secretConfig"] as? [String: Any] {
            let names = raw.keys.sorted().compactMap { raw[$0] as? [String: Any] }
                .compactMap { $0["name"] as? String }
            if !names.isEmpty { return names }
        }
        guard let data = env("E2E_SECRET_CONFIG", defaultValue: "").data(using: .utf8),
              let raw = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return [fallback] }
        let names = raw.keys.sorted().compactMap { raw[$0] as? [String: Any] }
            .compactMap { $0["name"] as? String }
        return names.isEmpty ? [fallback] : names
    }

    private func stepConfig() -> StepConfig {
        guard
            let data = FileManager.default.contents(atPath: Self.stepConfigPath),
            let config = try? JSONDecoder().decode(StepConfig.self, from: data)
        else {
            return StepConfig(role: nil, cycle: nil, step: nil, approvalPlatform: nil, sender: nil, secretName: nil, action: nil, expectedOutcome: nil, repeatApprove: nil, duplicateRecovery: nil, networkRole: nil, networkCycles: nil)
        }
        return config
    }
}

private extension XCUIElement {
    func tapIfExists() {
        if exists && isHittable { tap() }
    }
}
