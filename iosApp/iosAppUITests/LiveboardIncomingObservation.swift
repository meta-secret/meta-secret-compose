import XCTest

struct LiveboardIncomingSenderSample {
    let headIdentifier: String
    let kind: String
    let visibleHeadCount: Int
    var uuidValid: Bool?
    var exists: Bool?
    var targetFrame: CGRect?
    var appFrame: CGRect?
    var visible: Bool?
    var labelLength: Int?
    var nonempty: Bool?
}

@MainActor
final class LiveboardIncomingObservationState { var attempted = false }

private func incomingRectangle(_ value: CGRect?) -> Any {
    guard let value, !value.isNull, !value.isInfinite else { return "UNKNOWN" }
    let numbers = [value.minX, value.minY, value.width, value.height]
    return numbers.allSatisfy { $0.isFinite } ? numbers.map { Double($0) } : "UNKNOWN"
}

private func incomingFlag(_ value: Bool?) -> Any { value.map { ($0 ? 1 : 0) as Any } ?? "UNKNOWN" }

private func incomingEncoded(_ payload: [String: Any]) -> Data {
    guard let data = try? JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys]) else {
        return Data(#"{"VERSION":1,"STATUS":"INCONCLUSIVE","REASON":"SERIALIZATION"}"#.utf8)
    }
    return data.count <= 4096 ? data : Data(#"{"VERSION":1,"STATUS":"INCONCLUSIVE","REASON":"OUTPUT_LIMIT"}"#.utf8)
}

private func incomingPrint(_ payload: [String: Any]) {
    let prefix = "LIVEBOARD_INCOMING_OBSERVATION "
    let text = prefix + String(decoding: incomingEncoded(payload), as: UTF8.self)
    if text.utf8.count + 1 <= 512 { print(text) }
    else { print(prefix + #"{"VERSION":1,"STATUS":"INCONCLUSIVE","REASON":"STDOUT_LIMIT"}"#) }
}

@MainActor
extension XCUIApplication {
    private func lbIncomingGuardPayload(_ sample: LiveboardIncomingSenderSample) -> [String: Any] {
        let kinds = ["Join": "JOIN", "Recover": "RECOVER", "DeleteSecret": "DELETE_SECRET", "RemoveDevice": "REMOVE_DEVICE"]
        return ["VERSION": 1, "PHASE": "FIRST_OBSERVED_SENDER_GUARD_FAILURE", "STATUS": "CACHED_REAL_GUARD",
                "VISIBLE_HEAD_COUNT": sample.visibleHeadCount, "KIND": kinds[sample.kind] ?? "UNKNOWN", "UUID_VALID": incomingFlag(sample.uuidValid),
                "SENDER_EXISTS": incomingFlag(sample.exists), "SENDER_FRAME": incomingRectangle(sample.targetFrame),
                "APP_FRAME": incomingRectangle(sample.appFrame), "SENDER_VISIBLE": incomingFlag(sample.visible),
                "LABEL_LENGTH": sample.labelLength.map { $0 as Any } ?? "UNKNOWN", "LABEL_NONEMPTY": incomingFlag(sample.nonempty)]
    }

    private func lbIncomingRoleRows(_ roles: [(String, String)]) -> [[String: Any]] {
        roles.map { role, identifier in
            let query = descendants(matching: .any).matching(identifier: identifier)
            let count = query.count
            var row: [String: Any] = ["ROLE": role, "COUNT": count, "EXISTS": "UNKNOWN", "TYPE": "UNKNOWN", "FRAME": "UNKNOWN"]
            if count == 1 {
                let element = query.element(boundBy: 0)
                let exists = element.exists
                row["EXISTS"] = exists ? 1 : 0
                if exists { row["TYPE"] = element.elementType.rawValue; row["FRAME"] = incomingRectangle(element.frame) }
            }
            return row
        }
    }

    private func lbIncomingRootProjection(_ roles: [(String, String)]) -> [String: Any] {
        guard let root = try? snapshot() else { return ["STATUS": "INCONCLUSIVE", "REASON": "SNAPSHOT_UNAVAILABLE"] }
        typealias Entry = (node: any XCUIElementSnapshot, path: [Int], role: String?)
        var stack: [(Entry, [Entry])] = [((root, [], nil), [])]
        var retained = Set<[Int]>(), found = Set<String>(), nodes: [[String: Any]] = []
        var visited = 0
        var reason = "NONE"
        func incomplete(_ value: String) { if reason == "NONE" { reason = value } }
        while let (entry, ancestors) = stack.popLast() {
            visited += 1
            let identifier = entry.node.identifier
            let role = roles.first { $0.1 == identifier }?.0
            let current: Entry = (entry.node, entry.path, role)
            if let role {
                found.insert(role)
                for item in ancestors + [current] where !retained.contains(item.path) {
                    guard retained.count < 64 else { incomplete("RETAINED_LIMIT"); break }
                    retained.insert(item.path)
                    let rectangle = incomingRectangle(item.node.frame)
                    if rectangle is String { incomplete("GEOMETRY_UNKNOWN") }
                    nodes.append(["ROLE": item.role ?? "ANCESTOR", "PATH": item.path,
                                  "TYPE": item.node.elementType.rawValue, "FRAME": rectangle])
                }
            }
            let children = entry.node.children
            if entry.path.isEmpty && children.isEmpty { incomplete("NO_CHILDREN") }
            if entry.path.count >= 16 && !children.isEmpty { incomplete("DEPTH_LIMIT"); continue }
            let remaining = max(0, 512 - visited - stack.count)
            if children.count > remaining { incomplete("NODE_LIMIT") }
            for (index, child) in children.prefix(remaining).enumerated().reversed() {
                stack.append(((child, entry.path + [index], nil), ancestors + [current]))
            }
        }
        if found.count != roles.count { incomplete("ROLE_COVERAGE_INCOMPLETE") }
        return ["STATUS": reason == "NONE" ? "PROJECTED" : "INCONCLUSIVE", "REASON": reason,
                "VISITED": visited, "RETAINED": retained.count, "NODES": nodes]
    }

    /// First qualifying failure only; sequential metadata never admits, retries or changes an action.
    func lbRecordIncomingSenderObservation(_ sample: LiveboardIncomingSenderSample) {
        guard sample.visible != true || sample.nonempty != true else { return }
        let state = lbSelection.incomingObservation
        guard !state.attempted else { return }
        state.attempted = true
        var payload = lbIncomingGuardPayload(sample)
        var cached = payload
        cached.removeValue(forKey: "SENDER_FRAME"); cached.removeValue(forKey: "APP_FRAME")
        incomingPrint(cached)
        incomingPrint(["VERSION": 1, "PHASE": "CACHED_REAL_GEOMETRY",
                       "SENDER_FRAME": payload["SENDER_FRAME"] ?? "UNKNOWN", "APP_FRAME": payload["APP_FRAME"] ?? "UNKNOWN"])
        let roles = [("HOME", "lb-home"), ("INBOX", "lb-inbox"), ("HEAD", sample.headIdentifier),
                     ("SENDER", "lb-request-sender"), ("SUBJECT", "lb-request-subject"), ("COUNT", "lb-inbox-count")]
        payload["PHASE"] = "FIRST_FAILURE_SEQUENTIAL_PROJECTION_NOT_TERMINAL"
        payload["ROWS"] = lbIncomingRoleRows(roles)
        payload["ROOT"] = lbIncomingRootProjection(roles)
        let data = incomingEncoded(payload)
        if let final = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] {
            for row in (final["ROWS"] as? [[String: Any]] ?? []).prefix(6) {
                incomingPrint(["VERSION": 1, "PHASE": "SEQUENTIAL_ROLE", "ROW": row])
            }
            var root = final["ROOT"] as? [String: Any] ?? final
            let nodes = root.removeValue(forKey: "NODES") as? [[String: Any]] ?? []
            for node in nodes.prefix(64) { incomingPrint(["VERSION": 1, "PHASE": "SEQUENTIAL_ROOT_NODE", "NODE": node]) }
            incomingPrint(["VERSION": 1, "PHASE": "SEQUENTIAL_ROOT_STATUS_NOT_TERMINAL", "ROOT": root])
        }
        XCTContext.runActivity(named: "Liveboard incoming sender observation") { activity in
            let attachment = XCTAttachment(data: data, uniformTypeIdentifier: "public.json")
            attachment.name = "liveboard-incoming-sender-observation.json"; attachment.lifetime = .keepAlways
            activity.add(attachment)
        }
    }
}

struct LiveboardIncomingRevealState {
    var gestures = 0
    var previousDeficit: CGFloat?
}

/// Closed, payload-free last-poll metadata; UNKNOWN means the original operand was not consulted.
enum LiveboardIncomingObserveField: String, CaseIterable {
    case homeCount = "HC", inboxCount = "IC", scopeCount = "SC"
    case homeExists = "HE", inboxExists = "IE", scopeExists = "SE"
case appDimensions = "AD", homeDimensions = "HD", scopeDimensions = "SD", inboxDimensions = "ID", clipDimensions = "CD"
    case appValid = "AV", homeValid = "HV", scopeValid = "TV", inboxValid = "IV", clipValid = "CV", containedValid = "BV"
    case widthFits = "WF", heightFits = "HF", leftFits = "LF", rightFits = "RF", verticalContains = "VC"
    case deficit = "DF", deficitPositive = "DP", gestures = "GC", previousPresent = "PP", noProgress = "NP"
    case deadlinePresent = "DL", deadlineExpired = "DX", swipeUp = "UP", revealed = "RP", readPresent = "RR"
    case containedCount = "CC", containedExists = "CE", containedDimensions = "CB", contained = "CT"
    case kindMatch = "KM", subjectMatch = "SM", requesterMatch = "RM", requestIdMatch = "IM", countMatch = "NM"
    case senderExists = "SX", senderVisible = "SV", senderNonempty = "SN"
}

private let incomingObserveProgressFields: [LiveboardIncomingObserveField] = [
    .homeCount, .inboxCount, .scopeCount, .widthFits, .heightFits, .leftFits, .rightFits, .verticalContains,
    .revealed, .readPresent, .contained, .kindMatch, .subjectMatch, .requesterMatch, .requestIdMatch, .countMatch,
    .gestures, .previousPresent, .noProgress, .deadlinePresent, .deadlineExpired, .deficitPositive, .swipeUp,
]

final class LiveboardIncomingObserveSample {
    private var values: [LiveboardIncomingObserveField: Any] = [:]
    @discardableResult func flag(_ value: Bool, _ field: LiveboardIncomingObserveField) -> Bool {
        values[field] = value ? 1 : 0; return value
    }
    func count(_ value: Int, _ field: LiveboardIncomingObserveField) {
        values[field] = value < 0 ? "UNKNOWN" : value == 0 ? "ZERO" : value == 1 ? "ONE" : "MANY"
    }
    func dimensions(_ value: CGRect?, _ field: LiveboardIncomingObserveField) {
let validity: [LiveboardIncomingObserveField: LiveboardIncomingObserveField] = [
            .appDimensions: .appValid, .homeDimensions: .homeValid, .scopeDimensions: .scopeValid,
            .inboxDimensions: .inboxValid, .clipDimensions: .clipValid, .containedDimensions: .containedValid]
        guard let value, value.width.isFinite, value.height.isFinite else {
            if let key = validity[field] { values[key] = 0 }; return
        }
        if let key = validity[field] { values[key] = 1 }
        values[field] = [Double(value.width), Double(value.height)]
    }
    func scalar(_ value: CGFloat, _ field: LiveboardIncomingObserveField) {
        if value.isFinite { values[field] = Double(value) }
    }
    func gestures(_ value: Int) { values[.gestures] = (0...4).contains(value) ? value as Any : "MANY" }
    func sender(_ sample: LiveboardIncomingSenderSample) {
        if let value = sample.exists { flag(value, .senderExists) }
        if let value = sample.visible { flag(value, .senderVisible) }
        if let value = sample.nonempty { flag(value, .senderNonempty) }
    }
    func payload(summary: Bool = false) -> [String: Any] {
        let selected: [LiveboardIncomingObserveField] = summary
            ? [.homeCount, .inboxCount, .scopeCount, .widthFits, .heightFits, .leftFits, .rightFits, .verticalContains,
               .revealed, .readPresent, .contained, .kindMatch, .subjectMatch, .requesterMatch, .requestIdMatch, .countMatch]
            : LiveboardIncomingObserveField.allCases
        var result: [String: Any] = ["VERSION": 1, "PHASE": "OBSERVE_WAIT_FAILURE", "STATUS": "CACHED_REAL_CONJUNCTS"]
        for field in selected { result[field.rawValue] = values[field] ?? "UNKNOWN" }
        return result
    }

    /// Closed progress-only projection; the legacy failure payload remains unchanged.
    func progressPayload() -> [String: Any] {
        var result: [String: Any] = ["VERSION": 1, "PHASE": "OBSERVE_WAIT_PROGRESS", "STATUS": "CACHED_REAL_CONJUNCTS"]
        for field in incomingObserveProgressFields {
            let value = values[field]
            switch field {
            case .homeCount, .inboxCount, .scopeCount:
                let category = value as? String ?? "UNKNOWN"
                result[field.rawValue] = ["ZERO", "ONE", "MANY"].contains(category) ? category : "UNKNOWN"
            case .gestures:
                if let count = value as? Int, (0...4).contains(count) { result[field.rawValue] = count }
                else { result[field.rawValue] = (value as? String) == "MANY" ? "MANY" : "UNKNOWN" }
            default:
                if let flag = value as? Int, (0...1).contains(flag) { result[field.rawValue] = flag }
                else { result[field.rawValue] = "UNKNOWN" }
            }
        }
        return result
    }
}

/// Mutable state belongs to one observe invocation and retains only the last closed vector.
final class LiveboardIncomingObserveProgress {
    private var attempts = 0
    private var lastKey: [String]?

    func record(_ sample: LiveboardIncomingObserveSample) {
        guard attempts < 8 else { return }
        let payload = sample.progressPayload()
        let key = incomingObserveProgressFields.map { field -> String in
            if let value = payload[field.rawValue] as? Int { return String(value) }
            return payload[field.rawValue] as? String ?? "UNKNOWN"
        }
        guard key != lastKey else { return }
        attempts += 1
        lastKey = key
        incomingPrint(payload)
    }
}

func incomingObserve(_ value: Bool, _ field: LiveboardIncomingObserveField, _ sample: LiveboardIncomingObserveSample?) -> Bool {
    sample?.flag(value, field); return value
}
private func incomingObserveCount(_ value: Int, _ field: LiveboardIncomingObserveField, _ sample: LiveboardIncomingObserveSample?) -> Int {
    sample?.count(value, field); return value
}
private func incomingObserveFrame(_ value: CGRect?, _ field: LiveboardIncomingObserveField, _ sample: LiveboardIncomingObserveSample?) -> CGRect? {
    sample?.dimensions(value, field); return value
}
private func incomingObserveDeficit(_ value: CGFloat, _ sample: LiveboardIncomingObserveSample?) -> CGFloat {
    sample?.scalar(value, .deficit); return value
}
private func incomingObserveGestures(_ value: Int, _ sample: LiveboardIncomingObserveSample?) -> Int {
    sample?.gestures(value); return value
}
private func incomingObservePrevious(_ value: CGFloat?, _ sample: LiveboardIncomingObserveSample?) -> CGFloat? {
    sample?.flag(value != nil, .previousPresent); return value
}
private func incomingObserveDeadline(_ value: Date?, _ sample: LiveboardIncomingObserveSample?) -> Date? {
    sample?.flag(value != nil, .deadlinePresent); return value
}
func incomingObserveClip(_ value: CGRect?, _ sample: LiveboardIncomingObserveSample) -> CGRect? {
    sample.flag(value != nil, .revealed); return value
}

@MainActor
extension XCUIApplication {
    /// One event from the unchanged unsuccessful waiter: cached scalars only, no AX or private values.
    func lbRecordIncomingObserveFailure(_ sample: LiveboardIncomingObserveSample) {
        incomingPrint(sample.payload(summary: true))
        let data = incomingEncoded(sample.payload())
        XCTContext.runActivity(named: "Liveboard incoming observe failure") { activity in
            let attachment = XCTAttachment(data: data, uniformTypeIdentifier: "public.json")
            attachment.name = "liveboard-incoming-observe-failure.json"; attachment.lifetime = .keepAlways
            activity.add(attachment)
        }
    }
}

private func incomingRevealFrame(_ value: CGRect) -> CGRect? {
    let numbers = [value.minX, value.minY, value.width, value.height]
    guard !value.isEmpty, !value.isNull, !value.isInfinite, numbers.allSatisfy({ $0.isFinite }) else { return nil }
    return value
}

@MainActor
extension XCUIApplication {
    /// Existing observe deadline owns polling; only measured progress permits another scoped gesture.
    func lbRevealIncomingInbox(_ state: inout LiveboardIncomingRevealState, deadline: Date? = nil, observation: LiveboardIncomingObserveSample? = nil) -> CGRect? {
        let homeQuery = descendants(matching: .any).matching(NSPredicate(format: "identifier == %@", "lb-home"))
        let inboxQuery = descendants(matching: .any).matching(NSPredicate(format: "identifier == %@", "lb-inbox"))
        let scopeQuery = scrollViews.containing(NSPredicate(format: "identifier == %@", "lb-home"))
            .containing(NSPredicate(format: "identifier == %@", "lb-inbox"))
        guard incomingObserveCount(homeQuery.count, .homeCount, observation) == 1,
              incomingObserveCount(inboxQuery.count, .inboxCount, observation) == 1,
              incomingObserveCount(scopeQuery.count, .scopeCount, observation) == 1 else { return nil }
        let home = homeQuery.element(boundBy: 0), inbox = inboxQuery.element(boundBy: 0), scope = scopeQuery.element(boundBy: 0)
        guard incomingObserve(home.exists, .homeExists, observation), incomingObserve(inbox.exists, .inboxExists, observation),
              incomingObserve(scope.exists, .scopeExists, observation),
              let appBounds = incomingObserveFrame(incomingRevealFrame(frame), .appDimensions, observation),
              let homeBounds = incomingObserveFrame(incomingRevealFrame(home.frame), .homeDimensions, observation),
              let scopeBounds = incomingObserveFrame(incomingRevealFrame(scope.frame), .scopeDimensions, observation),
              let inboxBounds = incomingObserveFrame(incomingRevealFrame(inbox.frame), .inboxDimensions, observation),
              let clip = incomingObserveFrame(incomingRevealFrame(appBounds.intersection(scopeBounds).intersection(homeBounds)), .clipDimensions, observation),
              incomingObserve(inboxBounds.width <= clip.width, .widthFits, observation),
              incomingObserve(inboxBounds.height <= clip.height, .heightFits, observation),
              incomingObserve(inboxBounds.minX >= clip.minX, .leftFits, observation),
              incomingObserve(inboxBounds.maxX <= clip.maxX, .rightFits, observation) else { return nil }
        if incomingObserve(clip.contains(inboxBounds), .verticalContains, observation) { return clip }
        let below = max(0, inboxBounds.maxY - clip.maxY), above = max(0, clip.minY - inboxBounds.minY)
        let deficit = incomingObserveDeficit(above + below, observation)
        guard incomingObserve(deficit > 0, .deficitPositive, observation),
              incomingObserveGestures(state.gestures, observation) < 4 else { return nil }
        if let previous = incomingObservePrevious(state.previousDeficit, observation),
           incomingObserve(deficit >= previous, .noProgress, observation) { return nil }
        if let deadline = incomingObserveDeadline(deadline, observation),
           incomingObserve(Date() >= deadline, .deadlineExpired, observation) { return nil }
        state.previousDeficit = deficit; state.gestures += 1
        if incomingObserve(below > 0, .swipeUp, observation) { scope.swipeUp() } else { scope.swipeDown() }
        return nil
    }

    func lbIncomingSenderContained(in clip: CGRect, observation: LiveboardIncomingObserveSample? = nil) -> Bool {
        let query = descendants(matching: .any).matching(NSPredicate(format: "identifier == %@", "lb-request-sender"))
        guard incomingObserveCount(query.count, .containedCount, observation) == 1 else { return false }
        let sender = query.element(boundBy: 0)
        guard incomingObserve(sender.exists, .containedExists, observation),
              let senderBounds = incomingObserveFrame(incomingRevealFrame(sender.frame), .containedDimensions, observation) else { return false }
        return clip.contains(senderBounds)
    }
}

/// Test transport safety only: Core Recover ClaimId is opaque and is never rewritten.
func liveboardRecoveryRequestIdIsValid(_ value: String) -> Bool {
    !value.isEmpty && value.utf8.count <= 512 &&
        !value.unicodeScalars.contains { CharacterSet.controlCharacters.contains($0) }
}

struct LiveboardIncomingTerminalSample {
    var errorPresent: Bool?
    var headCount: Int?
    var badgeCount: Int?
}

@MainActor
extension XCUIApplication {
    /// Last real lazy poll only; UNKNOWN means that conjunct was not consulted.
    func lbRecordIncomingTerminalFailure(_ sample: LiveboardIncomingTerminalSample, expectedCount: Int) {
        let payload: [String: Any] = ["VERSION": 1, "PHASE": "TERMINAL_WAIT_FAILURE", "STATUS": "CACHED_REAL_CONJUNCTS",
                                    "ERROR_PRESENT": incomingFlag(sample.errorPresent),
                                    "HEAD_COUNT": sample.headCount.map { $0 as Any } ?? "UNKNOWN",
                                    "BADGE_COUNT": sample.badgeCount.map { $0 as Any } ?? "UNKNOWN",
                                    "EXPECTED_COUNT": expectedCount]
        incomingPrint(payload)
        XCTContext.runActivity(named: "Liveboard incoming terminal observation") { activity in
            let attachment = XCTAttachment(data: incomingEncoded(payload), uniformTypeIdentifier: "public.json")
            attachment.name = "liveboard-incoming-terminal-failure.json"; attachment.lifetime = .keepAlways
            activity.add(attachment)
        }
    }
}
