import XCTest

/// Pure key used by the real bounded list traversal below.
func liveboardViewportKey(id: String, row: CGRect, viewport: CGRect) -> String {
    let clippedTop = row.minY < viewport.minY - 8
    let clippedBottom = row.maxY > viewport.maxY + 8
    return "\(id):\(clippedTop):\(clippedBottom)"
}

private struct LiveboardSecretViewportNode {
    let identifier: String
    let label: String
    let frame: CGRect
}


private func liveboardSettledViewportKey(_ rows: [LiveboardSecretViewportNode], bounds: CGRect, empty: Bool) -> [String]? {
    let frames = [bounds] + rows.map(\.frame)
    guard frames.allSatisfy({ !$0.isNull && !$0.isEmpty &&
        [$0.minX, $0.minY, $0.width, $0.height].allSatisfy({ $0.isFinite }) }) else { return nil }
    return ["\(bounds.minX):\(bounds.minY):\(bounds.width):\(bounds.height):\(empty)"] + rows.map {
        "\($0.identifier):\($0.frame.minX - bounds.minX):\($0.frame.minY - bounds.minY):\($0.frame.width):\($0.frame.height)"
    }.sorted()
}

/// Passive card discovery uses one immutable AX/window observation, never a tap.
func liveboardPassiveSecretReachable(identifier: String, root: any XCUIElementSnapshot, bounds: CGRect) -> Bool {
    let prefix = "lb-secret-"
    guard identifier.hasPrefix(prefix), !identifier.hasPrefix("lb-secret-name-") else { return false }
    let id = String(identifier.dropFirst(prefix.count))
    guard !id.isEmpty else { return false }
    func finite(_ frame: CGRect) -> Bool {
        [frame.minX, frame.minY, frame.width, frame.height].allSatisfy { $0.isFinite }
            && !frame.isEmpty && !frame.isNull
    }
    guard finite(root.frame), finite(bounds) else { return false }
    var stack: [(any XCUIElementSnapshot, Int?)] = [(root, nil)]
    var windows: [Int: CGRect] = [:]
    var nodes: [String: [(CGRect, Int?)]] = [:]
    let required = ["lb-secrets", "lb-secrets-list", identifier, "lb-secret-name-" + id]
    var visited = 0, nextWindow = 0
    while let (node, inheritedWindow) = stack.popLast() {
        visited += 1
        guard visited <= 8192 else { return false }
        var window = inheritedWindow
        if node.elementType == .window {
            guard finite(node.frame) else { return false }
            nextWindow += 1
            window = nextWindow
            windows[nextWindow] = node.frame
        }
        if required.contains(node.identifier) {
            guard finite(node.frame) else { return false }
            nodes[node.identifier, default: []].append((node.frame, window))
        }
        stack.append(contentsOf: node.children.map { ($0, window) })
    }
    guard required.allSatisfy({ nodes[$0]?.count == 1 }),
          let route = nodes["lb-secrets"]?.first,
          let list = nodes["lb-secrets-list"]?.first,
          let card = nodes[identifier]?.first,
          let name = nodes["lb-secret-name-" + id]?.first,
          let windowId = route.1, let window = windows[windowId],
          list.1 == windowId, card.1 == windowId, name.1 == windowId else { return false }
    let clip = list.0.intersection(route.0).intersection(window).intersection(root.frame)
    guard finite(clip), clip == bounds else { return false }
    return finite(card.0.intersection(clip)) && finite(name.0.intersection(clip))
}

@MainActor
extension XCUIApplication {
    func lbVisibleSecretId(_ name: String) -> String? {
        let labels = descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@ AND label == %@", "lb-secret-name-", name))
        let matches = labels.allElementsBoundByIndex.filter { lbVisible($0) }
        XCTAssertLessThanOrEqual(matches.count, 1, "Ambiguous visible Secret name")
        guard matches.count == 1 else { return nil }
        return String(matches[0].identifier.dropFirst("lb-secret-name-".count))
    }

    func lbFindSecret(_ name: String, timeout: TimeInterval = 180) -> String {
        guard lbSecrets(timeout: timeout) else { return "invalid-ambiguous-pass" }
        var ids: Set<String> = []
        let deadline = Date().addingTimeInterval(timeout)
        repeat {
            ids = []
            lbTraverseSecrets(target: nil) { nodes in
                for label in nodes where label.label == name {
                    ids.insert(String(label.identifier.dropFirst("lb-secret-name-".count)))
                }
            }
        } while ids.isEmpty && Date() < deadline
        XCTAssertEqual(ids.count, 1, "Secret name must resolve to one opaque current Pass ID")
        guard ids.count == 1, let id = ids.first else { return "invalid-ambiguous-pass" }
        lbSelection.secrets[name] = id
        lbSelection.secretId = id
        XCTAssertTrue(lbFindInList("lb-secret-\(id)"), "Exact Secret card was not reachable")
        return id
    }

    @discardableResult
    func lbFindSecrets(_ names: [String], timeout: TimeInterval = 180) -> [String: String] {
        guard !names.isEmpty else { return [:] }
        guard timeout.isFinite, timeout > 0, timeout <= 180,
              Set(names).count == names.count, names.allSatisfy({ !$0.isEmpty }) else {
            XCTFail("Batch Secret names/budget are invalid"); return [:]
        }
        let deadline = Date().addingTimeInterval(timeout)
        guard lbSecrets(timeout: max(0, deadline.timeIntervalSinceNow)) else { return [:] }
        repeat {
            guard Date() < deadline else { break }
            var ids: [String: Set<String>] = [:]
            var scoped: Set<String> = []
            var valid = true, completed = false
            lbTraverseSecrets(target: nil, phaseDeadline: deadline, observeViewport: { rows, root, bounds in
                for row in rows where names.contains(row.label) {
                    let id = String(row.identifier.dropFirst("lb-secret-name-".count))
                    guard !id.isEmpty else { valid = false; continue }
                    ids[row.label, default: []].insert(id)
                    if liveboardPassiveSecretReachable(identifier: "lb-secret-" + id, root: root, bounds: bounds) {
                        scoped.insert(id)
                    } else { valid = false }
                }
            }, completed: { completed = true }, observe: { _ in })
            guard completed, valid, Date() < deadline else {
                XCTFail("Batch Secret sweep was incomplete or invalid"); return [:]
            }
            guard !ids.values.contains(where: { $0.count != 1 }) else {
                XCTFail("Ambiguous Secret name in complete batch sweep"); return [:]
            }
            if names.allSatisfy({ ids[$0]?.count == 1 }) {
                let resolved = Dictionary(uniqueKeysWithValues: names.map { ($0, ids[$0]!.first!) })
                guard Set(resolved.values).count == names.count,
                      resolved.values.allSatisfy({ scoped.contains($0) }) else {
                    XCTFail("Batch Secret identities/scoped cards are invalid"); return [:]
                }
                for (name, id) in resolved { lbSelection.secrets[name] = id }
                lbSelection.secretId = names.last.flatMap { resolved[$0] }
                return resolved
            }
        } while Date() < deadline
        XCTFail("Expected Secret set was not available within the shared phase budget")
        return [:]
    }

    @discardableResult
    func lbFindInList(_ intent: String) -> Bool {
        lbSecrets()
        if intent.hasPrefix("secret-row-") {
            let name = String(intent.dropFirst("secret-row-".count))
            var ids: Set<String> = []
            lbTraverseSecrets(target: nil) { nodes in
                for label in nodes where label.label == name { ids.insert(String(label.identifier.dropFirst("lb-secret-name-".count))) }
            }
            XCTAssertLessThanOrEqual(ids.count, 1, "Ambiguous Secret name in the complete list")
            guard let id = ids.first else { lbSelection.secrets.removeValue(forKey: name); return false }
            lbSelection.secrets[name] = id
            return lbFindInList("lb-secret-\(id)")
        }
        let target = lbElement(intent)
        if let (root, bounds) = try? lbSecretsViewportSnapshot(),
           lbSecretTargetReachable(target, root: root, bounds: bounds) { return true }
        return lbTraverseSecrets(target: target, observe: { _ in })
    }

    private func lbSecretTargetReachable(_ target: XCUIElement, root: any XCUIElementSnapshot, bounds: CGRect) -> Bool {
        guard lbVisible(target) else { return false }
        if target.identifier.hasPrefix("lb-secret-") && !target.identifier.hasPrefix("lb-secret-name-") {
            return liveboardPassiveSecretReachable(identifier: target.identifier, root: root, bounds: bounds)
        }
        return target.isHittable
    }

    @discardableResult
    private func lbTraverseSecrets(target: XCUIElement?, phaseDeadline: Date? = nil,
                                   observeViewport: (([LiveboardSecretViewportNode], any XCUIElementSnapshot, CGRect) -> Void)? = nil,
                                   completed: (() -> Void)? = nil,
                                   observe: @escaping ([LiveboardSecretViewportNode]) -> Void) -> Bool {
        if let phaseDeadline {
            guard Date() < phaseDeadline else { return false }
            _ = lbRequire("lb-secrets-list", timeout: max(0, phaseDeadline.timeIntervalSinceNow))
        } else {
            _ = lbRequire("lb-secrets-list")
        }
        var validSweep = true
        var directionEnd: Date?
        func withinPhase() -> Bool {
            (phaseDeadline.map { Date() < $0 } ?? true) && (directionEnd.map { Date() < $0 } ?? true)
        }
        func viewport() -> ([LiveboardSecretViewportNode], CGRect, Bool, CGRect, any XCUIElementSnapshot)? {
            let snapshotDeadline = Date().addingTimeInterval(15)
            for attempt in 1...3 {
                guard withinPhase() else { return nil }
                do {
                    let (root, bounds) = try lbSecretsViewportSnapshot()
                    guard withinPhase() else { return nil }
                    var stack: [any XCUIElementSnapshot] = [root]
                    var visited = 0
                    var rows: [LiveboardSecretViewportNode] = []
                    var empty = false
                    while let node = stack.popLast() {
                        visited += 1
                        guard visited <= 8192 else { XCTFail("Scoped list metadata exceeds traversal budget"); return nil }
                        if node.frame.intersects(bounds) {
                            if node.identifier.hasPrefix("lb-secret-name-") { rows.append(LiveboardSecretViewportNode(identifier: node.identifier, label: node.label, frame: node.frame)) }
                            if ["No secrets", "Нет секретов"].contains(node.label) { empty = true }
                        }
                        stack.append(contentsOf: node.children)
                    }
                    XCTAssertEqual(Set(rows.map(\.identifier)).count, rows.count, "Duplicate same-ID rows in one immutable viewport")
                    if Set(rows.map(\.identifier)).count != rows.count { validSweep = false }
                    return (rows, bounds, empty, root.frame, root)
                } catch {
                    print("E2E: IOS_TEST50_VIEWPORT_SNAPSHOT_RETRY attempt=\(attempt)")
                    if Date() >= snapshotDeadline || !withinPhase() { break }
                }
            }
            XCTFail("Immutable list viewport unavailable within bounded retries")
            return nil
        }
        // Idle equality settles geometry; only distinct directed gestures can prove an edge.
        func settled() -> ([LiveboardSecretViewportNode], CGRect, Bool, CGRect, any XCUIElementSnapshot)? {
            var previous: [String]?, result: ([LiveboardSecretViewportNode], CGRect, Bool, CGRect, any XCUIElementSnapshot)?
            var polls = 0, refused = false
            let expectation = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
                polls += 1
                guard polls <= 64, withinPhase(), let value = viewport(), validSweep else { refused = true; return true }
                observe(value.0); observeViewport?(value.0, value.4, value.1)
                guard withinPhase(), let key = liveboardSettledViewportKey(value.0, bounds: value.1, empty: value.2) else {
                    refused = true; return true
                }
                if key == previous { result = value; return true }
                previous = key
                return false
            }, object: nil)
            guard let end = directionEnd else { return nil }
            let limit = min(end, phaseDeadline ?? end)
            let outcome = XCTWaiter.wait(for: [expectation], timeout: max(0, limit.timeIntervalSinceNow))
            return outcome == .completed && !refused && validSweep && withinPhase() ? result : nil
        }
        var reachedBothEnds = true
        var sweepFacts: [String] = []
        for upward in [true, false] {
            let directionDeadline = Date().addingTimeInterval(60)
            directionEnd = directionDeadline
            var before = settled()
            var unchanged = 0
            var iterations = 0, resets = 0, rowCount = 0, topClipped = 0, bottomClipped = 0
            var lastEmpty = false
            for _ in 0..<64 {
                guard withinPhase(), validSweep, let current = before,
                      let key = liveboardSettledViewportKey(current.0, bounds: current.1, empty: current.2) else { break }
                let (_, bounds, _, appFrame, root) = current
                if let target, lbSecretTargetReachable(target, root: root, bounds: bounds), withinPhase(), validSweep { return true }
                let start = CGPoint(x: bounds.midX, y: bounds.minY + bounds.height * (upward ? 0.25 : 0.75))
                let end = CGPoint(x: bounds.midX, y: bounds.minY + bounds.height * (upward ? 0.75 : 0.25))
                guard [start.x, start.y, end.x, end.y].allSatisfy({ $0.isFinite }),
                      start.x > bounds.minX, start.x < bounds.maxX,
                      start.y > bounds.minY, start.y < bounds.maxY,
                      end.x > bounds.minX, end.x < bounds.maxX,
                      end.y > bounds.minY, end.y < bounds.maxY else {
                    XCTFail("List gesture does not fit the current viewport"); return false
                }
                guard withinPhase(), validSweep else { break }
                let origin = coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0))
                let from = origin.withOffset(CGVector(dx: start.x - appFrame.minX, dy: start.y - appFrame.minY))
                let to = origin.withOffset(CGVector(dx: end.x - appFrame.minX, dy: end.y - appFrame.minY))
                guard withinPhase(), validSweep else { break }
                from.press(forDuration: 0.05, thenDragTo: to)
                guard withinPhase(), let after = settled(), validSweep,
                      let next = liveboardSettledViewportKey(after.0, bounds: after.1, empty: after.2), withinPhase() else { break }
                unchanged = (!after.0.isEmpty || after.2) && next == key ? unchanged + 1 : 0
                iterations += 1
                rowCount = after.0.count; lastEmpty = after.2
                topClipped = after.0.filter { $0.frame.minY < after.1.minY - 8 }.count
                bottomClipped = after.0.filter { $0.frame.maxY > after.1.maxY + 8 }.count
                if unchanged == 0 { resets += 1 }
                if (iterations - 1) % 4 == 0 {
                    let direction = upward ? "UP" : "DOWN"
                    print("E2E_SECRET_SWEEP_PROGRESS_V1 D=\(direction) I=\(iterations) U=\(unchanged) R=\(rowCount) E=\(lastEmpty ? 1 : 0) T=\(topClipped) B=\(bottomClipped) X=\(resets)")
                }
                before = after
                if unchanged >= 3 { break }
            }
            if let (rows, bounds, _, _, root) = viewport() {
                observe(rows)
                observeViewport?(rows, root, bounds)
            } else { validSweep = false }
            let direction = upward ? "UP" : "DOWN"
            let expired = Date() >= directionDeadline ? 1 : 0
            sweepFacts.append("D=\(direction) I=\(iterations) U=\(unchanged) R=\(rowCount) E=\(lastEmpty ? 1 : 0) T=\(topClipped) B=\(bottomClipped) X=\(resets) EXPIRED=\(expired)")
            reachedBothEnds = reachedBothEnds && unchanged >= 3 && validSweep && withinPhase()
        }
        // Failure-only closed scalars from the existing sweep; never names, IDs, values or extra AX reads.
        if !reachedBothEnds {
            let line = "E2E_SECRET_SWEEP_FAIL_V1 " + sweepFacts.joined(separator: " | ")
            print(line.utf8.count + 1 <= 512 ? line : "E2E_SECRET_SWEEP_FAIL_V1 AVAILABILITY=UNAVAILABLE")
        }
        XCTAssertTrue(reachedBothEnds, "Absence/uniqueness needs both independently proven list ends")
        if reachedBothEnds && validSweep && withinPhase() { completed?() }
        return false
    }

    func lbHide(_ id: String) {
        let value = lbRequire("lb-value-\(id)")
        let primary = lbRequire("lb-primary-\(id)")
        XCTAssertTrue(lbLabel(primary, matches: ["Hide", "Скрыть"]), "Hide requires an already revealed exact card; never retry Show")
        XCTAssertTrue(value.exists)
        lbTap("lb-primary-\(id)")
        lbWait({ !self.descendants(matching: .any)["lb-value-\(id)"].exists && !self.descendants(matching: .any)["lb-copy-\(id)"].exists })
    }

    func lbCloseValueOrPending() {
        guard let id = lbSelection.secretId else { XCTFail("No selected Secret to close"); return }
        if descendants(matching: .any)["lb-value-\(id)"].exists { lbHide(id) }
        else {
            if descendants(matching: .any)["lb-home"].exists {
                XCTAssertEqual(descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "lb-value-")).count, 0)
                return
            }
            let primary = lbRequire("lb-primary-\(id)")
            XCTAssertTrue(!primary.isEnabled || lbLabel(primary, matches: ["Recover", "Восстановить"]),
                          "Close must preserve pending/declined state without issuing another Show")
            lbHome()
        }
    }

    func lbRequestRecovery(_ name: String) {
        let id = lbFindSecret(name)
        let primary = lbRequire("lb-primary-\(id)")
        lbWait({ primary.isEnabled && self.lbLabel(primary, matches: ["Recover", "Восстановить"]) }, timeout: 180)
        lbTap("lb-primary-\(id)")
        lbWait({ !primary.isEnabled && !self.descendants(matching: .any)["lb-delete-\(id)"].isEnabled }, timeout: 180)
    }

    func lbLabel(_ element: XCUIElement, matches choices: [String]) -> Bool {
        let labels = [element.label] + element.descendants(matching: .staticText).allElementsBoundByIndex.map(\.label)
        return choices.contains { choice in labels.contains { $0.trimmingCharacters(in: .whitespacesAndNewlines).caseInsensitiveCompare(choice) == .orderedSame } }
    }
}

@MainActor
extension XCUIApplication {
    // Bounded sequential test observation; not physical-hit or Core authority.
    private func lbRouteGeometry(_ target: CGRect?, app: CGRect?) -> String {
        guard let target, let app else { return "UNKNOWN" }
        let values = [target.minX, target.minY, target.width, target.height,
                      app.minX, app.minY, app.width, app.height]
        guard values.allSatisfy({ $0.isFinite }), !target.isNull, !app.isNull else { return "INVALID" }
        guard !target.isEmpty, !app.isEmpty else { return "EMPTY" }
        if app.contains(target) { return "CONTAINED" }
        return app.intersects(target) ? "INTERSECTING" : "OUTSIDE"
    }

    func lbObserveSecretsTap(_ sample: [String: Any]) {
        func flag(_ key: String) -> String {
            guard let value = sample[key] as? Bool else { return "UNKNOWN" }
            return value ? "TRUE" : "FALSE"
        }
        func bounds(_ key: String) -> CGRect? {
            guard let values = sample[key] as? [Double], values.count == 4 else { return nil }
            return CGRect(x: values[0], y: values[1], width: values[2], height: values[3])
        }
        let geometry = lbRouteGeometry(bounds("targetFrame"), app: bounds("appFrame"))
        print("E2E_SECRETS_TAP_V1 X=\(flag("exists")) V=\(flag("visible")) E=\(flag("enabled")) H=\(flag("hittable")) G=\(geometry)")
    }

    private func lbObserveSecretsRoutes() {
        let appBounds = frame
        let roles = [("HOME", "lb-home"), ("SECRETS", "lb-secrets"), ("OVERLAY", "lb-overlay-shade"),
                     ("CREATE", "lb-create"), ("PROFILE", "lb-profile")]
        var fields: [String] = []
        for (role, identifier) in roles {
            let query = descendants(matching: .any).matching(identifier: identifier)
            let count = query.count
            let category = count == 0 ? "ZERO" : count == 1 ? "ONE" : count > 1 ? "MANY" : "UNKNOWN"
            var geometry = "UNKNOWN"
            if count == 1 {
                let element = descendants(matching: .any)[identifier]
                if element.exists { geometry = lbRouteGeometry(element.frame, app: appBounds) }
            }
            fields.append("\(role)=\(category):\(geometry)")
        }
        print("E2E_SECRETS_ROUTES_V1 " + fields.joined(separator: " "))
    }

    func lbRequireSecretsObserved(timeout: TimeInterval) -> XCUIElement {
        var captured = false
        return lbRequire("lb-secrets", timeout: timeout, observedCount: { count in
            guard count != 1, !captured else { return }
            captured = true
            self.lbObserveSecretsRoutes()
        })
    }
}
