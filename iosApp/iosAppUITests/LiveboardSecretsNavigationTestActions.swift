import XCTest

private enum SecretsNavigationRoute: Equatable { case home, secrets }
private struct SecretsNavigationSample: Equatable {
    let route: SecretsNavigationRoute
    let window: CGRect
    let home: CGRect
    let button: CGRect
    let ancestry: [Int]
    func delta(_ other: SecretsNavigationSample) -> SecretsNavigationSampleDelta {
        let values: [(SecretsNavigationSampleDelta, Bool)] = [(.route, route != other.route), (.window, window != other.window),
            (.home, home != other.home), (.button, button != other.button), (.ordinal, ancestry != other.ancestry)]
        let changed = values.filter { $0.1 }.map { $0.0 }
        return changed.isEmpty ? .none : changed.count == 1 ? changed[0] : .multiple
    }
    static func == (left: Self, right: Self) -> Bool {
        left.route == right.route && left.window == right.window && left.home == right.home && left.button == right.button
    }
}
private enum SecretsNavigationSampleDelta: String { case unknown = "UNKNOWN", none = "NONE", route = "ROUTE", window = "WINDOW", home = "HOME", button = "BUTTON", ordinal = "ORDINAL", multiple = "MULTIPLE" }

/// A throwing closed endpoint preserves its error; native XCTest tap itself is nonthrowing.
func liveboardNavigationGesture(_ action: () throws -> Void) rethrows { try action() }

private enum SecretsNavigationConsumedReason: String {
    case unknown = "UNKNOWN", budget = "BUDGET", deadline = "DEADLINE", pollCap = "POLL_CAP"
    case appForeground = "APP_FOREGROUND", systemBackground = "SYSTEM_BACKGROUND"
    case appAlerts = "APP_ALERTS", appSheets = "APP_SHEETS", systemAlerts = "SYSTEM_ALERTS"
    case systemSheets = "SYSTEM_SHEETS", systemSecure = "SYSTEM_SECURE", snapshot = "SNAPSHOT"
    case finite = "FINITE", nodeCap = "NODE_CAP", buttonType = "BUTTON_TYPE", childrenCap = "CHILDREN_CAP"
    case forbidden = "FORBIDDEN", shades = "SHADES", windows = "WINDOWS", rootContains = "ROOT_CONTAINS"
    case routes = "ROUTES", routeContains = "ROUTE_CONTAINS", homes = "HOMES", buttons = "BUTTONS"
    case windowBinding = "WINDOW_BINDING", homeBinding = "HOME_BINDING", sameWindow = "SAME_WINDOW"
    case sameHome = "SAME_HOME", snapshotEnabled = "SNAPSHOT_ENABLED", contained = "CONTAINED"
    case targetCount = "TARGET_COUNT", exists = "EXISTS", enabled = "ENABLED", hittable = "HITTABLE"
    case frameMatch = "FRAME_MATCH", phaseDeadline = "PHASE_DEADLINE", observed = "OBSERVED"
    case waiter = "WAITER", notRefused = "NOT_REFUSED", settled = "SETTLED", secrets = "SECRETS"
    case list = "LIST", clearCount = "CLEAR_COUNT", stable = "STABLE", tapCap = "TAP_CAP"
}
private enum SecretsNavigationConsumedFlag: String { case unknown = "UNKNOWN", yes = "TRUE", no = "FALSE" }
private enum SecretsNavigationConsumedCount: String { case unknown = "UNKNOWN", zero = "ZERO", one = "ONE", many = "MANY", unobserved = "UNOBSERVED" }
private enum SecretsNavigationSystemState: String { case unobserved = "UNOBSERVED", unknown = "UNKNOWN", notRunning = "NOT_RUNNING", background = "BACKGROUND", suspended = "BACKGROUND_SUSPENDED", foreground = "FOREGROUND" }
private struct SecretsNavigationConsumedTrace {
    var sampleDelta = SecretsNavigationSampleDelta.unknown
    var topology = [SecretsNavigationConsumedFlag](repeating: .unknown, count: 4)
    var systemState = SecretsNavigationSystemState.unobserved
    var reason = SecretsNavigationConsumedReason.unknown
    var flag = SecretsNavigationConsumedFlag.unknown
    var countReason = SecretsNavigationConsumedReason.unknown, count = SecretsNavigationConsumedCount.unobserved
    var failed = false, emitted = false
    mutating func mark(_ reason: SecretsNavigationConsumedReason, _ value: Bool) -> Bool {
        if !failed { self.reason = reason; flag = value ? .yes : .no; failed = !value }
        return value
    }
    mutating func observedCount(_ reason: SecretsNavigationConsumedReason, _ value: Int) {
        if !failed { countReason = reason; count = value == 0 ? .zero : value == 1 ? .one : value > 1 ? .many : .unknown }
    }
    mutating func observedSystemState(_ state: XCUIApplication.State) {
        guard !failed else { return }
        switch state {
        case .unknown: systemState = .unknown
        case .notRunning: systemState = .notRunning
        case .runningBackground: systemState = .background
        case .runningBackgroundSuspended: systemState = .suspended
        case .runningForeground: systemState = .foreground
        @unknown default: systemState = .unknown
        }
    }
    mutating func beginObservation() {
        if !failed { topology = [SecretsNavigationConsumedFlag](repeating: .unknown, count: 4); sampleDelta = .unknown }
    }
    mutating func observedDelta(_ value: SecretsNavigationSampleDelta) { if !failed { sampleDelta = value } }
    mutating func observedTopology(home: Bool, window: Bool, clip: Bool, enabled: Bool) {
        if !failed { topology = [home, window, clip, enabled].map { $0 ? .yes : .no } }
    }
    mutating func emit() {
        guard !emitted else { return }
        emitted = true
        if let row = encoded() { print(row) }
    }
    func encoded(maxBytes: Int = 256) -> String? {
        let row = "E2E_SECRETS_NAV_REFUSAL_V1 R=\(reason.rawValue) V=\(flag.rawValue) CR=\(countReason.rawValue) C=\(count.rawValue) SS=\(systemState.rawValue) TH=\(topology[0].rawValue) TW=\(topology[1].rawValue) TC=\(topology[2].rawValue) TE=\(topology[3].rawValue) SD=\(sampleDelta.rawValue)"
        return row.utf8.count <= maxBytes ? row : nil
    }
}

@MainActor
extension XCUIApplication {
    /// Idempotent navigation only. This is UX readiness, never an OS authentication proof.
    @discardableResult
    func lbNavigateSecrets(timeout: TimeInterval = 180) -> Bool {
        var consumed = SecretsNavigationConsumedTrace()
        var successful = false
        defer { if !successful { consumed.emit() } }
        func record(_ reason: SecretsNavigationConsumedReason, _ value: Bool) -> Bool { consumed.mark(reason, value) }
        func counted(_ reason: SecretsNavigationConsumedReason, _ value: Int, _ predicate: (Int) -> Bool) -> Bool {
            consumed.observedCount(reason, value); return record(reason, predicate(value))
        }
        func capture<T>(_ reason: SecretsNavigationConsumedReason, _ value: T?) -> T? {
            _ = record(reason, value != nil); return value
        }
        guard record(.budget, timeout.isFinite && timeout > 0 && timeout <= 180) else {
            consumed.emit()
            XCTFail("Secrets navigation budget is invalid"); return false
        }
        let deadline = Date().addingTimeInterval(timeout)
        let system = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        var polls = 0
        func live() -> Bool { record(.deadline, Date() < deadline) }
        func remaining() -> TimeInterval { max(0, deadline.timeIntervalSinceNow) }
        func finite(_ frame: CGRect) -> Bool {
            record(.finite, [frame.minX, frame.minY, frame.width, frame.height].allSatisfy { $0.isFinite }
                && !frame.isEmpty && !frame.isNull && !frame.isInfinite)
        }
        func stable(_ left: SecretsNavigationSample, _ right: SecretsNavigationSample) -> Bool {
            consumed.observedDelta(left.delta(right)); return record(.stable, left == right)
        }
        func refusal() -> Bool { consumed.emit(); XCTFail("Scoped Secrets navigation was refused"); return false }
        func observed() -> SecretsNavigationSample? {
            consumed.beginObservation()
            guard live(), record(.pollCap, polls < 64) else { return nil }
            polls += 1
            guard record(.appForeground, state == .runningForeground), live() else { return nil }
            let systemState = system.state
            consumed.observedSystemState(systemState)
            guard live(), record(.systemBackground, systemState == .runningBackground || systemState == .runningBackgroundSuspended || systemState == .runningForeground),
                  counted(.appAlerts, alerts.count, { $0 == 0 }), live(), counted(.appSheets, sheets.count, { $0 == 0 }), live(),
                  counted(.systemAlerts, system.alerts.count, { $0 == 0 }), live(), counted(.systemSheets, system.sheets.count, { $0 == 0 }), live(),
                  counted(.systemSecure, system.secureTextFields.count, { $0 == 0 }), live(),
                  let root = capture(.snapshot, try? snapshot()), live(), finite(root.frame) else { return nil }
            var stack: [(any XCUIElementSnapshot, Int?, Int?)] = [(root, nil, nil)]
            var windows: [(Int, CGRect)] = [], homes: [(Int, CGRect, Int?)] = [], routes: [(Int, CGRect, Int?)] = [], lists: [(Int, CGRect, Int?)] = []
            var buttons: [(CGRect, Int?, Int?, Bool, Int)] = []
            var forbidden = false, shades = 0, visited = 0
            while let (node, parentWindow, parentHome) = stack.popLast() {
                visited += 1
                guard record(.nodeCap, visited <= 8192), live() else { return nil }
                let frame = node.frame
                let window = node.elementType == .window ? visited : parentWindow
                let home = node.identifier == "lb-home" ? visited : parentHome
                if node.elementType == .window { windows.append((visited, frame)) }
                if node.identifier == "lb-home" { homes.append((visited, frame, window)) }
                if node.identifier == "lb-secrets" { routes.append((visited, frame, window)) }
                if node.identifier == "lb-secrets-list" { lists.append((visited, frame, window)) }
                if node.identifier == "lb-overlay-shade" { shades += 1 }
                if ["lb-profile", "lb-create", "lb-invite-popover"].contains(node.identifier)
                    || node.elementType == .alert || node.elementType == .sheet { forbidden = true }
                if node.identifier == "lb-open-secrets" {
                    guard record(.buttonType, node.elementType == .button) else { return nil }
                    buttons.append((frame, window, home, node.isEnabled, visited))
                }
                let children = node.children
                guard record(.childrenCap, children.count <= 8192 - visited - stack.count) else { return nil }
                stack.append(contentsOf: children.map { ($0, window, home) })
            }
            guard live(), record(.forbidden, !forbidden), counted(.shades, shades, { $0 <= 1 }),
                  counted(.windows, windows.count, { $0 > 0 }), counted(.routes, routes.count, { $0 <= 1 }),
                  counted(.homes, homes.count, { $0 <= 1 }), counted(.buttons, buttons.count, { $0 <= 1 }), counted(.list, lists.count, { $0 <= 1 }) else { return nil }
            if routes.count == 1 {
                guard counted(.list, lists.count, { $0 == 1 }),
                      let ancestor = capture(.windowBinding, routes[0].2),
                      let selected = capture(.windowBinding, windows.first(where: { $0.0 == ancestor })),
                      finite(selected.1), record(.rootContains, root.frame.contains(selected.1)),
                      record(.sameWindow, lists[0].2 == ancestor), finite(routes[0].1), finite(lists[0].1),
                      record(.routeContains, selected.1.contains(routes[0].1)),
                      finite(lists[0].1.intersection(routes[0].1).intersection(selected.1)), live() else { return nil }
                return SecretsNavigationSample(route: .secrets, window: selected.1, home: .zero, button: .zero, ancestry: [ancestor, routes[0].0, lists[0].0])
            }
            guard counted(.shades, shades, { $0 == 0 }), counted(.homes, homes.count, { $0 == 1 }),
                  counted(.buttons, buttons.count, { $0 == 1 }),
                  let ancestor = capture(.windowBinding, homes[0].2),
                  let selected = capture(.windowBinding, windows.first(where: { $0.0 == ancestor })),
                  let buttonWindow = capture(.windowBinding, buttons[0].1),
                  finite(selected.1), record(.rootContains, root.frame.contains(selected.1)), finite(homes[0].1), finite(buttons[0].0) else { return nil }
            let buttonHome = buttons[0].2
            let clip = homes[0].1.intersection(selected.1).intersection(root.frame)
            let sameWindow = buttonWindow == ancestor, contained = clip.contains(buttons[0].0)
            consumed.observedTopology(home: buttonHome != nil, window: sameWindow, clip: contained, enabled: buttons[0].3)
            guard record(.sameWindow, sameWindow), record(.sameHome, buttonHome == nil || buttonHome == homes[0].0),
                  finite(clip), record(.snapshotEnabled, buttons[0].3), record(.contained, contained), live() else { return nil }
            let query = self.buttons.matching(identifier: "lb-open-secrets")
            guard counted(.targetCount, query.count, { $0 == 1 }), live() else { return nil }
            let target = query.element(boundBy: 0)
            guard record(.exists, target.exists), live(), record(.enabled, target.isEnabled), live(), record(.hittable, target.isHittable), live(),
                  record(.frameMatch, target.frame == buttons[0].0), live() else { return nil }
            return SecretsNavigationSample(route: .home, window: selected.1, home: homes[0].1, button: buttons[0].0, ancestry: [ancestor, homes[0].0, buttons[0].4, buttonHome ?? -1])
        }
        // Exactly two consecutive current observations; after a tap this window is at most 4s.
        func settled(window: TimeInterval, consecutive: Bool = false) -> SecretsNavigationSample? {
            let end = Date().addingTimeInterval(min(window, remaining()))
            var previous: SecretsNavigationSample?, result: SecretsNavigationSample?
            var refused = false
            func sample() -> Bool {
                guard live(), record(.phaseDeadline, Date() < end), let current = capture(.observed, observed()), live(), record(.phaseDeadline, Date() < end) else {
                    refused = true; return true
                }
                if current.route == .secrets { result = current; return true }
                if current == previous { result = current; return true }
                previous = current
                return false
            }
            let expectation = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
                if !consecutive { return sample() }
                while !sample() {}
                return true
            }, object: nil)
            let outcome = XCTWaiter.wait(for: [expectation], timeout: min(window, remaining()))
            guard record(.waiter, outcome == .completed), record(.notRefused, !refused), live() else { return nil }
            return result
        }
        func finish() -> Bool {
            guard live(), let current = capture(.observed, observed()), record(.secrets, current.route == .secrets), live(),
                  record(.list, (try? lbSecretsViewportSnapshot()) != nil), live() else { return refusal() }
            let clear = descendants(matching: .any).matching(identifier: "lb-search-clear")
            let count = clear.count
            guard live(), counted(.clearCount, count, { $0 <= 1 }) else { return refusal() }
            if count == 1 {
                let target = clear.element(boundBy: 0)
                guard record(.exists, target.exists), live(), record(.enabled, target.isEnabled), live(), record(.hittable, target.isHittable), live(),
                      finite(target.frame), record(.contained, current.window.contains(target.frame)), live() else { return refusal() }
                target.tap()
                guard live(), let final = capture(.observed, observed()), record(.secrets, final.route == .secrets), live(),
                      record(.list, (try? lbSecretsViewportSnapshot()) != nil), live() else { return refusal() }
            }
            let result = live()
            if result { successful = true }
            return result
        }
        // Keep original Home entry, but never give it a second clock or proceed after late return.
        let currentRoute = descendants(matching: .any).matching(identifier: "lb-secrets").count
        guard live(), counted(.routes, currentRoute, { $0 <= 1 }) else { return refusal() }
        if currentRoute == 1 { return finish() }
        lbHome(timeout: remaining())
        guard live(), var ready = capture(.settled, settled(window: remaining())) else { return refusal() }
        if ready.route == .secrets { return finish() }
        for attempt in 1...2 {
            guard live(), let fresh = capture(.observed, observed()), live() else { return refusal() }
            if fresh.route == .secrets { return finish() }
            guard stable(fresh, ready) else { return refusal() }
            let query = buttons.matching(identifier: "lb-open-secrets")
            guard counted(.targetCount, query.count, { $0 == 1 }), live() else { return refusal() }
            let target = query.element(boundBy: 0)
            guard record(.exists, target.exists), live(), record(.enabled, target.isEnabled), live(), record(.hittable, target.isHittable), live(),
                  record(.frameMatch, target.frame == fresh.button), live(),
                  let last = capture(.observed, observed()), live() else { return refusal() }
            if last.route == .secrets { return finish() }
            guard stable(last, fresh), live() else { return refusal() }
            print("E2E_SECRETS_NAVIGATION_V1 ATTEMPT=\(attempt)")
            // Never retry any thrown endpoint; native tap is nonthrowing and remains physical.
            liveboardNavigationGesture { target.tap() }
            guard live(), let next = capture(.settled, settled(window: 4, consecutive: true)) else { return refusal() }
            if next.route == .secrets { return finish() }
            guard record(.tapCap, attempt == 1) else { return refusal() }
            ready = next
        }
        return refusal()
    }
}
