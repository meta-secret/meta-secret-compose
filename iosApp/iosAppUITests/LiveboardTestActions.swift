import XCTest

/// Test-owned selectors only; no application state or business operation is injected.
@MainActor
final class LiveboardTestSelection {
    var secrets: [String: String] = [:]
    var secretId: String?
    var devicePasscode: String?
    var incoming: LiveboardIncoming?
    var deviceId: String?
    var actionabilityObservationAttempted = false
    let incomingObservation = LiveboardIncomingObservationState()
}

@MainActor private var liveboardSelections: [ObjectIdentifier: LiveboardTestSelection] = [:]

@MainActor
extension XCUIApplication {
    func lbApplyTestIdentity(requireRealOs: Bool = false) {
        if requireRealOs {
            let forbidden = ["METASECRET_UI_TEST_MODE", "SIMCTL_CHILD_METASECRET_UI_TEST_MODE"]
            precondition(!forbidden.contains { ProcessInfo.processInfo.environment[$0] != nil || launchEnvironment[$0] != nil },
                         "Real OS authentication requires an uncontaminated launch environment")
        }
        let key = "METASECRET_E2E_IDENTITY_NAMESPACE"
        guard let namespace = ProcessInfo.processInfo.environment[key], namespace.hasPrefix("f2-"),
              let uuid = UUID(uuidString: String(namespace.dropFirst(3))),
              namespace == "f2-" + uuid.uuidString.lowercased() else {
            preconditionFailure("A valid isolated test identity is required before app launch")
        }
        if let configured = launchEnvironment[key] {
            precondition(configured == namespace, "Conflicting E2E identity namespace")
        }
        launchEnvironment[key] = namespace
    }

    var lbSelection: LiveboardTestSelection {
        let key = ObjectIdentifier(self)
        if let existing = liveboardSelections[key] { return existing }
        let value = LiveboardTestSelection()
        liveboardSelections[key] = value
        return value
    }

    func lbResetSelectors() { liveboardSelections[ObjectIdentifier(self)] = LiveboardTestSelection() }

    func lbWait(_ predicate: @escaping () -> Bool, timeout: TimeInterval = 30, message: String = "Liveboard UI boundary was not reached", failureDiagnostic: (() -> Void)? = nil) {
        let expectation = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in predicate() }, object: nil)
        let result = XCTWaiter.wait(for: [expectation], timeout: timeout)
        if result != .completed { failureDiagnostic?() }
        XCTAssertEqual(result, .completed, message)
    }

    func lbVisible(_ element: XCUIElement, observed: ((Bool, CGRect?, CGRect?) -> Void)? = nil) -> Bool {
        guard element.exists else { observed?(false, nil, nil); return false }
        let bounds = element.frame
        guard !bounds.isEmpty && !bounds.isNull else { observed?(true, bounds, nil); return false }
        let appBounds = frame
        observed?(true, bounds, appBounds)
        return appBounds.intersects(bounds)
    }

    func lbTap(_ identifier: String, timeout: TimeInterval = 30, beforeInput: (() -> Bool)? = nil) {
        if ["lb-create-count-12", "lb-create-count-24"].contains(identifier) {
            lbTapCreateSeedCount(identifier)
            return
        }
        var precedingCount: Int?
        let element = lbRequire(identifier, timeout: timeout, observedCount: { precedingCount = $0 })
        lbDismissKeyboard()
        func rectangle(_ value: CGRect?) -> Any {
            guard let value, !value.isNull, !value.isInfinite else { return NSNull() }
            let numbers = [value.minX, value.minY, value.width, value.height]
            return numbers.allSatisfy { $0.isFinite } ? numbers.map { Double($0) } : NSNull()
        }
        let unobserved: [String: Any] = ["exists": NSNull(), "visible": NSNull(), "enabled": NSNull(), "hittable": NSNull(),
                                       "targetFrame": NSNull(), "appFrame": NSNull()]
        var sample = unobserved
        lbWait({
            sample = unobserved
            let visible = self.lbVisible(element, observed: { exists, target, app in
                sample["exists"] = exists; sample["targetFrame"] = rectangle(target); sample["appFrame"] = rectangle(app)
            })
            sample["visible"] = visible
            guard visible else { return false }
            let enabled = element.isEnabled; sample["enabled"] = enabled
            guard enabled else { return false }
            // XCTest can expose the liveboard delete button as visible and
            // enabled while its synthesized activation point is temporarily
            // invalid during tile resize. The action below uses the observed
            // frame center, so do not reject this test-only path on the
            // unreliable hittability bit.
            if identifier.hasPrefix("lb-delete-") { return true }
            if identifier.hasPrefix("lb-primary-") && identifier != "lb-primary-" { return enabled }
            let hittable = element.isHittable; sample["hittable"] = hittable
            return hittable
        }, timeout: timeout, failureDiagnostic: {
            var snapshot: [String: Any] = ["available": false, "type": NSNull(), "frame": NSNull()]
            if let node = try? element.snapshot() {
                snapshot = ["available": true, "type": node.elementType.rawValue, "frame": rectangle(node.frame)]
            }
            let payload: [String: Any] = ["version": 1, "precedingRequireCount": precedingCount.map { $0 as Any } ?? NSNull(),
                                        "lastPredicate": sample, "sequentialFailureTargetSnapshot": snapshot]
            guard let data = try? JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys]), data.count <= 4096 else { return }
            XCTContext.runActivity(named: "Liveboard actionability failure") { activity in
                let attachment = XCTAttachment(data: data, uniformTypeIdentifier: "public.json")
                attachment.name = "liveboard-actionability-failure.json"; attachment.lifetime = .keepAlways
                activity.add(attachment)
            }
        })
        if identifier == "lb-open-secrets" { lbObserveSecretsTap(sample) }
        lbObserveActionabilityTransition(identifier, target: element)
        if identifier.hasPrefix("lb-primary-"), identifier != "lb-primary-", !element.isHittable {
            _ = lbDispatchPrimaryGesture(identifier, target: element, beforeInput: beforeInput)
        } else {
            if let beforeInput, !beforeInput() {
                XCTFail("Fresh action scope changed at the actual input boundary"); return
            }
            if identifier.hasPrefix("lb-delete-") {
                // XCTest can report a delete button as hittable while omitting
                // its activation point after the liveboard tile resizes.
                // Keep the same visibility/enabled/hittable gates above, then
                // tap the center of the observed frame as a test-only fallback.
                let frame = element.frame
                if !frame.isEmpty && !frame.isNull && frame.width.isFinite && frame.height.isFinite {
                    element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
                } else {
                    element.tap()
                }
            } else {
                element.tap()
            }
        }
    }

    func lbDismissKeyboard() {
        guard keyboards.firstMatch.exists else { return }
        // Return can edit multiline content; the actual target owns any remaining keyboard geometry.
        for name in ["Done", "Готово"] {
            let button = keyboards.buttons[name]
            if button.exists && button.isHittable { button.tap(); return }
        }
    }


    private func lbCreateOrientationSample(portraitOnly: Bool) -> (rects: [String: CGRect], ready: Bool, bounded: Bool) {
        let root: any XCUIElementSnapshot
        do { root = try snapshot() } catch { return ([:], false, true) }
        let fixed = ["lb-create", "lb-create-fields", "lb-create-cancel", "lb-create-submit"]
        var stack: [(any XCUIElementSnapshot, CGRect?)] = [(root, nil)]
        var nodes: [any XCUIElementSnapshot] = [], modalWindows: [CGRect] = [], visited = 0
        while let (node, parentWindow) = stack.popLast() {
            visited += 1
            guard visited <= 8192 else { return (["app": root.frame], false, false) }
            let window = node.elementType == .window ? node.frame : parentWindow
            if node.identifier == fixed[0], let window { modalWindows.append(window) }
            if fixed.contains(node.identifier) ||
                (node.identifier.hasPrefix("lb-create-word-") && lbIsCreateField(node.identifier)) { nodes.append(node) }
            stack.append(contentsOf: node.children.map { ($0, window) })
        }
        var rectangles = ["app": root.frame]
        func finite(_ value: CGRect) -> Bool {
            !value.isNull && !value.isInfinite && value.width > 0 && value.height > 0 &&
                [value.minX, value.minY, value.width, value.height].allSatisfy { $0.isFinite }
        }
        for id in fixed {
            let matches = nodes.filter { $0.identifier == id }
            guard matches.count == 1 else { return (rectangles, false, true) }
            rectangles[id] = matches[0].frame
        }
        guard let modal = rectangles[fixed[0]], let viewport = rectangles[fixed[1]],
            let cancel = rectangles[fixed[2]], let submit = rectangles[fixed[3]] else { return (rectangles, false, true) }
        guard modalWindows.count == 1 else { return (rectangles, false, true) }
        let window = modalWindows[0]
        rectangles["window"] = window
        let words = nodes.filter { $0.identifier.hasPrefix("lb-create-word-") }
        guard Set(words.map { $0.identifier }).count == words.count else { return (rectangles, false, true) }
        let visible = words.filter { $0.elementType == .textView && $0.isEnabled && finite($0.frame) && viewport.contains($0.frame) }
            .sorted { $0.identifier < $1.identifier }
        if let word = visible.first { rectangles["visible-word"] = word.frame }
        let enabledEditor = nodes.filter { [fixed[0], fixed[1]].contains($0.identifier) }.allSatisfy { $0.isEnabled }
        let footerButtons = nodes.filter { [fixed[2], fixed[3]].contains($0.identifier) }.allSatisfy { $0.elementType == .button }
        let oriented = portraitOnly ? root.frame.height > root.frame.width && window.height > window.width :
            root.frame.width > root.frame.height && window.width > window.height
        let ready = XCUIDevice.shared.orientation == .landscapeLeft && oriented && rectangles.values.allSatisfy(finite) &&
            root.frame.contains(window) && window.contains(modal) && modal.contains(viewport) &&
            modal.contains(cancel) && modal.contains(submit) &&
            enabledEditor && footerButtons && !visible.isEmpty
        return (rectangles, ready, true)
    }

    func lbCaptureReadyCreate(portraitOnly: Bool, _ addAttachment: (XCTAttachment) -> Void) -> Bool {
        var previous: [String: CGRect]?, last: [String: CGRect] = [:], samples = 0, bounded = true
        let expectation = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            let sample = self.lbCreateOrientationSample(portraitOnly: portraitOnly)
            last = sample.rects; bounded = sample.bounded
            if sample.ready { samples = previous == sample.rects ? samples + 1 : 1; previous = sample.rects }
            else { samples = 0; previous = nil }
            return sample.ready && samples >= 2
        }, object: nil)
        let completed = XCTWaiter.wait(for: [expectation], timeout: 30) == .completed
        let rectangles: [String: Any] = last.mapValues { rectangle in
            let values = [rectangle.minX, rectangle.minY, rectangle.width, rectangle.height]
            return values.allSatisfy { $0.isFinite } ? values.map { Double($0) } as Any : NSNull()
        }
        let label = portraitOnly ? "04-phone-portrait-policy" : "04-landscape"
        let metadata: [String: Any] = ["ready": completed, "stableSamples": samples, "nodeLimitExceeded": !bounded, "snapshotAvailable": !last.isEmpty,
            "policy": portraitOnly ? "phone-portrait" : "tablet-landscape",
            "deviceOrientation": XCUIDevice.shared.orientation.rawValue, "rectangles": rectangles]
        do {
            let attachment = XCTAttachment(data: try JSONSerialization.data(withJSONObject: metadata, options: [.sortedKeys]),
                uniformTypeIdentifier: "public.json")
            attachment.name = label + "-geometry"; attachment.lifetime = .keepAlways; addAttachment(attachment)
        } catch { XCTFail("Orientation policy fixed geometry could not be encoded"); return false }
        let screen = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        screen.name = label + "-system-screen"; screen.lifetime = .keepAlways; addAttachment(screen)
        XCTAssertTrue(completed, "Orientation policy Create geometry did not reach two stable contained observations")
        return completed
    }

    /// First actual Saved frame only; keep fixed public metadata, never the AX tree or field values.
    func lbObserveCanonicalSaved(timeout: TimeInterval) -> (label: String, identifiers: Set<String>)? {
        guard timeout > 0 && timeout <= 180 else { XCTFail("Canonical Saved observation deadline is invalid"); return nil }
        let deadline = ProcessInfo.processInfo.systemUptime + timeout
        let saved = "lb-create-saved"
        let fixed = Set([saved, "lb-create-name", "lb-create-value", "lb-create-word-1", "lb-create-word-24", "lb-create-submit"])
        let labels = ["Secret created", "Секрет создан"]
        while ProcessInfo.processInfo.systemUptime < deadline {
            var stack: [any XCUIElementSnapshot]
            do { stack = [try snapshot()] }
            catch { XCTFail("Actual canonical Saved AX snapshot is unavailable"); return nil }
            var identifiers: Set<String> = [], savedCount = 0, visited = 0, label = ""
            while let node = stack.popLast() {
                visited += 1
                guard visited <= 8192 else { XCTFail("Canonical Saved AX node limit exceeded"); return nil }
                if fixed.contains(node.identifier) { identifiers.insert(node.identifier) }
                if node.identifier == saved {
                    savedCount += 1
                    if let accepted = labels.first(where: { $0 == node.label }) { label = accepted }
                }
                stack.append(contentsOf: node.children)
            }
            guard ProcessInfo.processInfo.systemUptime <= deadline else { break }
            if savedCount > 0 {
                guard savedCount == 1 else { XCTFail("Canonical Saved AX target is ambiguous"); return nil }
                return (label, identifiers)
            }
        }
        XCTFail("Missing renderer control lb-create-saved")
        return nil
    }


    /// One immutable app AX tree; route/list ownership and window geometry remain exact.
    func lbSecretsViewportSnapshot() throws -> (root: any XCUIElementSnapshot, bounds: CGRect) {
        let root = try snapshot()
        let invalid = NSError(domain: "LiveboardSecretsViewport", code: 1,
            userInfo: [NSLocalizedDescriptionKey: "Current Secrets route/list/window clip is unavailable or ambiguous"])
        func finite(_ frame: CGRect) -> Bool {
            [frame.minX, frame.minY, frame.width, frame.height].allSatisfy { $0.isFinite } && !frame.isEmpty && !frame.isNull
        }
        guard finite(root.frame) else { throw invalid }
        var stack: [(any XCUIElementSnapshot, CGRect?)] = [(root, nil)]
        var visited = 0, routeCount = 0, listCount = 0
        var route: (frame: CGRect, window: CGRect)?, list: (frame: CGRect, window: CGRect)?
        while let (node, inheritedWindow) = stack.popLast() {
            visited += 1
            guard visited <= 8192 else { throw invalid }
            let window: CGRect? = node.elementType == .window ? node.frame : inheritedWindow
            if ["lb-secrets", "lb-secrets-list"].contains(node.identifier) {
                guard let window, finite(node.frame), finite(window) else { throw invalid }
                if node.identifier == "lb-secrets" { routeCount += 1; route = (node.frame, window) }
                else { listCount += 1; list = (node.frame, window) }
            }
            stack.append(contentsOf: node.children.map { ($0, window) })
        }
        guard routeCount == 1, listCount == 1, let route, let list,
              route.window == list.window else { throw invalid }
        let bounds = list.frame.intersection(route.frame).intersection(list.window).intersection(root.frame)
        guard finite(bounds) else { throw invalid }
        return (root, bounds)
    }

    func lbWaitMain(timeout: TimeInterval = 180) {
        lbWait({
            _ = self.lbConfirmSystemIfPresent()
            return ["lb-home", "lb-secrets", "lb-profile"].contains { self.descendants(matching: .any)[$0].exists }
        }, timeout: timeout, message: "Actual Liveboard entry did not complete system authentication")
    }

    func lbHome(timeout: TimeInterval = 180) {
        if !["lb-home", "lb-secrets", "lb-profile"].contains(where: { descendants(matching: .any)[$0].exists }) { lbWaitMain(timeout: timeout) }
        if descendants(matching: .any)["lb-secrets"].exists { lbTap("lb-secrets-back") }
        if descendants(matching: .any)["lb-profile"].exists { lbTap("lb-profile-back") }
        _ = lbRequire("lb-home", timeout: timeout)
    }

    @discardableResult
    func lbSecrets(timeout: TimeInterval = 180) -> Bool {
        lbNavigateSecrets(timeout: timeout)
    }

    /// Maps old test intents to actual rendered controls; never mounts legacy UI.
    func lbElement(_ intent: String) -> XCUIElement {
        let simple = ["tab-secrets": "lb-secrets", "tab-devices": "lb-home",
            "add-secret-fab": "lb-open-create", "secret-name-input": "lb-create-name",
            "secret-value-input": "lb-create-value"]
        if let mapped = simple[intent] {
            return descendants(matching: .any)[mapped]
        }
        let decisions = ["alert-recovery-request-accept": "approve", "alert-recovery-request-decline": "decline",
            "alert-join-request-accept": "approve", "alert-join-request-decline": "decline", "remove-device-dialog-accept": "approve",
            "remove-device-dialog-decline": "decline", "confirm-secret-deletion": "approve", "decline-secret-deletion": "decline"]
        if let action = decisions[intent] {
            if let request = lbSelection.incoming { return descendants(matching: .any)["lb-\(action)-\(request.requestId)"] }
            return descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "lb-\(action)-")).firstMatch
        }
        if intent == "revealed-secret-value" || intent == "show-secret-dialog" {
            if let id = lbSelection.secretId {
                let value = descendants(matching: .any)["lb-value-\(id)"]
                if value.exists || intent == "revealed-secret-value" { return value }
                let primary = descendants(matching: .any)["lb-primary-\(id)"]
                if primary.exists && !primary.isEnabled { return descendants(matching: .any)["lb-secret-\(id)"] }
            }
            return descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "lb-value-")).firstMatch
        }
        for (prefix, actual) in [("secret-row-", "lb-secret-"), ("secret-primary-action-", "lb-primary-"),
            ("delete-secret-", "lb-delete-"), ("deletion-pending-", "lb-delete-")] where intent.hasPrefix(prefix) {
            let name = String(intent.dropFirst(prefix.count))
            let id = lbSelection.secrets[name] ?? lbVisibleSecretId(name)
            if let id {
                if prefix == "deletion-pending-" {
                    return descendants(matching: .any).matching(NSPredicate(format: "identifier == %@ AND enabled == false", actual + id)).firstMatch
                }
                return descendants(matching: .any)[actual + id]
            }
            return descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@ AND label == %@", "lb-secret-name-", name)).firstMatch
        }
        if intent.hasPrefix("device-row-") { return descendants(matching: .any)["lb-device-" + String(intent.dropFirst("device-row-".count))] }
        if intent.hasPrefix("remove-device-") && intent != "remove-device-dialog" { return descendants(matching: .any)["lb-remove-" + String(intent.dropFirst("remove-device-".count))] }
        if intent == "pending-device-row" || intent.hasPrefix("alert-join-request") { return lbIncomingElement(kind: "Join", processing: intent.hasSuffix("processing")) }
        if intent == "pending-removal-device-row" || intent == "remove-device-dialog" { return lbIncomingElement(kind: "RemoveDevice") }
        if intent.hasPrefix("alert-recovery-request") || intent.hasPrefix("recovery-request-badge-") || intent.hasPrefix("open-recovery-request-") {
            return lbIncomingElement(kind: "Recover", processing: intent.hasSuffix("processing"))
        }
        if intent.hasPrefix("open-deletion-request-") || intent == "secret-deletion-dialog" { return lbIncomingElement(kind: "DeleteSecret") }
        return descendants(matching: .any)[intent]
    }

    func lbWaitIntent(_ intent: String, timeout: TimeInterval = 30, visible: Bool = true) {
        let incomingIntent = intent.hasPrefix("alert-recovery-request") || intent.hasPrefix("recovery-request-badge-") || intent.hasPrefix("open-recovery-request-") || ["pending-device-row", "pending-removal-device-row", "remove-device-dialog", "alert-join-request", "secret-deletion-dialog"].contains(intent)
        if !visible && incomingIntent, let captured = lbSelection.incoming {
            let kind = intent.contains("recovery") ? "Recover" : intent.contains("removal") || intent == "remove-device-dialog" ? "RemoveDevice" : intent == "secret-deletion-dialog" ? "DeleteSecret" : "Join"
            XCTAssertTrue(captured.kind == kind, "Absence cannot be credited to an unrelated previously captured request")
            lbIncomingTerminal(captured, timeout: timeout); return
        }
        if visible && intent == "tab-devices" { lbHome(timeout: timeout); return }
        if visible && intent == "tab-secrets" { lbSecrets(timeout: timeout); return }
        if visible && intent.hasPrefix("secret-row-") {
            _ = lbFindSecret(String(intent.dropFirst("secret-row-".count)), timeout: timeout); return
        }
        if visible && (intent.hasPrefix("recovery-request-badge-") || intent.hasPrefix("open-recovery-request-")) {
            let prefix = intent.hasPrefix("recovery-request-badge-") ? "recovery-request-badge-" : "open-recovery-request-"
            _ = lbIncoming(LiveboardIncomingExpected(kind: "Recover", subject: String(intent.dropFirst(prefix.count))), timeout: timeout)
            return
        }
        if visible && ["pending-device-row", "pending-removal-device-row", "alert-join-request", "alert-recovery-request"].contains(intent) {
            let kind = intent.contains("removal") ? "RemoveDevice" : intent.contains("join") || intent == "pending-device-row" ? "Join" : "Recover"
            _ = lbIncoming(LiveboardIncomingExpected(kind: kind), timeout: timeout); return
        }
        lbWait({ self.lbVisible(self.lbElement(intent)) == visible }, timeout: timeout)
    }

    func lbTapIntent(_ intent: String, timeout: TimeInterval = 30) {
        switch intent {
        case "tab-devices": lbHome(timeout: timeout)
        case "tab-secrets": lbSecrets(timeout: timeout)
        case "add-secret-fab": lbHome(timeout: timeout); lbTap("lb-open-create", timeout: timeout)
        case "show-secret-close": lbCloseValueOrPending()
        case "pending-device-row", "pending-removal-device-row", "alert-recovery-request":
            let kind = intent.contains("removal") ? "RemoveDevice" : intent == "pending-device-row" ? "Join" : "Recover"
            _ = lbIncoming(LiveboardIncomingExpected(kind: kind), timeout: timeout)
        case "alert-recovery-request-accept", "alert-join-request-accept", "remove-device-dialog-accept", "confirm-secret-deletion":
            lbDecideCurrent(approve: true)
        case "alert-recovery-request-decline", "alert-join-request-decline", "remove-device-dialog-decline", "decline-secret-deletion":
            lbDecideCurrent(approve: false)
        default:
            if intent.hasPrefix("open-recovery-request-") || intent.hasPrefix("open-deletion-request-") {
                let prefix = intent.hasPrefix("open-recovery-request-") ? "open-recovery-request-" : "open-deletion-request-"
                let kind = intent.hasPrefix("open-recovery") ? "Recover" : "DeleteSecret"
                _ = lbIncoming(LiveboardIncomingExpected(kind: kind, subject: String(intent.dropFirst(prefix.count))), timeout: timeout)
                return
            }
            if intent.hasPrefix("secret-primary-action-") {
                let id = lbFindSecret(String(intent.dropFirst("secret-primary-action-".count)), timeout: timeout)
                lbSelection.secretId = id; lbTap("lb-primary-\(id)", timeout: timeout); return
            }
            let element = lbElement(intent)
            lbWait({ self.lbVisible(element) && element.isEnabled && element.isHittable }, timeout: timeout)
            element.tap()
        }
    }
}
