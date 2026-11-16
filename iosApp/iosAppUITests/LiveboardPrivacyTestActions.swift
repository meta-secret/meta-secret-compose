import XCTest
import UIKit

@MainActor
extension XCUIApplication {
    func lbShow(_ name: String, expected: String? = nil, selectedId: String? = nil,
                valueTimeout: TimeInterval = 180, freshActionResolution: Bool = false, inputReturned: (() -> Void)? = nil) {
        guard valueTimeout.isFinite, valueTimeout > 0, valueTimeout <= 180,
              !freshActionResolution || selectedId == nil else {
            XCTFail("Fresh direct Show requires a new global resolution and its original value budget"); return
        }
        var valueSamples: [String] = []
        var valueSamplesCapped = false
        func trace(_ stage: String, _ count: Int? = nil, _ visible: Bool? = nil) {
            let countText = count.map { String($0) } ?? "UNKNOWN"
            let visibleText = visible.map { $0 ? "TRUE" : "FALSE" } ?? "UNKNOWN"
            let line = "LB_SHOW_GATE_V1 stage=\(stage) count=\(countText) visible=\(visibleText)"
            if stage == "value-predicate" {
                guard !valueSamplesCapped, !valueSamples.contains(line) else { return }
                guard valueSamples.count < 2 else {
                    valueSamplesCapped = true
                    print("LB_SHOW_GATE_V1 stage=value-predicate count=UNKNOWN visible=UNKNOWN status=CAPPED")
                    return
                }
                valueSamples.append(line)
            }
            print(line)
        }
        trace("find-start")
        let id: String
        if let selectedId {
            guard !selectedId.isEmpty, lbSelection.secretId == selectedId, lbSelection.secrets[name] == selectedId,
                  lbFindInList("lb-secret-\(selectedId)"), lbHasExactShowSelection(name, id: selectedId) else {
                XCTFail("Exact same-request Show target is unavailable"); return
            }
            id = selectedId
        } else { id = lbFindSecret(name) }
        trace("find-complete")
        let primary = lbRequire("lb-primary-\(id)")
        var primaryWaited = false
        lbWait({
            let ready = primary.isEnabled && self.lbLabel(primary, matches: ["Show", "Показать"])
            if !ready { primaryWaited = true }
            return ready
        }, timeout: 180)
        if freshActionResolution && (primaryWaited || !lbHasExactShowSelection(name, id: id)) {
            let currentId = lbFindSecret(name, timeout: 30)
            guard currentId == id else {
                XCTFail("Fresh direct Show identity changed during readiness"); return
            }
        }
        if selectedId != nil || freshActionResolution {
            guard lbHasExactShowSelection(name, id: id), primary.isEnabled,
                  lbLabel(primary, matches: ["Show", "Показать"]) else {
                XCTFail("Exact same-request Show target changed before input"); return
            }
        }
        trace("show-ready")
        var freshInputAuthorized = !freshActionResolution
        let beforeInput: (() -> Bool)? = freshActionResolution ? {
            let allowed = self.lbHasExactShowSelection(name, id: id) && primary.isEnabled
                && self.lbLabel(primary, matches: ["Show", "Показать"])
            freshInputAuthorized = allowed
            return allowed
        } : nil
        lbTap("lb-primary-\(id)", beforeInput: beforeInput)
        guard freshInputAuthorized else { return }
        _ = lbConfirmSystemIfPresent()
        trace("tap-complete")
        inputReturned?()
        trace("require-value")
        _ = lbRequire("lb-value-\(id)", timeout: valueTimeout,
                      observedPredicate: { count, visible in trace("value-predicate", count, visible) })
        trace("value-ready")
        if let expected { lbAssertValue(expected, id: id) }
        trace("complete")
    }

    func lbAssertValue(_ expected: String, id: String? = nil) {
        let key = id ?? lbSelection.secretId
        XCTAssertNotNil(key, "No exact authorized Secret selection")
        guard let key else { return }
        let value = lbRequire("lb-value-\(key)", timeout: 180)
        lbWait({
            let text = value.label
            if text == expected || (value.value as? String) == expected { return true }
            let labels = value.descendants(matching: .staticText).allElementsBoundByIndex.filter { self.lbVisible($0) }.map(\.label)
            if labels.joined(separator: " ") == expected { return true }
            let words = expected.split(separator: " ").map(String.init)
            guard words.count == 12 || words.count == 24 else { return false }
            let wanted = words.enumerated().flatMap { [String($0.offset + 1), $0.element] }
            return labels == wanted
        }, timeout: 30, message: "Exact authorized plaintext or ordered seed words did not match")
        XCTAssertTrue(descendants(matching: .any)["lb-copy-\(key)"].exists, "Authorized disclosure lacks Copy")
    }

    private func lbHasExactShowSelection(_ name: String, id: String) -> Bool {
        var scope = ShowScopeWindow()
        func refused() -> Bool { scope.emit(); return false }
        guard !id.isEmpty, lbSelection.secretId == id, lbSelection.secrets[name] == id else { return refused() }
        let all = descendants(matching: .any)
        let routes = all.matching(identifier: "lb-secrets"), lists = all.matching(identifier: "lb-secrets-list")
        let cards = all.matching(identifier: "lb-secret-\(id)")
        let names = all.matching(identifier: "lb-secret-name-\(id)")
        let primaries = all.matching(identifier: "lb-primary-\(id)")
        guard routes.count == 1, lists.count == 1, cards.count == 1, names.count == 1, primaries.count == 1,
              names.firstMatch.label == name, primaries.firstMatch.elementType == .button else { return refused() }
        do {
            let (root, _) = try lbSecretsViewportSnapshot()
            guard let observed = ShowScopeWindow.read(root: root, id: id, name: name) else {
                scope.coverage = .unavailable; return refused()
            }
            scope = observed
        } catch { scope.coverage = .unavailable; return refused() }
        guard scope.sameWindow == .yes else { return refused() }
        return scope.associated ? true : refused()
    }

    private enum ShowScopeCount: String {
        case unknown = "UNKNOWN", zero = "ZERO", one = "ONE", many = "MANY"
        static func observed(_ count: Int) -> Self { count == 0 ? .zero : count == 1 ? .one : .many }
    }
    private enum ShowScopePresence: String {
        case unknown = "UNKNOWN", missing = "MISSING", present = "PRESENT"
    }
    private enum ShowScopeTruth: String {
        case unknown = "UNKNOWN", yes = "TRUE", no = "FALSE"
    }
    private enum ShowScopeCoverage: String {
        case noSnapshot = "NO_SNAPSHOT", unavailable = "UNAVAILABLE", snapshot = "SNAPSHOT"
    }
    @MainActor private struct ShowScopeWindow {
        var coverage = ShowScopeCoverage.noSnapshot
        var routeList = ShowScopeCount.unknown, listCard = ShowScopeCount.unknown
        var cardName = ShowScopeCount.unknown, cardPrimary = ShowScopeCount.unknown
        var routeWindow = ShowScopePresence.unknown, listWindow = ShowScopePresence.unknown
        var sameWindow = ShowScopeTruth.unknown
        var targetCounts = Array(repeating: ShowScopeCount.unknown, count: 5)
        var targetWindows = Array(repeating: ShowScopePresence.unknown, count: 5)
        var sameWindowAll = ShowScopeTruth.unknown
        var associated = false

        static func read(root: any XCUIElementSnapshot, id: String, name: String) -> Self? {
            let limit = 8192
            let identifiers = ["lb-secrets", "lb-secrets-list", "lb-secret-\(id)",
                "lb-secret-name-\(id)", "lb-primary-\(id)"]
            var stack: [(node: any XCUIElementSnapshot, parent: Int, window: Int?)] = [(root, -1, nil)]
            var parents: [Int] = [], windows: [Int?] = []
            var hits = Array(repeating: [Int](), count: identifiers.count)
            var tags: [String] = []
            var nameMatches = false, primaryMatches = false
            while let (node, parent, inheritedWindow) = stack.popLast() {
                guard parents.count < limit else { return nil }
                let index = parents.count
                parents.append(parent)
                let type = node.elementType
                let window: Int? = type == .window ? index : inheritedWindow
                windows.append(window)
                let identifier = node.identifier
                tags.append(identifier)
                if identifier == identifiers[3] { nameMatches = node.label == name }
                if identifier == identifiers[4] { primaryMatches = type == .button }
                for (target, expected) in identifiers.enumerated() where identifier == expected {
                    hits[target].append(index)
                }
                let children = node.children
                // Bound pending tuples and indexed metadata before allocating/enqueueing children.
                guard children.count <= limit - parents.count - stack.count else { return nil }
                for child in children.reversed() { stack.append((child, index, window)) }
            }
            guard hits[0].count == 1, hits[1].count == 1 else { return nil }
            // DFS indices name immutable root-to-node path occurrences, never CGRect identities.
            var ends = Array(parents.indices)
            for index in parents.indices.reversed() {
                let parent = parents[index]
                if parent >= 0 { ends[parent] = max(ends[parent], ends[index]) }
            }
            func relation(_ owners: [Int], _ targets: [Int]) -> ShowScopeCount {
                guard owners.count == 1, let owner = owners.first else { return .unknown }
                return .observed(targets.filter { $0 > owner && $0 <= ends[owner] }.count)
            }
            let route = windows[hits[0][0]], list = windows[hits[1][0]]
            var result = Self()
            result.coverage = .snapshot
            result.targetCounts = hits.map { ShowScopeCount.observed($0.count) }
            result.targetWindows = hits.map { hit -> ShowScopePresence in
                guard hit.count == 1 else { return .unknown }
                return windows[hit[0]] == nil ? .missing : .present
            }
            let exactWindows = hits.compactMap { hit -> Int? in
                guard hit.count == 1 else { return nil }
                return windows[hit[0]]
            }
            if exactWindows.count == identifiers.count, let first = exactWindows.first {
                result.sameWindowAll = exactWindows.allSatisfy { $0 == first } ? .yes : .no
            }
            result.routeWindow = route == nil ? .missing : .present
            result.listWindow = list == nil ? .missing : .present
            if let route, let list { result.sameWindow = route == list ? .yes : .no }
            result.routeList = relation(hits[0], hits[1])
            result.listCard = relation(hits[1], hits[2])
            result.cardName = relation(hits[2], hits[3])
            result.cardPrimary = relation(hits[2], hits[4])
            var associated = result.targetCounts.allSatisfy { $0 == .one } &&
                result.sameWindowAll == .yes && nameMatches && primaryMatches
            if associated {
                let counts = Dictionary(tags.map { ($0, 1) }, uniquingKeysWith: +)
                let prefix = "lb-primary-"
                var cardTags = Set<String>(), nameTags = Set<String>()
                // Renderer roles use exact full opaque suffixes; no UUID or substring identity model.
                for tag in tags where tag.hasPrefix(prefix) {
                    let ownerId = String(tag.dropFirst(prefix.count))
                    guard !ownerId.isEmpty else { continue }
                    let cardTag = "lb-secret-" + ownerId, nameTag = "lb-secret-name-" + ownerId
                    if counts[cardTag] == 1, counts[nameTag] == 1, counts[tag] == 1 {
                        cardTags.insert(cardTag); nameTags.insert(nameTag)
                    }
                }
                for hit in hits.dropFirst(2) {
                    var index = hit[0]
                    while index >= 0 {
                        let tag = tags[index]
                        let cardRole = cardTags.contains(tag), nameRole = nameTags.contains(tag)
                        if (cardRole && nameRole) || (cardRole && tag != identifiers[2]) ||
                            (nameRole && tag != identifiers[3]) ||
                            (tag.hasPrefix("lb-secret-") && tag != identifiers[2] &&
                                tag != identifiers[3] && !nameRole) {
                            associated = false; break
                        }
                        index = parents[index]
                    }
                    if !associated { break }
                }
            }
            result.associated = associated
            return result
        }

        func emit() {
            let row: [String: Any] = ["v": 1, "coverage": coverage.rawValue,
                "targetCounts": targetCounts.map(\.rawValue),
                "targetWindows": targetWindows.map(\.rawValue), "sameWindowAll": sameWindowAll.rawValue,
                "routeList": routeList.rawValue, "listCard": listCard.rawValue,
                "cardName": cardName.rawValue, "cardPrimary": cardPrimary.rawValue,
                "routeWindow": routeWindow.rawValue, "listWindow": listWindow.rawValue,
                "sameWindow": sameWindow.rawValue, "scope": coverage == .snapshot ? "VALID" : "UNKNOWN"]
            guard let data = try? JSONSerialization.data(withJSONObject: row, options: [.sortedKeys]),
                  let text = String(data: data, encoding: .utf8) else { return }
            let line = "LB_SHOW_SCOPE_V1 " + text
            guard line.utf8.count < 512 else { return }
            print(line)
        }
    }

    func lbPrivateGeometry(id: String) -> [String: CGRect] {
        XCTAssertEqual(state, .runningForeground)
        let window = windows.firstMatch
        XCTAssertTrue(window.exists)
        let bounds = window.frame
        let identifiers = ["show": "lb-secret-\(id)", "value": "lb-value-\(id)", "copy": "lb-copy-\(id)"]
        var measured: [String: CGRect] = ["window": bounds]
        for (key, identifier) in identifiers {
            let matches = descendants(matching: .any).matching(identifier: identifier)
            XCTAssertEqual(matches.count, 1, "Private geometry requires one exact same-card target")
            let element = matches.firstMatch
            XCTAssertTrue(lbVisible(element) && element.isHittable, "Private baseline target must be actually accessible")
            XCTAssertTrue(bounds.contains(element.frame), "Private baseline is outside the original application window")
            measured[key] = element.frame
        }
        if let card = measured["show"], let value = measured["value"], let copy = measured["copy"] {
            XCTAssertTrue(card.contains(value) && card.contains(copy), "Value and Copy must belong to the same selected card")
            XCTAssertFalse(value.intersects(copy), "Copy geometry is not a distinct action target")
        }
        return measured
    }

    func lbAssertReadGuard(_ expected: String, baseline: [String: CGRect], id: String? = nil) {
        XCTAssertEqual(state, .runningForeground, "Read guard is not inspected in the foreground application")
        let window = windows.firstMatch.frame
        if let original = baseline["window"] { XCTAssertEqual(window, original, "Private guard moved to a different window") }
        for key in ["show", "value", "copy"] {
            guard let rect = baseline[key], !rect.isEmpty && !rect.isNull else { XCTFail("Original private geometry missing"); return }
            XCTAssertTrue(window.contains(rect), "Original private region escaped the current application window")
        }
        let all = descendants(matching: .any).allElementsBoundByIndex
        let leaked = all.contains { element in
            guard element.exists else { return false }
            return element.label.contains(expected) || (element.value as? String)?.contains(expected) == true
        }
        XCTAssertFalse(leaked, "Private plaintext remains anywhere in the foreground accessibility tree")
        XCTAssertEqual(descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "lb-value-")).count, 0,
                       "Private value accessibility must be removed throughout the held read")
        XCTAssertEqual(descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "lb-copy-")).count, 0,
                       "Private Copy accessibility must be removed throughout the held read")
        for control in descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@ OR identifier BEGINSWITH %@", "lb-primary-", "lb-delete-")).allElementsBoundByIndex {
            XCTAssertFalse(control.isEnabled, "Held read exposed an enabled private command")
        }
        if let id {
            for tag in ["lb-primary-\(id)", "lb-delete-\(id)"] {
                let control = descendants(matching: .any)[tag]
                XCTAssertTrue(control.exists, "Held-read action control disappeared instead of being proven blocked")
                XCTAssertFalse(control.isEnabled, "Held-read command is enabled")
            }
        }
    }

    func lbProbeOldCopyPoint(_ expected: String, baseline: [String: CGRect], id: String? = nil) {
        lbAssertReadGuard(expected, baseline: baseline, id: id)
        guard let old = baseline["copy"], !old.isEmpty else { XCTFail("Verified original Copy geometry is missing"); return }
        let before = UIPasteboard.general.string
        let sentinel = "metasecret-blocked-copy-\(UUID().uuidString)"
        UIPasteboard.general.string = sentinel
        defer { UIPasteboard.general.string = before }
        coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: old.midX - frame.minX, dy: old.midY - frame.minY)).tap()
        XCTAssertTrue(UIPasteboard.general.string == sentinel, "Physical touch at the original Copy point changed clipboard")
        lbAssertReadGuard(expected, baseline: baseline, id: id)
    }

    func lbAssertNoPrivateContent(_ expected: String) {
        guard let observation = lbGlobalPrivacySnapshot(expected) else { return }
        XCTAssertFalse(observation.leaked, "Private plaintext appears in the foreground accessibility tree")
        XCTAssertEqual(observation.values, 0)
        XCTAssertEqual(observation.copies, 0)
    }

    private func lbGlobalPrivacySnapshot(_ expected: String) -> (leaked: Bool, values: Int, copies: Int)? {
        let started = ProcessInfo.processInfo.systemUptime
        let deadline = started + 15
        func inBudget() -> Bool {
            let now = ProcessInfo.processInfo.systemUptime
            return started.isFinite && deadline.isFinite && now.isFinite && now >= started && now <= deadline
        }
        func refused(_ reason: String) -> (leaked: Bool, values: Int, copies: Int)? {
            XCTFail(reason); return nil
        }
        guard inBudget() else { return refused("Global privacy snapshot time budget exceeded") }
        let root: any XCUIElementSnapshot
        do { root = try snapshot() }
        catch { return refused("Global privacy snapshot capture unavailable") }
        guard inBudget() else { return refused("Global privacy snapshot time budget exceeded") }
        // Descendants include every window and offscreen node; the application root is excluded.
        let descendants = root.children
        guard inBudget() else { return refused("Global privacy snapshot time budget exceeded") }
        let limit = 8192
        guard descendants.count <= limit else { return refused("Global privacy snapshot node budget exceeded") }
        var stack = descendants
        var visited = 0, values = 0, copies = 0
        var leaked = false
        while let node = stack.popLast() {
            guard inBudget() else { return refused("Global privacy snapshot time budget exceeded") }
            guard visited < limit else { return refused("Global privacy snapshot node budget exceeded") }
            visited += 1
            let identifier = node.identifier
            leaked = leaked || node.label.contains(expected) || (node.value as? String)?.contains(expected) == true
            if identifier.hasPrefix("lb-value-") { values += 1 }
            if identifier.hasPrefix("lb-copy-") { copies += 1 }
            let children = node.children
            guard inBudget() else { return refused("Global privacy snapshot time budget exceeded") }
            guard children.count <= limit - visited - stack.count else {
                return refused("Global privacy snapshot node budget exceeded")
            }
            stack.append(contentsOf: children)
        }
        guard inBudget() else { return refused("Global privacy snapshot time budget exceeded") }
        return (leaked, values, copies)
    }
}

/// Test22-only procedure observation; it never substitutes for value/Copy or the actor result.
enum Test22ShowPreparation: String, Sendable {
    case inputReturned = "INPUT_RETURNED", alreadyVisible = "ALREADY_VISIBLE"
}

struct Test22ShowPhaseContext: Decodable, Sendable {
    let testNumber: Int
    let role: String
    let nonce: String
    let cycle: Int
    let step: Int

    func emit(_ outcome: Test22ShowPreparation, cycle currentCycle: String, step currentStep: String) {
        guard testNumber == 22, role == "sender", (1...39).contains(cycle), step == 1,
              currentCycle == String(cycle), currentStep == String(step), nonce.utf8.count == 32,
              nonce.utf8.allSatisfy({ (48...57).contains($0) || (97...102).contains($0) }) else { return }
        let line = "E2E: IOS_TEST22_SHOW_PREP_V1 \(nonce) \(cycle) \(step) \(outcome.rawValue)"
        guard line.utf8.count + 1 <= 512 else { return }
        print(line)
    }
}
