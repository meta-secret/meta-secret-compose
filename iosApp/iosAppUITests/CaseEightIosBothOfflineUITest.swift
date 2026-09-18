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
    }

    private static let stepConfigPath = "/tmp/metasecret-e2e-ios-step.json"
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchEnvironment["METASECRET_UI_TEST_MODE"] = "true"
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
        waitForVisible(identifier: "open-recovery-request-\(secretName)", timeout: 30)
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
        while Date() < deadline {
            if action.exists, action.label.localizedCaseInsensitiveContains(expected) {
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        XCTFail("Primary action for \(secretName) did not become \(expected)")
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
            return StepConfig(role: nil, cycle: nil, step: nil, approvalPlatform: nil, sender: nil, secretName: nil)
        }
        return config
    }
}

private extension XCUIElement {
    func tapIfExists() {
        if exists && isHittable { tap() }
    }
}
