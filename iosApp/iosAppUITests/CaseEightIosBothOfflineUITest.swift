import XCTest
import Foundation

@MainActor
final class CaseEightIosBothOfflineUITest: XCTestCase {
    private struct StepConfig: Decodable, Sendable {
        let coreServerUrl: String?
        let requireCoreRouting: Bool?
        let expectRequest: Bool?
        let role: String?
        let cycle: String?
        let step: String?
        let approvalPlatform: String?
        let sender: String?
        let secretName: String?
        let secretValue: String?
        let action: String?
        let expectedOutcome: String?
        let repeatApprove: Bool?
        let duplicateRecovery: Bool?
        let verifyReceiverAlert: Bool?
        let receiverRequestAlreadySent: Bool?
        let networkRole: String?
        let networkCycles: String?
        let expectedWebDeviceName: String?
        let expectedAndroidDeviceName: String?
        let expectedIosDeviceName: String?
        let joinTarget: String?
    }

    private struct Test50GeometryFixture: Codable, Sendable {
        let cycle: String
        let sourceStep: String
        let secretName: String
        let window: [Double]
        let privateRects: [String: [Double]]
    }

    nonisolated private static let stepConfigPath = "/tmp/metasecret-e2e-ios-step.json"
    private var app: XCUIApplication!
    private var test37DeviceTopologyDiagnosticsEmitted = false

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchEnvironment["METASECRET_UI_TEST_MODE"] = "true"
        let routing = Self.readStepConfig()
        let e2eServerUrl = (routing.coreServerUrl
            ?? ProcessInfo.processInfo.environment["E2E_CORE_SERVER_URL"] ?? "")
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if routing.requireCoreRouting == true {
            guard e2eServerUrl == "http://127.0.0.1:5202" else {
                throw NSError(domain: "MetaSecretTest50Routing", code: 1,
                    userInfo: [NSLocalizedDescriptionKey: "Test50 requires its verified local iOS Core proxy before app launch"])
            }
            print("E2E: IOS_TEST50_CORE_ROUTING_VERIFIED")
        }
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

    /// Test #40 joins the Web-owned Vault with run-specific readiness markers.
    func joinWebInitiatedVaultForTest40() throws {
        let vaultName = coordinatorVaultName(defaultValue: "test40@test.ru")

        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(vaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        tap("email-confirmation-join", timeout: 60)
        enterSimulatorPasscodeIfNeeded()
        print("E2E: IOS_TEST40_JOIN_REQUEST_SENT")

        for name in configuredSecretNames() {
            waitForVisible(identifier: "secret-row-\(name)", timeout: 180)
        }
        print("E2E: IOS_TEST40_JOIN_READY")
        print("E2E: IOS_TEST40_SECRETS_READY")
    }

    /// Test #43 joins the Web-owned Vault before creating Secret3.
    func joinWebInitiatedVaultForTest43() throws {
        let vaultName = coordinatorVaultName(defaultValue: "test43@test.ru")

        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(vaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        tap("email-confirmation-join", timeout: 60)
        enterSimulatorPasscodeIfNeeded()
        print("E2E: IOS_TEST43_JOIN_REQUEST_SENT")

        for name in configuredSecretNames() {
            waitForVisible(identifier: "secret-row-\(name)", timeout: 180)
        }
        print("E2E: IOS_TEST43_JOIN_READY")
        print("E2E: IOS_TEST43_SECRETS_READY")
    }

    /// Test #46's initial iOS Join is approved by Android B.
    func joinWebInitiatedVaultForTest46() throws {
        try joinWebInitiatedVaultForTest46(markerPhase: "INITIAL")
    }

    /// After its local removal, iOS C registers again and Web A approves it.
    func rejoinWebInitiatedVaultForTest46() throws {
        try joinWebInitiatedVaultForTest46(markerPhase: "REJOIN")
    }

    private func joinWebInitiatedVaultForTest46(markerPhase: String) throws {
        let vaultName = coordinatorVaultName(defaultValue: "test46@test.ru")

        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(vaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        tap("email-confirmation-join", timeout: 60)
        enterSimulatorPasscodeIfNeeded()
        print("E2E: IOS_TEST46_\(markerPhase)_JOIN_REQUEST_SENT")

        for name in configuredSecretNames() {
            waitForVisible(identifier: "secret-row-\(name)", timeout: 180)
        }
        print("E2E: IOS_TEST46_\(markerPhase)_JOIN_READY")
        print("E2E: IOS_TEST46_\(markerPhase)_SECRETS_READY")
    }

    /// Test #47 joins the Web/Android Vault before creating Secret3.
    func joinWebInitiatedVaultForTest47() throws {
        let vaultName = coordinatorVaultName(defaultValue: "test47@test.ru")

        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(vaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        tap("email-confirmation-join", timeout: 60)
        enterSimulatorPasscodeIfNeeded()
        print("E2E: IOS_TEST47_JOIN_REQUEST_SENT")

        for name in configuredSecretNames() {
            waitForVisible(identifier: "secret-row-\(name)", timeout: 180)
        }
        print("E2E: IOS_TEST47_JOIN_READY")
        print("E2E: IOS_TEST47_SECRETS_READY")
    }

    /// Test #38 joins the Android-owned Vault with run-specific markers so a
    /// stale Test #37/Test #23 process cannot satisfy the readiness barrier.
    func joinAndroidInitiatedVaultForTest38() throws {
        let vaultName = coordinatorVaultName(defaultValue: "test38@test.ru")

        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(vaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        tap("email-confirmation-join", timeout: 60)
        enterSimulatorPasscodeIfNeeded()
        print("E2E: IOS_TEST38_JOIN_REQUEST_SENT")

        for name in configuredSecretNames() {
            waitForVisible(identifier: "secret-row-\(name)", timeout: 180)
        }
        print("E2E: IOS_TEST38_JOIN_READY")
        print("E2E: IOS_TEST38_SECRETS_READY")
    }

    /// Test #41 joins the Android-owned Vault before iOS creates Secrets 2/3.
    func joinAndroidInitiatedVaultForTest41() throws {
        let vaultName = coordinatorVaultName(defaultValue: "test41@test.ru")

        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(vaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        tap("email-confirmation-join", timeout: 60)
        enterSimulatorPasscodeIfNeeded()
        print("E2E: IOS_TEST41_JOIN_REQUEST_SENT")

        for name in configuredSecretNames() {
            waitForVisible(identifier: "secret-row-\(name)", timeout: 180)
        }
        print("E2E: IOS_TEST41_JOIN_READY")
        print("E2E: IOS_TEST41_SECRETS_READY")
    }

    /// Test #44 joins the Android-owned Vault before creating Secret2.
    func joinAndroidInitiatedVaultForTest44() throws {
        let vaultName = coordinatorVaultName(defaultValue: "test44@test.ru")

        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(vaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        tap("email-confirmation-join", timeout: 60)
        enterSimulatorPasscodeIfNeeded()
        print("E2E: IOS_TEST44_JOIN_REQUEST_SENT")

        for name in configuredSecretNames() {
            waitForVisible(identifier: "secret-row-\(name)", timeout: 180)
        }
        print("E2E: IOS_TEST44_JOIN_READY")
        print("E2E: IOS_TEST44_SECRETS_READY")
    }

    /// Test #48 joins the Android-owned Vault before creating Secret2.
    func joinAndroidInitiatedVaultForTest48() throws {
        let vaultName = coordinatorVaultName(defaultValue: "test48@test.ru")

        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(vaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        tap("email-confirmation-join", timeout: 60)
        enterSimulatorPasscodeIfNeeded()
        print("E2E: IOS_TEST48_JOIN_REQUEST_SENT")

        for name in configuredSecretNames() {
            waitForVisible(identifier: "secret-row-\(name)", timeout: 180)
        }
        print("E2E: IOS_TEST48_JOIN_READY")
        print("E2E: IOS_TEST48_SECRETS_READY")
    }

    /// Test #41 leaves iOS passive while Android requests its removal and Web
    /// approves it. Completion requires a full reset with no stale Vault UI.
    func observeOwnRemovalForTest41() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #41 requires exact Web, Android, and iOS device names")
            return
        }
        assertTest37DeviceLabelMatchingContract()

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(
                app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists,
                "iOS had a stale recovery badge before Test #41 removal"
            )
            XCTAssertFalse(
                app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists,
                "iOS had a stale recovery action before Test #41 removal"
            )
        }

        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let recoveryAlert = app.descendants(matching: .any)["alert-recovery-request"]
        let recoveryProcessing = app.descendants(matching: .any)["alert-recovery-request-processing"]
        let joinAlert = app.descendants(matching: .any)["alert-join-request"]
        let joinProcessing = app.descendants(matching: .any)["alert-join-request-processing"]
        test37DeviceTopologyDiagnosticsEmitted = false
        let cleanBoundary = {
            guard
                deviceRows.count == 3,
                let webDeviceId = self.exactDeviceRowIdentifier(
                    name: webDeviceName,
                    status: "Member"
                ),
                let androidDeviceId = self.exactDeviceRowIdentifier(
                    name: androidDeviceName,
                    status: "Member"
                ),
                let iosDeviceId = self.exactDeviceRowIdentifier(
                    name: iosDeviceName,
                    status: "Current"
                ),
                Set([webDeviceId, androidDeviceId, iosDeviceId]).count == 3
            else { return false }
            return self.test37RemoveActionQuery(deviceId: iosDeviceId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !recoveryAlert.exists
                && !recoveryProcessing.exists
                && !joinAlert.exists
                && !joinProcessing.exists
        }
        if !cleanBoundary() && deviceRows.count == 3 {
            emitTest37DeviceTopologyDiagnosticsOnce(deviceRows: deviceRows)
        }
        let ready = NSPredicate { _, _ in
            let isReady = cleanBoundary()
            if !isReady && deviceRows.count == 3 {
                self.emitTest37DeviceTopologyDiagnosticsOnce(deviceRows: deviceRows)
            }
            return isReady
        }
        let readyExpectation = XCTNSPredicateExpectation(predicate: ready, object: nil)
        let readyResult = XCTWaiter.wait(for: [readyExpectation], timeout: 180)
        if readyResult != .completed {
            emitTest37DeviceTopologyDiagnosticsOnce(deviceRows: deviceRows)
        }
        XCTAssertEqual(
            readyResult,
            .completed,
            "iOS did not reach a clean three-member Test #41 removal boundary"
        )
        XCTAssertEqual(deviceRows.count, 3, "iOS expected exactly three member rows")
        guard
            let webDeviceId = exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
            let androidDeviceId = exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
            let iosDeviceId = exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
        else {
            XCTFail("iOS could not retain exact Test #41 device-row identifiers")
            return
        }
        XCTAssertEqual(
            Set([webDeviceId, androidDeviceId, iosDeviceId]).count,
            3,
            "iOS Test #41 device rows did not resolve to three unique exact identifiers"
        )
        XCTAssertEqual(
            test37RemoveActionQuery(deviceId: iosDeviceId).count,
            0,
            "iOS Current row exposed an invalid self-remove action"
        )
        print("E2E: IOS_TEST41_REMOVAL_TARGET_READY")

        let manualSignIn = app.descendants(matching: .any)["signin-email-manual"]
        let secretsTab = app.descendants(matching: .any)["tab-secrets"]
        let devicesTab = app.descendants(matching: .any)["tab-devices"]
        let removed = NSPredicate { _, _ in
            manualSignIn.exists
                && manualSignIn.isHittable
                && !secretsTab.exists
                && !devicesTab.exists
                && deviceRows.count == 0
                && secretNames.allSatisfy {
                    !self.app.descendants(matching: .any)["secret-row-\($0)"].exists
                }
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !recoveryAlert.exists
                && !recoveryProcessing.exists
                && !joinAlert.exists
                && !joinProcessing.exists
                && !self.app.descendants(matching: .any)["remove-device-\(webDeviceId)"].exists
                && !self.app.descendants(matching: .any)["remove-device-\(androidDeviceId)"].exists
                && !self.app.descendants(matching: .any)["remove-device-\(iosDeviceId)"].exists
        }
        let removedExpectation = XCTNSPredicateExpectation(predicate: removed, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [removedExpectation], timeout: 180),
            .completed,
            "iOS did not reset to authentication with the Test #41 Vault UI removed"
        )
        XCTAssertTrue(manualSignIn.exists && manualSignIn.isHittable)
        XCTAssertFalse(secretsTab.exists || devicesTab.exists)
        XCTAssertTrue(secretNames.allSatisfy {
            !app.descendants(matching: .any)["secret-row-\($0)"].exists
        })
        XCTAssertFalse(app.descendants(matching: .any)["remove-device-\(webDeviceId)"].exists)
        XCTAssertFalse(app.descendants(matching: .any)["remove-device-\(androidDeviceId)"].exists)
        XCTAssertFalse(app.descendants(matching: .any)["remove-device-\(iosDeviceId)"].exists)
        print("E2E: IOS_TEST41_REMOVED_RESET")
    }

    /// Test #45 leaves iOS passive while Android requests its removal and Web
    /// approves it. Completion requires a full reset with no stale Vault UI.
    func observeOwnRemovalForTest45() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #45 requires exact Web, Android, and iOS device names")
            return
        }
        assertTest37DeviceLabelMatchingContract()

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(
                app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists,
                "iOS had a stale recovery badge before Test #45 removal"
            )
            XCTAssertFalse(
                app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists,
                "iOS had a stale recovery action before Test #45 removal"
            )
        }

        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let recoveryAlert = app.descendants(matching: .any)["alert-recovery-request"]
        let recoveryProcessing = app.descendants(matching: .any)["alert-recovery-request-processing"]
        let joinAlert = app.descendants(matching: .any)["alert-join-request"]
        let joinProcessing = app.descendants(matching: .any)["alert-join-request-processing"]
        test37DeviceTopologyDiagnosticsEmitted = false
        let cleanBoundary = {
            guard
                deviceRows.count == 3,
                let webDeviceId = self.exactDeviceRowIdentifier(
                    name: webDeviceName,
                    status: "Member"
                ),
                let androidDeviceId = self.exactDeviceRowIdentifier(
                    name: androidDeviceName,
                    status: "Member"
                ),
                let iosDeviceId = self.exactDeviceRowIdentifier(
                    name: iosDeviceName,
                    status: "Current"
                ),
                Set([webDeviceId, androidDeviceId, iosDeviceId]).count == 3
            else { return false }
            return self.test37RemoveActionQuery(deviceId: iosDeviceId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !recoveryAlert.exists
                && !recoveryProcessing.exists
                && !joinAlert.exists
                && !joinProcessing.exists
        }
        if !cleanBoundary() && deviceRows.count == 3 {
            emitTest37DeviceTopologyDiagnosticsOnce(deviceRows: deviceRows)
        }
        let ready = NSPredicate { _, _ in
            let isReady = cleanBoundary()
            if !isReady && deviceRows.count == 3 {
                self.emitTest37DeviceTopologyDiagnosticsOnce(deviceRows: deviceRows)
            }
            return isReady
        }
        let readyExpectation = XCTNSPredicateExpectation(predicate: ready, object: nil)
        let readyResult = XCTWaiter.wait(for: [readyExpectation], timeout: 180)
        if readyResult != .completed {
            emitTest37DeviceTopologyDiagnosticsOnce(deviceRows: deviceRows)
        }
        XCTAssertEqual(
            readyResult,
            .completed,
            "iOS did not reach a clean three-member Test #45 removal boundary"
        )
        XCTAssertEqual(deviceRows.count, 3, "iOS expected exactly three member rows")
        guard
            let webDeviceId = exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
            let androidDeviceId = exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
            let iosDeviceId = exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
        else {
            XCTFail("iOS could not retain exact Test #45 device-row identifiers")
            return
        }
        XCTAssertEqual(
            Set([webDeviceId, androidDeviceId, iosDeviceId]).count,
            3,
            "iOS Test #45 device rows did not resolve to three unique exact identifiers"
        )
        XCTAssertEqual(
            test37RemoveActionQuery(deviceId: iosDeviceId).count,
            0,
            "iOS Current row exposed an invalid self-remove action"
        )
        print("E2E: IOS_TEST45_REMOVAL_TARGET_READY")

        let manualSignIn = app.descendants(matching: .any)["signin-email-manual"]
        let secretsTab = app.descendants(matching: .any)["tab-secrets"]
        let devicesTab = app.descendants(matching: .any)["tab-devices"]
        let removed = NSPredicate { _, _ in
            manualSignIn.exists
                && manualSignIn.isHittable
                && !secretsTab.exists
                && !devicesTab.exists
                && deviceRows.count == 0
                && secretNames.allSatisfy {
                    !self.app.descendants(matching: .any)["secret-row-\($0)"].exists
                }
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !recoveryAlert.exists
                && !recoveryProcessing.exists
                && !joinAlert.exists
                && !joinProcessing.exists
                && !self.app.descendants(matching: .any)["remove-device-\(webDeviceId)"].exists
                && !self.app.descendants(matching: .any)["remove-device-\(androidDeviceId)"].exists
                && !self.app.descendants(matching: .any)["remove-device-\(iosDeviceId)"].exists
        }
        let removedExpectation = XCTNSPredicateExpectation(predicate: removed, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [removedExpectation], timeout: 180),
            .completed,
            "iOS did not reset to authentication with the Test #45 Vault UI removed"
        )
        XCTAssertTrue(manualSignIn.exists && manualSignIn.isHittable)
        XCTAssertFalse(secretsTab.exists || devicesTab.exists)
        XCTAssertTrue(secretNames.allSatisfy {
            !app.descendants(matching: .any)["secret-row-\($0)"].exists
        })
        XCTAssertFalse(app.descendants(matching: .any)["remove-device-\(webDeviceId)"].exists)
        XCTAssertFalse(app.descendants(matching: .any)["remove-device-\(androidDeviceId)"].exists)
        XCTAssertFalse(app.descendants(matching: .any)["remove-device-\(iosDeviceId)"].exists)
        print("E2E: IOS_TEST45_REMOVED_RESET")
    }

    /// Test #37 leaves iOS passive while Web requests its removal and Android
    /// approves it. The marker is emitted only from a clean three-member UI;
    /// completion requires the visible authentication screen and no Vault UI.
    func observeOwnRemovalForTest37() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #37 requires exact Web, Android, and iOS device names")
            return
        }
        assertTest37DeviceLabelMatchingContract()

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(
                app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists,
                "iOS had a stale recovery badge before Test #37 removal"
            )
            XCTAssertFalse(
                app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists,
                "iOS had a stale recovery action before Test #37 removal"
            )
        }

        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let recoveryAlert = app.descendants(matching: .any)["alert-recovery-request"]
        let recoveryProcessing = app.descendants(matching: .any)["alert-recovery-request-processing"]
        let joinAlert = app.descendants(matching: .any)["alert-join-request"]
        let joinProcessing = app.descendants(matching: .any)["alert-join-request-processing"]
        test37DeviceTopologyDiagnosticsEmitted = false
        let cleanBoundary = {
            guard
                deviceRows.count == 3,
                let webDeviceId = self.exactDeviceRowIdentifier(
                    name: webDeviceName,
                    status: "Member"
                ),
                let androidDeviceId = self.exactDeviceRowIdentifier(
                    name: androidDeviceName,
                    status: "Member"
                ),
                let iosDeviceId = self.exactDeviceRowIdentifier(
                    name: iosDeviceName,
                    status: "Current"
                ),
                Set([webDeviceId, androidDeviceId, iosDeviceId]).count == 3
            else { return false }
            return self.test37RemoveActionQuery(deviceId: iosDeviceId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !recoveryAlert.exists
                && !recoveryProcessing.exists
                && !joinAlert.exists
                && !joinProcessing.exists
        }
        if !cleanBoundary() && deviceRows.count == 3 {
            emitTest37DeviceTopologyDiagnosticsOnce(deviceRows: deviceRows)
        }
        let ready = NSPredicate { _, _ in
            let isReady = cleanBoundary()
            if !isReady && deviceRows.count == 3 {
                self.emitTest37DeviceTopologyDiagnosticsOnce(deviceRows: deviceRows)
            }
            return isReady
        }
        let readyExpectation = XCTNSPredicateExpectation(predicate: ready, object: nil)
        let readyResult = XCTWaiter.wait(for: [readyExpectation], timeout: 180)
        if readyResult != .completed {
            emitTest37DeviceTopologyDiagnosticsOnce(deviceRows: deviceRows)
        }
        XCTAssertEqual(
            readyResult,
            .completed,
            "iOS did not reach a clean three-member Test #37 removal boundary"
        )
        XCTAssertEqual(deviceRows.count, 3, "iOS expected exactly three member rows")
        guard
            let webDeviceId = exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
            let androidDeviceId = exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
            let iosDeviceId = exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
        else {
            XCTFail("iOS could not retain exact Test #37 device-row identifiers")
            return
        }
        XCTAssertEqual(
            Set([webDeviceId, androidDeviceId, iosDeviceId]).count,
            3,
            "iOS Test #37 device rows did not resolve to three unique exact identifiers"
        )
        XCTAssertEqual(
            test37RemoveActionQuery(deviceId: iosDeviceId).count,
            0,
            "iOS Current row exposed an invalid self-remove action"
        )
        print("E2E: IOS_TEST37_REMOVAL_TARGET_READY")

        let manualSignIn = app.descendants(matching: .any)["signin-email-manual"]
        let secretsTab = app.descendants(matching: .any)["tab-secrets"]
        let devicesTab = app.descendants(matching: .any)["tab-devices"]
        let removed = NSPredicate { _, _ in
            manualSignIn.exists
                && manualSignIn.isHittable
                && !secretsTab.exists
                && !devicesTab.exists
                && deviceRows.count == 0
                && secretNames.allSatisfy {
                    !self.app.descendants(matching: .any)["secret-row-\($0)"].exists
                }
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !recoveryAlert.exists
                && !recoveryProcessing.exists
                && !joinAlert.exists
                && !joinProcessing.exists
                && !recoveryAlert.exists
                && !recoveryProcessing.exists
                && !joinAlert.exists
                && !joinProcessing.exists
                && !self.app.descendants(matching: .any)["remove-device-\(webDeviceId)"].exists
                && !self.app.descendants(matching: .any)["remove-device-\(androidDeviceId)"].exists
                && !self.app.descendants(matching: .any)["remove-device-\(iosDeviceId)"].exists
        }
        let removedExpectation = XCTNSPredicateExpectation(predicate: removed, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [removedExpectation], timeout: 180),
            .completed,
            "iOS did not reset to authentication with the Vault UI removed"
        )
        XCTAssertTrue(manualSignIn.exists && manualSignIn.isHittable)
        XCTAssertFalse(secretsTab.exists || devicesTab.exists)
        XCTAssertTrue(secretNames.allSatisfy {
            !app.descendants(matching: .any)["secret-row-\($0)"].exists
        })
        XCTAssertFalse(app.descendants(matching: .any)["remove-device-\(webDeviceId)"].exists)
        XCTAssertFalse(app.descendants(matching: .any)["remove-device-\(androidDeviceId)"].exists)
        XCTAssertFalse(app.descendants(matching: .any)["remove-device-\(iosDeviceId)"].exists)
        print("E2E: IOS_TEST37_REMOVED_RESET")
    }

    /// iOS is the sole approver of Web's removal in Test #38. Completion is
    /// emitted only after the exact two-member topology and all Secrets are
    /// observable, which is also the reverse-resharing boundary.
    func approveWebRemovalForTest38() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #38 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists)
        }

        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let recoveryAlert = app.descendants(matching: .any)["alert-recovery-request"]
        let recoveryProcessing = app.descendants(matching: .any)["alert-recovery-request-processing"]
        let joinAlert = app.descendants(matching: .any)["alert-join-request"]
        let joinProcessing = app.descendants(matching: .any)["alert-join-request-processing"]
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current"),
                Set([webId, androidId, iosId]).count == 3
            else { return false }
            return self.test37RemoveActionQuery(deviceId: webId).count == 1
                && self.test37RemoveActionQuery(deviceId: androidId).count == 1
                && self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !recoveryAlert.exists
                && !recoveryProcessing.exists
                && !joinAlert.exists
                && !joinProcessing.exists
        }
        let cleanExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [cleanExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #38's clean three-member approval boundary"
        )
        print("E2E: IOS_TEST38_REMOVAL_RECEIVER_READY")

        waitForVisible(identifier: "pending-removal-device-row", timeout: 180)
        tap("pending-removal-device-row")
        waitForVisible(identifier: "remove-device-dialog", timeout: 30)
        let exactLocalizedRemovalTitles = [
            "Remove device \(webDeviceName)?",
            "Удалить устройство \(webDeviceName)?",
        ]
        let visibleExactRemovalTitles = exactLocalizedRemovalTitles.filter {
            exactVisibleText($0, belongsTo: removalDialog)
        }
        XCTAssertEqual(
            visibleExactRemovalTitles.count,
            1,
            "Test #38 removal dialog must expose one exact localized composite Web title"
        )
        print("E2E: IOS_TEST38_REMOVAL_REQUEST_VISIBLE")

        waitForApproval(platform: "ios-remove-web-approve", cycle: "1")
        tap("remove-device-dialog-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "remove-device-dialog", timeout: 120)
        waitForHidden(identifier: "pending-removal-device-row", timeout: 180)
        let resharedBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 2,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") == nil,
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: androidId).count == 1
                && self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !recoveryAlert.exists
                && !recoveryProcessing.exists
        }
        let resharedExpectation = XCTNSPredicateExpectation(predicate: resharedBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [resharedExpectation], timeout: 180),
            .completed,
            "iOS did not observe the two-member Test #38 resharing boundary"
        )
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        print("E2E: IOS_TEST38_WEB_REMOVAL_APPROVED")
    }

    /// iOS C is Test #46's sole approver and reverse resharer for Web A's removal.
    func approveWebRemovalForTest46() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #46 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists)
        }

        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let recoveryAlert = app.descendants(matching: .any)["alert-recovery-request"]
        let recoveryProcessing = app.descendants(matching: .any)["alert-recovery-request-processing"]
        let joinAlert = app.descendants(matching: .any)["alert-join-request"]
        let joinProcessing = app.descendants(matching: .any)["alert-join-request-processing"]
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current"),
                Set([webId, androidId, iosId]).count == 3
            else { return false }
            return self.test37RemoveActionQuery(deviceId: webId).count == 1
                && self.test37RemoveActionQuery(deviceId: androidId).count == 1
                && self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !recoveryAlert.exists
                && !recoveryProcessing.exists
                && !joinAlert.exists
                && !joinProcessing.exists
        }
        let cleanExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [cleanExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #46's clean three-member approval boundary"
        )
        print("E2E: IOS_TEST46_WEB_REMOVAL_RECEIVER_READY")

        waitForVisible(identifier: "pending-removal-device-row", timeout: 180)
        tap("pending-removal-device-row")
        waitForVisible(identifier: "remove-device-dialog", timeout: 30)
        let exactLocalizedRemovalTitles = [
            "Remove device \(webDeviceName)?",
            "Удалить устройство \(webDeviceName)?",
        ]
        let visibleExactRemovalTitles = exactLocalizedRemovalTitles.filter {
            exactVisibleText($0, belongsTo: removalDialog)
        }
        XCTAssertEqual(
            visibleExactRemovalTitles.count,
            1,
            "Test #46 removal dialog must expose one exact localized composite Web title"
        )
        print("E2E: IOS_TEST46_WEB_REMOVAL_REQUEST_VISIBLE")

        waitForApproval(platform: "ios-remove-web-approve-test46", cycle: "1")
        tap("remove-device-dialog-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "remove-device-dialog", timeout: 120)
        waitForHidden(identifier: "pending-removal-device-row", timeout: 180)
        let resharedBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 2,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") == nil,
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: androidId).count == 1
                && self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !recoveryAlert.exists
                && !recoveryProcessing.exists
        }
        let resharedExpectation = XCTNSPredicateExpectation(predicate: resharedBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [resharedExpectation], timeout: 180),
            .completed,
            "iOS did not observe Test #46's two-member reverse-resharing boundary"
        )
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        print("E2E: IOS_TEST46_WEB_REMOVAL_APPROVED")
    }

    /// Test #38 leaves iOS passive during Android's local two-to-one removal.
    func observeOwnRemovalForTest38() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #38 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 2,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") == nil,
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: androidId).count == 1
                && self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !self.app.descendants(matching: .any)["pending-device-row"].exists
                && !self.app.descendants(matching: .any)["pending-removal-device-row"].exists
                && !self.app.descendants(matching: .any)["remove-device-dialog"].exists
                && !self.app.descendants(matching: .any)["alert-recovery-request"].exists
                && !self.app.descendants(matching: .any)["alert-recovery-request-processing"].exists
        }
        let cleanExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [cleanExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #38's clean two-member target boundary"
        )
        print("E2E: IOS_TEST38_REMOVAL_TARGET_READY")

        let manualSignIn = app.descendants(matching: .any)["signin-email-manual"]
        let secretsTab = app.descendants(matching: .any)["tab-secrets"]
        let devicesTab = app.descendants(matching: .any)["tab-devices"]
        let removed = NSPredicate { _, _ in
            manualSignIn.exists
                && manualSignIn.isHittable
                && !secretsTab.exists
                && !devicesTab.exists
                && deviceRows.count == 0
                && secretNames.allSatisfy {
                    !self.app.descendants(matching: .any)["secret-row-\($0)"].exists
                }
                && !self.app.descendants(matching: .any)["pending-device-row"].exists
                && !self.app.descendants(matching: .any)["pending-removal-device-row"].exists
                && !self.app.descendants(matching: .any)["remove-device-dialog"].exists
                && !self.app.descendants(matching: .any)["alert-recovery-request"].exists
                && !self.app.descendants(matching: .any)["alert-recovery-request-processing"].exists
                && !self.app.descendants(matching: .any)["alert-join-request"].exists
                && !self.app.descendants(matching: .any)["alert-join-request-processing"].exists
        }
        let removedExpectation = XCTNSPredicateExpectation(predicate: removed, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [removedExpectation], timeout: 180),
            .completed,
            "iOS did not reset after Test #38's local removal"
        )
        print("E2E: IOS_TEST38_REMOVED_RESET")
    }

    /// Test #42 leaves iOS passive during Android's local two-to-one removal.
    func observeOwnRemovalForTest42() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #42 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists)
        }
        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 2,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") == nil,
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: androidId).count == 1
                && self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !self.app.descendants(matching: .any)["pending-device-row"].exists
                && !self.app.descendants(matching: .any)["pending-removal-device-row"].exists
                && !self.app.descendants(matching: .any)["remove-device-dialog"].exists
                && !self.app.descendants(matching: .any)["alert-recovery-request"].exists
                && !self.app.descendants(matching: .any)["alert-recovery-request-processing"].exists
        }
        let cleanExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [cleanExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #42's clean two-member target boundary"
        )
        print("E2E: IOS_TEST42_REMOVAL_TARGET_READY")

        let manualSignIn = app.descendants(matching: .any)["signin-email-manual"]
        let secretsTab = app.descendants(matching: .any)["tab-secrets"]
        let devicesTab = app.descendants(matching: .any)["tab-devices"]
        let removed = NSPredicate { _, _ in
            manualSignIn.exists
                && manualSignIn.isHittable
                && !secretsTab.exists
                && !devicesTab.exists
                && deviceRows.count == 0
                && secretNames.allSatisfy {
                    !self.app.descendants(matching: .any)["secret-row-\($0)"].exists
                }
                && !self.app.descendants(matching: .any)["pending-device-row"].exists
                && !self.app.descendants(matching: .any)["pending-removal-device-row"].exists
                && !self.app.descendants(matching: .any)["remove-device-dialog"].exists
                && !self.app.descendants(matching: .any)["alert-recovery-request"].exists
                && !self.app.descendants(matching: .any)["alert-recovery-request-processing"].exists
                && !self.app.descendants(matching: .any)["alert-join-request"].exists
                && !self.app.descendants(matching: .any)["alert-join-request-processing"].exists
        }
        let removedExpectation = XCTNSPredicateExpectation(predicate: removed, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [removedExpectation], timeout: 180),
            .completed,
            "iOS did not reset after Test #42's local removal"
        )
        print("E2E: IOS_TEST42_REMOVED_RESET")
    }

    /// Test #43 leaves iOS passive during Android's local two-to-one removal.
    func observeOwnRemovalForTest43() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #43 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists)
        }
        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 2,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") == nil,
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: androidId).count == 1
                && self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !self.app.descendants(matching: .any)["pending-device-row"].exists
                && !self.app.descendants(matching: .any)["pending-removal-device-row"].exists
                && !self.app.descendants(matching: .any)["remove-device-dialog"].exists
                && !self.app.descendants(matching: .any)["alert-recovery-request"].exists
                && !self.app.descendants(matching: .any)["alert-recovery-request-processing"].exists
        }
        let cleanExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [cleanExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #43's clean two-member target boundary"
        )
        print("E2E: IOS_TEST43_REMOVAL_TARGET_READY")

        let manualSignIn = app.descendants(matching: .any)["signin-email-manual"]
        let secretsTab = app.descendants(matching: .any)["tab-secrets"]
        let devicesTab = app.descendants(matching: .any)["tab-devices"]
        let removed = NSPredicate { _, _ in
            manualSignIn.exists
                && manualSignIn.isHittable
                && !secretsTab.exists
                && !devicesTab.exists
                && deviceRows.count == 0
                && secretNames.allSatisfy {
                    !self.app.descendants(matching: .any)["secret-row-\($0)"].exists
                }
                && !self.app.descendants(matching: .any)["pending-device-row"].exists
                && !self.app.descendants(matching: .any)["pending-removal-device-row"].exists
                && !self.app.descendants(matching: .any)["remove-device-dialog"].exists
                && !self.app.descendants(matching: .any)["alert-recovery-request"].exists
                && !self.app.descendants(matching: .any)["alert-recovery-request-processing"].exists
                && !self.app.descendants(matching: .any)["alert-join-request"].exists
                && !self.app.descendants(matching: .any)["alert-join-request-processing"].exists
        }
        let removedExpectation = XCTNSPredicateExpectation(predicate: removed, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [removedExpectation], timeout: 180),
            .completed,
            "iOS did not reset after Test #43's local removal"
        )
        print("E2E: IOS_TEST43_REMOVED_RESET")
    }

    /// Test #46 leaves iOS passive during Android's local two-to-one removal.
    func observeOwnRemovalForTest46() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #46 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists)
        }
        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let recoveryAlert = app.descendants(matching: .any)["alert-recovery-request"]
        let recoveryProcessing = app.descendants(matching: .any)["alert-recovery-request-processing"]
        let joinAlert = app.descendants(matching: .any)["alert-join-request"]
        let joinProcessing = app.descendants(matching: .any)["alert-join-request-processing"]
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 2,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") == nil,
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: androidId).count == 1
                && self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !recoveryAlert.exists
                && !recoveryProcessing.exists
                && !joinAlert.exists
                && !joinProcessing.exists
        }
        let cleanExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [cleanExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #46's clean two-member target boundary"
        )
        print("E2E: IOS_TEST46_REMOVAL_TARGET_READY")

        let manualSignIn = app.descendants(matching: .any)["signin-email-manual"]
        let secretsTab = app.descendants(matching: .any)["tab-secrets"]
        let devicesTab = app.descendants(matching: .any)["tab-devices"]
        let removed = NSPredicate { _, _ in
            manualSignIn.exists
                && manualSignIn.isHittable
                && !secretsTab.exists
                && !devicesTab.exists
                && deviceRows.count == 0
                && secretNames.allSatisfy {
                    !self.app.descendants(matching: .any)["secret-row-\($0)"].exists
                }
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !recoveryAlert.exists
                && !recoveryProcessing.exists
                && !joinAlert.exists
                && !joinProcessing.exists
        }
        let removedExpectation = XCTNSPredicateExpectation(predicate: removed, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [removedExpectation], timeout: 180),
            .completed,
            "iOS did not reset after Test #46's local removal"
        )
        print("E2E: IOS_TEST46_REMOVED_RESET")
    }

    /// iOS is Test #40's sole approver and reverse resharer for Android's
    /// removal. Completion requires the exact Web/iOS topology and all Secrets.
    func approveAndroidRemovalForTest40() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #40 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists)
        }

        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let recoveryAlert = app.descendants(matching: .any)["alert-recovery-request"]
        let recoveryProcessing = app.descendants(matching: .any)["alert-recovery-request-processing"]
        let joinAlert = app.descendants(matching: .any)["alert-join-request"]
        let joinProcessing = app.descendants(matching: .any)["alert-join-request-processing"]
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current"),
                Set([webId, androidId, iosId]).count == 3
            else { return false }
            return self.test37RemoveActionQuery(deviceId: webId).count == 1
                && self.test37RemoveActionQuery(deviceId: androidId).count == 1
                && self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !recoveryAlert.exists
                && !recoveryProcessing.exists
                && !joinAlert.exists
                && !joinProcessing.exists
        }
        let cleanExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [cleanExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #40's clean three-member approval boundary"
        )
        print("E2E: IOS_TEST40_REMOVAL_RECEIVER_READY")

        waitForVisible(identifier: "pending-removal-device-row", timeout: 180)
        tap("pending-removal-device-row")
        waitForVisible(identifier: "remove-device-dialog", timeout: 30)
        let exactLocalizedRemovalTitles = [
            "Remove device \(androidDeviceName)?",
            "Удалить устройство \(androidDeviceName)?",
        ]
        let visibleExactRemovalTitles = exactLocalizedRemovalTitles.filter {
            exactVisibleText($0, belongsTo: removalDialog)
        }
        XCTAssertEqual(
            visibleExactRemovalTitles.count,
            1,
            "Test #40 removal dialog must expose one exact localized composite Android title"
        )
        print("E2E: IOS_TEST40_REMOVAL_REQUEST_VISIBLE")

        waitForApproval(platform: "ios-remove-android-approve-test40", cycle: "1")
        tap("remove-device-dialog-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "remove-device-dialog", timeout: 120)
        waitForHidden(identifier: "pending-removal-device-row", timeout: 180)
        let resharedBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 2,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") == nil,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: webId).count == 1
                && self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !recoveryAlert.exists
                && !recoveryProcessing.exists
        }
        let resharedExpectation = XCTNSPredicateExpectation(predicate: resharedBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [resharedExpectation], timeout: 180),
            .completed,
            "iOS did not observe Test #40's two-member reverse-resharing boundary"
        )
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        print("E2E: IOS_TEST40_ANDROID_REMOVAL_APPROVED")
    }

    /// Test #15 approves a membership change while a Recover claim is pending.
    /// The membership update is expected to terminalize that old claim.
    func approveRemovalDuringRecoveryForTest15() throws {
        let config = stepConfig()
        guard let targetDeviceName = config.joinTarget, !targetDeviceName.isEmpty else {
            XCTFail("Test #15 requires the exact device name being removed")
            return
        }
        let cycle = config.cycle ?? "1"

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        waitForVisible(identifier: "secret-row-\(config.secretName ?? "test-secret")", timeout: 180)
        tap("tab-devices")

        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let targetRowReady = NSPredicate { _, _ in
            deviceRows.count == 3
                && self.exactDeviceRowIdentifier(name: targetDeviceName, status: "Member") != nil
        }
        let targetRowExpectation = XCTNSPredicateExpectation(predicate: targetRowReady, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [targetRowExpectation], timeout: 120),
            .completed,
            "Test #15 did not render the exact three-member removal target: \(targetDeviceName)"
        )
        let targetId = exactDeviceRowIdentifier(name: targetDeviceName, status: "Member")
        XCTAssertNotNil(targetId, "Test #15 removal target is not one exact current member")
        guard let targetId else { return }
        let targetRow = app.descendants(matching: .any)["device-row-\(targetId)"]
        XCTAssertTrue(targetRow.waitForExistence(timeout: 30))
        XCTAssertEqual(
            app.descendants(matching: .any).matching(
                NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
            ).count,
            3,
            "Test #15 removal starts from exactly three current members"
        )
        print("E2E: IOS_TEST15_REMOVAL_APPROVER_READY")

        waitForVisible(identifier: "pending-removal-device-row", timeout: 180)
        tap("pending-removal-device-row")
        waitForVisible(identifier: "remove-device-dialog", timeout: 30)
        let exactTitles = [
            "Remove device \(targetDeviceName)?",
            "Удалить устройство \(targetDeviceName)?",
        ]
        XCTAssertEqual(
            exactTitles.filter { exactVisibleText($0, belongsTo: app.descendants(matching: .any)["remove-device-dialog"]) }.count,
            1,
            "Test #15 removal dialog must name the exact target"
        )
        print("E2E: IOS_TEST15_REMOVAL_REQUEST_VISIBLE")
        waitForApproval(platform: "test15-remove-approve", cycle: cycle)
        tap("remove-device-dialog-accept")
        waitForHidden(identifier: "remove-device-dialog", timeout: 120)
        let targetRemoved = NSPredicate { _, _ in
            !self.app.descendants(matching: .any)["device-row-\(targetId)"].exists
                && self.app.descendants(matching: .any).matching(
                    NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
                ).count == 2
        }
        let removedWait = XCTNSPredicateExpectation(predicate: targetRemoved, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [removedWait], timeout: 180), .completed)
        tap("tab-secrets")
        let secretName = config.secretName ?? "test-secret"
        waitForVisible(identifier: "secret-row-\(secretName)", timeout: 60)
        XCTAssertFalse(app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists)
        XCTAssertFalse(app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists)
        print("E2E: IOS_TEST15_REMOVAL_APPROVED_RECOVERY_CLEARED")
        waitForApproval(platform: "test15-cli-member-accepted", cycle: cycle)
        tap("tab-devices")
        tap("tab-secrets")
        waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        print("E2E: IOS_TEST15_CLI_STATE_REFRESHED")
        waitForApproval(platform: "test15-cli-redistribution-observed", cycle: cycle)
    }

    /// Test #40's second removal is the local two-to-one iOS -> Web path.
    func removeWebLocallyForTest40() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #40 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists)
        }
        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 2,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") == nil,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return webId != iosId
                && self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !self.app.descendants(matching: .any)["alert-recovery-request"].exists
                && !self.app.descendants(matching: .any)["alert-recovery-request-processing"].exists
        }
        let cleanExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [cleanExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #40's clean two-member local-removal boundary"
        )
        guard let webId = exactDeviceRowIdentifier(name: webDeviceName, status: "Member") else {
            XCTFail("Test #40 could not retain Web's exact device row")
            return
        }
        print("E2E: IOS_TEST40_WEB_LOCAL_REMOVAL_READY")

        waitForApproval(platform: "ios-remove-web-local-test40", cycle: "2")
        test40SwipeAndConfirmRemoval(deviceId: webId, targetDeviceName: webDeviceName)
        print("E2E: IOS_TEST40_WEB_LOCAL_REMOVAL_REQUESTED")

        let singleMemberBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 1,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") == nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") == nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
        }
        let singleMemberExpectation = XCTNSPredicateExpectation(
            predicate: singleMemberBoundary,
            object: nil
        )
        XCTAssertEqual(
            XCTWaiter.wait(for: [singleMemberExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #40's exact single-member boundary"
        )
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        print("E2E: IOS_TEST40_WEB_LOCAL_REMOVAL_COMPLETED")
    }

    /// iOS is Test #44's sole approver and reverse resharer for Android's
    /// removal. Completion requires the exact Web/iOS topology and all Secrets.
    func approveAndroidRemovalForTest44() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #44 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists)
        }

        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let recoveryAlert = app.descendants(matching: .any)["alert-recovery-request"]
        let recoveryProcessing = app.descendants(matching: .any)["alert-recovery-request-processing"]
        let joinAlert = app.descendants(matching: .any)["alert-join-request"]
        let joinProcessing = app.descendants(matching: .any)["alert-join-request-processing"]
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current"),
                Set([webId, androidId, iosId]).count == 3
            else { return false }
            return self.test37RemoveActionQuery(deviceId: webId).count == 1
                && self.test37RemoveActionQuery(deviceId: androidId).count == 1
                && self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !recoveryAlert.exists
                && !recoveryProcessing.exists
                && !joinAlert.exists
                && !joinProcessing.exists
        }
        let cleanExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [cleanExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #44's clean three-member approval boundary"
        )
        print("E2E: IOS_TEST44_REMOVAL_RECEIVER_READY")

        waitForVisible(identifier: "pending-removal-device-row", timeout: 180)
        tap("pending-removal-device-row")
        waitForVisible(identifier: "remove-device-dialog", timeout: 30)
        let exactLocalizedRemovalTitles = [
            "Remove device \(androidDeviceName)?",
            "Удалить устройство \(androidDeviceName)?",
        ]
        let visibleExactRemovalTitles = exactLocalizedRemovalTitles.filter {
            exactVisibleText($0, belongsTo: removalDialog)
        }
        XCTAssertEqual(
            visibleExactRemovalTitles.count,
            1,
            "Test #44 removal dialog must expose one exact localized composite Android title"
        )
        print("E2E: IOS_TEST44_REMOVAL_REQUEST_VISIBLE")

        waitForApproval(platform: "ios-remove-android-approve-test44", cycle: "1")
        tap("remove-device-dialog-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "remove-device-dialog", timeout: 120)
        waitForHidden(identifier: "pending-removal-device-row", timeout: 180)
        let resharedBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 2,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") == nil,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: webId).count == 1
                && self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !recoveryAlert.exists
                && !recoveryProcessing.exists
        }
        let resharedExpectation = XCTNSPredicateExpectation(predicate: resharedBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [resharedExpectation], timeout: 180),
            .completed,
            "iOS did not observe Test #44's two-member reverse-resharing boundary"
        )
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        print("E2E: IOS_TEST44_ANDROID_REMOVAL_APPROVED")
    }

    /// Test #44's second removal is the local two-to-one iOS -> Web path.
    func removeWebLocallyForTest44() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #44 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists)
        }
        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 2,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") == nil,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return webId != iosId
                && self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !self.app.descendants(matching: .any)["alert-recovery-request"].exists
                && !self.app.descendants(matching: .any)["alert-recovery-request-processing"].exists
        }
        let cleanExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [cleanExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #44's clean two-member local-removal boundary"
        )
        guard let webId = exactDeviceRowIdentifier(name: webDeviceName, status: "Member") else {
            XCTFail("Test #44 could not retain Web's exact device row")
            return
        }
        print("E2E: IOS_TEST44_WEB_LOCAL_REMOVAL_READY")

        waitForApproval(platform: "ios-remove-web-local-test44", cycle: "2")
        test44SwipeAndConfirmRemoval(deviceId: webId, targetDeviceName: webDeviceName)
        print("E2E: IOS_TEST44_WEB_LOCAL_REMOVAL_REQUESTED")

        let singleMemberBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 1,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") == nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") == nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
        }
        let singleMemberExpectation = XCTNSPredicateExpectation(
            predicate: singleMemberBoundary,
            object: nil
        )
        XCTAssertEqual(
            XCTWaiter.wait(for: [singleMemberExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #44's exact single-member boundary"
        )
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        print("E2E: IOS_TEST44_WEB_LOCAL_REMOVAL_COMPLETED")
    }

    /// iOS initiates Android's three-to-two removal. The request marker is
    /// emitted only after the visible confirmation/authentication flow has
    /// returned; completion additionally requires the exact reshared
    /// iOS/Web topology and all Secret rows.
    func requestAndroidRemovalForTest39() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #39 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists)
        }
        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current"),
                Set([webId, androidId, iosId]).count == 3
            else { return false }
            return self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !self.app.descendants(matching: .any)["alert-recovery-request"].exists
                && !self.app.descendants(matching: .any)["alert-recovery-request-processing"].exists
                && !self.app.descendants(matching: .any)["alert-join-request"].exists
                && !self.app.descendants(matching: .any)["alert-join-request-processing"].exists
        }
        let cleanExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [cleanExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #39's clean three-member requester boundary"
        )
        guard let androidId = exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") else {
            XCTFail("Test #39 could not retain Android's exact device row")
            return
        }
        print("E2E: IOS_TEST39_ANDROID_REMOVAL_REQUESTER_READY")

        waitForApproval(platform: "ios-remove-android-start", cycle: "1")
        test39SwipeAndConfirmRemoval(deviceId: androidId, targetDeviceName: androidDeviceName)
        print("E2E: IOS_TEST39_ANDROID_REMOVAL_REQUESTED")

        let resharedBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 2,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") == nil,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") != nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
        }
        let resharedExpectation = XCTNSPredicateExpectation(predicate: resharedBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [resharedExpectation], timeout: 180),
            .completed,
            "iOS did not observe Test #39's two-member reverse-resharing boundary"
        )
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        print("E2E: IOS_TEST39_ANDROID_REMOVAL_COMPLETED")
    }

    /// iOS requests Web removal in Test #42. Android is already waiting as
    /// the sole receiver and reverse resharer before the swipe is released.
    func requestWebRemovalForTest42() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #42 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists)
        }
        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current"),
                Set([webId, androidId, iosId]).count == 3
            else { return false }
            return self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !self.app.descendants(matching: .any)["alert-recovery-request"].exists
                && !self.app.descendants(matching: .any)["alert-recovery-request-processing"].exists
                && !self.app.descendants(matching: .any)["alert-join-request"].exists
                && !self.app.descendants(matching: .any)["alert-join-request-processing"].exists
        }
        let cleanExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [cleanExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #42's clean three-member requester boundary"
        )
        guard let webId = exactDeviceRowIdentifier(name: webDeviceName, status: "Member") else {
            XCTFail("Test #42 could not retain Web's exact device row")
            return
        }
        print("E2E: IOS_TEST42_WEB_REMOVAL_REQUESTER_READY")

        waitForApproval(platform: "ios-remove-web-start-test42", cycle: "1")
        test42SwipeAndConfirmRemoval(deviceId: webId, targetDeviceName: webDeviceName)
        print("E2E: IOS_TEST42_WEB_REMOVAL_REQUESTED")

        let resharedBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 2,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") == nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") != nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
        }
        let resharedExpectation = XCTNSPredicateExpectation(predicate: resharedBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [resharedExpectation], timeout: 180),
            .completed,
            "iOS did not observe Test #42's two-member reverse-resharing boundary"
        )
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        print("E2E: IOS_TEST42_WEB_REMOVAL_COMPLETED")
    }

    /// iOS requests Web removal in Test #43 after creating Secret3.
    func requestWebRemovalForTest43() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #43 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists)
        }
        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current"),
                Set([webId, androidId, iosId]).count == 3
            else { return false }
            return self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !self.app.descendants(matching: .any)["alert-recovery-request"].exists
                && !self.app.descendants(matching: .any)["alert-recovery-request-processing"].exists
                && !self.app.descendants(matching: .any)["alert-join-request"].exists
                && !self.app.descendants(matching: .any)["alert-join-request-processing"].exists
        }
        let cleanExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [cleanExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #43's clean three-member requester boundary"
        )
        guard let webId = exactDeviceRowIdentifier(name: webDeviceName, status: "Member") else {
            XCTFail("Test #43 could not retain Web's exact device row")
            return
        }
        print("E2E: IOS_TEST43_WEB_REMOVAL_REQUESTER_READY")

        waitForApproval(platform: "ios-remove-web-start-test43", cycle: "1")
        test43SwipeAndConfirmRemoval(deviceId: webId, targetDeviceName: webDeviceName)
        print("E2E: IOS_TEST43_WEB_REMOVAL_REQUESTED")

        let resharedBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 2,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") == nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") != nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
        }
        let resharedExpectation = XCTNSPredicateExpectation(predicate: resharedBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [resharedExpectation], timeout: 180),
            .completed,
            "iOS did not observe Test #43's two-member reverse-resharing boundary"
        )
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        print("E2E: IOS_TEST43_WEB_REMOVAL_COMPLETED")
    }

    /// iOS C requests removal of Web A and waits for Android B's terminal Decline.
    func requestWebRemovalForTest47() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #47 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists)
        }
        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let threeMemberBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") != nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") != nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
        }
        let readyExpectation = XCTNSPredicateExpectation(predicate: threeMemberBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [readyExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #47 phase-1 requester boundary"
        )
        guard let webId = exactDeviceRowIdentifier(name: webDeviceName, status: "Member") else {
            XCTFail("Test #47 could not retain Web's exact device row")
            return
        }
        print("E2E: IOS_TEST47_PHASE1_REQUESTER_READY")

        waitForApproval(platform: "ios-remove-web-start-test47", cycle: "1")
        test47SwipeAndConfirmRemoval(deviceId: webId, targetDeviceName: webDeviceName)
        print("E2E: IOS_TEST47_PHASE1_REMOVAL_REQUESTED")

        waitForApproval(platform: "ios-phase1-decline-observed-test47", cycle: "1")
        tap("tab-devices")
        let terminalExpectation = XCTNSPredicateExpectation(predicate: threeMemberBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [terminalExpectation], timeout: 180),
            .completed,
            "iOS did not preserve the three-member boundary after phase-1 Decline"
        )
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        print("E2E: IOS_TEST47_PHASE1_DECLINE_OBSERVED")
    }

    /// iOS C is the passive removal target while Web A declines Android B's request.
    func observeIosRemovalDeclinedForTest47() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #47 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let threeMemberBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") != nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") != nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
        }
        let readyExpectation = XCTNSPredicateExpectation(predicate: threeMemberBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [readyExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #47 phase-2 passive-target boundary"
        )
        print("E2E: IOS_TEST47_PHASE2_TARGET_READY")

        waitForApproval(platform: "ios-phase2-decline-observed-test47", cycle: "2")
        tap("tab-devices")
        let terminalExpectation = XCTNSPredicateExpectation(predicate: threeMemberBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [terminalExpectation], timeout: 180),
            .completed,
            "iOS did not remain a member after Test #47 phase-2 Decline"
        )
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        print("E2E: IOS_TEST47_PHASE2_DECLINE_OBSERVED")
    }

    /// iOS C terminally declines Web A's request to remove Android B.
    func declineAndroidRemovalForTest47() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #47 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists)
        }
        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") != nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") != nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
        }
        let readyExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [readyExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #47 phase-3 decliner boundary"
        )
        print("E2E: IOS_TEST47_PHASE3_DECLINER_READY")

        waitForVisible(identifier: "pending-removal-device-row", timeout: 180)
        tap("pending-removal-device-row")
        waitForVisible(identifier: "remove-device-dialog", timeout: 30)
        let exactLocalizedTitles = [
            "Remove device \(androidDeviceName)?",
            "Удалить устройство \(androidDeviceName)?",
        ]
        let matchingTitles = exactLocalizedTitles.filter {
            exactVisibleText($0, belongsTo: removalDialog)
        }
        XCTAssertEqual(
            matchingTitles.count,
            1,
            "Test #47 Decline dialog must expose one exact Android title"
        )
        print("E2E: IOS_TEST47_PHASE3_REMOVAL_REQUEST_VISIBLE")

        waitForApproval(platform: "ios-decline-android-test47", cycle: "3")
        tap("remove-device-dialog-decline")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "remove-device-dialog", timeout: 120)
        waitForHidden(identifier: "pending-removal-device-row", timeout: 180)
        let terminalExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [terminalExpectation], timeout: 180),
            .completed,
            "iOS did not preserve the three-member boundary after phase-3 Decline"
        )
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        print("E2E: IOS_TEST47_PHASE3_REMOVAL_DECLINED")
    }

    /// iOS B terminally declines Web C's request to remove Android A.
    func declineAndroidRemovalForTest48() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #48 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists)
        }
        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") != nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") != nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
        }
        let readyExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [readyExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #48 phase-1 decliner boundary"
        )
        print("E2E: IOS_TEST48_PHASE1_DECLINER_READY")

        waitForVisible(identifier: "pending-removal-device-row", timeout: 180)
        tap("pending-removal-device-row")
        waitForVisible(identifier: "remove-device-dialog", timeout: 30)
        let exactLocalizedTitles = [
            "Remove device \(androidDeviceName)?",
            "Удалить устройство \(androidDeviceName)?",
        ]
        let matchingTitles = exactLocalizedTitles.filter {
            exactVisibleText($0, belongsTo: removalDialog)
        }
        XCTAssertEqual(
            matchingTitles.count,
            1,
            "Test #48 Decline dialog must expose one exact Android title"
        )
        print("E2E: IOS_TEST48_PHASE1_REMOVAL_REQUEST_VISIBLE")

        waitForApproval(platform: "ios-decline-android-test48", cycle: "1")
        tap("remove-device-dialog-decline")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "remove-device-dialog", timeout: 120)
        waitForHidden(identifier: "pending-removal-device-row", timeout: 180)
        let terminalExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [terminalExpectation], timeout: 180),
            .completed,
            "iOS did not preserve the three-member boundary after phase-1 Decline"
        )
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        print("E2E: IOS_TEST48_PHASE1_REMOVAL_DECLINED")
    }

    /// iOS B requests removal of Web C and waits for Android A's terminal Decline.
    func requestWebRemovalForTest48() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #48 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists)
        }
        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let threeMemberBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") != nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") != nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
        }
        let readyExpectation = XCTNSPredicateExpectation(predicate: threeMemberBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [readyExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #48 phase-2 requester boundary"
        )
        guard let webId = exactDeviceRowIdentifier(name: webDeviceName, status: "Member") else {
            XCTFail("Test #48 could not retain Web's exact device row")
            return
        }
        print("E2E: IOS_TEST48_PHASE2_REQUESTER_READY")

        waitForApproval(platform: "ios-remove-web-start-test48", cycle: "2")
        test48SwipeAndConfirmRemoval(deviceId: webId, targetDeviceName: webDeviceName)
        print("E2E: IOS_TEST48_PHASE2_REMOVAL_REQUESTED")

        waitForApproval(platform: "ios-phase2-decline-observed-test48", cycle: "2")
        tap("tab-devices")
        let terminalExpectation = XCTNSPredicateExpectation(predicate: threeMemberBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [terminalExpectation], timeout: 180),
            .completed,
            "iOS did not preserve the three-member boundary after phase-2 Decline"
        )
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        print("E2E: IOS_TEST48_PHASE2_DECLINE_OBSERVED")
    }

    /// iOS B remains a member while Web C declines Android A's removal request.
    func observeIosRemovalDeclinedForTest48() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #48 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let threeMemberBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") != nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") != nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
        }
        let readyExpectation = XCTNSPredicateExpectation(predicate: threeMemberBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [readyExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #48 phase-3 passive-target boundary"
        )
        print("E2E: IOS_TEST48_PHASE3_TARGET_READY")

        waitForApproval(platform: "ios-phase3-decline-observed-test48", cycle: "3")
        tap("tab-devices")
        let terminalExpectation = XCTNSPredicateExpectation(predicate: threeMemberBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [terminalExpectation], timeout: 180),
            .completed,
            "iOS did not remain a member after Test #48 phase-3 Decline"
        )
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        print("E2E: IOS_TEST48_PHASE3_DECLINE_OBSERVED")
    }

    /// iOS A remains a member while Web B declines Android C's removal request.
    func observeIosRemovalDeclinedForTest49() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #49 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let threeMemberBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") != nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") != nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
        }
        let readyExpectation = XCTNSPredicateExpectation(predicate: threeMemberBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [readyExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #49 phase-1 passive-target boundary"
        )
        print("E2E: IOS_TEST49_PHASE1_TARGET_READY")

        waitForApproval(platform: "ios-phase1-decline-observed-test49", cycle: "1")
        tap("tab-devices")
        let terminalExpectation = XCTNSPredicateExpectation(predicate: threeMemberBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [terminalExpectation], timeout: 180),
            .completed,
            "iOS did not remain a member after Test #49 phase-1 Decline"
        )
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        print("E2E: IOS_TEST49_PHASE1_DECLINE_OBSERVED")
    }

    /// iOS A terminally declines Web B's request to remove Android C.
    func declineAndroidRemovalForTest49() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #49 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists)
        }
        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") != nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") != nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
        }
        let readyExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [readyExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #49 phase-2 decliner boundary"
        )
        print("E2E: IOS_TEST49_PHASE2_DECLINER_READY")

        waitForVisible(identifier: "pending-removal-device-row", timeout: 180)
        tap("pending-removal-device-row")
        waitForVisible(identifier: "remove-device-dialog", timeout: 30)
        let exactLocalizedTitles = [
            "Remove device \(androidDeviceName)?",
            "Удалить устройство \(androidDeviceName)?",
        ]
        let matchingTitles = exactLocalizedTitles.filter {
            exactVisibleText($0, belongsTo: removalDialog)
        }
        XCTAssertEqual(
            matchingTitles.count,
            1,
            "Test #49 Decline dialog must expose one exact Android title"
        )
        print("E2E: IOS_TEST49_PHASE2_REMOVAL_REQUEST_VISIBLE")

        waitForApproval(platform: "ios-decline-android-test49", cycle: "2")
        tap("remove-device-dialog-decline")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "remove-device-dialog", timeout: 120)
        waitForHidden(identifier: "pending-removal-device-row", timeout: 180)
        let terminalExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [terminalExpectation], timeout: 180),
            .completed,
            "iOS did not preserve the three-member boundary after phase-2 Decline"
        )
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        print("E2E: IOS_TEST49_PHASE2_REMOVAL_DECLINED")
    }

    /// iOS A requests removal of Web B and waits for Android C's terminal Decline.
    func requestWebRemovalForTest49() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #49 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists)
        }
        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let threeMemberBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") != nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") != nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
        }
        let readyExpectation = XCTNSPredicateExpectation(predicate: threeMemberBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [readyExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #49 phase-3 requester boundary"
        )
        guard let webId = exactDeviceRowIdentifier(name: webDeviceName, status: "Member") else {
            XCTFail("Test #49 could not retain Web's exact device row")
            return
        }
        print("E2E: IOS_TEST49_PHASE3_REQUESTER_READY")

        waitForApproval(platform: "ios-remove-web-start-test49", cycle: "3")
        test49SwipeAndConfirmRemoval(deviceId: webId, targetDeviceName: webDeviceName)
        print("E2E: IOS_TEST49_PHASE3_REMOVAL_REQUESTED")

        waitForApproval(platform: "ios-phase3-decline-observed-test49", cycle: "3")
        tap("tab-devices")
        let terminalExpectation = XCTNSPredicateExpectation(predicate: threeMemberBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [terminalExpectation], timeout: 180),
            .completed,
            "iOS did not preserve the three-member boundary after phase-3 Decline"
        )
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        print("E2E: IOS_TEST49_PHASE3_DECLINE_OBSERVED")
    }

    /// Test #39's two-to-one transition is local. Web is already at its
    /// passive-target boundary before the coordinator releases this swipe.
    func removeWebLocallyForTest39() throws {
        let config = stepConfig()
        let secretNames = configuredSecretNames()
        guard
            let webDeviceName = config.expectedWebDeviceName,
            let androidDeviceName = config.expectedAndroidDeviceName,
            let iosDeviceName = config.expectedIosDeviceName
        else {
            XCTFail("Test #39 requires exact Web, Android, and iOS device names")
            return
        }

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 60)
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
            XCTAssertFalse(app.descendants(matching: .any)["recovery-request-badge-\(secretName)"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["open-recovery-request-\(secretName)"].exists)
        }
        tap("tab-devices")
        let deviceRows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        let pendingJoin = app.descendants(matching: .any)["pending-device-row"]
        let pendingRemoval = app.descendants(matching: .any)["pending-removal-device-row"]
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 2,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") == nil,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return webId != iosId
                && self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !self.app.descendants(matching: .any)["alert-recovery-request"].exists
                && !self.app.descendants(matching: .any)["alert-recovery-request-processing"].exists
        }
        let cleanExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [cleanExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #39's clean two-member local-removal boundary"
        )
        guard let webId = exactDeviceRowIdentifier(name: webDeviceName, status: "Member") else {
            XCTFail("Test #39 could not retain Web's exact device row")
            return
        }
        print("E2E: IOS_TEST39_WEB_LOCAL_REMOVAL_READY")

        waitForApproval(platform: "ios-remove-web-local", cycle: "2")
        test39SwipeAndConfirmRemoval(deviceId: webId, targetDeviceName: webDeviceName)
        print("E2E: IOS_TEST39_WEB_LOCAL_REMOVAL_REQUESTED")

        let singleMemberBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 1,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") == nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") == nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.test37RemoveActionQuery(deviceId: iosId).count == 0
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
        }
        let singleMemberExpectation = XCTNSPredicateExpectation(
            predicate: singleMemberBoundary,
            object: nil
        )
        XCTAssertEqual(
            XCTWaiter.wait(for: [singleMemberExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #39's exact single-member boundary"
        )
        tap("tab-secrets")
        for secretName in secretNames {
            waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        }
        print("E2E: IOS_TEST39_WEB_LOCAL_REMOVAL_COMPLETED")
    }

    /// Test #23 uses iOS as the first joined device and verifies that a
    /// two-device vault can show its own locally available secret without a
    /// recovery request. The method is intentionally a UI-only action: the
    /// orchestrator invokes it once per direct-show cycle with a fresh XCTest
    /// process, so no timing sleep or hidden native shortcut is involved.
    func showLocalSecret() throws {
        showLocalSecretForCurrentStep(testMarkerPrefix: nil)
    }

    /// Test #26 uses iOS as secondary Secret creator B. Run-specific markers
    /// keep the 39-cycle timeline unambiguous without changing production UI.
    func showLocalSecretForTest26() throws {
        showLocalSecretForCurrentStep(testMarkerPrefix: "IOS_TEST26")
    }

    /// Test #27 keeps exact-value direct-Show evidence distinct from the
    /// neighboring lifecycle scenarios while reusing the same visible UI.
    func showLocalSecretForTest27() throws {
        showLocalSecretForCurrentStep(testMarkerPrefix: "IOS_TEST27")
    }

    func showLocalSecretForTest38() throws {
        showLocalSecretForCurrentStep(testMarkerPrefix: "IOS_TEST38")
    }

    func showLocalSecretForTest39() throws {
        showLocalSecretForCurrentStep(testMarkerPrefix: "IOS_TEST39")
    }

    func showLocalSecretForTest40() throws {
        showLocalSecretForCurrentStep(testMarkerPrefix: "IOS_TEST40")
    }

    func showLocalSecretForTest42() throws {
        showLocalSecretForCurrentStep(testMarkerPrefix: "IOS_TEST42")
    }

    func showLocalSecretForTest43() throws {
        showLocalSecretForCurrentStep(testMarkerPrefix: "IOS_TEST43")
    }

    func showLocalSecretForTest44() throws {
        showLocalSecretForCurrentStep(testMarkerPrefix: "IOS_TEST44")
    }

    private func showLocalSecretForCurrentStep(testMarkerPrefix: String?) {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test-secret")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test-secret-value")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "1")

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        waitForPrimaryAction(secretName, expected: "Show", timeout: 180)
        tap("secret-primary-action-\(secretName)")
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "revealed-secret-value", timeout: 60)
        XCTAssertTrue(
            app.descendants(matching: .any)[secretValue].waitForExistence(timeout: 10),
            "iOS did not reveal the expected local secret"
        )
        if let testMarkerPrefix {
            print("E2E: \(testMarkerPrefix)_LOCAL_SECRET_VALUE_VERIFIED_\(cycle)")
            print("E2E: \(testMarkerPrefix)_LOCAL_SECRET_VISIBLE_\(cycle)")
        } else {
            print("E2E: IOS_LOCAL_SECRET_VISIBLE_\(cycle)")
        }
        tap("show-secret-close")
        waitForHidden(identifier: "revealed-secret-value", timeout: 30)
        waitForHidden(identifier: "show-secret-dialog", timeout: 30)
        waitForPrimaryAction(secretName, expected: "Show", timeout: 30)
        if let testMarkerPrefix {
            print("E2E: \(testMarkerPrefix)_LOCAL_SECRET_CLOSED_\(cycle)")
        } else {
            print("E2E: IOS_LOCAL_SECRET_CLOSED_\(cycle)")
        }
    }

    /// Test #24 setup is intentionally split into small persisted UI actions.
    /// Each xcodebuild invocation relaunches the app, while the simulator keeps
    /// the same Core database and device identity between invocations.
    func createVaultForTest24() throws {
        let config = stepConfig()
        let vaultName = coordinatorVaultName(defaultValue: "test24@test.ru")
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test24-secret1")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test24-secret1-value")

        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(vaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "add-secret-fab", timeout: 180)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST24_SECRET1_READY")
        print("E2E: IOS_TEST24_INITIATOR_SETUP_READY")
    }

    /// Test #27 rotates the secondary Secret creator to Web. iOS still owns
    /// the visible Vault/Secret1 setup, so keep a run-specific completion
    /// marker for deterministic orchestration.
    func createVaultForTest27() throws {
        let config = stepConfig()
        let vaultName = coordinatorVaultName(defaultValue: "test27@test.ru")
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test27-secret1")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test27-secret1-value")

        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(vaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "add-secret-fab", timeout: 180)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST27_SECRET1_READY")
        print("E2E: IOS_TEST27_INITIATOR_SETUP_READY")
    }

    /// Test #30 rotates the third Secret creator to Android while iOS remains
    /// the visible Vault owner and Secret1 creator.
    func createVaultForTest30() throws {
        let config = stepConfig()
        let vaultName = coordinatorVaultName(defaultValue: "test30@test.ru")
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test30-secret1")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test30-secret1-value")

        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(vaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "add-secret-fab", timeout: 180)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST30_SECRET1_READY")
        print("E2E: IOS_TEST30_INITIATOR_SETUP_READY")
    }

    /// Test #36 keeps iOS as Vault owner and Secret1 creator while Web owns
    /// Secret2 and the sole approval of Android's later Join.
    func createVaultForTest36() throws {
        let config = stepConfig()
        let vaultName = coordinatorVaultName(defaultValue: "test36@test.ru")
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test36-secret1")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test36-secret1-value")

        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(vaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "add-secret-fab", timeout: 180)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST36_SECRET1_READY")
        print("E2E: IOS_TEST36_INITIATOR_SETUP_READY")
    }

    /// Test #33 keeps both Secret1 and Secret2 on the iOS owner before the
    /// Android Join. The Web client, not iOS, approves that second Join.
    func createVaultForTest33() throws {
        let config = stepConfig()
        let vaultName = coordinatorVaultName(defaultValue: "test33@test.ru")
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test33-secret1")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test33-secret1-value")

        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(vaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "add-secret-fab", timeout: 180)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST33_SECRET1_READY")
        print("E2E: IOS_TEST33_INITIATOR_SETUP_READY")
    }

    /// Test #39 rotates membership removal ownership to iOS. This setup uses
    /// run-specific completion markers so no neighboring XCTest can satisfy
    /// the Test39 readiness barrier.
    func createVaultForTest39() throws {
        let config = stepConfig()
        let vaultName = coordinatorVaultName(defaultValue: "test39@test.ru")
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test39-secret1")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test39-secret1-value")

        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(vaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "add-secret-fab", timeout: 180)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST39_SECRET1_READY")
        print("E2E: IOS_TEST39_INITIATOR_SETUP_READY")
    }

    /// Test #42 keeps Vault ownership on iOS while Web creates Secrets 2/3.
    func createVaultForTest42() throws {
        let config = stepConfig()
        let vaultName = coordinatorVaultName(defaultValue: "test42@test.ru")
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test42-secret1")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test42-secret1-value")

        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(vaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "add-secret-fab", timeout: 180)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST42_SECRET1_READY")
        print("E2E: IOS_TEST42_INITIATOR_SETUP_READY")
    }

    /// Test #45 keeps Vault ownership on iOS while Web and Android create
    /// Secrets 2 and 3 on their own visible clients.
    func createVaultForTest45() throws {
        let config = stepConfig()
        let vaultName = coordinatorVaultName(defaultValue: "test45@test.ru")
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test45-secret1")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test45-secret1-value")

        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(vaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "add-secret-fab", timeout: 180)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST45_SECRET1_READY")
        print("E2E: IOS_TEST45_INITIATOR_SETUP_READY")
    }

    /// Test #49 starts the final declined-removal role rotation on iOS.
    // Test50 uses the same persisted actor configuration and coordinator gates as
    // the passing recovery/removal tests. Never print plaintext in diagnostics.
    func createVaultForTest50() throws {
        let config = stepConfig()
        guard let name = config.secretName, let value = config.secretValue else {
            XCTFail("Test50 requires explicit Secret selectors"); return
        }
        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(coordinatorVaultName(defaultValue: "test50@test.ru"))
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "add-secret-fab", timeout: 180)
        createSecretForTest24Ui(name: name, value: value, scrollForTest50: true)
        print("E2E: IOS_TEST50_SECRET1_READY")
        print("E2E: IOS_TEST50_INITIATOR_SETUP_READY")
    }

    func createSecretForTest50() throws {
        let config = stepConfig()
        guard let name = config.secretName, let value = config.secretValue else {
            XCTFail("Test50 requires explicit Secret selectors"); return
        }
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        createSecretForTest24Ui(name: name, value: value, scrollForTest50: true)
        print("E2E: IOS_TEST50_SECRET_CREATED_\(config.cycle ?? "missing")_\(config.step ?? "missing")")
    }

    func approveJoinForTest50() throws {
        let config = stepConfig()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-devices", timeout: 60)
        tap("pending-device-row", timeout: 180)
        tap("alert-join-request-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "alert-join-request", timeout: 120)
        waitForHidden(identifier: "pending-device-row", timeout: 120)
        print("E2E: IOS_TEST50_JOIN_APPROVED_\((config.joinTarget ?? config.sender ?? "device").uppercased())")
    }

    func joinVaultForTest50() throws {
        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(coordinatorVaultName(defaultValue: "test50@test.ru"))
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        tap("email-confirmation-join", timeout: 60)
        enterSimulatorPasscodeIfNeeded()
        print("E2E: IOS_JOIN_REQUEST_SENT")
        // Request submission precedes membership acceptance. Pending Join has
        // unrelated scroll containers; only search after Main/Secrets is ready.
        waitForVisible(identifier: "tab-secrets", timeout: 180)
        tap("tab-secrets")
        waitForVisible(identifier: "add-secret-fab", timeout: 180)
        waitForHidden(identifier: "vault-sync-blocker", timeout: 180)
        for name in configuredSecretNames() {
            test50RequireVisible("secret-row-\(name)")
        }
        print("E2E: IOS_JOIN_READY")
        print("E2E: IOS_SECRETS_READY")
    }

    func handleSecretDeletionStep() throws {
        let config = stepConfig()
        guard let role = config.role, let cycle = config.cycle, let step = config.step,
              let name = config.secretName, let value = config.secretValue,
              let outcome = config.expectedOutcome,
              ["accepted", "declined", "pending"].contains(outcome) else {
            XCTFail("Test50 actor configuration is incomplete"); return
        }
        func marker(_ action: String) { print("E2E: IOS_DELETION_\(action)_\(cycle)_\(step)") }
        func gate(_ phase: String) {
            waitForApproval(platform: "ios-deletion-\(phase)", cycle: cycle, step: step)
        }
        let row = "secret-row-\(name)"
        let request = "open-deletion-request-\(name)"
        let pending = "deletion-pending-\(name)"
        var baselineGeometry: [String: CGRect] = [:]
        if role == "startup-gone" {
            XCTAssertEqual(app.state, .runningForeground, "Cold app is not foreground")
            XCTAssertTrue(app.windows.firstMatch.waitForExistence(timeout: 30), "Cold app window missing")
            XCTAssertFalse(app.descendants(matching: .any)[row].exists, "Cached deleted row rendered before canonical sync")
            XCTAssertFalse(app.descendants(matching: .any)["show-secret-dialog"].exists, "Cold startup retained Show")
            XCTAssertFalse(app.descendants(matching: .any)["revealed-secret-value"].exists, "Cold startup retained plaintext")
            let tab = app.descendants(matching: .any)["tab-secrets"]
            let blocker = app.descendants(matching: .any)["vault-sync-blocker"]
            XCTAssertTrue(!tab.exists || blocker.exists, "Cold startup exposed Main without canonical guard")
            let fixtureData = try Data(contentsOf: URL(fileURLWithPath: Self.stepConfigPath + ".test50-geometry.json"))
            let fixture = try JSONDecoder().decode(Test50GeometryFixture.self, from: fixtureData)
            XCTAssertTrue(step.hasSuffix("-startup"), "Cold geometry fixture actor scope invalid")
            let expectedSourceStep = String(step.dropLast("-startup".count)) + "-main"
            XCTAssertTrue(fixture.cycle == cycle && fixture.sourceStep == expectedSourceStep && fixture.secretName == name,
                "Cold geometry fixture scope mismatch")
            XCTAssertTrue(fixture.window == test50RectNumbers(app.windows.firstMatch.frame), "Cold geometry window changed")
            let storedGeometry = fixture.privateRects.mapValues { values -> CGRect in
                guard values.count == 4 else { return .zero }
                return CGRect(x: values[0], y: values[1], width: values[2], height: values[3])
            }
            for key in ["show", "value", "copy"] {
                XCTAssertTrue(storedGeometry[key].map { !$0.isEmpty } ?? false, "Cold private-content geometry missing")
            }
            if blocker.exists {
                test50AssertOverlayCoverage(blocker.frame, window: app.windows.firstMatch.frame, baseline: storedGeometry)
            }
            let coldCopy = app.buttons.matching(NSPredicate(format: "label == %@ OR label == %@", "Copy secret", "Copy phrase")).firstMatch
            XCTAssertFalse(coldCopy.exists, "Cold startup exposed private Copy control")
            marker("READY")
            gate("action")
            gate("terminal")
            enterSimulatorPasscodeIfNeeded()
            waitForVisible(identifier: "tab-secrets", timeout: 180)
            waitForHidden(identifier: "vault-sync-blocker", timeout: 120)
            XCTAssertFalse(test50FindInList(row), "Deleted Secret row remains in list")
            waitForHidden(identifier: "show-secret-dialog", timeout: 30)
            waitForHidden(identifier: "revealed-secret-value", timeout: 30)
            marker("TERMINAL")
            return
        }
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        waitForHidden(identifier: "vault-sync-blocker", timeout: 180)
        test50RequireVisible(row)
        waitForHidden(identifier: "secret-deletion-dialog", timeout: 30)

        if role == "sync-observe" {
            test50RequireVisible("secret-primary-action-\(name)")
            waitForPrimaryAction(name, expected: "Show", timeout: 180)
            test50Tap("secret-primary-action-\(name)")
            enterSimulatorPasscodeIfNeeded()
            assertTest50Plaintext(value)
            if role == "sync-observe" {
                let copy = app.buttons.matching(NSPredicate(format: "label == %@ OR label == %@", "Copy secret", "Copy phrase")).firstMatch
                XCTAssertTrue(copy.waitForExistence(timeout: 30), "Copy control unavailable before sync")
                XCTAssertTrue(copy.isHittable, "Copy control blocked before sync")
                baselineGeometry = test50SyncGeometry(phase: "baseline", baseline: [:])
                let fixture = Test50GeometryFixture(cycle: cycle, sourceStep: step, secretName: name,
                    window: test50RectNumbers(app.windows.firstMatch.frame),
                    privateRects: baselineGeometry.filter { ["show", "value", "copy"].contains($0.key) }.mapValues(test50RectNumbers))
                try JSONEncoder().encode(fixture).write(to: URL(fileURLWithPath: Self.stepConfigPath + ".test50-geometry.json"), options: .atomic)
            }
        }
        marker("READY")
        switch role {
        case "requester", "cancel":
            gate("action")
            test50Tap("delete-secret-\(name)")
            waitForVisible(identifier: "secret-deletion-dialog", timeout: 30)
            if role == "cancel" {
                // Cancel has no stable identifier in the production dialog.
                let cancel = app.buttons["Cancel"]
                XCTAssertTrue(cancel.waitForExistence(timeout: 30), "Deletion Cancel unavailable")
                cancel.tap()
                waitForHidden(identifier: "secret-deletion-dialog", timeout: 30)
                test50RequireVisible(row)
                XCTAssertFalse(app.descendants(matching: .any)[pending].exists)
                XCTAssertFalse(app.descendants(matching: .any)[request].exists)
                marker("CANCELLED")
                return
            }
            tap("confirm-secret-deletion")
            enterSimulatorPasscodeIfNeeded()
            waitForHidden(identifier: "secret-deletion-dialog", timeout: 120)
            if outcome == "pending" {
                test50RequireVisible(pending)
            } else if outcome == "accepted" {
                // For N=3 the request is pending until the peer decision; for
                // N=1/2 it is already gone. The coordinator supplies action.
                let settled = NSPredicate { _, _ in
                    !self.app.descendants(matching: .any)[row].exists ||
                    self.app.descendants(matching: .any)[pending].exists
                }
                XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: settled, object: nil)], timeout: 120), .completed)
            } else {
                test50RequireVisible(pending)
            }
            marker("SUBMITTED")
        case "approve", "decline", "observe":
            if config.expectRequest != false {
                test50RequireVisible(request)
                marker("REQUEST_VISIBLE")
            }
            if role != "observe" {
                gate("action")
                test50Tap(request)
                tap(role == "approve" ? "confirm-secret-deletion" : "decline-secret-deletion")
                enterSimulatorPasscodeIfNeeded()
                waitForHidden(identifier: "secret-deletion-dialog", timeout: 120)
                XCTAssertFalse(test50FindInList(request), "Incoming deletion request remains in list")
                marker("SUBMITTED")
            }
        case "show":
            gate("action")
            test50RequireVisible("secret-primary-action-\(name)")
            waitForPrimaryAction(name, expected: "Show", timeout: 180)
            test50Tap("secret-primary-action-\(name)")
            enterSimulatorPasscodeIfNeeded()
            assertTest50Plaintext(value)
            closeShowSecretDialog()
            marker("SHOW_VALID")
            return
        case "sync-observe":
            gate("action")
            XCUIDevice.shared.press(.home)
            app.activate() // Hot foreground refresh retains the composed Show.
            waitForVisible(identifier: "vault-sync-blocker", timeout: 120)
            let blocker = app.descendants(matching: .any)["vault-sync-blocker"]
            let window = app.windows.firstMatch.frame
            _ = test50SyncGeometry(phase: "blocked", baseline: baselineGeometry)
            test50AssertOverlayCoverage(blocker.frame, window: window, baseline: baselineGeometry)
            let plaintext = app.descendants(matching: .any)["revealed-secret-value"]
            XCTAssertFalse(plaintext.exists, "Plaintext remains accessible under sync overlay")
            let copy = app.buttons.matching(NSPredicate(format: "label == %@ OR label == %@", "Copy secret", "Copy phrase")).firstMatch
            XCTAssertFalse(copy.exists, "Copy remains accessible under sync overlay")
            marker("SYNC_BLOCKED")
            gate("sync-release")
            let retry = app.descendants(matching: .any)["retry-vault-sync"]
            if retry.exists { retry.tap() }
        default:
            XCTFail("Unsupported Test50 role"); return
        }
        gate("terminal")
        waitForHidden(identifier: "vault-sync-blocker", timeout: 120)
        XCTAssertFalse(test50FindInList(request), "Incoming deletion request remains in list")
        waitForHidden(identifier: "secret-deletion-dialog", timeout: 120)
        if outcome == "accepted" {
            XCTAssertFalse(test50FindInList(row), "Deleted Secret row remains in list")
            waitForHidden(identifier: "show-secret-dialog", timeout: 30)
            waitForHidden(identifier: "revealed-secret-value", timeout: 30)
        } else {
            test50RequireVisible(row)
            if outcome == "declined" { XCTAssertFalse(test50FindInList(pending), "Pending deletion remains in list") }
        }
        marker("TERMINAL")
    }

    // Search only rendered accessibility metadata. Reaching a stable viewport
    // at both ends establishes absence across the LazyColumn, not just offscreen.
    private func test50RectNumbers(_ rect: CGRect) -> [Double] {
        [Double(rect.minX), Double(rect.minY), Double(rect.width), Double(rect.height)]
    }

    private func test50AssertOverlayCoverage(_ blocker: CGRect, window: CGRect, baseline: [String: CGRect]) {
        XCTAssertTrue(!blocker.isEmpty, "Opaque synchronization surface missing geometry")
        XCTAssertTrue(blocker.minX <= window.minX && blocker.maxX >= window.maxX, "Sync overlay misses application width")
        XCTAssertTrue(blocker.contains(CGPoint(x: window.midX, y: window.midY)), "Sync overlay misses application center")
        for key in ["show", "value", "copy"] {
            guard let frame = baseline[key], !frame.isEmpty else {
                XCTFail("Measured private-content geometry missing"); return
            }
            XCTAssertTrue(blocker.contains(frame), "Sync overlay fails full measured private-content coverage")
        }
    }

    // Geometry only: never emit accessibility labels or Secret payloads.
    private func test50SyncGeometry(phase: String, baseline: [String: CGRect]) -> [String: CGRect] {
        let blocker = app.descendants(matching: .any)["vault-sync-blocker"]
        let show = app.descendants(matching: .any)["show-secret-dialog"]
        let value = app.descendants(matching: .any)["revealed-secret-value"]
        let copy = app.buttons.matching(NSPredicate(format: "label == %@ OR label == %@", "Copy secret", "Copy phrase")).firstMatch
        var geometry: [String: CGRect] = [:]
        for (key, element) in [("blocker", blocker), ("show", show), ("value", value), ("copy", copy)] {
            if element.exists { geometry[key] = element.frame }
            print("E2E: IOS_TEST50_GEOMETRY phase=\(phase) node=\(key) exists=\(element.exists) hittable=\(element.exists && element.isHittable) frame=\(element.exists ? diagnosticFrame(element.frame) : "absent")")
        }
        for (index, window) in app.windows.allElementsBoundByIndex.enumerated() {
            let frame = window.frame
            let widthRatio = blocker.exists && frame.width > 0 ? blocker.frame.width / frame.width : 0
            let heightRatio = blocker.exists && frame.height > 0 ? blocker.frame.height / frame.height : 0
            print("E2E: IOS_TEST50_GEOMETRY phase=\(phase) window=\(index) frame=\(diagnosticFrame(frame)) widthRatio=\(widthRatio) heightRatio=\(heightRatio) covered=\(blocker.exists && blocker.frame.contains(frame))")
        }
        for key in ["show", "value", "copy"] {
            if let frame = baseline[key] {
                print("E2E: IOS_TEST50_GEOMETRY phase=\(phase) baseline=\(key) frame=\(diagnosticFrame(frame)) covered=\(blocker.exists && blocker.frame.contains(frame)) centerCovered=\(blocker.exists && blocker.frame.contains(CGPoint(x: frame.midX, y: frame.midY)))")
            }
            if let frame = geometry[key] {
                print("E2E: IOS_TEST50_GEOMETRY phase=\(phase) active=\(key) covered=\(blocker.exists && blocker.frame.contains(frame))")
            }
        }
        return geometry
    }

    private func test50FindInList(_ identifier: String) -> Bool {
        waitForVisible(identifier: "tab-secrets", timeout: 180)
        waitForVisible(identifier: "add-secret-fab", timeout: 180)
        waitForHidden(identifier: "vault-sync-blocker", timeout: 180)
        let target = app.descendants(matching: .any)[identifier]
        let scrollContainers = app.scrollViews
        XCTAssertEqual(scrollContainers.count, 1, "Test50 Secret scroll container is ambiguous")
        let scroll = scrollContainers.firstMatch
        if target.exists && target.isHittable { return true }
        guard scroll.waitForExistence(timeout: 30) else {
            XCTFail("Test50 semantic Secret scroll container missing")
            return false
        }
        func viewport() -> [String]? {
            let snapshotDeadline = Date().addingTimeInterval(15)
            for attempt in 1...3 {
                do {
                    // One public immutable snapshot: canonical deletion cannot
                    // shift live index-bound elements between property reads.
                    let root = try scroll.snapshot()
                    var stack: [any XCUIElementSnapshot] = [root]
                    var visited = 0
                    var keys: [String] = []
                    while let node = stack.popLast() {
                        visited += 1
                        guard visited <= 8192 else {
                            XCTFail("Test50 scoped snapshot exceeds metadata traversal budget")
                            return nil
                        }
                        if node.identifier.hasPrefix("secret-row-"), node.frame.intersects(root.frame) {
                            keys.append(Self.test50ViewportKey(id: node.identifier, row: node.frame, viewport: root.frame))
                        }
                        stack.append(contentsOf: node.children)
                    }
                    return keys.sorted()
                } catch {
                    // Error descriptions can contain accessibility content;
                    // record only retry metadata, never the snapshot or payload.
                    print("E2E: IOS_TEST50_VIEWPORT_SNAPSHOT_RETRY attempt=\(attempt)")
                    if Date() >= snapshotDeadline { break }
                }
            }
            XCTFail("Test50 immutable viewport snapshot unavailable within bounded retries")
            return nil
        }
        var reachedBothEnds = true
        // Seek below first: newly added sorted rows can be below the current
        // viewport. Each direction owns its own budget, so bounce cannot starve
        // the opposite sweep. Never infer absence unless BOTH ends are proven.
        for upward in [true, false] {
            let directionDeadline = Date().addingTimeInterval(60)
            var unchanged = 0
            for _ in 0..<64 {
                if target.exists && target.isHittable { return true }
                guard let before = viewport() else { return false }
                if upward { scroll.swipeUp() } else { scroll.swipeDown() }
                if target.exists && target.isHittable { return true }
                guard let after = viewport() else { return false }
                unchanged = !after.isEmpty && before == after ? unchanged + 1 : 0
                if unchanged >= 3 { break }
                if Date() >= directionDeadline { break }
            }
            reachedBothEnds = reachedBothEnds && unchanged >= 3
        }
        XCTAssertTrue(reachedBothEnds, "Test50 complete list absence cannot be established within bounded sweeps")
        return false
    }

    nonisolated private static func test50ViewportKey(id: String, row: CGRect, viewport: CGRect) -> String {
        // Discrete clipping states retain partial-row movement evidence without
        // letting elastic bounce pixel jitter prevent an otherwise stable end.
        let clipsTop = row.minY < viewport.minY - 8
        let clipsBottom = row.maxY > viewport.maxY + 8
        return "\(id):\(clipsTop):\(clipsBottom)"
    }

    private func test50RequireVisible(_ identifier: String) {
        let deadline = Date().addingTimeInterval(180)
        repeat {
            if test50FindInList(identifier) { return }
        } while Date() < deadline
        XCTFail("Test50 target unavailable after bounded complete list searches")
    }

    private func test50Tap(_ identifier: String) {
        test50RequireVisible(identifier)
        tap(identifier)
    }

    private func assertTest50Plaintext(_ expected: String) {
        let plaintext = app.descendants(matching: .any)["revealed-secret-value"]
        XCTAssertTrue(plaintext.waitForExistence(timeout: 30), "Plaintext node missing")
        // Compare as a Boolean: XCTest must never include values in a diff.
        XCTAssertTrue(plaintext.label == expected, "Plaintext equality check failed")
    }

    func createVaultForTest49() throws {
        let config = stepConfig()
        let vaultName = coordinatorVaultName(defaultValue: "test49@test.ru")
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test49-secret1")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test49-secret1-value")

        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(vaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "add-secret-fab", timeout: 180)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST49_SECRET1_READY")
        print("E2E: IOS_TEST49_INITIATOR_SETUP_READY")
    }

    func approvePendingJoinForTest24() throws {
        let config = stepConfig()
        let target = (config.sender ?? "device").uppercased()

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-devices", timeout: 60)
        waitForVisible(identifier: "pending-device-row", timeout: 180)
        tap("pending-device-row")
        waitForVisible(identifier: "alert-join-request", timeout: 30)
        tap("alert-join-request-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "alert-join-request", timeout: 120)
        waitForHidden(identifier: "pending-device-row", timeout: 120)
        print("E2E: IOS_JOIN_APPROVED_\(target)")
    }

    func approvePendingJoinForTest27() throws {
        let config = stepConfig()
        let target = (config.sender ?? "device").uppercased()

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-devices", timeout: 60)
        waitForVisible(identifier: "pending-device-row", timeout: 180)
        tap("pending-device-row")
        waitForVisible(identifier: "alert-join-request", timeout: 30)
        tap("alert-join-request-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "alert-join-request", timeout: 120)
        waitForHidden(identifier: "pending-device-row", timeout: 120)
        print("E2E: IOS_TEST27_JOIN_APPROVED_\(target)")
    }

    func approvePendingJoinForTest30() throws {
        let config = stepConfig()
        let target = (config.sender ?? "device").uppercased()

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-devices", timeout: 60)
        waitForVisible(identifier: "pending-device-row", timeout: 180)
        tap("pending-device-row")
        waitForVisible(identifier: "alert-join-request", timeout: 30)
        tap("alert-join-request-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "alert-join-request", timeout: 120)
        waitForHidden(identifier: "pending-device-row", timeout: 120)
        print("E2E: IOS_TEST30_JOIN_APPROVED_\(target)")
    }

    /// Test #36 uses this iOS action only for Web's first Join. Android's
    /// second Join is approved exclusively by Web in the orchestrator.
    func approvePendingJoinForTest36() throws {
        let config = stepConfig()
        let target = (config.sender ?? "web").uppercased()

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-devices", timeout: 60)
        waitForVisible(identifier: "pending-device-row", timeout: 180)
        tap("pending-device-row")
        waitForVisible(identifier: "alert-join-request", timeout: 30)
        tap("alert-join-request-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "alert-join-request", timeout: 120)
        waitForHidden(identifier: "pending-device-row", timeout: 120)
        print("E2E: IOS_TEST36_JOIN_APPROVED_\(target)")
    }

    func approvePendingJoinForTest33() throws {
        let config = stepConfig()
        let target = (config.sender ?? "web").uppercased()

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-devices", timeout: 60)
        waitForVisible(identifier: "pending-device-row", timeout: 180)
        tap("pending-device-row")
        waitForVisible(identifier: "alert-join-request", timeout: 30)
        tap("alert-join-request-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "alert-join-request", timeout: 120)
        waitForHidden(identifier: "pending-device-row", timeout: 120)
        print("E2E: IOS_TEST33_JOIN_APPROVED_\(target)")
    }

    func approvePendingJoinForTest39() throws {
        let config = stepConfig()
        let target = (config.sender ?? "device").uppercased()

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-devices", timeout: 60)
        waitForVisible(identifier: "pending-device-row", timeout: 180)
        tap("pending-device-row")
        waitForVisible(identifier: "alert-join-request", timeout: 30)
        tap("alert-join-request-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "alert-join-request", timeout: 120)
        waitForHidden(identifier: "pending-device-row", timeout: 120)
        print("E2E: IOS_TEST39_JOIN_APPROVED_\(target)")
    }

    func approvePendingJoinForTest42() throws {
        let config = stepConfig()
        let target = (config.sender ?? "device").uppercased()

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-devices", timeout: 60)
        waitForVisible(identifier: "pending-device-row", timeout: 180)
        tap("pending-device-row")
        waitForVisible(identifier: "alert-join-request", timeout: 30)
        tap("alert-join-request-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "alert-join-request", timeout: 120)
        waitForHidden(identifier: "pending-device-row", timeout: 120)
        print("E2E: IOS_TEST42_JOIN_APPROVED_\(target)")
    }

    func approvePendingJoinForTest45() throws {
        let config = stepConfig()
        let target = (config.sender ?? "device").uppercased()

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-devices", timeout: 60)
        waitForVisible(identifier: "pending-device-row", timeout: 180)
        tap("pending-device-row")
        waitForVisible(identifier: "alert-join-request", timeout: 30)
        tap("alert-join-request-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "alert-join-request", timeout: 120)
        waitForHidden(identifier: "pending-device-row", timeout: 120)
        print("E2E: IOS_TEST45_JOIN_APPROVED_\(target)")
    }

    /// iOS A is the sole visible Join approver for both Test #49 members.
    func approvePendingJoinForTest49() throws {
        let config = stepConfig()
        let target = (config.sender ?? "device").uppercased()

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-devices", timeout: 60)
        waitForVisible(identifier: "pending-device-row", timeout: 180)
        tap("pending-device-row")
        waitForVisible(identifier: "alert-join-request", timeout: 30)
        tap("alert-join-request-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "alert-join-request", timeout: 120)
        waitForHidden(identifier: "pending-device-row", timeout: 120)
        print("E2E: IOS_TEST49_JOIN_APPROVED_\(target)")
    }

    /// Test #35 iOS is the sole approver of Web's second Join.
    func approvePendingJoinForTest35() throws {
        let config = stepConfig()
        let target = (config.sender ?? "web").uppercased()

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-devices", timeout: 60)
        waitForVisible(identifier: "pending-device-row", timeout: 180)
        tap("pending-device-row")
        waitForVisible(identifier: "alert-join-request", timeout: 30)
        tap("alert-join-request-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "alert-join-request", timeout: 120)
        waitForHidden(identifier: "pending-device-row", timeout: 120)
        print("E2E: IOS_TEST35_JOIN_APPROVED_\(target)")
    }

    /// Test #32 deliberately lets the already-joined iOS member approve Web.
    /// Android receives no second-Join approval command from the orchestrator.
    func approvePendingJoinForTest32() throws {
        let config = stepConfig()
        let target = (config.sender ?? "web").uppercased()

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-devices", timeout: 60)
        waitForVisible(identifier: "pending-device-row", timeout: 180)
        tap("pending-device-row")
        waitForVisible(identifier: "alert-join-request", timeout: 30)
        tap("alert-join-request-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "alert-join-request", timeout: 120)
        waitForHidden(identifier: "pending-device-row", timeout: 120)
        print("E2E: IOS_TEST32_JOIN_APPROVED_\(target)")
    }

    func createSecretForTest24() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test24-secret2")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test24-secret2-value")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "0")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST24_SECRET_CREATED_\(cycle)")
    }

    func createSecretForTest26() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test26-secret2")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test26-secret2-value")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "0")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST26_SECRET_CREATED_\(cycle)")
    }

    /// Test #28 creates Secret3 on iOS only after iOS has joined the
    /// Web/Android Vault. The completion marker is emitted after the Secret
    /// row is observable, so the recovery matrix needs no time-based delay.
    func createSecretForTest28() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test28-secret3")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test28-secret3-value")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "0")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST28_SECRET_CREATED_\(cycle)")
    }

    /// Test #29 uses iOS as the first joined member and Secret2 creator.
    /// The marker is emitted only after the created row is observable.
    func createSecretForTest29() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test29-secret2")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test29-secret2-value")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "0")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST29_SECRET_CREATED_\(cycle)")
    }

    func createSecretForTest33() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test33-secret2")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test33-secret2-value")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "0")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST33_SECRET_CREATED_\(cycle)")
    }

    func createSecretForTest39() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test39-secret2")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test39-secret2-value")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "0")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST39_SECRET_CREATED_\(cycle)")
    }

    /// Test #41 uses iOS as Secret creator B on both sides of the Web Join.
    func createSecretForTest41() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test41-secret2")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test41-secret2-value")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "0")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST41_SECRET_CREATED_\(cycle)")
    }

    /// Test #44 creates Secret2 before Web joins the Android/iOS Vault.
    func createSecretForTest44() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test44-secret2")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test44-secret2-value")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "2")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST44_SECRET_CREATED_\(cycle)")
    }

    /// Test #48 creates Secret2 before Web joins the Android/iOS Vault.
    func createSecretForTest48() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test48-secret2")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test48-secret2-value")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "2")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST48_SECRET_CREATED_\(cycle)")
    }

    /// Test #43 creates Secret3 after joining the Web/Android Vault.
    func createSecretForTest43() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test43-secret3")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test43-secret3-value")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "3")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST43_SECRET_CREATED_\(cycle)")
    }

    /// Test #46 creates Secret3 on iOS before the removal/re-Join lifecycle.
    func createSecretForTest46() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test46-secret3")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test46-secret3-value")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "3")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST46_SECRET_CREATED_\(cycle)")
    }

    /// Test #47 creates Secret3 after joining the Web/Android Vault.
    func createSecretForTest47() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test47-secret3")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test47-secret3-value")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "3")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST47_SECRET_CREATED_\(cycle)")
    }

    /// Test #34 creates Secret3 after the first 18 recovery cycles.
    func createSecretForTest34() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test34-secret3")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test34-secret3-value")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "18")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST34_SECRET_CREATED_\(cycle)")
    }

    /// Test #35 creates Secret2 after iOS has joined the Android Vault.
    func createSecretForTest35() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test35-secret2")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test35-secret2-value")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "0")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        createSecretForTest24Ui(name: secretName, value: secretValue)
        print("E2E: IOS_TEST35_SECRET_CREATED_\(cycle)")
    }

    /// Verifies the Web-created Secret3 reached iOS before recovery begins.
    func assertSecretVisibleForTest29() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test29-secret3")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "0")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        print("E2E: IOS_TEST29_SECRET_VISIBLE_\(cycle)")
    }

    /// Verifies both Web-created Secret2 and Android-created Secret3 reached
    /// the iOS owner at their respective redistribution boundaries.
    func assertSecretVisibleForTest30() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test30-secret2")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "0")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        print("E2E: IOS_TEST30_SECRET_VISIBLE_\(cycle)")
    }

    /// Verifies Web-created Secret2 and Android-created Secret3 reach iOS at
    /// their explicit Test #36 redistribution boundaries.
    func assertSecretVisibleForTest36() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test36-secret2")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "0")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        print("E2E: IOS_TEST36_SECRET_VISIBLE_\(cycle)")
    }

    /// Verifies Web-created Secret3 reached iOS before Test #31 starts its
    /// final recovery block.
    func assertSecretVisibleForTest31() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test31-secret3")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "18")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        print("E2E: IOS_TEST31_SECRET_VISIBLE_\(cycle)")
    }

    /// Test #32 uses this assertion at all explicit redistribution boundaries:
    /// Secret1 after iOS joins, Secret2 before iOS approves Web, and Secret3
    /// after Android creates it at the completed cycle-18 boundary.
    func assertSecretVisibleForTest32() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test32-secret1")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "0")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        print("E2E: IOS_TEST32_SECRET_VISIBLE_\(cycle)")
    }

    func assertSecretVisibleForTest33() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test33-secret1")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "0")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        print("E2E: IOS_TEST33_SECRET_VISIBLE_\(cycle)")
    }

    func assertSecretVisibleForTest34() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test34-secret3")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "18")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        print("E2E: IOS_TEST34_SECRET_VISIBLE_\(cycle)")
    }

    func assertSecretVisibleForTest35() throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test35-secret3")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "3")

        skipOnboardingIfNeeded()
        enterSimulatorPasscodeIfNeeded()
        tap("tab-secrets", timeout: 60)
        waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)
        print("E2E: IOS_TEST35_SECRET_VISIBLE_\(cycle)")
    }

    func rejectFourthDeviceAndRegisterNewVault() throws {
        // Read the active scenario from the coordinator. XCTest does not
        // consistently forward xcodebuild's environment into the test
        // process, while the coordinator is already the source of truth used
        // by the join flow.
        let vaultName = coordinatorVaultName(defaultValue: env("E2E_VAULT_NAME", defaultValue: "test21@test.ru"))
        let retryVaultName = env("E2E_RETRY_VAULT_NAME", defaultValue: "test21-retry@test.ru")

        skipOnboardingIfNeeded()
        openManualEmailSignIn()
        typeEmail(vaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        tap("email-confirmation-join", timeout: 60)
        enterSimulatorPasscodeIfNeeded()

        waitForVisible(identifier: "email-confirmation-error", timeout: 120)
        let error = app.descendants(matching: .any)["email-confirmation-error"]
        XCTAssertTrue(error.label.lowercased().contains("maximum of 3 devices"), "Unexpected fourth-device error: \(error.label)")
        print("E2E: IOS_FOURTH_DEVICE_REJECTED")
        waitForApproval(platform: "ios-rejection", cycle: "1", step: "screenshot")

        tap("email-confirmation-error-reset")
        openManualEmailSignIn()
        typeEmail(retryVaultName)
        tap("manual-signin-continue")
        tap("email-confirmation-continue")
        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "tab-secrets", timeout: 120)
        print("E2E: IOS_FOURTH_DEVICE_RETRY_REGISTERED")
    }

    func runNetworkLossCycles() throws {
        let config = stepConfig()
        let role = config.networkRole ?? config.role ?? env("E2E_NETWORK_ROLE", defaultValue: "")
        let sender = config.sender ?? env("E2E_SENDER", defaultValue: "")
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
                assertRecoveryAlertVisible(
                    secretName: secretName,
                    requester: sender.isEmpty ? nil : sender,
                    timeout: 10
                )
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
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test-secret-value")
        let role = config.role ?? env("E2E_ROLE", defaultValue: "receiver")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "1")
        let step = config.step ?? env("E2E_STEP", defaultValue: "1")
        let approvalPlatform = config.approvalPlatform ?? env("E2E_APPROVAL_PLATFORM", defaultValue: "")
        let sender = config.sender ?? env("E2E_SENDER", defaultValue: "")
        let verifyReceiverAlert = config.verifyReceiverAlert
            ?? (env("E2E_VERIFY_RECEIVER_ALERT", defaultValue: "0") == "1")
        let receiverRequestAlreadySent = config.receiverRequestAlreadySent ?? false

        enterSimulatorPasscodeIfNeeded()
        waitForVisible(identifier: "secret-row-\(secretName)", timeout: 180)

        if role == "sender" {
            // A receiver that approved an earlier cycle legitimately has a
            // Show action. Consume that accepted claim before creating the
            // next recovery request; otherwise waiting for Recover would hang
            // forever even though the application is in the correct state.
            showAcceptedSecretIfNeeded(secretName, cycle: cycle, step: step)
            waitForSenderRecoveryUiReady(secretName, timeout: 180)
            print("E2E: IOS_SENDER_UI_READY_\(cycle)_\(step)")
            print("E2E: IOS_STEP_CONFIG role=\(role) cycle=\(cycle) step=\(step) approval=\(approvalPlatform)")
            waitForApproval(platform: "ios-sender", cycle: cycle, step: step)
            tap("secret-primary-action-\(secretName)")
            enterSimulatorPasscodeIfNeeded()
            waitForVisible(identifier: "show-secret-dialog", timeout: 30)
            print("E2E: IOS_RECOVERY_REQUEST_SENT_\(cycle)_\(step)")
            if approvalPlatform == "cli" {
                if config.expectedOutcome == "membership-removal" {
                    waitForApproval(platform: "ios-request-committed", cycle: cycle, step: step)
                }
                closeShowSecretDialog()
                if config.expectedOutcome == "membership-removal" {
                    print("E2E: IOS_RECOVERY_REQUEST_READY_FOR_REMOVAL_\(cycle)_\(step)")
                    return
                }
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
            closeShowSecretDialog()
            waitForHidden(identifier: "revealed-secret-value", timeout: 30)
            print("E2E: IOS_RECOVERY_CLOSED_\(cycle)_\(step)")
            return
        }

        let readyBadge = app.descendants(matching: .any)["recovery-request-badge-\(secretName)"]
        let readyOpen = app.descendants(matching: .any)["open-recovery-request-\(secretName)"]
        let readyAlert = app.descendants(matching: .any)["alert-recovery-request"]
        let readyProcessing = app.descendants(matching: .any)["alert-recovery-request-processing"]
        let receiverReady = NSPredicate { _, _ in
            !readyBadge.exists && !readyOpen.exists && !readyAlert.exists && !readyProcessing.exists
        }
        let receiverReadyWait = XCTNSPredicateExpectation(predicate: receiverReady, object: nil)
        if !receiverRequestAlreadySent {
            XCTAssertEqual(
                XCTWaiter.wait(for: [receiverReadyWait], timeout: 30),
                .completed,
                "iOS receiver did not reach a clean pre-request recovery UI state"
            )
        }
        print("E2E: IOS_RECEIVER_UI_READY_\(cycle)_\(step)")
        print("E2E: IOS_STEP_CONFIG role=\(role) cycle=\(cycle) step=\(step) approval=\(approvalPlatform)")

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
        if config.expectedOutcome == "membership-removal" {
            print("E2E: IOS_RECOVERY_READY_FOR_REMOVAL_\(cycle)_\(step)")
            return
        }
        if approvalPlatform == "ios" || verifyReceiverAlert {
            let alert = app.descendants(matching: .any)["alert-recovery-request"]
            let badge = app.descendants(matching: .any)["recovery-request-badge-\(secretName)"]
            let open = app.descendants(matching: .any)["open-recovery-request-\(secretName)"]

            logRecoveryUiState(
                cycle: cycle,
                step: step,
                phase: "before-open",
                secretName: secretName,
                alert: alert,
                badge: badge,
                open: open
            )

            // The request button and the sheet are rendered by separate
            // Compose state transitions. On a repeated recovery cycle the
            // first tap can land while the previous transition is still
            // settling. Reuse an already visible sheet; otherwise retry the
            // state-based open action once before declaring the UI broken.
            var alertVisible = alert.exists
            for attempt in 0..<2 where !alertVisible {
                if !badge.exists {
                    print("E2E: IOS_ACTION_SKIPPED_AFTER_TERMINAL_\(cycle)_\(step)")
                    return
                }
                if !open.exists {
                    _ = open.waitForExistence(timeout: 15)
                }
                if open.exists {
                    if attempt > 0 { print("E2E: IOS_OPEN_REQUEST_RETRY_\(cycle)_\(step)") }
                    open.tap()
                    logRecoveryUiState(
                        cycle: cycle,
                        step: step,
                        phase: "after-open-\(attempt + 1)",
                        secretName: secretName,
                        alert: alert,
                        badge: badge,
                        open: open
                    )
                }
                let alertOrTerminal = NSPredicate { _, _ in alert.exists || !badge.exists }
                let expectation = XCTNSPredicateExpectation(predicate: alertOrTerminal, object: nil)
                _ = XCTWaiter.wait(for: [expectation], timeout: 15)
                alertVisible = alert.exists
            }
            if !alertVisible {
                // A repeated cycle can leave the renderer with the previous
                // claim snapshot even though the badge/button are still
                // visible. Relaunch only as a diagnostic test boundary: it
                // forces a fresh appState read without changing Core or the
                // production Compose flow.
                print("E2E: IOS_RECOVERY_UI_REFRESH_\(cycle)_\(step)")
                app.terminate()
                app.launch()
                enterSimulatorPasscodeIfNeeded()
                waitForVisible(identifier: "secret-row-\(secretName)", timeout: 60)
                waitForVisible(identifier: "recovery-request-badge-\(secretName)", timeout: 60)
                waitForVisible(identifier: "open-recovery-request-\(secretName)", timeout: 60)
                if !alert.exists {
                    app.descendants(matching: .any)["open-recovery-request-\(secretName)"].tap()
                    logRecoveryUiState(
                        cycle: cycle,
                        step: step,
                        phase: "after-refresh-open",
                        secretName: secretName,
                        alert: alert,
                        badge: badge,
                        open: open
                    )
                    let alertOrTerminal = NSPredicate { _, _ in alert.exists || !badge.exists }
                    let expectation = XCTNSPredicateExpectation(predicate: alertOrTerminal, object: nil)
                    _ = XCTWaiter.wait(for: [expectation], timeout: 30)
                }
                alertVisible = alert.exists
            }
            if !alertVisible {
                if !badge.exists {
                    print("E2E: IOS_ACTION_SKIPPED_AFTER_TERMINAL_\(cycle)_\(step)")
                    return
                }
                print(
                    "E2E: IOS_RECOVERY_ALERT_MISSING_\(cycle)_\(step) "
                        + "secret=\(secretName) alertExists=\(alert.exists) "
                        + "badgeExists=\(badge.exists) badgeLabel=\(badge.label) "
                        + "openExists=\(open.exists) openLabel=\(open.label) "
                        + "openHittable=\(open.isHittable)"
                )
                print("E2E: IOS_ACCESSIBILITY_TREE_\(cycle)_\(step)\n\(app.debugDescription)")
                XCTFail("iOS recovery alert did not appear while the request remained pending")
                return
            }
            assertRecoveryAlertVisible(
                secretName: secretName,
                requester: sender.isEmpty ? nil : sender,
                timeout: 10
            )
            print("E2E: IOS_INCOMING_ALERT_VISIBLE_\(cycle)_\(step)")
            if approvalPlatform == "ios" {
                waitForApproval(platform: "ios-approve", cycle: cycle, step: step)
                tap("alert-recovery-request-accept")
                enterSimulatorPasscodeIfNeeded()
                waitForHidden(identifier: "alert-recovery-request", timeout: 120)
                waitForHidden(identifier: "alert-recovery-request-processing", timeout: 120)
                print("E2E: IOS_APPROVED_INCOMING_\(cycle)_\(step)")
            } else {
                let dismissBackdrop = app.descendants(matching: .any)["alert-dismiss"]
                XCTAssertTrue(dismissBackdrop.waitForExistence(timeout: 30))
                // Tap an uncovered backdrop coordinate above the bottom
                // sheet. Tapping the element center can land on the dialog
                // content and would not prove a visible no-decision close.
                dismissBackdrop.coordinate(
                    withNormalizedOffset: CGVector(dx: 0.5, dy: 0.1)
                ).tap()
                waitForHidden(identifier: "alert-recovery-request", timeout: 30)
                XCTAssertTrue(badge.exists, "iOS observer badge disappeared after local alert dismissal")
                XCTAssertTrue(open.exists, "iOS observer open action disappeared after local alert dismissal")
                print("E2E: IOS_INCOMING_ALERT_DISMISSED_WITHOUT_DECISION_\(cycle)_\(step)")
                waitForApproval(platform: "ios-dismiss", cycle: cycle, step: step)
                waitForHidden(identifier: "recovery-request-badge-\(secretName)", timeout: 120)
                waitForHidden(identifier: "open-recovery-request-\(secretName)", timeout: 120)
                print("E2E: IOS_DISMISSED_INCOMING_\(cycle)_\(step)")
            }
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
        let sender = config.sender ?? env("E2E_SENDER", defaultValue: "")

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
                XCTAssertTrue(app.descendants(matching: .any)[config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test-secret-value")].exists)
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
                XCTAssertTrue(app.descendants(matching: .any)[config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test-secret-value")].exists)
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
        assertRecoveryAlertVisible(
            secretName: secretName,
            requester: sender.isEmpty ? nil : sender,
            timeout: 10
        )
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

    private func assertRecoveryAlertVisible(
        secretName: String,
        requester: String?,
        timeout: TimeInterval
    ) {
        let alert = app.descendants(matching: .any)["alert-recovery-request"]
        XCTAssertTrue(
            alert.waitForExistence(timeout: timeout),
            "iOS recovery alert container did not appear for \(secretName)"
        )

        let alertVisible = NSPredicate { _, _ in
            guard alert.exists else { return false }
            let frame = alert.frame
            return !frame.isEmpty && !frame.isNull && self.app.frame.intersects(frame)
        }
        let alertVisibleWait = XCTNSPredicateExpectation(predicate: alertVisible, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [alertVisibleWait], timeout: timeout),
            .completed,
            "iOS recovery alert container was not visibly rendered for \(secretName)"
        )

        let quotedSecretName = "\"\(secretName)\""
        let textPredicate: NSPredicate
        if let requester, !requester.isEmpty {
            textPredicate = NSPredicate(
                format: "label CONTAINS %@ AND label CONTAINS[c] %@",
                quotedSecretName,
                requester
            )
        } else {
            textPredicate = NSPredicate(format: "label CONTAINS %@", quotedSecretName)
        }
        let matchingTexts = app.staticTexts.matching(textPredicate)
        let matchingTextVisible = NSPredicate { _, _ in
            guard alert.exists else { return false }
            let alertFrame = alert.frame
            for index in 0..<matchingTexts.count {
                let candidate = matchingTexts.element(boundBy: index)
                guard candidate.exists else { continue }
                let candidateFrame = candidate.frame
                if !candidateFrame.isEmpty
                    && !candidateFrame.isNull
                    && self.app.frame.intersects(candidateFrame)
                    && alertFrame.intersects(candidateFrame) {
                    return true
                }
            }
            return false
        }
        let matchingTextWait = XCTNSPredicateExpectation(predicate: matchingTextVisible, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [matchingTextWait], timeout: timeout),
            .completed,
            "iOS recovery alert visible StaticText did not contain quoted secret \(quotedSecretName)"
                + (requester.map { " and requester \($0)" } ?? "")
        )

        XCTAssertTrue(
            app.descendants(matching: .any)["alert-recovery-request-accept"].exists,
            "iOS recovery alert did not expose its generic accept action"
        )
        XCTAssertTrue(
            app.descendants(matching: .any)["alert-recovery-request-decline"].exists,
            "iOS recovery alert did not expose its generic decline action"
        )
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

    private func createSecretForTest24Ui(name: String, value: String, scrollForTest50: Bool = false) {
        tap("add-secret-fab", timeout: 180)
        let nameField = app.descendants(matching: .any)["secret-name-input"]
        XCTAssertTrue(nameField.waitForExistence(timeout: 30), "secret-name-input was not visible")
        nameField.typeText(name)

        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.40)).tap()
        let valueField = app.descendants(matching: .any)["secret-value-input"]
        XCTAssertTrue(valueField.waitForExistence(timeout: 30), "secret-value-input was not visible")
        valueField.typeText(value)

        // The software keyboard covers the semantic submit control. Use the
        // established iOS E2E coordinate for the dialog action, then wait on
        // the persisted Secret row rather than on an animation duration.
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.47)).tap()
        enterSimulatorPasscodeIfNeeded()
        if scrollForTest50 {
            waitForHidden(identifier: "secret-name-input", timeout: 30)
            waitForHidden(identifier: "vault-sync-blocker", timeout: 180)
            test50RequireVisible("secret-row-\(name)")
        } else {
            waitForVisible(identifier: "secret-row-\(name)", timeout: 180)
        }
        waitForHidden(identifier: "secret-name-input", timeout: 30)
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

    private func waitForSenderRecoveryUiReady(_ secretName: String, timeout: TimeInterval) {
        let action = app.descendants(matching: .any)["secret-primary-action-\(secretName)"]
        let dialog = app.descendants(matching: .any)["show-secret-dialog"]
        let value = app.descendants(matching: .any)["revealed-secret-value"]
        let badge = app.descendants(matching: .any)["recovery-request-badge-\(secretName)"]
        let open = app.descendants(matching: .any)["open-recovery-request-\(secretName)"]
        let alert = app.descendants(matching: .any)["alert-recovery-request"]
        let processing = app.descendants(matching: .any)["alert-recovery-request-processing"]
        let ready = NSPredicate { _, _ in
            action.exists
                && action.label.trimmingCharacters(in: .whitespacesAndNewlines)
                    .caseInsensitiveCompare("Recover") == .orderedSame
                && !dialog.exists
                && !value.exists
                && !badge.exists
                && !open.exists
                && !alert.exists
                && !processing.exists
        }
        let expectation = XCTNSPredicateExpectation(predicate: ready, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [expectation], timeout: timeout),
            .completed,
            "iOS sender did not reach exact Recover with a clean recovery UI"
        )
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

    private func waitForApproval(platform: String, cycle: String, step: String? = nil) {
        let baseUrl = env("E2E_APPROVAL_COORDINATOR_URL", defaultValue: "http://127.0.0.1:5180")
        let deadline = Date().addingTimeInterval(240)
        let approvalKey = step.map { "\(platform)-\($0)" } ?? platform
        while Date() < deadline {
            if let url = URL(string: "\(baseUrl)/approval?platform=\(approvalKey)&cycle=\(cycle)"),
               let result = try? String(contentsOf: url, encoding: .utf8),
               result == "allowed" {
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        XCTFail("Timed out waiting for \(approvalKey), cycle \(cycle)")
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

    private func logRecoveryUiState(
        cycle: String,
        step: String,
        phase: String,
        secretName: String,
        alert: XCUIElement,
        badge: XCUIElement,
        open: XCUIElement
    ) {
        print(
            "E2E: IOS_RECOVERY_UI_STATE_\(cycle)_\(step) phase=\(phase) "
                + "secret=\(secretName) "
                + "alert=\(alert.exists) badge=\(badge.exists) "
                + "open=\(open.exists) openHittable=\(open.isHittable)"
        )
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
                do {
                    close.tap()
                } catch {
                    // A live recovery update can invalidate the accessibility
                    // element after the hittable check. Re-read the dialog
                    // and retry only while that dialog remains on screen.
                    print(
                        "E2E: IOS_SHOW_SECRET_CLOSE_STALE_\(tapAttempts) " +
                            "dialogExists=\(dialog.exists)"
                    )
                    if !dialog.exists {
                        return
                    }
                    continue
                }
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
           let scenario = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            if let expected = scenario["joinExpectedSecretNames"] as? [String], !expected.isEmpty {
                return expected
            }
            guard let raw = scenario["secretConfig"] as? [String: Any] else {
                return [fallback]
            }
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

    private func exactDeviceRowIdentifier(name: String, status: String) -> String? {
        let rows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "device-row-")
        )
        var matchingRows: [XCUIElement] = []
        for index in 0..<rows.count {
            let row = rows.element(boundBy: index)
            if exactVisibleText(name, belongsTo: row)
                && exactVisibleText(status, belongsTo: row) {
                matchingRows.append(row)
            }
        }
        guard matchingRows.count == 1 else { return nil }
        let row = matchingRows[0]
        guard row.identifier.hasPrefix("device-row-") else { return nil }
        let deviceId = String(row.identifier.dropFirst("device-row-".count))
        return deviceId.isEmpty ? nil : deviceId
    }

    private func test37RemoveActionQuery(deviceId: String) -> XCUIElementQuery {
        app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier == %@", "remove-device-\(deviceId)")
        )
    }

    private func test39SwipeAndConfirmRemoval(deviceId: String, targetDeviceName: String) {
        let row = app.descendants(matching: .any)["device-row-\(deviceId)"]
        XCTAssertTrue(row.waitForExistence(timeout: 30), "Test #39 removal row is missing")
        row.swipeLeft()

        let action = test37RemoveActionQuery(deviceId: deviceId).firstMatch
        XCTAssertTrue(action.waitForExistence(timeout: 30), "Test #39 swipe did not expose Remove")
        action.tap()
        waitForVisible(identifier: "remove-device-dialog", timeout: 30)
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let exactLocalizedTitles = [
            "Remove device \(targetDeviceName)?",
            "Удалить устройство \(targetDeviceName)?",
        ]
        let matchingTitles = exactLocalizedTitles.filter {
            exactVisibleText($0, belongsTo: removalDialog)
        }
        XCTAssertEqual(
            matchingTitles.count,
            1,
            "Test #39 removal dialog must expose one exact localized composite title"
        )
        tap("remove-device-dialog-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "remove-device-dialog", timeout: 120)
    }

    private func test47SwipeAndConfirmRemoval(deviceId: String, targetDeviceName: String) {
        let row = app.descendants(matching: .any)["device-row-\(deviceId)"]
        XCTAssertTrue(row.waitForExistence(timeout: 30), "Test #47 removal row is missing")
        row.swipeLeft()

        let action = test37RemoveActionQuery(deviceId: deviceId).firstMatch
        XCTAssertTrue(action.waitForExistence(timeout: 30), "Test #47 swipe did not expose Remove")
        action.tap()
        waitForVisible(identifier: "remove-device-dialog", timeout: 30)
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let exactLocalizedTitles = [
            "Remove device \(targetDeviceName)?",
            "Удалить устройство \(targetDeviceName)?",
        ]
        let matchingTitles = exactLocalizedTitles.filter {
            exactVisibleText($0, belongsTo: removalDialog)
        }
        XCTAssertEqual(
            matchingTitles.count,
            1,
            "Test #47 removal dialog must expose one exact localized composite title"
        )
        tap("remove-device-dialog-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "remove-device-dialog", timeout: 120)
    }

    private func test48SwipeAndConfirmRemoval(deviceId: String, targetDeviceName: String) {
        let row = app.descendants(matching: .any)["device-row-\(deviceId)"]
        XCTAssertTrue(row.waitForExistence(timeout: 30), "Test #48 removal row is missing")
        row.swipeLeft()

        let action = test37RemoveActionQuery(deviceId: deviceId).firstMatch
        XCTAssertTrue(action.waitForExistence(timeout: 30), "Test #48 swipe did not expose Remove")
        action.tap()
        waitForVisible(identifier: "remove-device-dialog", timeout: 30)
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let exactLocalizedTitles = [
            "Remove device \(targetDeviceName)?",
            "Удалить устройство \(targetDeviceName)?",
        ]
        let matchingTitles = exactLocalizedTitles.filter {
            exactVisibleText($0, belongsTo: removalDialog)
        }
        XCTAssertEqual(
            matchingTitles.count,
            1,
            "Test #48 removal dialog must expose one exact localized composite title"
        )
        tap("remove-device-dialog-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "remove-device-dialog", timeout: 120)
    }

    private func test49SwipeAndConfirmRemoval(deviceId: String, targetDeviceName: String) {
        let row = app.descendants(matching: .any)["device-row-\(deviceId)"]
        XCTAssertTrue(row.waitForExistence(timeout: 30), "Test #49 removal row is missing")
        row.swipeLeft()

        let action = test37RemoveActionQuery(deviceId: deviceId).firstMatch
        XCTAssertTrue(action.waitForExistence(timeout: 30), "Test #49 swipe did not expose Remove")
        action.tap()
        waitForVisible(identifier: "remove-device-dialog", timeout: 30)
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let exactLocalizedTitles = [
            "Remove device \(targetDeviceName)?",
            "Удалить устройство \(targetDeviceName)?",
        ]
        let matchingTitles = exactLocalizedTitles.filter {
            exactVisibleText($0, belongsTo: removalDialog)
        }
        XCTAssertEqual(
            matchingTitles.count,
            1,
            "Test #49 removal dialog must expose one exact localized composite title"
        )
        tap("remove-device-dialog-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "remove-device-dialog", timeout: 120)
    }

    private func test40SwipeAndConfirmRemoval(deviceId: String, targetDeviceName: String) {
        let row = app.descendants(matching: .any)["device-row-\(deviceId)"]
        XCTAssertTrue(row.waitForExistence(timeout: 30), "Test #40 removal row is missing")
        row.swipeLeft()

        let action = test37RemoveActionQuery(deviceId: deviceId).firstMatch
        XCTAssertTrue(action.waitForExistence(timeout: 30), "Test #40 swipe did not expose Remove")
        action.tap()
        waitForVisible(identifier: "remove-device-dialog", timeout: 30)
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let exactLocalizedTitles = [
            "Remove device \(targetDeviceName)?",
            "Удалить устройство \(targetDeviceName)?",
        ]
        let matchingTitles = exactLocalizedTitles.filter {
            exactVisibleText($0, belongsTo: removalDialog)
        }
        XCTAssertEqual(
            matchingTitles.count,
            1,
            "Test #40 removal dialog must expose one exact localized composite title"
        )
        tap("remove-device-dialog-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "remove-device-dialog", timeout: 120)
    }

    private func test42SwipeAndConfirmRemoval(deviceId: String, targetDeviceName: String) {
        let row = app.descendants(matching: .any)["device-row-\(deviceId)"]
        XCTAssertTrue(row.waitForExistence(timeout: 30), "Test #42 removal row is missing")
        row.swipeLeft()

        let action = test37RemoveActionQuery(deviceId: deviceId).firstMatch
        XCTAssertTrue(action.waitForExistence(timeout: 30), "Test #42 swipe did not expose Remove")
        action.tap()
        waitForVisible(identifier: "remove-device-dialog", timeout: 30)
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let exactLocalizedTitles = [
            "Remove device \(targetDeviceName)?",
            "Удалить устройство \(targetDeviceName)?",
        ]
        let matchingTitles = exactLocalizedTitles.filter {
            exactVisibleText($0, belongsTo: removalDialog)
        }
        XCTAssertEqual(
            matchingTitles.count,
            1,
            "Test #42 removal dialog must expose one exact localized composite title"
        )
        tap("remove-device-dialog-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "remove-device-dialog", timeout: 120)
    }

    private func test43SwipeAndConfirmRemoval(deviceId: String, targetDeviceName: String) {
        let row = app.descendants(matching: .any)["device-row-\(deviceId)"]
        XCTAssertTrue(row.waitForExistence(timeout: 30), "Test #43 removal row is missing")
        row.swipeLeft()

        let action = test37RemoveActionQuery(deviceId: deviceId).firstMatch
        XCTAssertTrue(action.waitForExistence(timeout: 30), "Test #43 swipe did not expose Remove")
        action.tap()
        waitForVisible(identifier: "remove-device-dialog", timeout: 30)
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let exactLocalizedTitles = [
            "Remove device \(targetDeviceName)?",
            "Удалить устройство \(targetDeviceName)?",
        ]
        let matchingTitles = exactLocalizedTitles.filter {
            exactVisibleText($0, belongsTo: removalDialog)
        }
        XCTAssertEqual(
            matchingTitles.count,
            1,
            "Test #43 removal dialog must expose one exact localized composite title"
        )
        tap("remove-device-dialog-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "remove-device-dialog", timeout: 120)
    }

    private func test44SwipeAndConfirmRemoval(deviceId: String, targetDeviceName: String) {
        let row = app.descendants(matching: .any)["device-row-\(deviceId)"]
        XCTAssertTrue(row.waitForExistence(timeout: 30), "Test #44 removal row is missing")
        row.swipeLeft()

        let action = test37RemoveActionQuery(deviceId: deviceId).firstMatch
        XCTAssertTrue(action.waitForExistence(timeout: 30), "Test #44 swipe did not expose Remove")
        action.tap()
        waitForVisible(identifier: "remove-device-dialog", timeout: 30)
        let removalDialog = app.descendants(matching: .any)["remove-device-dialog"]
        let exactLocalizedTitles = [
            "Remove device \(targetDeviceName)?",
            "Удалить устройство \(targetDeviceName)?",
        ]
        let matchingTitles = exactLocalizedTitles.filter {
            exactVisibleText($0, belongsTo: removalDialog)
        }
        XCTAssertEqual(
            matchingTitles.count,
            1,
            "Test #44 removal dialog must expose one exact localized composite title"
        )
        tap("remove-device-dialog-accept")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "remove-device-dialog", timeout: 120)
    }

    private func exactVisibleText(_ expected: String, belongsTo row: XCUIElement) -> Bool {
        if accessibilityLabelComponents(row.label).contains(expected) {
            return true
        }
        let staticTexts = app.staticTexts
        for index in 0..<staticTexts.count {
            let text = staticTexts.element(boundBy: index)
            if text.exists
                && exactStaticAccessibilityLabel(text.label, expected: expected)
                && row.frame.intersects(text.frame)
                && app.frame.intersects(text.frame) {
                return true
            }
        }
        return false
    }

    private func accessibilityLabelComponents(_ label: String) -> [String] {
        newlineSeparatedAccessibilityLabels(label).flatMap { line in
            line.components(separatedBy: ", ")
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
        }
    }

    private func exactStaticAccessibilityLabel(_ label: String, expected: String) -> Bool {
        label == expected || newlineSeparatedAccessibilityLabels(label).contains(expected)
    }

    private func newlineSeparatedAccessibilityLabels(_ label: String) -> [String] {
        label
            .split(whereSeparator: { $0 == "\r" || $0 == "\n" })
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }

    private func assertTest37DeviceLabelMatchingContract() {
        let composite = accessibilityLabelComponents(
            "vault, Current, iPhone18,3, 3 secrets"
        )
        XCTAssertEqual(composite, ["vault", "Current", "iPhone18,3", "3 secrets"])
        XCTAssertTrue(composite.contains("iPhone18,3"))
        XCTAssertFalse(composite.contains("iPhone18"))
        XCTAssertFalse(composite.contains("iPhone18,4"))
        XCTAssertTrue(exactStaticAccessibilityLabel("iPhone18,3", expected: "iPhone18,3"))
        XCTAssertTrue(
            exactStaticAccessibilityLabel(
                "vault\nCurrent\niPhone18,3\n3 secrets",
                expected: "iPhone18,3"
            )
        )
        XCTAssertFalse(exactStaticAccessibilityLabel("iPhone18,3", expected: "iPhone18"))
        XCTAssertFalse(exactStaticAccessibilityLabel("iPhone18,3", expected: "iPhone18,4"))
    }

    private func emitTest37DeviceTopologyDiagnosticsOnce(deviceRows: XCUIElementQuery) {
        guard !test37DeviceTopologyDiagnosticsEmitted else { return }
        test37DeviceTopologyDiagnosticsEmitted = true
        print("E2E_DIAGNOSTIC: IOS_TEST37_DEVICE_TOPOLOGY rowCount=\(deviceRows.count)")
        let staticTexts = app.staticTexts
        for rowIndex in 0..<deviceRows.count {
            let row = deviceRows.element(boundBy: rowIndex)
            print(
                "E2E_DIAGNOSTIC: IOS_TEST37_DEVICE_ROW index=\(rowIndex) "
                    + "identifier=\(diagnosticText(row.identifier)) "
                    + "label=\(diagnosticText(row.label)) frame=\(diagnosticFrame(row.frame))"
            )
            for textIndex in 0..<staticTexts.count {
                let text = staticTexts.element(boundBy: textIndex)
                guard text.exists,
                      app.frame.intersects(text.frame),
                      row.frame.intersects(text.frame)
                else { continue }
                print(
                    "E2E_DIAGNOSTIC: IOS_TEST37_DEVICE_ROW_TEXT rowIndex=\(rowIndex) "
                        + "label=\(diagnosticText(text.label)) frame=\(diagnosticFrame(text.frame))"
                )
            }
        }
    }

    private func diagnosticText(_ value: String) -> String {
        let escaped = value
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\r", with: "\\r")
            .replacingOccurrences(of: "\n", with: "\\n")
        return "\"\(escaped.replacingOccurrences(of: "\"", with: "\\\""))\""
    }

    private func diagnosticFrame(_ frame: CGRect) -> String {
        "{x:\(frame.origin.x),y:\(frame.origin.y),w:\(frame.size.width),h:\(frame.size.height)}"
    }

    private func stepConfig() -> StepConfig { Self.readStepConfig() }

    nonisolated private static func readStepConfig() -> StepConfig {
        guard
            let data = FileManager.default.contents(atPath: Self.stepConfigPath),
            let config = try? JSONDecoder().decode(StepConfig.self, from: data)
        else {
            return StepConfig(coreServerUrl: nil, requireCoreRouting: nil, expectRequest: nil, role: nil, cycle: nil, step: nil, approvalPlatform: nil, sender: nil, secretName: nil, secretValue: nil, action: nil, expectedOutcome: nil, repeatApprove: nil, duplicateRecovery: nil, verifyReceiverAlert: nil, receiverRequestAlreadySent: nil, networkRole: nil, networkCycles: nil, expectedWebDeviceName: nil, expectedAndroidDeviceName: nil, expectedIosDeviceName: nil, joinTarget: nil)
        }
        return config
    }
}

private extension XCUIElement {
    func tapIfExists() {
        if exists && isHittable { tap() }
    }
}
