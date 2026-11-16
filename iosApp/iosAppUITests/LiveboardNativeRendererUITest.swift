import XCTest
import UIKit

/// Dedicated native renderer verification. Original scenario files and 1–50 inventory stay unchanged.
/// The supervisor supplies an isolated identity; unavailable prerequisites are skips, never passing proof.
@MainActor
final class LiveboardNativeRendererUITest: XCTestCase {
    private var app: XCUIApplication! {
        didSet { app?.lbApplyTestIdentity() }
    }

    override func setUpWithError() throws { continueAfterFailure = false }

    override func tearDownWithError() throws {
        XCUIDevice.shared.orientation = .portrait
        app?.terminate()
    }

    func testActualRegistrationMainFullFormAndBackgroundPrivacy() throws {
        let name = try required("LIVEBOARD_RENDERER_VAULT")
        app = XCUIApplication()
        // This form/render case does not claim system authentication. The second case uses real OS auth.
        app.launchEnvironment["METASECRET_UI_TEST_MODE"] = "true"
        app.launch()
        app.orbitEnterVaultName(name)
        app.orbitWaitForTag("orbit-transition-ready")
        app.orbitTap("orbit-create")
        wait("lb-home", timeout: 180)
        capture("01-real-core-home")
        tap("lb-open-create")
        replace("lb-create-name", "Renderer form")
        replace("lb-create-value", "Synthetic plain draft")
        // Actual native typing and exact readback above establish interaction; inspect the resulting UI.
        let activeQueries = ["lb-create-value", "lb-create-fields", "lb-create"].map {
            app.descendants(matching: .any).matching(identifier: $0)
        }
        guard activeQueries.allSatisfy({ $0.count == 1 }) else {
            XCTFail("Active input requires unique current Value, viewport and modal"); return
        }
        let activeValue = activeQueries[0].element(boundBy: 0)
        let activeViewport = activeQueries[1].element(boundBy: 0), activeModal = activeQueries[2].element(boundBy: 0)
        let activeRect = activeValue.frame, viewportRect = activeViewport.frame, modalRect = activeModal.frame
        let currentRects = [app.frame, modalRect, viewportRect, activeRect]
        let keyboard = app.keyboards.firstMatch
        let keyboardClear = !keyboard.exists || (!keyboard.frame.isNull && !keyboard.frame.isInfinite &&
            keyboard.frame.width > 0 && keyboard.frame.height > 0 &&
            [keyboard.frame.minX, keyboard.frame.minY, keyboard.frame.width, keyboard.frame.height].allSatisfy { $0.isFinite } &&
            !keyboard.frame.intersects(activeRect))
        XCTAssertTrue(activeValue.elementType == .textView && activeValue.isEnabled &&
            activeViewport.isEnabled && activeModal.isEnabled && currentRects.allSatisfy({ rectangle in
                !rectangle.isNull && !rectangle.isInfinite && rectangle.width > 0 && rectangle.height > 0 &&
                    [rectangle.minX, rectangle.minY, rectangle.width, rectangle.height].allSatisfy { $0.isFinite }
            }) && app.frame.contains(modalRect) && modalRect.contains(viewportRect) &&
            viewportRect.contains(activeRect) && keyboardClear && (activeValue.value as? String) == "Synthetic plain draft",
            "Actual entered draft must remain enabled, fully visible, unobscured and exactly readable")
        capture("02-active-plain-ime")
        tap("lb-create-seed")
        tap("lb-create-count-24")
        replace("lb-create-word-24", "lastword")
        tap("lb-create-count-12")
        tap("lb-create-count-24")
        reveal("lb-create-word-24")
        XCTAssertTrue((element("lb-create-word-24").value as? String) == "lastword", "Inactive words must survive mode changes")
        capture("03-seed24")
        XCUIDevice.shared.orientation = .landscapeLeft
        wait("lb-create")
        let phonePortrait = UIDevice.current.userInterfaceIdiom == .phone
        guard app.lbCaptureReadyCreate(portraitOnly: phonePortrait, { self.add($0) }) else { return }
        capture(phonePortrait ? "04-phone-portrait-policy-form" : "04-landscape-form")
        XCUIDevice.shared.orientation = .portrait
        XCUIDevice.shared.press(.home)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 10), "Actual background boundary was not reached")
        app.lbWait({
            let state = self.app.state
            return (state == .runningBackground || state == .runningBackgroundSuspended) &&
                !self.element("lb-create").exists && !self.element("lb-create-word-24").exists
        }, timeout: 10, message: "Background Create accessibility publication was not cleared")
        XCTAssertFalse(element("lb-create-value").exists, "Background content must leave the accessibility tree")
        let backgroundWordExists = element("lb-create-word-24").exists
        XCTAssertFalse(backgroundWordExists, "Inactive/full form must leave the accessibility tree")
        // Keep the actual system screenshot as evidence; it is not an assertion about an unseen task-switcher snapshot.
        let background = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        background.name = "05-actual-background-system-screen"
        background.lifetime = .keepAlways
        add(background)
        app.activate()
        wait("lb-home")
        XCTAssertFalse(element("lb-create").exists, "Actual background must close and clear Create")
        tap("lb-open-create")
        XCTAssertTrue((element("lb-create-name").value as? String ?? "").isEmpty, "Background must clear authored name")
        tap("lb-create-seed")
        tap("lb-create-count-24")
        reveal("lb-create-word-24")
        XCTAssertTrue((element("lb-create-word-24").value as? String ?? "").isEmpty, "Hard clear includes inactive words")
        tap("lb-create-close")
        tap("lb-invite")
        wait("lb-invite-qr")
        capture("06-invite-qr-for-independent-decode")
        tap("lb-invite-close")
        tap("lb-open-profile")
        wait("lb-profile")
        tap("lb-profile-back")
        wait("lb-home")
        // Reset only this method's just-created, run-owned identity through the actual UI.
        tap("lb-open-profile")
        wait("lb-profile")
        tap("lb-profile-reset")
        wait("lb-reset-confirmation")
        tap("lb-reset-submit")
        wait("orbit-enter", timeout: 180)
        wait("orbit-registration")
        wait("orbit-phase-idle")
        wait("orbit-transition-ready")
        for tag in ["lb-home", "lb-secrets", "lb-profile", "lb-reset-confirmation", "lb-profile-reset-failed"] {
            XCTAssertFalse(element(tag).exists, "Owned Profile Reset must leave no Member UI or reset failure")
        }
    }

    func testRealSystemShowCancelThenApprovalKeepsInactiveContentPrivate() throws {
        let secretId = try required("LIVEBOARD_RENDERER_SECRET_ID")
        let expectedValue = try required("LIVEBOARD_RENDERER_SECRET_VALUE")
        let environment = ProcessInfo.processInfo.environment
        let authMode = environment["LIVEBOARD_RENDERER_AUTH_MODE"] ?? "declared-passcode"
        guard ["declared-passcode", "observed-faceid"].contains(authMode),
              authMode != "observed-faceid" || environment["LIVEBOARD_SIMULATOR_PASSCODE"] == nil else {
            XCTFail("Unsupported real OS authentication inputs"); return
        }
        let passcode: String
        if authMode == "observed-faceid" { passcode = "" }
        else { passcode = try required("LIVEBOARD_SIMULATOR_PASSCODE") }
        app = XCUIApplication()
        // Entire dedicated launch uses the real authenticator; never restart to toggle this flag.
        app.launchEnvironment.removeValue(forKey: "METASECRET_UI_TEST_MODE")
        app.launch()
        approveSystemIfPresented(passcode)
        wait("lb-home", timeout: 180)
        tap("lb-open-secrets")
        let primary = "lb-primary-\(secretId)"
        tap(primary)
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let cancel = systemCancel(springboard)
        XCTAssertTrue(cancel.waitForExistence(timeout: 15), "A real system Cancel control is required")
        XCTAssertTrue(cancel.isHittable, "The real OS prompt must own interaction before inspecting the covered app")
        XCTAssertFalse(element("lb-secret-name-\(secretId)").exists, "Inactive cover must suppress underlying accessibility")
        let inactive = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        inactive.name = "07-real-os-inactive-cover"
        inactive.lifetime = .keepAlways
        add(inactive)
        cancel.tap()
        wait("lb-secrets")
        XCTAssertFalse(element("lb-value-\(secretId)").exists, "System Cancel must not disclose plaintext")
        XCTAssertFalse(element("lb-copy-\(secretId)").exists, "System Cancel must not enable Copy")
        // Show Cancel is silent; inspect the real Inbox without dismissing an invented error.
        tap("lb-secrets-back")
        let errorLabels = ["ERROR", "Error", "ОШИБКА", "Ошибка"]
        let error = element("lb-inbox").descendants(matching: .any)
            .matching(NSPredicate(format: "label IN %@", errorLabels)).firstMatch
        XCTAssertFalse(error.exists, "System Show Cancel must not pin an Inbox error")
        tap("lb-open-secrets")
        tap(primary)
        approveSystemIfPresented(passcode, requiredPrompt: true, phase: "show")
        wait("lb-value-\(secretId)", timeout: 60)
        let disclosed = element("lb-value-\(secretId)")
        let readable = disclosed.descendants(matching: .staticText).allElementsBoundByIndex
        XCTAssertTrue(disclosed.label == expectedValue || expectedValue.split(separator: " ").allSatisfy { word in
            readable.contains { $0.label == String(word) }
        }, "Authorized exact value mismatch")
        XCTAssertTrue(disclosed.isHittable || readable.contains { $0.isHittable }, "Authorized Show must remain accessible")
        tap("lb-copy-\(secretId)")
        tap(primary)
        XCTAssertFalse(element("lb-value-\(secretId)").exists, "Hide must remove plaintext semantics")
        XCTAssertFalse(element("lb-copy-\(secretId)").exists, "Hide must remove Copy")
    }

    func testActualCanonicalCreateShowsOnlyNonsensitiveReceiptThenClearsTheForm() throws {
        let vault = try required("LIVEBOARD_RENDERER_CREATE_VAULT")
        app = XCUIApplication()
        // Dedicated actual-Core render case; controlled platform auth is not real OS approval evidence.
        app.launchEnvironment["METASECRET_UI_TEST_MODE"] = "true"
        app.launch()
        app.orbitEnterVaultName(vault)
        app.orbitWaitForTag("orbit-transition-ready")
        app.orbitTap("orbit-create")
        wait("lb-home", timeout: 180)
        tap("lb-open-create")
        replace("lb-create-name", "Native renderer canonical item")
        replace("lb-create-value", "Synthetic native canonical value")
        XCTAssertFalse(element("lb-create-success").exists, "Editing is not canonical success")
        tap("lb-create-submit")
        guard let observed = app.lbObserveCanonicalSaved(timeout: 180) else { return }
        let saved = element("lb-create-saved")
        XCTAssertTrue(["Secret created", "Секрет создан"].contains(observed.label), "Canonical receipt has a fixed nonsensitive label")
        for tag in ["lb-create-name", "lb-create-value", "lb-create-word-1", "lb-create-word-24", "lb-create-submit"] {
            XCTAssertFalse(observed.identifiers.contains(tag), "Canonical completion must remove editable/sensitive fields")
        }
        capture("08-actual-core-canonical-saved")
        // XCTest wall-time polling is not the 520ms render-clock oracle; Android's Compose clock supplies it.
        XCTAssertTrue(saved.waitForNonExistence(timeout: 10), "Canonical receipt must finish rather than remain sticky")
        XCTAssertFalse(element("lb-create").exists)
        tap("lb-open-create")
        XCTAssertTrue((element("lb-create-name").value as? String ?? "").isEmpty)
        XCTAssertTrue((element("lb-create-value").value as? String ?? "").isEmpty)
        XCTAssertFalse(element("lb-create-success").exists, "Fresh editing cannot replay canonical completion")
        tap("lb-create-close")
        if let nonce = ProcessInfo.processInfo.environment["LIVEBOARD_RENDERER_CHAIN_NONCE"] {
            guard nonce.range(of: "^[A-Za-z0-9_-]{1,80}$", options: .regularExpression) != nil,
                  !nonce.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }) else {
                XCTFail("Saved chain nonce is invalid"); return
            }
            let passId = app.lbFindSecret("Native renderer canonical item")
            guard !passId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, passId.unicodeScalars.count <= 160,
                  passId != "invalid-ambiguous-pass", !passId.contains("[REDACTED"),
                  !passId.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }) else {
                XCTFail("Saved chain requires one safe opaque ID"); return
            }
            let data = try JSONSerialization.data(withJSONObject: ["version": 1, "chainNonce": nonce, "passId": passId], options: .sortedKeys)
            let prefix = "NATIVE_RENDERER_SAVED_RECEIPT "
            guard let receipt = String(data: data, encoding: .utf8), prefix.utf8.count + data.count <= 512 else {
                XCTFail("Saved chain receipt exceeds its bound"); return
            }
            print(prefix + receipt)
        }
    }

    func testActualNativeCreateNameTapDiagnostic() throws {
        let vault = try required("LIVEBOARD_RENDERER_DIAGNOSTIC_VAULT")
        let diagnosticName = "Synthetic native diagnostic name"
        app = XCUIApplication()
        // Controlled auth for this isolated reachability diagnostic; it is not real OS consent proof.
        app.launchEnvironment["METASECRET_UI_TEST_MODE"] = "true"
        app.launch()
        app.orbitEnterVaultName(vault)
        app.orbitWaitForTag("orbit-transition-ready")
        app.orbitTap("orbit-create")
        wait("lb-home", timeout: 180)
        tap("lb-open-create")
        wait("lb-create-name")
        let field = element("lb-create-name")
        captureNameReachability("before-native-tap")
        capture("09-create-name-native-before")
        field.tap()
        field.typeText(diagnosticName)
        XCTAssertTrue((field.value as? String) == diagnosticName, "Exact native diagnostic Name must be entered")
        captureNameReachability("after-native-type")
        capture("10-create-name-native-after")
    }

    func testActualNativeCreateNamePointerDiagnostic() throws {
        let vault = try required("LIVEBOARD_RENDERER_DIAGNOSTIC_VAULT")
        let diagnosticName = "Synthetic native diagnostic name"
        app = XCUIApplication()
        // A physical pointer diagnostic only; it cannot accept the original renderer's AX failure.
        app.launchEnvironment["METASECRET_UI_TEST_MODE"] = "true"
        app.launch()
        app.orbitEnterVaultName(vault)
        app.orbitWaitForTag("orbit-transition-ready")
        app.orbitTap("orbit-create")
        wait("lb-home", timeout: 180)
        tap("lb-open-create")
        wait("lb-create-name")
        let field = element("lb-create-name")
        captureNameReachability("before-native-pointer")
        capture("11-create-name-pointer-before")
        let frame = field.frame, viewport = element("lb-create-fields").frame
        let modal = element("lb-create").frame, appFrame = app.frame
        let rectangles = [frame, viewport, modal, appFrame]
        guard field.exists && field.isEnabled && rectangles.allSatisfy({ rectangle in
            rectangle.width > 0 && rectangle.height > 0 &&
                [rectangle.origin.x, rectangle.origin.y, rectangle.width, rectangle.height].allSatisfy { $0.isFinite }
        }) && viewport.contains(frame) && modal.contains(frame) && appFrame.contains(frame) else {
            XCTFail("Native pointer requires an enabled Name with finite, nonempty, contained geometry")
            return
        }
        let offset = CGVector(dx: frame.midX - appFrame.minX, dy: frame.midY - appFrame.minY)
        print("NATIVE_RENDERER_POINTER center[x,y]=\(Double(frame.midX)),\(Double(frame.midY)) " +
              "offset[x,y]=\(Double(offset.dx)),\(Double(offset.dy))")
        app.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0)).withOffset(offset).tap()
        captureNameReachability("after-native-pointer")
        capture("12-create-name-pointer-after")
        app.typeText(diagnosticName)
        XCTAssertTrue((field.value as? String) == diagnosticName, "Exact pointer diagnostic Name must be entered")
    }

    private func captureNameReachability(_ phase: String) {
        let field = element("lb-create-name")
        let targets: [(String, XCUIElement)] = [
            ("field", field),
            ("placeholder", field.children(matching: .staticText).firstMatch),
            ("fields", element("lb-create-fields")),
            ("modal", element("lb-create")),
            ("shade", element("lb-overlay-shade")),
            ("seed", element("lb-create-seed")),
            ("close", element("lb-create-close"))
        ]
        var records = ["phase=\(phase); flags: 1=true, 0=false, NA=not observed"]
        for (role, control) in targets {
            guard control.exists else {
                records.append("target=\(role) exists=0 enabled=NA hittable=NA type=NA")
                continue
            }
            let frame = control.frame
            let rectangle = [frame.origin.x, frame.origin.y, frame.size.width, frame.size.height]
            let geometry = rectangle.allSatisfy { $0.isFinite }
                ? rectangle.map { String(Double($0)) }.joined(separator: ",") : "nonfinite"
            records.append("target=\(role) exists=1 enabled=\(control.isEnabled ? 1 : 0) " +
                           "hittable=\(control.isHittable ? 1 : 0) type=\(control.elementType.rawValue) " +
                           "rect[x,y,width,height]=\(geometry)")
        }
        records.append("keyboardExists=\(app.keyboards.firstMatch.exists ? 1 : 0)")
        let numericMetadata = records.joined(separator: "\n")
        print("NATIVE_RENDERER_REACHABILITY \(numericMetadata)")
        let attachment = XCTAttachment(string: numericMetadata)
        attachment.name = "create-name-reachability-\(phase)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func element(_ tag: String) -> XCUIElement { app.descendants(matching: .any)[tag] }

    private func wait(_ tag: String, timeout: TimeInterval = 30) {
        XCTAssertTrue(element(tag).waitForExistence(timeout: timeout), "Missing renderer control \(tag)")
    }

    private func reveal(_ tag: String) {
        if app.lbIsCreateField(tag) {
            _ = app.lbRequireCreateField(tag)
            return
        }
        wait(tag)
        let field = element(tag)
        for _ in 0..<8 where !field.isHittable { app.swipeUp() }
        XCTAssertTrue(field.isHittable, "Renderer control \(tag) is not reachable")
    }

    private func tap(_ tag: String) {
        if ["lb-create-count-12", "lb-create-count-24"].contains(tag) {
            app.lbTap(tag)
            return
        }
        reveal(tag)
        let field = element(tag)
        let enabled = NSPredicate { _, _ in field.exists && field.isEnabled && field.isHittable }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: enabled, object: nil)], timeout: 30), .completed,
                       "Renderer control \(tag) did not become actionable")
        field.tap()
    }

    private func replace(_ tag: String, _ value: String) {
        app.lbReplaceField(tag, value: value)
    }

    private func approveSystemIfPresented(_ passcode: String, requiredPrompt: Bool = false, phase: String = "startup") {
        let board = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        if ProcessInfo.processInfo.environment["LIVEBOARD_RENDERER_AUTH_MODE"] == "observed-faceid" {
            let cancel = systemCancel(board)
            guard cancel.waitForExistence(timeout: requiredPrompt ? 15 : 10) else {
                XCTAssertFalse(requiredPrompt, "An actual system approval prompt is required"); return
            }
            XCTAssertTrue(cancel.isHittable, "The actual system prompt must own interaction")
            print("NATIVE_RENDERER_AUTH_PROMPT \(phase)")
            XCTAssertTrue(cancel.waitForNonExistence(timeout: 60), "Observed host approval must settle the real system prompt")
            return
        }
        let secure = board.secureTextFields.firstMatch
        if secure.waitForExistence(timeout: 10) {
            secure.tap(); secure.typeText(passcode); return
        }
        let usePasscode = board.buttons.matching(NSPredicate(format: "label IN %@", ["Enter Passcode", "Use Passcode", "Ввести код-пароль"])).firstMatch
        if usePasscode.exists { usePasscode.tap() }
        let first = passcode.first.map(String.init) ?? ""
        let digit = board.buttons[first]
        if digit.waitForExistence(timeout: 5) {
            for character in passcode { board.buttons[String(character)].tap() }
            return
        }
        XCTAssertFalse(requiredPrompt, "Real OS approval controls require an enrolled Simulator; auto-success is not proof")
    }

    private func systemCancel(_ board: XCUIApplication) -> XCUIElement {
        board.buttons.matching(NSPredicate(format: "label IN %@", ["Cancel", "Отменить", "Отмена"])).firstMatch
    }

    private func capture(_ name: String) {
        let image = XCTAttachment(screenshot: app.screenshot())
        image.name = name; image.lifetime = .keepAlways; add(image)
    }

    private func required(_ key: String) throws -> String {
        guard let value = ProcessInfo.processInfo.environment[key], !value.isEmpty else {
            throw XCTSkip("Dedicated native renderer prerequisite missing: \(key)")
        }
        return value
    }
}
