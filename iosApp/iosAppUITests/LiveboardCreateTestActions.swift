import XCTest

@MainActor
extension XCUIApplication {
    @discardableResult
    func lbFillCreate(name: String, value: String) -> Bool {
        guard lbReplaceField("lb-create-name", value: name) else { return false }
        return lbReplaceField("lb-create-value", value: value)
    }

    func lbIsCreateField(_ identifier: String) -> Bool {
        if ["lb-create-name", "lb-create-value"].contains(identifier) { return true }
        let prefix = "lb-create-word-"
        guard identifier.hasPrefix(prefix), let number = Int(identifier.dropFirst(prefix.count)) else { return false }
        return (1...24).contains(number) && identifier == prefix + String(number)
    }

    func lbRequireCreateField(_ identifier: String, timeout: TimeInterval = 30) -> XCUIElement? {
        guard lbIsCreateField(identifier), timeout > 0, timeout <= 30 else {
            XCTFail("Unsupported Create field or reachability budget"); return nil
        }
        let deadline = ProcessInfo.processInfo.systemUptime + timeout
        lbWait({ self.descendants(matching: .any).matching(identifier: "lb-create").count == 1 &&
            self.descendants(matching: .any).matching(identifier: "lb-create-fields").count == 1 }, timeout: timeout,
            message: "One active Create editor viewport is required")
        var previous: [String]?, unchanged = 0
        for gesture in 0...8 {
            guard ProcessInfo.processInfo.systemUptime < deadline else {
                XCTFail("Create field reachability deadline expired"); return nil
            }
            guard let state = lbCreateFieldState(identifier) else { return nil }
            if let field = state.field, state.clip.contains(state.frame) { return field }
            guard gesture < 8 else { break }
            var stack: [any XCUIElementSnapshot]
            do { stack = [try snapshot()] } catch { XCTFail("Immutable Create viewport metadata is unavailable"); return nil }
            var visited = 0, matches = 0
            var visible: [(identifier: String, frame: CGRect)] = []
            while let node = stack.popLast() {
                visited += 1
                guard visited <= 8192 else { XCTFail("Create snapshot exceeds its finite traversal budget"); return nil }
                let id = node.identifier, rectangle = node.frame
                if id.hasPrefix("lb-create-") {
                    matches += 1
                    guard matches <= 64 else { XCTFail("Create viewport metadata exceeds its finite budget"); return nil }
                    if lbIsCreateField(id) && rectangle.intersects(state.clip) { visible.append((id, rectangle)) }
                }
                stack.append(contentsOf: node.children)
            }
            guard Set(visible.map(\.identifier)).count == visible.count else {
                XCTFail("Visible Create field identifiers are ambiguous"); return nil
            }
            let keys = visible.map { "\($0.identifier):\($0.frame.minY):\($0.frame.maxY)" }.sorted()
            if keys == previous { unchanged += 1 } else { unchanged = 0 }
            guard unchanged < 2 else { XCTFail("Create viewport did not progress after native scrolling"); return nil }
            previous = keys
            let upward: Bool
            if state.field != nil {
                guard state.frame.width <= state.clip.width && state.frame.height <= state.clip.height,
                      state.frame.minX >= state.clip.minX && state.frame.maxX <= state.clip.maxX else {
                    XCTFail("Create field cannot fit the actual viewport"); return nil
                }
                upward = state.frame.midY > state.clip.midY
            } else if ["lb-create-name", "lb-create-value"].contains(identifier) {
                guard !visible.isEmpty else { XCTFail("No current editor rows establish scrolling ownership"); return nil }
                upward = false
            } else {
                let words = visible.compactMap { Int($0.identifier.dropFirst("lb-create-word-".count)) }
                guard let first = words.min(), let last = words.max(),
                      let wanted = Int(identifier.dropFirst("lb-create-word-".count)), wanted < first || wanted > last else {
                    XCTFail("Missing Create word is not beyond the observed current viewport"); return nil
                }
                upward = wanted > last
            }
            let start = CGPoint(x: state.clip.midX, y: state.clip.minY + state.clip.height * (upward ? 0.75 : 0.25))
            let end = CGPoint(x: state.clip.midX, y: state.clip.minY + state.clip.height * (upward ? 0.25 : 0.75))
            let origin = coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0)), appFrame = frame
            origin.withOffset(CGVector(dx: start.x - appFrame.minX, dy: start.y - appFrame.minY))
                .press(forDuration: 0.05, thenDragTo: origin.withOffset(CGVector(dx: end.x - appFrame.minX, dy: end.y - appFrame.minY)))
        }
        XCTFail("Exact Create field was not fully reachable within eight viewport gestures")
        return nil
    }

    func lbTapCreateSeedCount(_ identifier: String) {
        let identifiers = ["lb-create-count-12", "lb-create-count-24"]
        guard identifiers.contains(identifier) else { XCTFail("Unsupported Create count control"); return }
        let deadline = ProcessInfo.processInfo.systemUptime + 30
        lbWait({ identifiers.allSatisfy { self.descendants(matching: .any).matching(identifier: $0).count == 1 } },
               timeout: 30, message: "One current Seed count pair is required")
        var previous: [CGFloat]?, unchanged = 0
        for gesture in 0...8 {
            guard ProcessInfo.processInfo.systemUptime < deadline else {
                XCTFail("Create count reachability deadline expired"); return
            }
            guard let state = lbCreateFieldState(identifier, countControl: true), state.field != nil else {
                XCTFail("Exact current Seed count control is unavailable"); return
            }
            let center = CGPoint(x: state.frame.midX, y: state.frame.midY)
            if state.clip.contains(center), state.frame.width <= state.clip.width, state.frame.height <= state.clip.height {
                // Re-read current scope and measured geometry immediately before native input.
                guard let current = lbCreateFieldState(identifier, countControl: true), current.field != nil,
                      current.frame.width <= current.clip.width, current.frame.height <= current.clip.height,
                      current.clip.contains(CGPoint(x: current.frame.midX, y: current.frame.midY)) else {
                    XCTFail("Seed count moved before native input"); return
                }
                let origin = coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0)), appFrame = frame
                origin.withOffset(CGVector(dx: current.frame.midX - appFrame.minX,
                                           dy: current.frame.midY - appFrame.minY)).tap()
                guard ProcessInfo.processInfo.systemUptime < deadline else { XCTFail("Seed count result deadline expired"); return }
                let count = identifier == "lb-create-count-12" ? 12 : 24
                let expected = Set((1...count).map { "lb-create-word-\($0)" })
                lbWait({
                    guard let root = try? self.snapshot() else { return false }
                    var stack: [any XCUIElementSnapshot] = [root], words: [any XCUIElementSnapshot] = []
                    var visited = 0
                    while let node = stack.popLast() {
                        visited += 1
                        guard visited <= 8192 else { return false }
                        if node.identifier.hasPrefix("lb-create-word-") {
                            words.append(node)
                            guard words.count <= count else { return false }
                        }
                        stack.append(contentsOf: node.children)
                    }
                    return words.count == count && Set(words.map { $0.identifier }) == expected &&
                        words.allSatisfy { $0.elementType == .textView && $0.isEnabled }
                }, timeout: max(0, deadline - ProcessInfo.processInfo.systemUptime),
                   message: "Actual Seed count action must expose exactly its enabled canonical word fields")
                guard let completed = lbCreateFieldState(identifier, countControl: true), completed.field != nil,
                      completed.clip.contains(CGPoint(x: completed.frame.midX, y: completed.frame.midY)) else { XCTFail("Selected Seed count is no longer visible"); return }
                return
            }
            guard gesture < 8, state.frame.width <= state.clip.width, state.frame.height <= state.clip.height,
                  center.x > state.clip.minX, center.x < state.clip.maxX,
                  center.y <= state.clip.minY || center.y >= state.clip.maxY else {
                XCTFail("Seed count cannot fit the actual editor viewport"); return
            }
            let key = [state.frame.minY, state.frame.maxY, state.clip.minY, state.clip.maxY]
            if key == previous { unchanged += 1 } else { unchanged = 0 }
            guard unchanged < 2 else { XCTFail("Seed count viewport made no native scroll progress"); return }
            previous = key
            let upward = state.frame.midY > state.clip.midY
            let start = CGPoint(x: state.clip.midX, y: state.clip.minY + state.clip.height * (upward ? 0.75 : 0.25))
            let end = CGPoint(x: state.clip.midX, y: state.clip.minY + state.clip.height * (upward ? 0.25 : 0.75))
            let origin = coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0)), appFrame = frame
            origin.withOffset(CGVector(dx: start.x - appFrame.minX, dy: start.y - appFrame.minY))
                .press(forDuration: 0.05, thenDragTo: origin.withOffset(CGVector(dx: end.x - appFrame.minX, dy: end.y - appFrame.minY)))
        }
        XCTFail("Exact Seed count was not reachable within eight viewport gestures")
    }

    private func lbCreateFieldState(_ identifier: String, countControl: Bool = false) -> (field: XCUIElement?, viewport: XCUIElement, frame: CGRect, clip: CGRect)? {
        let all = descendants(matching: .any).matching(identifier: identifier)
        let modals = descendants(matching: .any).matching(identifier: "lb-create")
        let viewports = descendants(matching: .any).matching(identifier: "lb-create-fields")
        guard (countControl ? ["lb-create-count-12", "lb-create-count-24"].contains(identifier) : lbIsCreateField(identifier)),
              all.count <= 1, modals.count == 1, viewports.count == 1 else {
            XCTFail("Create field or owning modal/viewport identity is ambiguous"); return nil
        }
        let modal = modals.element(boundBy: 0), viewport = viewports.element(boundBy: 0)
        guard modal.isEnabled && viewport.isEnabled else {
            XCTFail("Unique current Create modal and viewport must remain enabled"); return nil
        }
        let finite: (CGRect) -> Bool = { rect in
            !rect.isNull && !rect.isInfinite && rect.width > 0 && rect.height > 0 &&
                [rect.minX, rect.minY, rect.width, rect.height].allSatisfy { $0.isFinite }
        }
        guard [frame, modal.frame, viewport.frame].allSatisfy(finite),
              frame.contains(modal.frame), modal.frame.contains(viewport.frame) else {
            XCTFail("Current Create viewport geometry is unavailable"); return nil
        }
        var clip = viewport.frame.intersection(modal.frame).intersection(frame)
        let keyboard = keyboards.firstMatch
        if keyboard.exists {
            guard finite(keyboard.frame) else { XCTFail("Keyboard geometry is unavailable"); return nil }
            if keyboard.frame.intersects(clip) {
                clip.size.height = max(0, min(clip.maxY, keyboard.frame.minY) - clip.minY)
            }
        }
        guard finite(clip) else { XCTFail("Create viewport is completely clipped or covered by the keyboard"); return nil }
        guard all.count == 1 else { return (nil, viewport, .zero, clip) }
        let field = all.element(boundBy: 0), rectangle = field.frame
        guard field.elementType == (countControl ? .button : .textView), field.isEnabled, finite(rectangle) else {
            XCTFail("Exact Compose Create field is disabled or lacks proven text-input geometry"); return nil
        }
        return (field, viewport, rectangle, clip)
    }

    private func lbCreateInputRefusal(_ phase: String, _ reason: String, _ shape: String = "UNAVAILABLE") {
        print("E2E: CREATE_INPUT_REFUSAL phase=\(phase) reason=\(reason) shape=\(shape)")
    }

    // One immutable public AX observation supplies all pre-input/ACK metadata.
    private func lbCreateInputScope(_ identifier: String, phase: String) -> Bool {
        guard lbIsCreateField(identifier) else { lbCreateInputRefusal(phase, "unsupported-field"); return false }
        guard let root = try? snapshot() else { lbCreateInputRefusal(phase, "snapshot-error"); return false }
        func finite(_ rectangle: CGRect) -> Bool {
            !rectangle.isNull && !rectangle.isInfinite && rectangle.width > 0 && rectangle.height > 0 &&
                [rectangle.minX, rectangle.minY, rectangle.width, rectangle.height].allSatisfy { $0.isFinite }
        }
        guard finite(root.frame) else { lbCreateInputRefusal(phase, "root-frame-invalid"); return false }
        let required = ["lb-create", "lb-create-fields", identifier]
        var stack: [(any XCUIElementSnapshot, Int?)] = [(root, nil)]
        var nodes: [String: [(any XCUIElementSnapshot, Int?)]] = [:]
        var windows: [Int: CGRect] = [:], keyboards: [CGRect] = []
        var visited = 0, windowCount = 0, scanned = false
        func refuse(_ reason: String) -> Bool {
            func countClass(_ count: Int) -> String { count == 0 ? "ZERO" : (count == 1 ? "ONE" : "MANY") }
            let shape = "scanned=\(scanned ? "T" : "F"),visited=\(visited),queued=\(stack.count),windows=\(windowCount),keyboards=\(keyboards.count),modal=\(countClass(nodes["lb-create"]?.count ?? 0)),viewport=\(countClass(nodes["lb-create-fields"]?.count ?? 0)),field=\(countClass(nodes[identifier]?.count ?? 0))"
            lbCreateInputRefusal(phase, reason, shape)
            return false
        }
        while let (node, inheritedWindow) = stack.popLast() {
            visited += 1
            guard visited <= 8192 else { return refuse("node-budget") }
            var window = inheritedWindow
            if node.elementType == .window {
                guard finite(node.frame) else { return refuse("window-frame-invalid") }
                windowCount += 1; window = windowCount; windows[windowCount] = node.frame
            }
            if required.contains(node.identifier) {
                guard finite(node.frame) else { return refuse("required-node-frame-invalid") }
                guard node.isEnabled else { return refuse("required-node-disabled") }
                nodes[node.identifier, default: []].append((node, window))
            }
            if node.elementType == .keyboard {
                guard finite(node.frame) else { return refuse("keyboard-frame-invalid") }
                keyboards.append(node.frame)
            }
            let children = node.children
            let available = 8192 - visited - stack.count
            guard available >= 0, children.count <= available else { return refuse("frontier-budget") }
            stack.append(contentsOf: children.map { ($0, window) })
        }
        scanned = true
        guard required.allSatisfy({ nodes[$0]?.count == 1 }) else { return refuse("required-cardinality") }
        guard let modal = nodes["lb-create"]?.first, let viewport = nodes["lb-create-fields"]?.first,
              let field = nodes[identifier]?.first else { return refuse("required-node-unavailable") }
        guard let windowId = modal.1 else { return refuse("modal-no-window") }
        guard let window = windows[windowId] else { return refuse("window-not-found") }
        guard viewport.1 == windowId else { return refuse("viewport-window-mismatch") }
        guard field.1 == windowId else { return refuse("field-window-mismatch") }
        guard field.0.elementType == .textView else { return refuse("field-type") }
        guard root.frame.contains(modal.0.frame) else { return refuse("root-modal-containment") }
        guard window.contains(modal.0.frame) else { return refuse("window-modal-containment") }
        guard modal.0.frame.contains(viewport.0.frame) else { return refuse("modal-viewport-containment") }
        var clip = root.frame.intersection(window).intersection(modal.0.frame).intersection(viewport.0.frame)
        for keyboard in keyboards where keyboard.intersects(clip) {
            clip.size.height = max(0, min(clip.maxY, keyboard.minY) - clip.minY)
        }
        guard finite(clip) else { return refuse("clip-invalid") }
        guard clip.contains(field.0.frame) else { return refuse("field-outside-clip") }
        return true
    }

    @discardableResult
    func lbReplaceField(_ identifier: String, value: String) -> Bool {
        let deadline = ProcessInfo.processInfo.systemUptime + 60
        func remaining() -> TimeInterval { max(0, deadline - ProcessInfo.processInfo.systemUptime) }
        guard remaining() > 0, lbRequireCreateField(identifier, timeout: min(30, remaining())) != nil,
              let state = lbCreateFieldState(identifier), let field = state.field,
              state.clip.contains(state.frame), remaining() > 0 else {
            XCTFail("Exact Create field changed before the input action"); return false
        }
        let hittable = field.isHittable
        guard remaining() > 0 else { XCTFail("Create typing phase deadline expired before tap"); return false }
        if hittable { field.tap() }
        else {
            let origin = coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0)), appFrame = frame
            let point = origin.withOffset(CGVector(dx: state.frame.midX - appFrame.minX, dy: state.frame.midY - appFrame.minY))
            guard remaining() > 0 else { XCTFail("Create typing phase deadline expired before pointer tap"); return false }
            point.tap()
        }
        guard remaining() > 0, lbRequireCreateField(identifier, timeout: min(30, remaining())) != nil,
              let current = lbCreateFieldState(identifier), let focused = current.field,
              current.clip.contains(current.frame), let rawOld = focused.value as? String,
              remaining() > 0 else {
            XCTFail("Exact Create field became unavailable after keyboard reflow"); return false
        }
        let initialPlaceholder = focused.placeholderValue
        guard remaining() > 0 else { XCTFail("Create typing phase deadline expired before reading input"); return false }
        let old = rawOld == initialPlaceholder ? "" : rawOld
        func currentText(_ phase: String) -> (XCUIElement, String, String?)? {
            guard remaining() > 0 else { lbCreateInputRefusal(phase, "pre-observation-deadline"); return nil }
            guard lbCreateInputScope(identifier, phase: phase) else { return nil }
            guard remaining() > 0 else { lbCreateInputRefusal(phase, "post-observation-deadline"); return nil }
            let observed = focused.value
            guard remaining() > 0 else { lbCreateInputRefusal(phase, "post-live-value-deadline"); return nil }
            guard let raw = observed as? String else {
                lbCreateInputRefusal(phase, observed == nil ? "live-value-nil" : "live-value-other"); return nil
            }
            return (focused, raw, initialPlaceholder)
        }
        func sendOnce(_ input: String, previous: String, expected: String) -> Bool {
            guard let (target, text, placeholder) = currentText("PRE_SEND") else {
                XCTFail("Exact Create prefix changed before ordinary input"); return false
            }
            guard text == previous || (previous.isEmpty && text == placeholder) else {
                lbCreateInputRefusal("PRE_SEND", "prefix-mismatch")
                XCTFail("Exact Create prefix changed before ordinary input"); return false
            }
            guard remaining() > 0 else { XCTFail("Create typing phase deadline expired before input"); return false }
            target.typeText(input)
            guard remaining() > 0 else { XCTFail("Create typing phase deadline expired"); return false }
            var accepted = false, invalid = false
            let observe: () -> Bool = {
                guard let (_, observed, placeholder) = currentText("ACK") else { invalid = true; return true }
                if observed == expected || (expected.isEmpty && observed == placeholder) { accepted = true; return true }
                if observed != previous && !(previous.isEmpty && observed == placeholder) { self.lbCreateInputRefusal("ACK", "unexpected-prefix"); invalid = true; return true }
                return false
            }
            if !observe(), remaining() > 0 {
                lbWait(observe, timeout: remaining(), message: "Exact Create prefix acknowledgement was not observed")
            }
            guard accepted, !invalid, remaining() > 0 else {
                if !invalid { lbCreateInputRefusal("ACK", "bounded-unacknowledged") }
                XCTFail("Create input was not acknowledged within the shared typing phase"); return false
            }
            return true
        }
        var prefix = old
        while !prefix.isEmpty {
            var next = prefix
            next.removeLast()
            guard sendOnce(XCUIKeyboardKey.delete.rawValue, previous: prefix, expected: next) else { return false }
            prefix = next
        }
        var chunk = ""
        for character in value {
            chunk.append(character)
            if chunk.count == 3 {
                let next = prefix + chunk
                guard sendOnce(chunk, previous: prefix, expected: next) else { return false }
                prefix = next
                chunk = ""
            }
        }
        if !chunk.isEmpty {
            let next = prefix + chunk
            guard sendOnce(chunk, previous: prefix, expected: next) else { return false }
            prefix = next
        }
        guard let (_, completed, _) = currentText("FINAL") else {
            XCTFail("Exact Create field changed during ordinary typing"); return false
        }
        XCTAssertTrue(completed == value, "Exact Create field value did not match the requested input")
        return completed == value
    }

    func lbSubmitCreate() { lbDismissKeyboard(); lbTap("lb-create-submit"); _ = lbConfirmSystemIfPresent() }

    func lbCreate(name: String, value: String) {
        lbHome(); lbTap("lb-open-create")
        guard lbFillCreate(name: name, value: value) else { return }
        lbSubmitCreate()
        lbWait({ !self.descendants(matching: .any)["lb-create"].exists }, timeout: 180)
        _ = lbFindSecret(name)
    }

}
