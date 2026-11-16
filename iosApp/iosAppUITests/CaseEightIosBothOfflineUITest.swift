import XCTest
import Foundation
import CoreImage

@MainActor
final class CaseEightIosBothOfflineUITest: XCTestCase {
    private struct StepConfig: Decodable, Sendable {
        let coreServerUrl: String?
        let devicePasscode: String?
        let requireCoreRouting: Bool?
        let authProfile: String?
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
        let recoveryPhase: String?
        let recoveryPhaseRequired: Bool?
        let recoveryPhaseSelectedProfile: String?
        let recoveryPhaseExpected: String?
        let networkRole: String?
        let networkCycles: String?
        let expectedWebDeviceName: String?
        let expectedAndroidDeviceName: String?
        let expectedIosDeviceName: String?
        let joinTarget: String?
        let test22ShowPhase: Test22ShowPhaseContext?
        let test25ShowPhase: String?
        let recoveryShowPhase: String?
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

    private struct RegistrationCase51: Decodable {
        let id: String
        let runId: String
        let actor: String
        let platform: String
        let provider: String
        let vaultName: String
        let retryVaultName: String?
        let outcome: String?
        let decision: String?
        let stage: String?
        let markerPrefix: String
    }

    /// Test51 uses the existing target-included source and persisted actor configuration.
    /// Real provider account authorization and physical camera input are never replaced.
    func handleRegistrationForTest51() throws {
        struct Payload: Decodable { let registrationCase: RegistrationCase51 }
        let config = try JSONDecoder().decode(Payload.self,
            from: Data(contentsOf: URL(fileURLWithPath: Self.stepConfigPath))).registrationCase
        XCTAssertEqual(config.platform, "ios")
        XCTAssertTrue(config.markerPrefix.hasPrefix("TEST51_"))
        XCTAssertTrue(["manual", "apple", "google"].contains(config.provider), "Unsupported registration provider")
        switch config.actor {
        case "create":
            registration51ReadyInitial(config)
            registration51EnterName(config.vaultName, available: true, provider: config.provider)
            registration51Marker(config, "AVAILABLE")
            app.orbitCreateVault()
            registration51Marker(config, "CREATED")
            try registration51GeneratedQr(config)
        case "requester":
            try registration51Requester(config)
        case "responder":
            registration51Responder(config)
        default:
            XCTFail("Unsupported Test51 registration actor")
        }
    }

    private func registration51Marker(_ config: RegistrationCase51, _ action: String) {
        print("E2E: \(config.markerPrefix)_\(action)")
    }

    private func registration51ReadyInitial(_ config: RegistrationCase51) {
        let skip = app.descendants(matching: .any)["onboarding-skip"]
        let enter = app.descendants(matching: .any)["orbit-enter"]
        let landing = NSPredicate { _, _ in skip.exists || enter.exists }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: landing, object: nil)], timeout: 30), .completed,
                       "Test51 expected initial provider screen01")
        if skip.exists { app.orbitTap("onboarding-skip") }
        app.orbitWaitForTag("orbit-transition-ready")
        app.orbitWaitForTag("orbit-apple")
        app.orbitWaitForTag("orbit-google")
        XCTAssertEqual(app.descendants(matching: .any)["orbit-heading"].label, "MetaSecret")
        XCTAssertFalse(app.descendants(matching: .any)["orbit-email-input"].exists)
        XCTAssertFalse(app.descendants(matching: .any)["orbit-create"].exists)
        registration51Marker(config, "READY")
    }

    private func registration51EnterName(_ name: String, available: Bool, provider: String = "manual") {
        if provider == "manual" { app.orbitEnterVaultName(name) }
        else {
            // OS consent uses real accounts only; owner/manual handoff or preseeded account completes it.
            app.orbitTap("orbit-\(provider)")
            let resolved = NSPredicate { _, _ in
                self.app.descendants(matching: .any)["orbit-phase-idle"].exists
                    && self.app.descendants(matching: .any)["orbit-create"].exists
                    && self.app.descendants(matching: .any)["orbit-heading"].label == name
            }
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: resolved, object: nil)], timeout: 120), .completed,
                           "Real provider consent did not resolve the configured fixture Vault email")
        }
        app.orbitWaitForTag("orbit-transition-ready")
        XCTAssertEqual(app.descendants(matching: .any)["orbit-heading"].label, name)
        XCTAssertEqual(app.descendants(matching: .any)["orbit-create"].isEnabled, available,
                       "Create availability must reflect Core's Vault lookup")
        app.orbitWaitForTag("orbit-join")
        XCTAssertEqual(app.descendants(matching: .any)["orbit-join"].isEnabled, !available,
                       "Join must be enabled only when Core reports an existing Vault")
    }

    private func registration51Requester(_ config: RegistrationCase51) throws {
        registration51ReadyInitial(config)
        registration51EnterName(config.vaultName, available: false, provider: config.provider)
        registration51Marker(config, "TAKEN")
        app.orbitRequestJoin()
        registration51Marker(config, "PENDING")
        if config.outcome == "accepted" {
            app.orbitWaitForTag("tab-secrets", timeout: 180)
            registration51Marker(config, "MAIN")
            return
        }
        XCTAssertTrue(config.outcome == "declined-retry" || config.outcome == "back-retry")
        // Host proves positive scoped key count before allowing either incomplete-attempt Back.
        waitForApproval(platform: "registration-\(config.id)-cleanup-presence-verified", cycle: "0")
        if config.outcome == "declined-retry" {
            app.orbitWaitForTag("orbit-phase-declined", timeout: 180)
            registration51Marker(config, "DECLINED")
        }
        app.orbitTap("orbit-back")
        let reset = NSPredicate { _, _ in
            self.app.descendants(matching: .any)["orbit-transition-ready"].exists
                && self.app.descendants(matching: .any)["orbit-apple"].exists
                && self.app.descendants(matching: .any)["orbit-google"].exists
                && self.app.descendants(matching: .any)["orbit-heading"].label == "MetaSecret"
        }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: reset, object: nil)], timeout: 30), .completed,
                       "Incomplete-attempt Back must clear registration and return to initial01")
        // The host checks zero scoped key count before RESET01 and before another name is entered.
        registration51Marker(config, "RESET_UI01")
        waitForApproval(platform: "registration-\(config.id)-reset-verified", cycle: "0")
        registration51Marker(config, "RESET01")
        guard let retryName = config.retryVaultName, !retryName.isEmpty, retryName != config.vaultName else {
            XCTFail("Test51 retry requires a different explicit existing Vault name"); return
        }
        registration51EnterName(retryName, available: false)
        app.orbitRequestJoin()
        registration51Marker(config, "RETRY_PENDING")
        app.orbitWaitForTag("tab-secrets", timeout: 180)
        registration51Marker(config, "MAIN")
    }

    private func registration51Responder(_ config: RegistrationCase51) {
        enterSimulatorPasscodeIfNeeded()
        tap("tab-devices", timeout: 60)
        XCTAssertFalse(app.descendants(matching: .any)["pending-device-row"].exists, "Responder has stale pending UI")
        XCTAssertFalse(app.descendants(matching: .any)["alert-join-request"].exists, "Responder has stale approval dialog")
        registration51Marker(config, "READY")
        waitForApproval(platform: "registration-\(config.id)-\(config.stage ?? "primary")", cycle: "0")
        waitForVisible(identifier: "pending-device-row", timeout: 180)
        let pendingRows = app.descendants(matching: .any).matching(NSPredicate(format: "identifier == %@", "pending-device-row"))
        guard pendingRows.count == 1 else { XCTFail("Isolated Test51 requires exactly one pending candidate"); return }
        let name = app.descendants(matching: .any)["pending-device-name"].label
        XCTAssertFalse(name.isEmpty, "Core candidate name was empty")
        let rows = registration51VisibleDeviceRows()
        let currentRows = rows.filter { exactVisibleText("Current", belongsTo: $0) }
        guard currentRows.count == 1 else { XCTFail("Receiving Current row must be uniquely visible"); return }
        let currentRowIdentifier = currentRows[0].identifier
        XCTAssertTrue(exactVisibleText(config.vaultName, belongsTo: pendingRows.element(boundBy: 0)))
        XCTAssertTrue(exactVisibleText(name, belongsTo: pendingRows.element(boundBy: 0)))
        XCTAssertFalse(rows.contains { exactVisibleText(name, belongsTo: $0) }, "Candidate name must not ambiguously match an existing device")
        tap("pending-device-row")
        waitForVisible(identifier: "alert-join-request", timeout: 30)
        let accepted = config.decision == "accepted"
        tap(accepted ? "alert-join-request-accept" : "alert-join-request-decline")
        enterSimulatorPasscodeIfNeeded()
        waitForHidden(identifier: "alert-join-request", timeout: 120)
        waitForHidden(identifier: "pending-device-row", timeout: 120)
        let terminal = NSPredicate { _, _ in
            if accepted { return self.exactDeviceRowIdentifier(name: name, status: "Member") != nil }
            // Core client_view filters Declined outsiders. Preserve the visible receiver as a non-empty AX witness.
            let visibleRows = self.registration51VisibleDeviceRows()
            return self.app.descendants(matching: .any)["tab-devices"].exists
                && visibleRows.contains { $0.identifier == currentRowIdentifier }
                && !visibleRows.contains { self.exactVisibleText(name, belongsTo: $0) }
                && !self.app.descendants(matching: .any)["pending-device-row"].exists
        }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: terminal, object: nil)], timeout: 120), .completed,
                       "The correlated Core candidate did not reach the expected client-view postcondition")
        registration51Marker(config, "DECIDED")
    }

    private func registration51VisibleDeviceRows() -> [XCUIElement] {
        app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "device-row-"))
            .allElementsBoundByIndex.filter { $0.exists && !$0.frame.isEmpty && self.app.frame.intersects($0.frame) }
    }

    private func registration51GeneratedQr(_ config: RegistrationCase51) throws {
        tap("tab-devices", timeout: 60)
        tap("add-device-fab")
        waitForVisible(identifier: "add-device-dialog", timeout: 30)
        let dialog = app.descendants(matching: .any)["add-device-dialog"]
        let name = app.descendants(matching: .any)["vault-qr-name"]
        let qr = app.descendants(matching: .any)["vault-name-qr"]
        waitForVisible(identifier: "vault-qr-name", timeout: 30)
        waitForVisible(identifier: "vault-name-qr", timeout: 30)
        let viewport = dialog.frame.intersection(app.windows.firstMatch.frame)
        // AddingDevice renders a fixed164dp square. AX reports only its clipped
        // visible frame, so containment alone cannot prove the entire code is shown.
        let expectedQrSide: CGFloat = 164
        func fullyVisible() -> Bool {
            guard dialog.exists, name.exists, qr.exists else { return false }
            return !viewport.isEmpty && qr.frame.width > 0 && qr.frame.height > 0
                && abs(qr.frame.width - expectedQrSide) <= 0.5
                && abs(qr.frame.height - expectedQrSide) <= 0.5
                && abs(qr.frame.width - qr.frame.height) <= 0.5
                && name.frame.width > 0 && name.frame.height > 0
                && viewport.contains(name.frame) && viewport.contains(qr.frame)
        }
        // Compose exposes the dismiss backdrop and sheet as AX siblings, without
        // a native ScrollView. Start inside the observed QR pixels, never the backdrop.
        for attempt in 1...8 {
            if fullyVisible() { break }
            let before = qr.frame
            let visibleQr = before.intersection(viewport)
            let visibleName = name.frame.intersection(viewport)
            guard !visibleQr.isEmpty, !visibleName.isEmpty,
                  visibleQr.midY > visibleName.midY + 12 else {
                XCTFail("Clipped QR has no safe visible content anchors for an upward gesture"); return
            }
            let origin = app.coordinate(withNormalizedOffset: .zero)
            let start = origin.withOffset(CGVector(dx: visibleQr.midX - app.frame.minX,
                                                  dy: visibleQr.midY - app.frame.minY))
            let end = origin.withOffset(CGVector(dx: visibleName.midX - app.frame.minX,
                                                dy: visibleName.midY - app.frame.minY))
            start.press(forDuration: 0.05, thenDragTo: end)
            XCTAssertTrue(dialog.exists, "Content drag unexpectedly dismissed the Add-device sheet")
            let moved = NSPredicate { _, _ in qr.exists && qr.frame.minY < before.minY - 1 }
            let progress = XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: moved, object: nil)], timeout: 5)
            print("E2E: IOS_TEST51_QR_SCROLL attempt=\(attempt) before=\(diagnosticFrame(before)) after=\(diagnosticFrame(qr.frame)) moved=\(progress == .completed)")
            guard progress == .completed else {
                XCTFail("QR content did not move after its anchored drag; refusing an unchanged retry"); return
            }
        }
        print("E2E: IOS_TEST51_QR_GEOMETRY viewport=\(diagnosticFrame(viewport)) name=\(diagnosticFrame(name.frame)) qr=\(diagnosticFrame(qr.frame)) fullyVisible=\(fullyVisible())")
        XCTAssertTrue(fullyVisible(), "Generated Vault QR/name remained clipped after bounded dialog scrolling")
        XCTAssertEqual(name.label, config.vaultName)
        let capture = qr.screenshot()
        let attachment = XCTAttachment(screenshot: capture)
        attachment.name = "Test51 actual fully visible Vault QR"
        attachment.lifetime = .keepAlways
        add(attachment)
        guard let image = capture.image.cgImage else { XCTFail("QR screenshot unavailable"); return }
        // QRKit's existing iOS image-scanning path also uses CIDetector. CPU
        // decoding is verified offline on actual QR pixels in this simulator.
        let context = CIContext(options: [.useSoftwareRenderer: true])
        guard let detector = CIDetector(ofType: CIDetectorTypeQRCode, context: context,
            options: [CIDetectorAccuracy: CIDetectorAccuracyHigh]) else {
            XCTFail("QR CPU decoder unavailable"); return
        }
        let decoded = detector.features(in: CIImage(cgImage: image))
            .compactMap { ($0 as? CIQRCodeFeature)?.messageString }
        // Boolean comparison avoids logging any unexpected decoded payload in an assertion diff.
        XCTAssertTrue(decoded.count == 1 && decoded[0] == config.vaultName,
                      "Real generated QR must decode to exactly the canonical Vault name")
        registration51Marker(config, "QR_GENERATED")
    }

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.lbResetSelectors()
        let routing = Self.readStepConfig()
        if TestRecoveryProfile.future(routing.recoveryPhaseSelectedProfile) != nil || routing.recoveryShowPhase != nil {
            _ = try RecoveryShowPhaseContext.readEntry(RecoveryShowEntry(raw: routing.recoveryShowPhase,
                phase: Test24PhaseEntry(raw: routing.recoveryPhase, required: routing.recoveryPhaseRequired == true,
                    selectedProfile: routing.recoveryPhaseSelectedProfile, expected: routing.recoveryPhaseExpected),
                current: RecoveryShowCurrent(role: routing.role, cycle: routing.cycle, step: routing.step,
                    hasLegacy22: routing.test22ShowPhase != nil, hasLiteral25: routing.test25ShowPhase != nil)))
        }
        if routing.recoveryPhaseSelectedProfile == "test25:web-secondary-creator-lifecycle" || routing.test25ShowPhase != nil {
            _ = try Test25ShowPhaseContext.readEntry(Test25ShowEntry(raw: routing.test25ShowPhase,
                phase: Test24PhaseEntry(raw: routing.recoveryPhase, required: routing.recoveryPhaseRequired == true,
                    selectedProfile: routing.recoveryPhaseSelectedProfile, expected: routing.recoveryPhaseExpected),
                current: Test25ShowCurrent(role: routing.role, cycle: routing.cycle, step: routing.step,
                    hasLegacy22: routing.test22ShowPhase != nil)))
        }
        let authProfile = routing.authProfile ?? "real-os"
        if let required = ProcessInfo.processInfo.environment["E2E_AUTH_PROFILE"] {
            guard required == authProfile && routing.requireCoreRouting == true else {
                throw NSError(domain: "MetaSecretTest50ProfileBinding", code: 1)
            }
        }
        if routing.authProfile != nil && routing.requireCoreRouting != true {
            throw NSError(domain: "MetaSecretTest50Profile", code: 1)
        }
        if authProfile == "controlled-business" {
#if !targetEnvironment(simulator)
            throw NSError(domain: "MetaSecretTest50SimulatorRequired", code: 1)
#endif
            guard routing.devicePasscode == nil else { throw NSError(domain: "MetaSecretTest50NoCredentials", code: 1) }
        }
        app.lbApplyTestIdentity(requireRealOs: routing.requireCoreRouting == true)
        if let passcode = routing.devicePasscode {
            guard passcode.range(of: "^[0-9]{4,16}$", options: .regularExpression) != nil else {
                throw NSError(domain: "MetaSecretTestDevicePIN", code: 1,
                    userInfo: [NSLocalizedDescriptionKey: "Declared E2E PIN is invalid (value omitted)"])
            }
            app.lbSelection.devicePasscode = passcode
        }
        // Default Test50 keeps real auth; the explicit business profile is independent of Core routing.
        if routing.requireCoreRouting != true || authProfile == "controlled-business" { app.launchEnvironment["METASECRET_UI_TEST_MODE"] = "true" }
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

    override func tearDownWithError() throws {
        app?.lbSelection.devicePasscode = nil
        try super.tearDownWithError()
    }

    func joinWebInitiatedVault() throws {
        let vaultName = coordinatorVaultName(defaultValue: "test8@test.ru")

        app.orbitEnterVaultName(vaultName)
        app.orbitRequestJoin()
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

        app.orbitEnterVaultName(vaultName)
        app.orbitRequestJoin()
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

        app.orbitEnterVaultName(vaultName)
        app.orbitRequestJoin()
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

        app.orbitEnterVaultName(vaultName)
        app.orbitRequestJoin()
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

        app.orbitEnterVaultName(vaultName)
        app.orbitRequestJoin()
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

        app.orbitEnterVaultName(vaultName)
        app.orbitRequestJoin()
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

        app.orbitEnterVaultName(vaultName)
        app.orbitRequestJoin()
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

        app.orbitEnterVaultName(vaultName)
        app.orbitRequestJoin()
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

        app.orbitEnterVaultName(vaultName)
        app.orbitRequestJoin()
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
                app.lbElement("recovery-request-badge-\(secretName)").exists,
                "iOS had a stale recovery badge before Test #41 removal"
            )
            XCTAssertFalse(
                app.lbElement("open-recovery-request-\(secretName)").exists,
                "iOS had a stale recovery action before Test #41 removal"
            )
        }

        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let recoveryAlert = app.lbElement("alert-recovery-request")
        let recoveryProcessing = app.lbElement("alert-recovery-request-processing")
        let joinAlert = app.lbElement("alert-join-request")
        let joinProcessing = app.lbElement("alert-join-request-processing")
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
            return !self.app.lbCanRemove(iosDeviceId)
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
        XCTAssertFalse(
            app.lbCanRemove(iosDeviceId),
            "iOS Current row exposed an invalid self-remove action"
        )
        print("E2E: IOS_TEST41_REMOVAL_TARGET_READY")

        let manualSignIn = app.lbElement("orbit-enter")
        let secretsTab = app.lbElement("tab-secrets")
        let devicesTab = app.lbElement("tab-devices")
        let orbitReady = app.lbElement("orbit-transition-ready")
        let orbitIdle = app.lbElement("orbit-phase-idle")
        let removed = NSPredicate { _, _ in
            orbitReady.exists
                && orbitIdle.exists
                && !secretsTab.exists
                && !devicesTab.exists
                && deviceRows.count == 0
                && secretNames.allSatisfy {
                    !self.app.lbElement("secret-row-\($0)").exists
                }
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !recoveryAlert.exists
                && !recoveryProcessing.exists
                && !joinAlert.exists
                && !joinProcessing.exists
                && !self.app.lbElement("remove-device-\(webDeviceId)").exists
                && !self.app.lbElement("remove-device-\(androidDeviceId)").exists
                && !self.app.lbElement("remove-device-\(iosDeviceId)").exists
                && self.app.lbVisible(manualSignIn)
                && manualSignIn.isHittable
        }
        let removedExpectation = XCTNSPredicateExpectation(predicate: removed, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [removedExpectation], timeout: 180),
            .completed,
            "iOS did not reset to authentication with the Test #41 Vault UI removed"
        )
        XCTAssertTrue(app.lbVisible(manualSignIn) && manualSignIn.isHittable)
        XCTAssertFalse(secretsTab.exists || devicesTab.exists)
        XCTAssertTrue(secretNames.allSatisfy {
            !app.lbElement("secret-row-\($0)").exists
        })
        XCTAssertFalse(app.lbElement("remove-device-\(webDeviceId)").exists)
        XCTAssertFalse(app.lbElement("remove-device-\(androidDeviceId)").exists)
        XCTAssertFalse(app.lbElement("remove-device-\(iosDeviceId)").exists)
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
                app.lbElement("recovery-request-badge-\(secretName)").exists,
                "iOS had a stale recovery badge before Test #45 removal"
            )
            XCTAssertFalse(
                app.lbElement("open-recovery-request-\(secretName)").exists,
                "iOS had a stale recovery action before Test #45 removal"
            )
        }

        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let recoveryAlert = app.lbElement("alert-recovery-request")
        let recoveryProcessing = app.lbElement("alert-recovery-request-processing")
        let joinAlert = app.lbElement("alert-join-request")
        let joinProcessing = app.lbElement("alert-join-request-processing")
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
            return !self.app.lbCanRemove(iosDeviceId)
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
        XCTAssertFalse(
            app.lbCanRemove(iosDeviceId),
            "iOS Current row exposed an invalid self-remove action"
        )
        print("E2E: IOS_TEST45_REMOVAL_TARGET_READY")

        let manualSignIn = app.lbElement("orbit-enter")
        let secretsTab = app.lbElement("tab-secrets")
        let devicesTab = app.lbElement("tab-devices")
        let orbitReady = app.lbElement("orbit-transition-ready")
        let orbitIdle = app.lbElement("orbit-phase-idle")
        let removed = NSPredicate { _, _ in
            orbitReady.exists
                && orbitIdle.exists
                && !secretsTab.exists
                && !devicesTab.exists
                && deviceRows.count == 0
                && secretNames.allSatisfy {
                    !self.app.lbElement("secret-row-\($0)").exists
                }
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !recoveryAlert.exists
                && !recoveryProcessing.exists
                && !joinAlert.exists
                && !joinProcessing.exists
                && !self.app.lbElement("remove-device-\(webDeviceId)").exists
                && !self.app.lbElement("remove-device-\(androidDeviceId)").exists
                && !self.app.lbElement("remove-device-\(iosDeviceId)").exists
                && self.app.lbVisible(manualSignIn)
                && manualSignIn.isHittable
        }
        let removedExpectation = XCTNSPredicateExpectation(predicate: removed, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [removedExpectation], timeout: 180),
            .completed,
            "iOS did not reset to authentication with the Test #45 Vault UI removed"
        )
        XCTAssertTrue(app.lbVisible(manualSignIn) && manualSignIn.isHittable)
        XCTAssertFalse(secretsTab.exists || devicesTab.exists)
        XCTAssertTrue(secretNames.allSatisfy {
            !app.lbElement("secret-row-\($0)").exists
        })
        XCTAssertFalse(app.lbElement("remove-device-\(webDeviceId)").exists)
        XCTAssertFalse(app.lbElement("remove-device-\(androidDeviceId)").exists)
        XCTAssertFalse(app.lbElement("remove-device-\(iosDeviceId)").exists)
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
                app.lbElement("recovery-request-badge-\(secretName)").exists,
                "iOS had a stale recovery badge before Test #37 removal"
            )
            XCTAssertFalse(
                app.lbElement("open-recovery-request-\(secretName)").exists,
                "iOS had a stale recovery action before Test #37 removal"
            )
        }

        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let recoveryAlert = app.lbElement("alert-recovery-request")
        let recoveryProcessing = app.lbElement("alert-recovery-request-processing")
        let joinAlert = app.lbElement("alert-join-request")
        let joinProcessing = app.lbElement("alert-join-request-processing")
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
            return !self.app.lbCanRemove(iosDeviceId)
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
        XCTAssertFalse(
            app.lbCanRemove(iosDeviceId),
            "iOS Current row exposed an invalid self-remove action"
        )
        print("E2E: IOS_TEST37_REMOVAL_TARGET_READY")

        let manualSignIn = app.lbElement("orbit-enter")
        let secretsTab = app.lbElement("tab-secrets")
        let devicesTab = app.lbElement("tab-devices")
        let removed = NSPredicate { _, _ in
            manualSignIn.exists
                && manualSignIn.isHittable
                && !secretsTab.exists
                && !devicesTab.exists
                && deviceRows.count == 0
                && secretNames.allSatisfy {
                    !self.app.lbElement("secret-row-\($0)").exists
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
                && !self.app.lbElement("remove-device-\(webDeviceId)").exists
                && !self.app.lbElement("remove-device-\(androidDeviceId)").exists
                && !self.app.lbElement("remove-device-\(iosDeviceId)").exists
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
            !app.lbElement("secret-row-\($0)").exists
        })
        XCTAssertFalse(app.lbElement("remove-device-\(webDeviceId)").exists)
        XCTAssertFalse(app.lbElement("remove-device-\(androidDeviceId)").exists)
        XCTAssertFalse(app.lbElement("remove-device-\(iosDeviceId)").exists)
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
            XCTAssertFalse(app.lbElement("recovery-request-badge-\(secretName)").exists)
            XCTAssertFalse(app.lbElement("open-recovery-request-\(secretName)").exists)
        }

        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let recoveryAlert = app.lbElement("alert-recovery-request")
        let recoveryProcessing = app.lbElement("alert-recovery-request-processing")
        let joinAlert = app.lbElement("alert-join-request")
        let joinProcessing = app.lbElement("alert-join-request-processing")
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current"),
                Set([webId, androidId, iosId]).count == 3
            else { return false }
            return self.app.lbCanRemove(webId)
                && self.app.lbCanRemove(androidId)
                && !self.app.lbCanRemove(iosId)
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
        app.lbAssertRemovalSubject(webDeviceName)
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
            return self.app.lbCanRemove(androidId)
                && !self.app.lbCanRemove(iosId)
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
        let resolved = app.lbFindSecrets(secretNames, timeout: 180)
        XCTAssertEqual(Set(resolved.keys), Set(secretNames), "iOS did not find every Test #38 Secret after resharing")
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
            XCTAssertFalse(app.lbElement("recovery-request-badge-\(secretName)").exists)
            XCTAssertFalse(app.lbElement("open-recovery-request-\(secretName)").exists)
        }

        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let recoveryAlert = app.lbElement("alert-recovery-request")
        let recoveryProcessing = app.lbElement("alert-recovery-request-processing")
        let joinAlert = app.lbElement("alert-join-request")
        let joinProcessing = app.lbElement("alert-join-request-processing")
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current"),
                Set([webId, androidId, iosId]).count == 3
            else { return false }
            return self.app.lbCanRemove(webId)
                && self.app.lbCanRemove(androidId)
                && !self.app.lbCanRemove(iosId)
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
        app.lbAssertRemovalSubject(webDeviceName)
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
            return self.app.lbCanRemove(androidId)
                && !self.app.lbCanRemove(iosId)
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
        let resolved = app.lbFindSecrets(secretNames, timeout: 180)
        XCTAssertEqual(Set(resolved.keys), Set(secretNames), "iOS did not find every Test #46 Secret after the removal decision")
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
        let deviceRows = app.lbConnectedSlots
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 2,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") == nil,
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.app.lbCanRemove(androidId)
                && !self.app.lbCanRemove(iosId)
                && !self.app.lbElement("pending-device-row").exists
                && !self.app.lbElement("pending-removal-device-row").exists
                && !self.app.lbElement("remove-device-dialog").exists
                && !self.app.lbElement("alert-recovery-request").exists
                && !self.app.lbElement("alert-recovery-request-processing").exists
        }
        let cleanExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [cleanExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #38's clean two-member target boundary"
        )
        print("E2E: IOS_TEST38_REMOVAL_TARGET_READY")

        let manualSignIn = app.lbElement("orbit-enter")
        let secretsTab = app.lbElement("tab-secrets")
        let devicesTab = app.lbElement("tab-devices")
        let removed = NSPredicate { _, _ in
            manualSignIn.exists
                && manualSignIn.isHittable
                && !secretsTab.exists
                && !devicesTab.exists
                && deviceRows.count == 0
                && secretNames.allSatisfy {
                    !self.app.lbElement("secret-row-\($0)").exists
                }
                && !self.app.lbElement("pending-device-row").exists
                && !self.app.lbElement("pending-removal-device-row").exists
                && !self.app.lbElement("remove-device-dialog").exists
                && !self.app.lbElement("alert-recovery-request").exists
                && !self.app.lbElement("alert-recovery-request-processing").exists
                && !self.app.lbElement("alert-join-request").exists
                && !self.app.lbElement("alert-join-request-processing").exists
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
            XCTAssertFalse(app.lbElement("recovery-request-badge-\(secretName)").exists)
            XCTAssertFalse(app.lbElement("open-recovery-request-\(secretName)").exists)
        }
        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 2,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") == nil,
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.app.lbCanRemove(androidId)
                && !self.app.lbCanRemove(iosId)
                && !self.app.lbElement("pending-device-row").exists
                && !self.app.lbElement("pending-removal-device-row").exists
                && !self.app.lbElement("remove-device-dialog").exists
                && !self.app.lbElement("alert-recovery-request").exists
                && !self.app.lbElement("alert-recovery-request-processing").exists
        }
        let cleanExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [cleanExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #42's clean two-member target boundary"
        )
        print("E2E: IOS_TEST42_REMOVAL_TARGET_READY")

        let manualSignIn = app.lbElement("orbit-enter")
        let secretsTab = app.lbElement("tab-secrets")
        let devicesTab = app.lbElement("tab-devices")
        let removed = NSPredicate { _, _ in
            manualSignIn.exists
                && manualSignIn.isHittable
                && !secretsTab.exists
                && !devicesTab.exists
                && deviceRows.count == 0
                && secretNames.allSatisfy {
                    !self.app.lbElement("secret-row-\($0)").exists
                }
                && !self.app.lbElement("pending-device-row").exists
                && !self.app.lbElement("pending-removal-device-row").exists
                && !self.app.lbElement("remove-device-dialog").exists
                && !self.app.lbElement("alert-recovery-request").exists
                && !self.app.lbElement("alert-recovery-request-processing").exists
                && !self.app.lbElement("alert-join-request").exists
                && !self.app.lbElement("alert-join-request-processing").exists
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
            XCTAssertFalse(app.lbElement("recovery-request-badge-\(secretName)").exists)
            XCTAssertFalse(app.lbElement("open-recovery-request-\(secretName)").exists)
        }
        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 2,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") == nil,
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.app.lbCanRemove(androidId)
                && !self.app.lbCanRemove(iosId)
                && !self.app.lbElement("pending-device-row").exists
                && !self.app.lbElement("pending-removal-device-row").exists
                && !self.app.lbElement("remove-device-dialog").exists
                && !self.app.lbElement("alert-recovery-request").exists
                && !self.app.lbElement("alert-recovery-request-processing").exists
        }
        let cleanExpectation = XCTNSPredicateExpectation(predicate: cleanBoundary, object: nil)
        XCTAssertEqual(
            XCTWaiter.wait(for: [cleanExpectation], timeout: 180),
            .completed,
            "iOS did not reach Test #43's clean two-member target boundary"
        )
        print("E2E: IOS_TEST43_REMOVAL_TARGET_READY")

        let manualSignIn = app.lbElement("orbit-enter")
        let secretsTab = app.lbElement("tab-secrets")
        let devicesTab = app.lbElement("tab-devices")
        let orbitReady = app.lbElement("orbit-transition-ready")
        let orbitIdle = app.lbElement("orbit-phase-idle")
        let removed = NSPredicate { _, _ in
            orbitReady.exists
                && orbitIdle.exists
                && !secretsTab.exists
                && !devicesTab.exists
                && deviceRows.count == 0
                && secretNames.allSatisfy {
                    !self.app.lbElement("secret-row-\($0)").exists
                }
                && !self.app.lbElement("pending-device-row").exists
                && !self.app.lbElement("pending-removal-device-row").exists
                && !self.app.lbElement("remove-device-dialog").exists
                && !self.app.lbElement("alert-recovery-request").exists
                && !self.app.lbElement("alert-recovery-request-processing").exists
                && !self.app.lbElement("alert-join-request").exists
                && !self.app.lbElement("alert-join-request-processing").exists
                && self.app.lbVisible(manualSignIn)
                && manualSignIn.isHittable
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
            XCTAssertFalse(app.lbElement("recovery-request-badge-\(secretName)").exists)
            XCTAssertFalse(app.lbElement("open-recovery-request-\(secretName)").exists)
        }
        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let recoveryAlert = app.lbElement("alert-recovery-request")
        let recoveryProcessing = app.lbElement("alert-recovery-request-processing")
        let joinAlert = app.lbElement("alert-join-request")
        let joinProcessing = app.lbElement("alert-join-request-processing")
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 2,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") == nil,
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return self.app.lbCanRemove(androidId)
                && !self.app.lbCanRemove(iosId)
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

        let manualSignIn = app.lbElement("orbit-enter")
        let secretsTab = app.lbElement("tab-secrets")
        let devicesTab = app.lbElement("tab-devices")
        let orbitReady = app.lbElement("orbit-transition-ready")
        let orbitIdle = app.lbElement("orbit-phase-idle")
        let removed = NSPredicate { _, _ in
            orbitReady.exists
                && orbitIdle.exists
                && !secretsTab.exists
                && !devicesTab.exists
                && deviceRows.count == 0
                && secretNames.allSatisfy {
                    !self.app.lbElement("secret-row-\($0)").exists
                }
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !recoveryAlert.exists
                && !recoveryProcessing.exists
                && !joinAlert.exists
                && !joinProcessing.exists
                && self.app.lbVisible(manualSignIn)
                && manualSignIn.isHittable
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
            XCTAssertFalse(app.lbElement("recovery-request-badge-\(secretName)").exists)
            XCTAssertFalse(app.lbElement("open-recovery-request-\(secretName)").exists)
        }

        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let recoveryAlert = app.lbElement("alert-recovery-request")
        let recoveryProcessing = app.lbElement("alert-recovery-request-processing")
        let joinAlert = app.lbElement("alert-join-request")
        let joinProcessing = app.lbElement("alert-join-request-processing")
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current"),
                Set([webId, androidId, iosId]).count == 3
            else { return false }
            return self.app.lbCanRemove(webId)
                && self.app.lbCanRemove(androidId)
                && !self.app.lbCanRemove(iosId)
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
        app.lbAssertRemovalSubject(androidDeviceName)
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
            return self.app.lbCanRemove(webId)
                && !self.app.lbCanRemove(iosId)
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
        let resolved = app.lbFindSecrets(secretNames, timeout: 180)
        XCTAssertEqual(Set(resolved.keys), Set(secretNames), "iOS did not find every Test #40 Secret after resharing")
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

        let deviceRows = app.lbConnectedSlots
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
        let targetRow = app.lbElement("device-row-\(targetId)")
        XCTAssertTrue(targetRow.waitForExistence(timeout: 30))
        XCTAssertEqual(
            app.lbConnectedSlots.count,
            3,
            "Test #15 removal starts from exactly three current members"
        )
        print("E2E: IOS_TEST15_REMOVAL_APPROVER_READY")

        waitForVisible(identifier: "pending-removal-device-row", timeout: 180)
        tap("pending-removal-device-row")
        waitForVisible(identifier: "remove-device-dialog", timeout: 30)
        app.lbAssertRemovalSubject(targetDeviceName)
        print("E2E: IOS_TEST15_REMOVAL_REQUEST_VISIBLE")
        waitForApproval(platform: "test15-remove-approve", cycle: cycle)
        tap("remove-device-dialog-accept")
        waitForHidden(identifier: "remove-device-dialog", timeout: 120)
        let targetRemoved = NSPredicate { _, _ in
            !self.app.lbElement("device-row-\(targetId)").exists
                && self.app.lbConnectedSlots.count == 2
        }
        let removedWait = XCTNSPredicateExpectation(predicate: targetRemoved, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [removedWait], timeout: 180), .completed)
        tap("tab-secrets")
        let secretName = config.secretName ?? "test-secret"
        waitForVisible(identifier: "secret-row-\(secretName)", timeout: 60)
        XCTAssertFalse(app.lbElement("recovery-request-badge-\(secretName)").exists)
        XCTAssertFalse(app.lbElement("open-recovery-request-\(secretName)").exists)
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
            XCTAssertFalse(app.lbElement("recovery-request-badge-\(secretName)").exists)
            XCTAssertFalse(app.lbElement("open-recovery-request-\(secretName)").exists)
        }
        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 2,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") == nil,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return webId != iosId
                && !self.app.lbCanRemove(iosId)
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !self.app.lbElement("alert-recovery-request").exists
                && !self.app.lbElement("alert-recovery-request-processing").exists
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
            return !self.app.lbCanRemove(iosId)
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
            XCTAssertFalse(app.lbElement("recovery-request-badge-\(secretName)").exists)
            XCTAssertFalse(app.lbElement("open-recovery-request-\(secretName)").exists)
        }

        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let recoveryAlert = app.lbElement("alert-recovery-request")
        let recoveryProcessing = app.lbElement("alert-recovery-request-processing")
        let joinAlert = app.lbElement("alert-join-request")
        let joinProcessing = app.lbElement("alert-join-request-processing")
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current"),
                Set([webId, androidId, iosId]).count == 3
            else { return false }
            return self.app.lbCanRemove(webId)
                && self.app.lbCanRemove(androidId)
                && !self.app.lbCanRemove(iosId)
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
        app.lbAssertRemovalSubject(androidDeviceName)
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
            return self.app.lbCanRemove(webId)
                && !self.app.lbCanRemove(iosId)
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
        let resolved = app.lbFindSecrets(secretNames, timeout: 180)
        XCTAssertEqual(Set(resolved.keys), Set(secretNames), "iOS did not find every Test #44 Secret after resharing")
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
            XCTAssertFalse(app.lbElement("recovery-request-badge-\(secretName)").exists)
            XCTAssertFalse(app.lbElement("open-recovery-request-\(secretName)").exists)
        }
        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 2,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") == nil,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return webId != iosId
                && !self.app.lbCanRemove(iosId)
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !self.app.lbElement("alert-recovery-request").exists
                && !self.app.lbElement("alert-recovery-request-processing").exists
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
            return !self.app.lbCanRemove(iosId)
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
            XCTAssertFalse(app.lbElement("recovery-request-badge-\(secretName)").exists)
            XCTAssertFalse(app.lbElement("open-recovery-request-\(secretName)").exists)
        }
        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current"),
                Set([webId, androidId, iosId]).count == 3
            else { return false }
            return !self.app.lbCanRemove(iosId)
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !self.app.lbElement("alert-recovery-request").exists
                && !self.app.lbElement("alert-recovery-request-processing").exists
                && !self.app.lbElement("alert-join-request").exists
                && !self.app.lbElement("alert-join-request-processing").exists
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
            return !self.app.lbCanRemove(iosId)
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
            XCTAssertFalse(app.lbElement("recovery-request-badge-\(secretName)").exists)
            XCTAssertFalse(app.lbElement("open-recovery-request-\(secretName)").exists)
        }
        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current"),
                Set([webId, androidId, iosId]).count == 3
            else { return false }
            return !self.app.lbCanRemove(iosId)
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !self.app.lbElement("alert-recovery-request").exists
                && !self.app.lbElement("alert-recovery-request-processing").exists
                && !self.app.lbElement("alert-join-request").exists
                && !self.app.lbElement("alert-join-request-processing").exists
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
            return !self.app.lbCanRemove(iosId)
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
        let resolved = app.lbFindSecrets(secretNames, timeout: 180)
        XCTAssertEqual(Set(resolved.keys), Set(secretNames), "iOS did not find every Test #42 Secret after resharing")
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
            XCTAssertFalse(app.lbElement("recovery-request-badge-\(secretName)").exists)
            XCTAssertFalse(app.lbElement("open-recovery-request-\(secretName)").exists)
        }
        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let androidId = self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current"),
                Set([webId, androidId, iosId]).count == 3
            else { return false }
            return !self.app.lbCanRemove(iosId)
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !self.app.lbElement("alert-recovery-request").exists
                && !self.app.lbElement("alert-recovery-request-processing").exists
                && !self.app.lbElement("alert-join-request").exists
                && !self.app.lbElement("alert-join-request-processing").exists
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
            return !self.app.lbCanRemove(iosId)
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
        let resolved = app.lbFindSecrets(secretNames, timeout: 180)
        XCTAssertEqual(Set(resolved.keys), Set(secretNames), "iOS did not find every Test #43 Secret after resharing")
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
            XCTAssertFalse(app.lbElement("recovery-request-badge-\(secretName)").exists)
            XCTAssertFalse(app.lbElement("open-recovery-request-\(secretName)").exists)
        }
        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let threeMemberBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") != nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") != nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return !self.app.lbCanRemove(iosId)
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
        let resolved = app.lbFindSecrets(secretNames, timeout: 180)
        XCTAssertEqual(Set(resolved.keys), Set(secretNames), "iOS did not find every Test #47 Secret after the removal decision")
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
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let threeMemberBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") != nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") != nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return !self.app.lbCanRemove(iosId)
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
        let resolved = app.lbFindSecrets(secretNames, timeout: 180)
        XCTAssertEqual(Set(resolved.keys), Set(secretNames), "iOS did not find every Test #47 Secret after the removal decision")
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
            XCTAssertFalse(app.lbElement("recovery-request-badge-\(secretName)").exists)
            XCTAssertFalse(app.lbElement("open-recovery-request-\(secretName)").exists)
        }
        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") != nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") != nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return !self.app.lbCanRemove(iosId)
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
        app.lbAssertRemovalSubject(androidDeviceName)
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
        let resolved = app.lbFindSecrets(secretNames, timeout: 180)
        XCTAssertEqual(Set(resolved.keys), Set(secretNames), "iOS did not find every Test #47 Secret after the removal decision")
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
            XCTAssertFalse(app.lbElement("recovery-request-badge-\(secretName)").exists)
            XCTAssertFalse(app.lbElement("open-recovery-request-\(secretName)").exists)
        }
        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") != nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") != nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return !self.app.lbCanRemove(iosId)
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
        app.lbAssertRemovalSubject(androidDeviceName)
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
        let resolved = app.lbFindSecrets(secretNames, timeout: 180)
        XCTAssertEqual(Set(resolved.keys), Set(secretNames), "iOS did not find every Test #48 Secret after the removal decision")
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
            XCTAssertFalse(app.lbElement("recovery-request-badge-\(secretName)").exists)
            XCTAssertFalse(app.lbElement("open-recovery-request-\(secretName)").exists)
        }
        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let threeMemberBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") != nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") != nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return !self.app.lbCanRemove(iosId)
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
        let resolved = app.lbFindSecrets(secretNames, timeout: 180)
        XCTAssertEqual(Set(resolved.keys), Set(secretNames), "iOS did not find every Test #48 Secret after the removal decision")
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
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let threeMemberBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") != nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") != nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return !self.app.lbCanRemove(iosId)
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
        let resolved = app.lbFindSecrets(secretNames, timeout: 180)
        XCTAssertEqual(Set(resolved.keys), Set(secretNames), "iOS did not find every Test #48 Secret after the removal decision")
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
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let threeMemberBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") != nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") != nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return !self.app.lbCanRemove(iosId)
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
        let resolved = app.lbFindSecrets(secretNames, timeout: 180)
        XCTAssertEqual(Set(resolved.keys), Set(secretNames), "iOS did not find every Test #49 Secret after the removal decision")
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
            XCTAssertFalse(app.lbElement("recovery-request-badge-\(secretName)").exists)
            XCTAssertFalse(app.lbElement("open-recovery-request-\(secretName)").exists)
        }
        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") != nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") != nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return !self.app.lbCanRemove(iosId)
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
        app.lbAssertRemovalSubject(androidDeviceName)
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
        let resolved = app.lbFindSecrets(secretNames, timeout: 180)
        XCTAssertEqual(Set(resolved.keys), Set(secretNames), "iOS did not find every Test #49 Secret after the removal decision")
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
            XCTAssertFalse(app.lbElement("recovery-request-badge-\(secretName)").exists)
            XCTAssertFalse(app.lbElement("open-recovery-request-\(secretName)").exists)
        }
        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let threeMemberBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 3,
                self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member") != nil,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") != nil,
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return !self.app.lbCanRemove(iosId)
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
        let resolved = app.lbFindSecrets(secretNames, timeout: 180)
        XCTAssertEqual(Set(resolved.keys), Set(secretNames), "iOS did not find every Test #49 Secret after the removal decision")
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
            XCTAssertFalse(app.lbElement("recovery-request-badge-\(secretName)").exists)
            XCTAssertFalse(app.lbElement("open-recovery-request-\(secretName)").exists)
        }
        tap("tab-devices")
        let deviceRows = app.lbConnectedSlots
        let pendingJoin = app.lbElement("pending-device-row")
        let pendingRemoval = app.lbElement("pending-removal-device-row")
        let removalDialog = app.lbElement("remove-device-dialog")
        let cleanBoundary = NSPredicate { _, _ in
            guard
                deviceRows.count == 2,
                self.exactDeviceRowIdentifier(name: androidDeviceName, status: "Member") == nil,
                let webId = self.exactDeviceRowIdentifier(name: webDeviceName, status: "Member"),
                let iosId = self.exactDeviceRowIdentifier(name: iosDeviceName, status: "Current")
            else { return false }
            return webId != iosId
                && !self.app.lbCanRemove(iosId)
                && !pendingJoin.exists
                && !pendingRemoval.exists
                && !removalDialog.exists
                && !self.app.lbElement("alert-recovery-request").exists
                && !self.app.lbElement("alert-recovery-request-processing").exists
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
            return !self.app.lbCanRemove(iosId)
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
        var showInputReturned = false
        app.lbShow(secretName, valueTimeout: 60, freshActionResolution: true,
                   inputReturned: { showInputReturned = true })
        guard showInputReturned else { return }
        app.lbAssertValue(secretValue)
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

        app.orbitEnterVaultName(vaultName)
        app.orbitCreateVault()
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

        app.orbitEnterVaultName(vaultName)
        app.orbitCreateVault()
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

        app.orbitEnterVaultName(vaultName)
        app.orbitCreateVault()
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

        app.orbitEnterVaultName(vaultName)
        app.orbitCreateVault()
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

        app.orbitEnterVaultName(vaultName)
        app.orbitCreateVault()
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

        app.orbitEnterVaultName(vaultName)
        app.orbitCreateVault()
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

        app.orbitEnterVaultName(vaultName)
        app.orbitCreateVault()
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

        app.orbitEnterVaultName(vaultName)
        app.orbitCreateVault()
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
        app.orbitEnterVaultName(coordinatorVaultName(defaultValue: "test50@test.ru"))
        app.orbitCreateVault()
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
        // Each controlled-business repetition starts from the same persisted
        // identity, but the previous XCTest subprocess may leave a live
        // native window on a stale route. Terminate and relaunch this same
        // app (no wipe, reset, or simulator replacement) before entering
        // Secrets so the marker deadline measures the actual create flow.
        if config.step?.contains("recreate") == true {
            relaunchTest50AppUntilReady()
        } else {
            app.launch()
            enterSimulatorPasscodeIfNeeded()
            waitForVisible(identifier: "tab-secrets", timeout: 180)
        }
        tap("tab-secrets", timeout: 60)
        createSecretForTest24Ui(name: name, value: value, scrollForTest50: true)
        print("E2E: IOS_TEST50_SECRET_CREATED_\(config.cycle ?? "missing")_\(config.step ?? "missing")")
    }

    private func relaunchTest50AppUntilReady() {
        func launchAndWait() -> Bool {
            app.launch()
            enterSimulatorPasscodeIfNeeded()
            return app.lbElement("tab-secrets").waitForExistence(timeout: 90)
        }

        app.terminate()
        RunLoop.current.run(until: Date().addingTimeInterval(1))
        if !launchAndWait() {
            // A stale native window can survive one XCTest teardown. Retry
            // once on the same simulator without clearing app data.
            app.terminate()
            RunLoop.current.run(until: Date().addingTimeInterval(1))
            XCTAssertTrue(launchAndWait(), "Test50 iOS app did not expose the Secrets tab after relaunch")
        }
        waitForVisible(identifier: "tab-secrets", timeout: 60)
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
        app.orbitEnterVaultName(coordinatorVaultName(defaultValue: "test50@test.ru"))
        app.orbitRequestJoin()
        print("E2E: IOS_JOIN_REQUEST_SENT")
        // Request submission precedes membership acceptance. Pending Join has
        // unrelated scroll containers; only search after Main/Secrets is ready.
        waitForVisible(identifier: "tab-secrets", timeout: 180)
        tap("tab-secrets")
        waitForVisible(identifier: "add-secret-fab", timeout: 180)

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
              let outcome = config.expectedOutcome, ["accepted", "declined", "pending"].contains(outcome) else {
            XCTFail("Test50 actor configuration is incomplete"); return
        }
        XCTAssertTrue(config.requireCoreRouting == true, "Test50 requires the verified local Core route")
        if config.authProfile == "controlled-business" {
            XCTAssertTrue(app.launchEnvironment["METASECRET_UI_TEST_MODE"] == "true", "Controlled business requires its admitted app-local mode")
        } else {
            XCTAssertNil(app.launchEnvironment["METASECRET_UI_TEST_MODE"], "Entire Test50 launch must use actual OS authentication")
        }
        func marker(_ action: String) { print("E2E: IOS_DELETION_\(action)_\(cycle)_\(step)") }
        func gate(_ phase: String) { waitForApproval(platform: "ios-deletion-\(phase)", cycle: cycle, step: step) }
        let row = "secret-row-\(name)"
        var baselineGeometry: [String: CGRect] = [:]
        var incoming: LiveboardIncoming?
        if role == "startup-gone" {
            XCTAssertEqual(app.state, .runningForeground, "Cold app is not foreground")
            XCTAssertTrue(app.windows.firstMatch.waitForExistence(timeout: 30), "Cold app window missing")
            XCTAssertFalse(app.lbElement(row).exists, "Cached deleted row rendered before canonical sync")
            app.lbAssertNoPrivateContent(value)
            let fixtureData = try Data(contentsOf: URL(fileURLWithPath: Self.stepConfigPath + ".test50-geometry.json"))
            let fixture = try JSONDecoder().decode(Test50GeometryFixture.self, from: fixtureData)
            XCTAssertTrue(step.hasSuffix("-startup"), "Cold geometry fixture actor scope invalid")
            let expectedSourceStep = String(step.dropLast("-startup".count)) + "-main"
            XCTAssertTrue(fixture.cycle == cycle && fixture.sourceStep == expectedSourceStep && fixture.secretName == name,
                          "Cold geometry fixture scope mismatch")
            XCTAssertTrue(fixture.window == test50RectNumbers(app.windows.firstMatch.frame), "Cold geometry window changed")
            baselineGeometry = fixture.privateRects.mapValues { values -> CGRect in
                guard values.count == 4 else { return .zero }
                return CGRect(x: values[0], y: values[1], width: values[2], height: values[3])
            }
            baselineGeometry["window"] = app.windows.firstMatch.frame
            test50AssertOverlayCoverage(app.windows.firstMatch.frame, window: app.windows.firstMatch.frame, baseline: baselineGeometry)
            app.lbProbeOldCopyPoint(value, baseline: baselineGeometry)
            marker("READY")
            gate("action")
            // The coordinator still holds this actor's canonical read and proves zero Core commands.
            app.lbProbeOldCopyPoint(value, baseline: baselineGeometry)
            gate("terminal")
            enterSimulatorPasscodeIfNeeded()
            app.lbSecrets()
            XCTAssertFalse(test50FindInList(row), "Deleted Secret row remains in complete list")
            app.lbAssertNoPrivateContent(value)
            marker("TERMINAL")
            return
        }
        enterSimulatorPasscodeIfNeeded()
        app.lbSecrets()
        test50RequireVisible(row)
        let id = app.lbFindSecret(name)
        if role == "sync-observe" {
            app.lbShow(name, expected: value)
            baselineGeometry = test50SyncGeometry(phase: "baseline", baseline: [:])
            let fixture = Test50GeometryFixture(cycle: cycle, sourceStep: step, secretName: name,
                window: test50RectNumbers(app.windows.firstMatch.frame),
                privateRects: baselineGeometry.filter { ["show", "value", "copy"].contains($0.key) }.mapValues(test50RectNumbers))
            try JSONEncoder().encode(fixture).write(to: URL(fileURLWithPath: Self.stepConfigPath + ".test50-geometry.json"), options: .atomic)
        }
        marker("READY")
        switch role {
        case "requester", "cancel":
            gate("action")
            // The coordinator may leave the iOS app with a blank native window
            // after a long approval gate. Re-activate the same app and restore
            // the Secrets route before interacting with the exact card.
            // The selected XCTest suite can leave a live but unrendered app
            // window after teardown. Relaunch the same bundle (without wiping
            // the simulator or its data) so the next suite gets a real route.
            app.launch()
            _ = app.lbSecrets(timeout: 180)
            test50RequireVisible(row)
            if role == "cancel" && config.authProfile == "controlled-business" {
                XCTAssertTrue(app.lbSelection.secretId == id, "Manual-pending boundary lost the exact Pass ID")
                XCTAssertTrue(app.lbElement("lb-delete-\(id)").isEnabled && app.lbElement("lb-primary-\(id)").isEnabled)
                app.lbAssertNoPrivateContent(value)
                marker("AUTH_MANUAL_PENDING")
                return
            }
            app.lbTap("lb-delete-\(id)")
            if role == "cancel" {
                app.lbCancelSystemAuthentication()
                test50RequireVisible(row)
                XCTAssertTrue(app.lbElement("lb-secrets").exists, "System Cancel closed the Secret list")
                XCTAssertTrue(app.lbElement("lb-delete-\(id)").isEnabled, "System Cancel left Delete pending")
                XCTAssertTrue(app.lbElement("lb-primary-\(id)").isEnabled, "System Cancel left the primary action pending")
                app.lbAssertNoPrivateContent(value)
                marker("CANCELLED")
                return
            }
            enterSimulatorPasscodeIfNeeded()
            app.lbWait({
                !self.app.lbElement("lb-secret-\(id)").exists ||
                (!self.app.lbElement("lb-delete-\(id)").isEnabled && !self.app.lbElement("lb-primary-\(id)").isEnabled)
            }, timeout: 180, message: "Delete neither removed the exact card nor entered actual inline pending state")
            if outcome != "accepted" {
                XCTAssertTrue(app.lbElement("lb-secret-\(id)").exists)
                XCTAssertFalse(app.lbElement("lb-delete-\(id)").isEnabled)
            }
            marker("SUBMITTED")
        case "approve", "decline", "observe":
            if config.expectRequest != false {
                incoming = app.lbObserveIncoming(LiveboardIncomingExpected(kind: "DeleteSecret", subject: name))
                marker("REQUEST_VISIBLE")
            } else {
                app.lbHome()
                XCTAssertTrue(app.lbInboxIdle(), "Expected the actual idle inbox at the clean observer boundary")
            }
            if role != "observe" {
                gate("action")
                guard let head = incoming else { XCTFail("Deletion decision lacks a captured request"); return }
                _ = app.lbIncoming(LiveboardIncomingExpected(kind: head.kind, subject: head.subject,
                    requester: head.sender, requestId: head.requestId, count: head.count))
                app.lbDecideCurrent(approve: role == "approve")
                marker("SUBMITTED")
            }
        case "show":
            gate("action")
            app.lbShow(name, expected: value)
            app.lbHide(id)
            marker("SHOW_VALID")
            return
        case "sync-observe":
            gate("action")
            XCUIDevice.shared.press(.home)
            app.activate()
            // Retired full-screen overlay becomes actual revoked disclosure + disabled commands.
            app.lbWait({ !self.app.lbElement("lb-value-\(id)").exists && !self.app.lbElement("lb-copy-\(id)").exists
                && self.app.lbElement("lb-primary-\(id)").exists && !self.app.lbElement("lb-primary-\(id)").isEnabled
                && self.app.lbElement("lb-delete-\(id)").exists && !self.app.lbElement("lb-delete-\(id)").isEnabled }, timeout: 120)
            _ = test50SyncGeometry(phase: "blocked", baseline: baselineGeometry)
            app.lbProbeOldCopyPoint(value, baseline: baselineGeometry, id: id)
            marker("SYNC_BLOCKED")
            gate("sync-release")
        default:
            XCTFail("Unsupported Test50 role"); return
        }
        gate("terminal")
        if let incoming { app.lbIncomingTerminal(incoming) }
        if outcome == "accepted" {
            XCTAssertFalse(test50FindInList(row), "Deleted Secret row remains in complete list")
            app.lbAssertNoPrivateContent(value)
        } else {
            test50RequireVisible(row)
            if outcome == "declined" {
                app.lbWait({ self.app.lbElement("lb-delete-\(id)").isEnabled && self.app.lbElement("lb-primary-\(id)").isEnabled }, timeout: 180)
            } else { XCTAssertFalse(app.lbElement("lb-delete-\(id)").isEnabled) }
        }
        marker("TERMINAL")
    }
    // Search only rendered accessibility metadata. Reaching a stable viewport
    // at both ends establishes absence across the LazyColumn, not just offscreen.
    private func test50RectNumbers(_ rect: CGRect) -> [Double] {
        [Double(rect.minX), Double(rect.minY), Double(rect.width), Double(rect.height)]
    }

    private func test50AssertOverlayCoverage(_ blocker: CGRect, window: CGRect, baseline: [String: CGRect]) {
        // Signature is retained for the old geometry fixture. The actual guard
        // assertions also require foreground plaintext/Copy absence, disabled commands,
        // and the physical old-Copy-point clipboard probe under the held Core read.
        XCTAssertTrue(blocker == window && !window.isEmpty, "Guard changed the original application window")
        for key in ["show", "value", "copy"] {
            guard let rect = baseline[key], !rect.isEmpty && !rect.isNull else { XCTFail("Measured private geometry missing"); return }
            XCTAssertTrue(window.contains(rect), "Measured private content escaped the original application window")
        }
    }
    // Geometry only: never emit accessibility labels or Secret payloads.
    private func test50SyncGeometry(phase: String, baseline: [String: CGRect]) -> [String: CGRect] {
        guard let id = app.lbSelection.secretId else { XCTFail("Geometry has no exact selected Pass ID"); return [:] }
        let geometry = phase == "baseline" ? app.lbPrivateGeometry(id: id) : baseline
        for key in ["window", "show", "value", "copy"] {
            if let rect = geometry[key] { print("E2E: IOS_TEST50_GEOMETRY phase=\(phase) node=\(key) frame=\(diagnosticFrame(rect))") }
        }
        return geometry
    }
    private func test50FindInList(_ identifier: String) -> Bool {
        app.lbFindInList(identifier)
    }
    nonisolated private static func test50ViewportKey(id: String, row: CGRect, viewport: CGRect) -> String {
        liveboardViewportKey(id: id, row: row, viewport: viewport)
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
        app.lbAssertValue(expected)
    }
    func createVaultForTest49() throws {
        let config = stepConfig()
        let vaultName = coordinatorVaultName(defaultValue: "test49@test.ru")
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test49-secret1")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test49-secret1-value")

        app.orbitEnterVaultName(vaultName)
        app.orbitCreateVault()
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

        app.orbitEnterVaultName(vaultName)
        app.orbitRequestJoin(awaitPending: false)

        app.orbitWaitForTag("orbit-phase-error", timeout: 120)
        let error = app.lbElement("orbit-status")
        XCTAssertTrue(error.label.lowercased().contains("maximum of 3 devices"), "Unexpected fourth-device error: \(error.label)")
        print("E2E: IOS_FOURTH_DEVICE_REJECTED")
        waitForApproval(platform: "ios-rejection", cycle: "1", step: "screenshot")

        tap("orbit-back")
        app.orbitEnterVaultName(retryVaultName)
        app.orbitCreateVault()
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
                guard let reconnectHead = app.lbSelection.incoming else {
                    XCTFail("Test16 has no captured original request"); return
                }
                print("E2E: IOS_NETWORK_LOSS_READY_\(cycle)")
                waitForApproval(platform: "ios-offline-attempt", cycle: cycle, step: "1")
                app.lbSendDecision(approve: true)
                enterSimulatorPasscodeIfNeeded()
                print("E2E: IOS_OFFLINE_APPROVE_CLICKED_\(cycle)")
                waitForApproval(platform: "ios-offline-online", cycle: cycle, step: "1")
                guard app.lbTest16ReconnectApprove(reconnectHead) else {
                    XCTFail("Test16 reconnect did not establish original terminal proof"); return
                }
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

    func handleRecoveryStep() async throws {
        let config = stepConfig()
        let secretName = config.secretName ?? env("E2E_SECRET_NAME", defaultValue: "test-secret")
        let secretValue = config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test-secret-value")
        let role = config.role ?? env("E2E_ROLE", defaultValue: "receiver")
        let cycle = config.cycle ?? env("E2E_CYCLE", defaultValue: "1")
        let step = config.step ?? env("E2E_STEP", defaultValue: "1")
        let approvalPlatform = config.approvalPlatform ?? env("E2E_APPROVAL_PLATFORM", defaultValue: "")
        let sender = config.sender ?? env("E2E_SENDER", defaultValue: "")
        let verifyReceiverAlert = config.verifyReceiverAlert ?? (env("E2E_VERIFY_RECEIVER_ALERT", defaultValue: "0") == "1")
        let phaseContext = try Test24RecoveryPhaseContext.readEntry(Test24PhaseEntry(raw: config.recoveryPhase,
            required: config.recoveryPhaseRequired == true, selectedProfile: config.recoveryPhaseSelectedProfile,
            expected: config.recoveryPhaseExpected))
        try phaseContext?.requireCurrent(role: role, cycle: cycle, step: step)
        let recoveryShowEntry = RecoveryShowEntry(raw: config.recoveryShowPhase,
                phase: Test24PhaseEntry(raw: config.recoveryPhase, required: config.recoveryPhaseRequired == true,
                    selectedProfile: config.recoveryPhaseSelectedProfile, expected: config.recoveryPhaseExpected),
                current: RecoveryShowCurrent(role: role, cycle: cycle, step: step,
                    hasLegacy22: config.test22ShowPhase != nil, hasLiteral25: config.test25ShowPhase != nil))
        let recoveryShowPhase = try RecoveryShowPhaseContext.readEntry(recoveryShowEntry)
        let showEntry = Test25ShowEntry(raw: config.test25ShowPhase,
                phase: Test24PhaseEntry(raw: config.recoveryPhase, required: config.recoveryPhaseRequired == true,
                    selectedProfile: config.recoveryPhaseSelectedProfile, expected: config.recoveryPhaseExpected),
                current: Test25ShowCurrent(role: role, cycle: cycle, step: step,
                    hasLegacy22: config.test22ShowPhase != nil))
        let showPhase: Test25ShowPhaseContext?
        if config.recoveryPhaseSelectedProfile == "test25:web-secondary-creator-lifecycle" || config.test25ShowPhase != nil {
            showPhase = try Test25ShowPhaseContext.readEntry(showEntry)
        } else { showPhase = nil }
        let phase = try phaseContext.map { try makeTest24RecoveryPhaseClient($0,
            base: env("E2E_APPROVAL_COORDINATOR_URL", defaultValue: "http://127.0.0.1:5180")) }
        enterSimulatorPasscodeIfNeeded()
        _ = app.lbFindSecret(secretName)
        if role == "sender" {
            showAcceptedSecretIfNeeded(secretName, cycle: cycle, step: step)
            waitForSenderRecoveryUiReady(secretName, timeout: 180)
            print("E2E: IOS_SENDER_UI_READY_\(cycle)_\(step)")
            print("E2E: IOS_STEP_CONFIG role=\(role) cycle=\(cycle) step=\(step) approval=\(approvalPlatform)")
            if let phase {
                try phase.context.emit("READY", failureCount: testRun?.failureCount)
                try await phase.awaitDispatch()
            } else { waitForApproval(platform: "ios-sender", cycle: cycle, step: step) }
            app.lbRequestRecovery(secretName)
            print("E2E: IOS_RECOVERY_REQUEST_SENT_\(cycle)_\(step)")
            try phase?.context.emit("REQUEST_SENT", failureCount: testRun?.failureCount)
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
            let recoveryPrepared: ((Test22ShowPreparation) -> Void)? = recoveryShowPhase.map { scope in
                { (outcome: Test22ShowPreparation) in
                    guard let line = scope.line(outcome.rawValue, current: recoveryShowEntry, failureCount: self.testRun?.failureCount) else {
                        XCTFail("Invalid current recovery Show procedure boundary"); return
                    }
                    print(line)
                }
            }
            let prepared: ((Test22ShowPreparation) -> Void)? = recoveryPrepared ?? showPhase.map { scope in
                { (outcome: Test22ShowPreparation) in
                    guard let line = scope.line(outcome.rawValue, current: showEntry, failureCount: self.testRun?.failureCount) else {
                        XCTFail("Invalid current Test25 Show procedure boundary"); return
                    }
                    print(line)
                }
            } ?? config.test22ShowPhase.map { context in
                { context.emit($0, cycle: cycle, step: step) }
            }
            revealAcceptedSecret(secretName, cycle: cycle, step: step, prepared: prepared)
            app.lbAssertValue(secretValue)
            print("E2E: IOS_RECOVERY_SECRET_VISIBLE_\(cycle)_\(step)")
            closeShowSecretDialog()
            waitForHidden(identifier: "revealed-secret-value", timeout: 30)
            print("E2E: IOS_RECOVERY_CLOSED_\(cycle)_\(step)")
            return
        }
        app.lbHome()
        if config.receiverRequestAlreadySent != true {
            app.lbWait({ self.app.lbIncomingCount() == 0 && self.app.lbReadIncoming() == nil }, timeout: 30,
                       message: "Receiver pre-request boundary contains stale addressed work")
        }
        print("E2E: IOS_RECEIVER_UI_READY_\(cycle)_\(step)")
        print("E2E: IOS_STEP_CONFIG role=\(role) cycle=\(cycle) step=\(step) approval=\(approvalPlatform)")
        let badgeTimeout: TimeInterval
        if let phase {
            try phase.context.emit("READY", failureCount: testRun?.failureCount)
            badgeTimeout = Double(try await phase.awaitRequestSent()) / 1000
        } else { badgeTimeout = 180 }
        let head = app.lbObserveIncoming(LiveboardIncomingExpected(kind: "Recover", subject: secretName), timeout: badgeTimeout)
        print("E2E: IOS_INCOMING_VISIBLE_\(cycle)_\(step)")
        if let phase {
            guard !head.identifier.isEmpty, head.count > 0, head.kind == "Recover", head.subject == secretName else {
                throw Test24PhaseError.invalidResponse
            }
            try phase.context.emit("INCOMING_VISIBLE", failureCount: testRun?.failureCount)
        }
        if config.expectedOutcome == "membership-removal" {
            print("E2E: IOS_RECOVERY_READY_FOR_REMOVAL_\(cycle)_\(step)")
            return
        }
        if approvalPlatform == "ios" || verifyReceiverAlert {
            _ = app.lbIncoming(LiveboardIncomingExpected(kind: head.kind, subject: head.subject,
                requester: head.sender, requestId: head.requestId, count: head.count))
            assertRecoveryAlertVisible(secretName: secretName, requester: sender.isEmpty ? nil : sender, timeout: 10)
            print("E2E: IOS_INCOMING_ALERT_VISIBLE_\(cycle)_\(step)")
            if approvalPlatform == "ios" {
                waitForApproval(platform: "ios-approve", cycle: cycle, step: step)
                app.lbDecideCurrent(approve: true)
                print("E2E: IOS_APPROVED_INCOMING_\(cycle)_\(step)")
            } else {
                // The new tile remains expanded. This historical marker still proves zero decisions.
                XCTAssertTrue(app.lbElement("lb-approve-\(head.requestId)").exists)
                XCTAssertTrue(app.lbElement("lb-decline-\(head.requestId)").exists)
                print("E2E: IOS_INCOMING_ALERT_DISMISSED_WITHOUT_DECISION_\(cycle)_\(step) expanded=true decisions=0")
                waitForApproval(platform: "ios-dismiss", cycle: cycle, step: step)
                app.lbIncomingTerminal(head)
                print("E2E: IOS_DISMISSED_INCOMING_\(cycle)_\(step)")
            }
        } else {
            waitForApproval(platform: "ios-dismiss", cycle: cycle, step: step)
            app.lbIncomingTerminal(head)
            print("E2E: IOS_DISMISSED_INCOMING_\(cycle)_\(step)")
        }
    }
    private func makeTest24RecoveryPhaseClient(_ context: Test24RecoveryPhaseContext, base: String) throws -> Test24RecoveryPhaseClient {
        try Test24RecoveryPhaseClient(context: context, base: base)
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
        _ = app.lbFindSecret(secretName)
        if role == "sender" {
            waitForApproval(platform: "ios-sender", cycle: cycle, step: step)
            showAcceptedSecretIfNeeded(secretName, cycle: cycle, step: step)
            app.lbRequestRecovery(secretName)
            print("E2E: IOS_RECOVERY_REQUEST_SENT_\(cycle)_\(step)")
            if duplicateRecovery {
                let duplicateAction = app.lbElement("secret-primary-action-\(secretName)")
                if duplicateAction.exists && duplicateAction.isHittable && duplicateAction.isEnabled {
                    duplicateAction.tap()
                    print("E2E: IOS_DUPLICATE_RECOVERY_SENT_\(cycle)_\(step)")
                } else { print("E2E: IOS_DUPLICATE_RECOVERY_SKIPPED_AFTER_GUARD_\(cycle)_\(step)") }
                closeShowSecretDialog()
                print("E2E: IOS_DUPLICATE_RECOVERY_PENDING_CLOSED_\(cycle)_\(step)")
                waitForApproval(platform: "ios-duplicate-finish", cycle: cycle, step: step)
                revealAcceptedSecret(secretName, cycle: cycle, step: step)
                app.lbAssertValue(config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test-secret-value"))
                print("E2E: IOS_RECOVERY_SECRET_VISIBLE_\(cycle)_\(step)")
                closeShowSecretDialog()
                waitForPrimaryAction(secretName, expected: "Recover", timeout: 30)
                print("E2E: IOS_RECOVERY_CLOSED_\(cycle)_\(step)")
                return
            }
            closeShowSecretDialog()
            if expectedOutcome == "approved" {
                waitForApproval(platform: "ios-show", cycle: cycle, step: step)
                revealAcceptedSecret(secretName, cycle: cycle, step: step)
                app.lbAssertValue(config.secretValue ?? env("E2E_SECRET_VALUE", defaultValue: "test-secret-value"))
                print("E2E: IOS_RECOVERY_SECRET_VISIBLE_\(cycle)_\(step)")
                closeShowSecretDialog()
            } else {
                waitForApproval(platform: "ios-declined", cycle: cycle, step: step)
                waitForHidden(identifier: "revealed-secret-value", timeout: 30)
                print("E2E: IOS_RECOVERY_NOT_VISIBLE_\(cycle)_\(step)")
            }
            waitForPrimaryAction(secretName, expected: "Recover", timeout: 30)
            print("E2E: IOS_RECOVERY_CLOSED_\(cycle)_\(step)")
            return
        }
        let head = app.lbObserveIncoming(LiveboardIncomingExpected(kind: "Recover", subject: secretName))
        if duplicateRecovery {
            XCTAssertEqual(head.count, 1, "Duplicate request produced more than one addressed claim")
            print("E2E: IOS_SINGLE_ACTIVE_CLAIM_\(cycle)_\(step)")
        }
        print("E2E: IOS_INCOMING_VISIBLE_\(cycle)_\(step)")
        waitForApproval(platform: "ios-\(action)", cycle: cycle, step: step)
        switch app.lbOpenIncomingOrTerminal(head) {
        case .terminal:
            print("E2E: IOS_ACTION_SKIPPED_AFTER_TERMINAL_\(cycle)_\(step)")
            return
        case .refused: XCTFail("Current request opening or terminal proof was refused"); return
        case .opened: break
        }
        if !sender.isEmpty {
            let exactNames = ["web": config.expectedWebDeviceName, "android": config.expectedAndroidDeviceName, "ios": config.expectedIosDeviceName]
            if let candidate = exactNames[sender.lowercased()], let canonical = candidate, !canonical.isEmpty {
                guard head.sender == canonical else { XCTFail("Incoming requester differs from the canonical configured Device"); return }
            } else {
                guard head.sender.localizedCaseInsensitiveContains(sender) else { XCTFail("Incoming requester does not match the existing scenario"); return }
            }
        }
        guard app.lbElement("lb-approve-\(head.requestId)").exists,
              app.lbElement("lb-decline-\(head.requestId)").exists else { XCTFail("Current recovery controls are unavailable"); return }
        guard ["approve", "decline"].contains(action) else { XCTFail("Unsupported recovery response"); return }
        print("E2E: IOS_ACTION_STARTED_\(cycle)_\(step)")
        app.lbSendDecision(approve: action == "approve")
        if repeatApprove && action == "approve" {
            let secondApprove = app.lbElement("lb-approve-\(head.requestId)")
            if secondApprove.exists && secondApprove.isHittable && secondApprove.isEnabled {
                print("E2E: IOS_REPEAT_APPROVE_SECOND_TAP_SENT_\(cycle)_\(step)")
                secondApprove.tap()
            } else { print("E2E: IOS_REPEAT_APPROVE_SECOND_TAP_SKIPPED_AFTER_DISMISS_\(cycle)_\(step)") }
        }
        app.lbIncomingTerminal(head)
        print("E2E: IOS_\(action == "decline" ? "DECLINED" : "APPROVED")_INCOMING_\(cycle)_\(step)")
    }
    private func assertRecoveryAlertVisible(
        secretName: String,
        requester: String?,
        timeout: TimeInterval
    ) {
        let captured = app.lbIncoming(LiveboardIncomingExpected(kind: "Recover", subject: secretName), timeout: timeout)
        if let requester, !requester.isEmpty {
            let config = stepConfig()
            let exactNames = ["web": config.expectedWebDeviceName, "android": config.expectedAndroidDeviceName, "ios": config.expectedIosDeviceName]
            if let candidate = exactNames[requester.lowercased()], let canonical = candidate, !canonical.isEmpty {
                XCTAssertTrue(captured.sender == canonical, "Incoming requester differs from the canonical configured Device")
            } else {
                // Older single-claim fixtures supplied a platform substring, not a canonical Device name.
                // Keep their original requester assertion; the full observed sender is frozen for the SAME-ID decision.
                XCTAssertTrue(captured.sender.localizedCaseInsensitiveContains(requester), "Incoming requester does not match the existing scenario")
            }
        }
        XCTAssertTrue(app.lbElement("lb-approve-\(captured.requestId)").exists)
        XCTAssertTrue(app.lbElement("lb-decline-\(captured.requestId)").exists)
    }
    private func revealAcceptedSecret(_ secretName: String, cycle: String, step: String, prepared: ((Test22ShowPreparation) -> Void)? = nil) {
        if let id = app.lbSelection.secrets[secretName], app.lbElement("lb-value-\(id)").exists {
            app.lbSelection.secretId = id
            prepared?(.alreadyVisible)
            return // Preserve the same authorized disclosure; never issue a second Show.
        }
        print("E2E: IOS_SHOW_ACTION_AFTER_APPROVAL_\(cycle)_\(step)")
        app.lbShow(secretName, inputReturned: prepared.map { notify in { notify(.inputReturned) } })
    }
    private func createSecretForTest24Ui(name: String, value: String, scrollForTest50: Bool = false) {
        app.lbCreate(name: name, value: value)
        if scrollForTest50 { test50RequireVisible("secret-row-\(name)") }
    }
    private func waitForPrimaryAction(_ secretName: String, expected: String, timeout: TimeInterval) {
        let id = app.lbFindSecret(secretName, timeout: timeout)
        let action = app.lbRequire("lb-primary-\(id)")
        app.lbWait({ action.isEnabled && self.app.lbLabel(action, matches: [expected]) }, timeout: timeout)
    }
    private func waitForSenderRecoveryUiReady(_ secretName: String, timeout: TimeInterval) {
        let id = app.lbFindSecret(secretName, timeout: timeout)
        let primary = app.lbRequire("lb-primary-\(id)")
        app.lbWait({ primary.isEnabled && self.app.lbLabel(primary, matches: ["Recover", "Восстановить"])
            && !self.app.lbElement("lb-value-\(id)").exists }, timeout: timeout)
        app.lbHome()
        XCTAssertEqual(app.lbIncomingCount(), 0, "Sender clean boundary contains stale incoming requests")
        _ = app.lbFindSecret(secretName, timeout: timeout)
    }
    private func waitForSingleActiveClaim(_ secretName: String, cycle: String, step: String) {
        _ = app.lbIncoming(LiveboardIncomingExpected(kind: "Recover", subject: secretName, count: 1))
        print("E2E: IOS_SINGLE_ACTIVE_CLAIM_\(cycle)_\(step)")
    }
    private func showAcceptedSecretIfNeeded(_ secretName: String, cycle: String, step: String) {
        let action = app.lbElement("secret-primary-action-\(secretName)")
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
        let skip = app.lbElement("onboarding-skip")
        if skip.waitForExistence(timeout: 10) { skip.tap() }
    }

    private func tap(_ identifier: String, timeout: TimeInterval = 30) {
        app.lbTapIntent(identifier, timeout: timeout)
    }
    private func waitForVisible(identifier: String, timeout: TimeInterval) {
        app.lbWaitIntent(identifier, timeout: timeout)
    }
    private func waitForHidden(identifier: String, timeout: TimeInterval) {
        app.lbWaitIntent(identifier, timeout: timeout, visible: false)
    }
    private func waitForRecoveryResolutionOrApprove(timeout: TimeInterval) -> Bool {
        app.lbResumeDecisionAfterReconnect(timeout: timeout)
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
        app.lbCloseValueOrPending()
    }
    private func dismissKeyboardIfNeeded() {
        app.lbDismissKeyboard()
    }
    private func enterSimulatorPasscodeIfNeeded() {
        _ = app.lbConfirmSystemIfPresent()
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
        app.lbDeviceId(name: name, status: status)
    }
    private func test37RemoveActionQuery(deviceId: String) -> XCUIElementQuery {
        app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier == %@", "lb-remove-\(deviceId)")
        )
    }

    private func test39SwipeAndConfirmRemoval(deviceId: String, targetDeviceName: String) {
        app.lbRemoveDevice(deviceId, name: targetDeviceName)
    }
    private func test47SwipeAndConfirmRemoval(deviceId: String, targetDeviceName: String) {
        app.lbRemoveDevice(deviceId, name: targetDeviceName)
    }
    private func test48SwipeAndConfirmRemoval(deviceId: String, targetDeviceName: String) {
        app.lbRemoveDevice(deviceId, name: targetDeviceName)
    }
    private func test49SwipeAndConfirmRemoval(deviceId: String, targetDeviceName: String) {
        app.lbRemoveDevice(deviceId, name: targetDeviceName)
    }
    private func test40SwipeAndConfirmRemoval(deviceId: String, targetDeviceName: String) {
        app.lbRemoveDevice(deviceId, name: targetDeviceName)
    }
    private func test42SwipeAndConfirmRemoval(deviceId: String, targetDeviceName: String) {
        app.lbRemoveDevice(deviceId, name: targetDeviceName)
    }
    private func test43SwipeAndConfirmRemoval(deviceId: String, targetDeviceName: String) {
        app.lbRemoveDevice(deviceId, name: targetDeviceName)
    }
    private func test44SwipeAndConfirmRemoval(deviceId: String, targetDeviceName: String) {
        app.lbRemoveDevice(deviceId, name: targetDeviceName)
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
        guard let data = FileManager.default.contents(atPath: Self.stepConfigPath) else {
            precondition(ProcessInfo.processInfo.environment["E2E_AUTH_PROFILE"] == nil,
                         "A scoped Test50 actor cannot launch without its supplied configuration")
            return StepConfig(coreServerUrl: nil, devicePasscode: nil, requireCoreRouting: nil, authProfile: nil, expectRequest: nil, role: nil, cycle: nil, step: nil, approvalPlatform: nil, sender: nil, secretName: nil, secretValue: nil, action: nil, expectedOutcome: nil, repeatApprove: nil, duplicateRecovery: nil, verifyReceiverAlert: nil, receiverRequestAlreadySent: nil, recoveryPhase: nil, recoveryPhaseRequired: nil, recoveryPhaseSelectedProfile: nil, recoveryPhaseExpected: nil, networkRole: nil, networkCycles: nil, expectedWebDeviceName: nil, expectedAndroidDeviceName: nil, expectedIosDeviceName: nil, joinTarget: nil, test22ShowPhase: nil, test25ShowPhase: nil, recoveryShowPhase: nil)
        }
        do {
            guard let fields = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                preconditionFailure("Invalid supplied XCTest actor configuration")
            }
            if let supplied = fields["authProfile"] {
                guard let profile = supplied as? String, ["real-os", "controlled-business"].contains(profile) else {
                    preconditionFailure("Invalid supplied Test50 authentication profile")
                }
            }
            for key in ["recoveryPhase", "recoveryPhaseSelectedProfile", "recoveryPhaseExpected", "test25ShowPhase", "recoveryShowPhase"] where fields.keys.contains(key) {
                guard let value = fields[key] as? String, key == "recoveryPhase" || !value.isEmpty else {
                    preconditionFailure("Invalid supplied recovery phase launch metadata")
                }
            }
            if fields.keys.contains("recoveryShowPhase") || TestRecoveryProfile.future(fields["recoveryPhaseSelectedProfile"] as? String) != nil {
                guard !fields.keys.contains("test22ShowPhase"), !fields.keys.contains("test25ShowPhase") else {
                    preconditionFailure("Conflicting supplied recovery Show procedure metadata")
                }
            }
            if fields["recoveryPhaseRequired"] is NSNull {
                preconditionFailure("Invalid supplied recovery phase requirement")
            }
            return try JSONDecoder().decode(StepConfig.self, from: data)
        } catch {
            preconditionFailure("Supplied XCTest actor configuration cannot use a legacy fallback")
        }
    }
}

private extension XCUIElement {
    func tapIfExists() {
        if exists && isHittable { tap() }
    }
}
