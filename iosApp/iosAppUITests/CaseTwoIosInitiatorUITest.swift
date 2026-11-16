import XCTest

@MainActor
final class CaseTwoIosInitiatorUITest: XCTestCase {
    private var app: XCUIApplication!

    private struct SecretDefinition {
        let name: String
        let value: String
    }

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.lbApplyTestIdentity()
        app.lbResetSelectors()
        app.launchEnvironment["METASECRET_UI_TEST_MODE"] = "true"
        app.launch()
    }

    func testCreateVaultThenApproveAndroidAndWeb() throws {
        let vaultName = env("E2E_VAULT_NAME", defaultValue: "test@test.ru")
        let secretName = env("E2E_SECRET_NAME", defaultValue: "test-secret")
        let secretValue = env("E2E_SECRET_VALUE", defaultValue: "test-secret-value")

        app.orbitEnterVaultName(vaultName)
        app.orbitCreateVault()
        tap("add-secret-fab", timeout: 180)
        guard typeSecretNameAndValue(name: secretName, value: secretValue) else { return }
        app.lbSubmitCreate()
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        tap("secret-primary-action-\(secretName)")
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "revealed-secret-value", timeout: 90)
        app.lbAssertValue(secretValue)
        tap("show-secret-close")
        app.lbAssertNoPrivateContent(secretValue)
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
            tap("secret-primary-action-\(secretName)")
            enterSimulatorPasscodeIfNeeded()
            print("E2E: IOS_RECOVERY_REQUEST_SENT_\(cycle)")
            app.lbShow(secretName, expected: secretValue)
            waitForVisible(identifier: "revealed-secret-value", timeout: 180)
            app.lbAssertValue(secretValue)
            print("E2E: IOS_RECOVERY_SECRET_VISIBLE_\(cycle)")
            tap("show-secret-close")
            print("E2E: IOS_RECOVERY_CLOSED_\(cycle)")
        }
    }

    func createVaultAndWaitForJoins() throws {
        let vaultName = coordinatorVaultName(defaultValue: "test@test.ru")
        let secrets = configuredSecrets()
        app.orbitEnterVaultName(vaultName)
        app.orbitCreateVault()
        for secret in secrets {
            tap("add-secret-fab", timeout: 180)
            guard typeSecretNameAndValue(name: secret.name, value: secret.value) else { return }
            app.lbSubmitCreate()
            enterSimulatorPasscodeIfNeeded()
            let closeDeadline = Date().addingTimeInterval(30)
            let modalAbsent: () -> Bool = {
                self.app.descendants(matching: .any).matching(identifier: "lb-create").count == 0
            }
            if !modalAbsent(), Date() < closeDeadline {
                app.lbWait(modalAbsent, timeout: max(0, closeDeadline.timeIntervalSinceNow), message: "Create modal did not close")
            }
            guard Date() < closeDeadline else { XCTFail("Create close deadline expired"); return }
            let absent = modalAbsent()
            guard absent, Date() < closeDeadline else { XCTFail("Create modal absence was not proved within its close phase"); return }
        }
        let names = secrets.map(\.name)
        func completeSecretSet() -> Bool {
            let resolved = app.lbFindSecrets(names, timeout: 180)
            return !names.isEmpty && resolved.count == names.count && Set(resolved.keys) == Set(names) &&
                Set(resolved.values).count == names.count && resolved.values.allSatisfy { !$0.isEmpty }
        }
        guard completeSecretSet() else {
            XCTFail("Created Secret set failed its complete uniqueness barrier"); return
        }
        print("E2E: IOS_INITIATOR_READY")
        approvePendingJoin(marker: "IOS_WEB_JOIN_APPROVED")
        let joinPlatforms = Set(env("E2E_JOIN_PLATFORMS", defaultValue: "web,android").split(separator: ",").map(String.init))
        if joinPlatforms.contains("android") {
            approvePendingJoin(marker: "IOS_ANDROID_JOIN_APPROVED")
        }
        // Join approval leaves the app on the Devices tab. Return to Secrets
        // before asserting that the owner's secret rows remain available.
        tap("tab-secrets")
        guard completeSecretSet() else {
            XCTFail("Joined Secret set failed its complete uniqueness barrier"); return
        }
        print("E2E: IOS_SECRETS_READY")
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

    private func typeSecretNameAndValue(name: String, value: String) -> Bool {
        app.lbFillCreate(name: name, value: value)
    }
    private func configuredSecrets() -> [SecretDefinition] {
        let fallbackName = env("E2E_SECRET_NAME", defaultValue: "test-secret")
        let fallbackValue = env("E2E_SECRET_VALUE", defaultValue: "test-secret-value")
        let baseUrl = env("E2E_APPROVAL_COORDINATOR_URL", defaultValue: "http://127.0.0.1:5180")
        if let url = URL(string: "\(baseUrl)/scenario"),
           let data = try? Data(contentsOf: url),
           let scenario = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let raw = scenario["secretConfig"] as? [String: Any] {
            let secrets = raw.keys.sorted().compactMap { key -> SecretDefinition? in
                guard let definition = raw[key] as? [String: Any],
                      let name = definition["name"] as? String,
                      let value = definition["value"] as? String else { return nil }
                return SecretDefinition(name: name, value: value)
            }
            if !secrets.isEmpty { return secrets }
        }
        guard let data = env("E2E_SECRET_CONFIG", defaultValue: "").data(using: .utf8),
              let raw = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            return [SecretDefinition(name: fallbackName, value: fallbackValue)]
        }
        let secrets = raw.keys.sorted().compactMap { key -> SecretDefinition? in
            guard let definition = raw[key] as? [String: Any],
                  let name = definition["name"] as? String,
                  let value = definition["value"] as? String else { return nil }
            return SecretDefinition(name: name, value: value)
        }
        return secrets.isEmpty
            ? [SecretDefinition(name: fallbackName, value: fallbackValue)]
            : secrets
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
        waitForHidden(identifier: "pending-device-row", timeout: 120)
        print("E2E: \(marker)")
    }

    private func tap(_ identifier: String, timeout: TimeInterval = 30) {
        app.lbTapIntent(identifier, timeout: timeout)
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
