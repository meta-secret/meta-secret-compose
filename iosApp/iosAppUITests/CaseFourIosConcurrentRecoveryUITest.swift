import Foundation
import XCTest

@MainActor
final class CaseFourIosConcurrentRecoveryUITest: XCTestCase {
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

    func testJoinAndroidInitiatedVaultAndHandleConcurrentRecovery() throws {
        try joinAndroidInitiatedVaultAndRunRecovery()
    }

    func testJoinAndroidInitiatedVaultAndHandleConcurrentRecoveryBlock1() throws {
        try joinAndroidInitiatedVaultAndRunRecovery()
    }

    func testJoinAndroidInitiatedVaultAndHandleConcurrentRecoveryBlock2() throws {
        try joinAndroidInitiatedVaultAndRunRecovery()
    }

    func testJoinAndroidInitiatedVaultAndHandleConcurrentRecoveryBlock3() throws {
        try joinAndroidInitiatedVaultAndRunRecovery()
    }

    func assertNoStaleRecoveryAlertAfterOfflineRestart() throws {
        let coordinator = loadCoordinatorScenario()
        let secrets = coordinator?.secrets ?? secretDefinitions()
        guard let secret = secrets.first else {
            XCTFail("No configured secret available for stale-alert assertion")
            return
        }
        print("E2E: IOS_STALE_ALERT_ASSERTION_STARTED secret=\(secret.name)")
        waitForVisible(identifier: "secret-row-\(secret.name)", timeout: 180)
        waitForHidden(identifier: "recovery-request-badge-\(secret.name)", timeout: 30)
        waitForHidden(identifier: "open-recovery-request-\(secret.name)", timeout: 30)
        waitForHidden(identifier: "alert-recovery-request", timeout: 30)
        waitForHidden(identifier: "alert-recovery-request-processing", timeout: 30)
        print("E2E: IOS_STALE_ALERT_ABSENT")
    }

    private func joinAndroidInitiatedVaultAndRunRecovery() throws {
        // xcodebuild does not consistently forward arbitrary environment
        // variables to the XCTest runner. Read the orchestrator's authoritative
        // scenario over the same localhost coordinator used for approvals, and
        // keep environment variables as a fallback for standalone runs.
        let coordinator = loadCoordinatorScenario()
        let vaultName = coordinator?.vaultName
            ?? env("E2E_VAULT_NAME", defaultValue: "test@test.ru")
        let secrets = coordinator?.secrets ?? secretDefinitions()
        let recoveryPlan = coordinator?.recoveryPlan ?? recoveryCyclePlan()
        let secretCreationPlan = coordinator?.secretCreationPlan ?? self.secretCreationPlan()
        print(
            "E2E: IOS_SCENARIO_CONFIG vault=\(vaultName) " +
                "secrets=\(secrets.map(\.name).joined(separator: ",")) " +
                "cycles=\(recoveryPlan.count)"
        )
        print(
            "E2E: IOS_SECRET_CREATION_PLAN initial=\(configuredSecretNames(plan: secretCreationPlan, stage: "initial")) " +
                "afterWebJoin=\(configuredSecretNames(plan: secretCreationPlan, stage: "afterWebJoin", platform: "web")) " +
                "afterIosJoin=\(configuredSecretNames(plan: secretCreationPlan, stage: "afterIosJoin", platform: "ios"))"
        )

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
        // Test #6 deliberately creates its final secret on iOS after this
        // join, so first wait only for the secrets redistributed to the new
        // member, then perform the iOS-owned creation and wait for the final
        // set on every device.
        let preExistingSecrets = secretsBeforeIosJoin(secrets, plan: secretCreationPlan)
        waitForSecretsAfterJoin(preExistingSecrets, timeout: 180)
        for secretName in configuredSecretNames(
            plan: secretCreationPlan,
            stage: "afterIosJoin",
            platform: "ios"
        ) {
            guard let secret = secrets.first(where: { $0.name == secretName }) else {
                XCTFail("secretCreationPlan.afterIosJoin.ios contains unknown secret \(secretName)")
                continue
            }
            createSecret(secret)
        }
        waitForSecretsAfterJoin(secrets, timeout: 180)
        print("E2E: IOS_MAIN_AFTER_APPROVE")
        print("E2E: IOS_SECRETS_READY")

        if env("E2E_SETUP_ONLY", defaultValue: "0") == "1" {
            print("E2E: IOS_SETUP_ONLY_DONE")
            return
        }

        for cycle in recoveryPlan {
            if cycle.offlineReceiver == "ios" {
                // The orchestrator terminates iOS only after this semantic
                // boundary. Waiting for its gate keeps the XCTest process
                // alive long enough to distinguish a deliberate offline
                // interval from an unexpected app crash.
                print("E2E: IOS_OFFLINE_READY_\(cycle.number)")
                waitForApproval(platform: "ios-offline-stop", cycle: cycle.number)
                print("E2E: IOS_OFFLINE_STOP_ALLOWED_\(cycle.number)")
                return
            }

            if let ownSecret = cycle.senderSecrets["ios"] {
                waitForApproval(platform: "ios-sender", cycle: cycle.number)
                requestRecovery(ownSecret, cycle: cycle.number)
                print("E2E: IOS_RECOVERY_REQUEST_SENT_\(cycle.number)")
                // Preserve the sender dialog until both approvals finish. A
                // receiver-only iOS sender must not reopen it, because that
                // would issue a second recovery request rather than reveal
                // the accepted claim. Close early only when iOS also needs
                // to approve its peer's request in this cycle.
                if cycle.approvals.contains(where: { $0.platform == "ios" }) {
                    closeShowSecretDialog()
                }
            }

            for (index, approval) in cycle.approvals.enumerated() where approval.platform == "ios" {
                let step = index + 1
                waitForApproval(platform: "ios-approve-\(step)", cycle: cycle.number)
                approveIncomingRecovery(approval.secret, cycle: cycle.number, request: step)
                print("E2E: IOS_APPROVED_INCOMING_\(cycle.number)_\(step)")
            }

            if let ownSecret = cycle.senderSecrets["ios"] {
                waitForApproval(platform: "ios-show", cycle: cycle.number)
                if cycle.approvals.contains(where: { $0.platform == "ios" }) {
                    showAcceptedSecret(ownSecret, cycle: cycle.number)
                }
                waitForVisible(identifier: "revealed-secret-value", timeout: 180)
                print("E2E: IOS_RECOVERY_SECRET_VISIBLE_\(cycle.number)")
                closeShowSecretDialog()
                waitForHidden(identifier: "revealed-secret-value", timeout: 30)
                print("E2E: IOS_RECOVERY_CLOSED_\(cycle.number)")
            }
        }
    }

    func runNetworkLossCycles() throws {
        let role = env("E2E_NETWORK_ROLE", defaultValue: "")
        let block = env("E2E_NETWORK_BLOCK", defaultValue: "")
        XCTAssertTrue(
            ["sender", "approver", "offline-receiver"].contains(role),
            "Unsupported network-loss role: \(role)"
        )
        guard let coordinator = loadCoordinatorScenario() else {
            XCTFail("Network-loss test could not load coordinator scenario")
            return
        }
        let cycles = coordinator.recoveryPlan.filter { $0.block == block }
        XCTAssertFalse(cycles.isEmpty, "No network-loss cycles found for block=\(block)")
        print(
            "E2E: IOS_NETWORK_CONFIG role=\(role) block=\(block) "
                + "cycles=\(cycles.map { String($0.number) }.joined(separator: ","))"
        )

        for cycle in cycles {
            let senderSecret = cycle.senderSecrets["ios"]
                ?? cycle.senderSecrets.values.first
                ?? coordinator.secrets.first?.name
                ?? "test-secret"
            let incomingSecret = cycle.approvals.first?.secret ?? senderSecret
            switch role {
            case "sender":
                waitForApproval(platform: "ios-sender", cycle: cycle.number)
                requestRecovery(senderSecret, cycle: cycle.number)
                print("E2E: IOS_RECOVERY_REQUEST_SENT_\(cycle.number)_1")
                waitForApproval(platform: "ios-show", cycle: cycle.number)
                showAcceptedSecret(senderSecret, cycle: cycle.number)
                waitForVisible(identifier: "revealed-secret-value", timeout: 180)
                print("E2E: IOS_RECOVERY_SECRET_VISIBLE_\(cycle.number)_1")
                closeShowSecretDialog()
                print("E2E: IOS_RECOVERY_CLOSED_\(cycle.number)_1")

            case "approver":
                waitForApproval(platform: "ios-approve", cycle: cycle.number)
                approveIncomingRecovery(incomingSecret, cycle: cycle.number, request: 1)
                print("E2E: IOS_APPROVED_INCOMING_\(cycle.number)_1")

            case "offline-receiver":
                tap("open-recovery-request-\(incomingSecret)")
                waitForVisible(identifier: "alert-recovery-request", timeout: 180)
                print("E2E: IOS_INCOMING_VISIBLE_\(cycle.number)_1")
                print("E2E: IOS_NETWORK_LOSS_READY_\(cycle.number)")
                waitForApproval(platform: "ios-offline-attempt", cycle: cycle.number)
                tap("alert-recovery-request-accept")
                enterSimulatorPasscodeIfNeeded()
                print("E2E: IOS_OFFLINE_APPROVE_CLICKED_\(cycle.number)")
                waitForApproval(platform: "ios-offline-online", cycle: cycle.number)
                waitForVisible(identifier: "alert-recovery-request-accept", timeout: 120)
                tap("alert-recovery-request-accept")
                enterSimulatorPasscodeIfNeeded()
                waitForHidden(identifier: "alert-recovery-request", timeout: 120)
                waitForHidden(identifier: "alert-recovery-request-processing", timeout: 120)
                print("E2E: IOS_APPROVED_AFTER_RECONNECT_\(cycle.number)_1")

            default:
                XCTFail("Unsupported network-loss role: \(role)")
            }
        }
        print("E2E: IOS_NETWORK_LOSS_DONE")
    }

    private struct RecoveryApproval {
        let platform: String
        let secret: String
    }

    private struct RecoveryCycle {
        let number: Int
        let block: String
        let senderSecrets: [String: String]
        let approvals: [RecoveryApproval]
        let offlineReceiver: String?

        var sender: String { senderSecrets.keys.first ?? "" }
    }

    private struct SecretDefinition {
        let name: String
        let value: String
    }

    private struct CoordinatorScenario {
        let vaultName: String
        let secrets: [SecretDefinition]
        let recoveryPlan: [RecoveryCycle]
        let secretCreationPlan: [String: Any]?
    }

    private func loadCoordinatorScenario() -> CoordinatorScenario? {
        let baseUrl = env("E2E_APPROVAL_COORDINATOR_URL", defaultValue: "http://127.0.0.1:5180")
        guard let url = URL(string: "\(baseUrl)/scenario") else { return nil }

        let deadline = Date().addingTimeInterval(30)
        while Date() < deadline {
            if let data = readDataResponse(url),
               let raw = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let vaultName = raw["vaultName"] as? String,
               let secretConfig = raw["secretConfig"] as? [String: Any],
               let rawCycles = raw["recoveryPlan"] as? [[String: Any]] {
                let secrets = parseSecretDefinitions(secretConfig)
                let recoveryPlan = parseRecoveryCyclePlan(rawCycles, defaultSecret: secrets.first?.name ?? "test-secret")
                guard !secrets.isEmpty, !recoveryPlan.isEmpty else { return nil }
                return CoordinatorScenario(
                    vaultName: vaultName,
                    secrets: secrets,
                    recoveryPlan: recoveryPlan,
                    secretCreationPlan: raw["secretCreation"] as? [String: Any]
                )
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        return nil
    }

    private func secretDefinitions() -> [SecretDefinition] {
        let fallbackName = env("E2E_SECRET_NAME", defaultValue: "test-secret")
        let fallbackValue = env("E2E_SECRET_VALUE", defaultValue: "test-secret-value")
        guard let data = env("E2E_SECRET_CONFIG", defaultValue: "").data(using: .utf8),
              let raw = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            return [SecretDefinition(name: fallbackName, value: fallbackValue)]
        }

        let definitions = parseSecretDefinitions(raw)
        return definitions.isEmpty
            ? [SecretDefinition(name: fallbackName, value: fallbackValue)]
            : definitions
    }

    private func secretCreationPlan() -> [String: Any]? {
        let raw = env("E2E_SECRET_CREATION_PLAN", defaultValue: "")
        guard !raw.isEmpty, raw != "null", let data = raw.data(using: .utf8) else { return nil }
        return try? JSONSerialization.jsonObject(with: data) as? [String: Any]
    }

    private func configuredSecretNames(
        plan: [String: Any]?,
        stage: String,
        platform: String
    ) -> [String] {
        guard let plan,
              let stagePlan = plan[stage] as? [String: Any],
              let rawNames = stagePlan[platform] as? [Any]
        else { return [] }
        return rawNames.compactMap { $0 as? String }
    }

    private func configuredSecretNames(
        plan: [String: Any]?,
        stage: String
    ) -> [String] {
        guard let plan, let stagePlan = plan[stage] as? [String: Any] else { return [] }
        return stagePlan.values
            .compactMap { $0 as? [Any] }
            .flatMap { $0.compactMap { $0 as? String } }
    }

    private func secretsBeforeIosJoin(
        _ allSecrets: [SecretDefinition],
        plan: [String: Any]?
    ) -> [SecretDefinition] {
        guard plan != nil else { return allSecrets }
        let names = Set(
            configuredSecretNames(plan: plan, stage: "initial")
                + configuredSecretNames(plan: plan, stage: "afterWebJoin", platform: "web")
        )
        return allSecrets.filter { names.contains($0.name) }
    }

    private func parseSecretDefinitions(_ raw: [String: Any]) -> [SecretDefinition] {
        raw.keys.sorted().compactMap { key -> SecretDefinition? in
            guard let value = raw[key] as? [String: Any],
                  let name = value["name"] as? String,
                  let secretValue = value["value"] as? String else { return nil }
            return SecretDefinition(name: name, value: secretValue)
        }
    }

    private func recoveryCyclePlan() -> [RecoveryCycle] {
        let defaultSecret = secretDefinitions().first?.name ?? "test-secret"
        let rawPlan = env("E2E_RECOVERY_PLAN", defaultValue: "")
        if let data = rawPlan.data(using: .utf8),
           let rawCycles = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]],
           !rawCycles.isEmpty {
            return parseRecoveryCyclePlan(rawCycles, defaultSecret: defaultSecret)
        }

        let count = Int(env("E2E_RECOVERY_CYCLES", defaultValue: "18")) ?? 18
        let senderCycles = cycleSet(
            "E2E_IOS_SENDER_CYCLES",
            defaultValue: "7,8,9,10,11,12,13,14,15,16,17,18"
        )
        let approvalSteps = approvalStepSet(
            defaultValue: "1:1,1:2,2:1,2:2,9:2,10:1,11:2,12:1,15:2,16:1,17:2,18:1"
        )
        return (1...count).map { number in
            let senderSecrets = senderCycles.contains(number) ? ["ios": defaultSecret] : [:]
            let approvals: [RecoveryApproval] = (1...2).compactMap { step in
                guard approvalSteps.contains("\(number):\(step)") else { return nil }
                return RecoveryApproval(platform: "ios", secret: defaultSecret)
            }
            return RecoveryCycle(
                number: number,
                block: "",
                senderSecrets: senderSecrets,
                approvals: approvals,
                offlineReceiver: nil
            )
        }
    }

    private func parseRecoveryCyclePlan(
        _ rawCycles: [[String: Any]],
        defaultSecret: String
    ) -> [RecoveryCycle] {
        rawCycles.compactMap { rawCycle in
                guard let number = rawCycle["number"] as? Int else { return nil }
                var senderSecrets: [String: String] = [:]
                if let explicitSecrets = rawCycle["senderSecrets"] as? [String: Any] {
                    for (platform, secret) in explicitSecrets {
                        if let secret = secret as? String {
                            senderSecrets[platform] = secret
                        }
                    }
                }
                if let senders = rawCycle["senders"] as? [Any] {
                    for sender in senders {
                        if let sender = sender as? [String: Any],
                           let platform = sender["platform"] as? String,
                           let secret = sender["secret"] as? String {
                            senderSecrets[platform] = senderSecrets[platform] ?? secret
                        } else if let platform = sender as? String {
                            senderSecrets[platform] = senderSecrets[platform] ?? defaultSecret
                        }
                    }
                }
                var approvals: [RecoveryApproval] = []
                if let rawApprovals = rawCycle["approvals"] as? [Any] {
                    for approval in rawApprovals {
                        if let approval = approval as? [String: Any],
                           let platform = (approval["platform"] ?? approval["approver"]) as? String,
                           let secret = approval["secret"] as? String {
                            approvals.append(RecoveryApproval(platform: platform, secret: secret))
                        } else if let platform = approval as? String {
                            approvals.append(RecoveryApproval(platform: platform, secret: defaultSecret))
                        }
                    }
                } else {
                    if let first = rawCycle["firstApprover"] as? String {
                        approvals.append(RecoveryApproval(platform: first, secret: defaultSecret))
                    }
                    if let second = rawCycle["secondApprover"] as? String {
                        approvals.append(RecoveryApproval(platform: second, secret: defaultSecret))
                    }
                }
                let offlineReceiver = rawCycle["offlineReceiver"] as? String
                let cycle = RecoveryCycle(
                    number: number,
                    block: rawCycle["block"] as? String ?? "",
                    senderSecrets: senderSecrets,
                    approvals: approvals,
                    offlineReceiver: offlineReceiver
                )
                print(
                    "E2E: IOS_RECOVERY_PLAN_\(number) "
                        + "senders=\(senderSecrets) approvals="
                        + approvals.map { "\($0.platform):\($0.secret)" }.joined(separator: ",")
                )
                return cycle
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
        guard let data = readDataResponse(url) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func readDataResponse(_ url: URL) -> Data? {
        let response = ResponseBox()
        let semaphore = DispatchSemaphore(value: 0)
        URLSession.shared.dataTask(with: url) { data, _, _ in
            response.set(data)
            semaphore.signal()
        }.resume()
        _ = semaphore.wait(timeout: .now() + 2)
        return response.getData()
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
        // The add-secret dialog auto-focuses the name field. Tapping it again
        // can report `not hittable` while the keyboard is animating in; the
        // semantic existence check above is sufficient before typing.
        nameField.typeText(name)

        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.40)).tap()
        let valueField = app.descendants(matching: .any)["secret-value-input"]
        XCTAssertTrue(valueField.waitForExistence(timeout: 30), "secret-value-input was not visible")
        valueField.typeText(value)
    }

    private func createSecret(_ secret: SecretDefinition) {
        print("E2E: IOS_CREATING_SECRET_\(secret.name)")
        tap("add-secret-fab", timeout: 180)
        typeSecretNameAndValue(name: secret.name, value: secret.value)
        // The Compose dialog keeps the submit button behind the software
        // keyboard, so XCTest reports the accessibility button as existing
        // but not hittable. The established iOS E2E flow submits by tapping
        // the dialog's semantic button coordinate after both fields are set.
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.47)).tap()
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(
            identifier: "secret-row-\(secret.name)",
            timeout: 180,
            failureMessage: "iOS secret \(secret.name) was not visible after creation"
        )
        waitForHidden(identifier: "secret-name-input", timeout: 30)
        print("E2E: IOS_SECRET_ADDED_\(secret.name)")
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

    private func waitForSecretsAfterJoin(_ secrets: [SecretDefinition], timeout: TimeInterval) {
        for secret in secrets {
            waitForSecretAfterJoin(secret.name, timeout: timeout)
        }
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

private final class ResponseBox: @unchecked Sendable {
    private let lock = NSLock()
    private var value: String?
    private var data: Data?

    func set(_ value: String?) {
        lock.lock()
        defer { lock.unlock() }
        self.value = value
    }

    func get() -> String? {
        lock.lock()
        defer { lock.unlock() }
        return value
    }

    func set(_ data: Data?) {
        lock.lock()
        defer { lock.unlock() }
        self.data = data
    }

    func getData() -> Data? {
        lock.lock()
        defer { lock.unlock() }
        return data
    }
}

private extension XCUIElement {
    func tapIfExists() {
        if exists && isHittable {
            tap()
        }
    }
}
