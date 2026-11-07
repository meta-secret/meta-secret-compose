import XCTest

private final class LiveboardCoordinatorResponse: @unchecked Sendable {
    private let lock = NSLock()
    private var bytes: Data?
    func set(_ value: Data?) { lock.lock(); defer { lock.unlock() }; bytes = value }
    func get() -> Data? { lock.lock(); defer { lock.unlock() }; return bytes }
}

private func liveboardCoordinatorData(_ url: URL) -> Data? {
    let box = LiveboardCoordinatorResponse()
    let semaphore = DispatchSemaphore(value: 0)
    var request = URLRequest(url: url)
    request.timeoutInterval = 2
    request.cachePolicy = .reloadIgnoringLocalCacheData
    let task = URLSession.shared.dataTask(with: request) { data, response, _ in
        if (response as? HTTPURLResponse)?.statusCode == 200 { box.set(data) }
        semaphore.signal()
    }
    task.resume()
    if semaphore.wait(timeout: .now() + 2) != .success { task.cancel(); return nil }
    return box.get()
}

enum LiveboardIncomingOpenResult { case opened, terminal, refused }

struct LiveboardIncomingExpected {
    let kind: String
    var subject: String? = nil
    var requester: String? = nil
    var requestId: String? = nil
    var count: Int? = nil
}

struct LiveboardIncoming {
    let identifier: String
    let kind: String
    let sender: String
    let subject: String
    let count: Int
    var requestId: String { String(identifier.dropFirst("lb-inbox-head-\(kind)-".count)) }
}

struct LiveboardFifoContext: Decodable {
    struct Identity: Decodable { let deviceId: String; let deviceName: String }
    struct Request: Decodable {
        let kind: String; let requestId: String; let passId: String
        let requesterId: String; let requesterName: String; let subject: String; let eligiblePeerIds: [String]
    }
    struct Approval: Decodable { let logicalStep: Int; let approverPlatform: String; let passId: String; let subject: String; let action: String }
    let version: Int; let runId: String; let testNumber: Int; let blockId: String; let cycle: Int
    let identities: [String: Identity]; let requests: [Request]; let approvals: [Approval]
}

@MainActor
extension XCUIApplication {
    func lbIncomingElement(kind: String, processing: Bool = false) -> XCUIElement {
        let head = descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "lb-inbox-head-\(kind)-")).firstMatch
        if !processing { return head }
        return head.descendants(matching: .any).matching(NSPredicate(format: "label IN %@", ["Pending", "В ожидании"])).firstMatch
    }

    func lbInboxError() -> XCUIElement {
        descendants(matching: .any)["lb-inbox"].descendants(matching: .any).matching(NSPredicate(format: "label IN %@",
            ["Tap to dismiss and refresh", "Нажмите, чтобы закрыть и обновить"])).firstMatch
    }

    func lbInboxIdle() -> Bool {
        let idle = descendants(matching: .any)["lb-inbox"].descendants(matching: .any).matching(NSPredicate(format: "label IN %@",
            ["No actions waiting", "Нет ожидающих действий"])).firstMatch
        return lbVisible(idle) && !lbInboxError().exists && lbIncomingCount() == 0 && lbReadIncoming() == nil
    }

    func lbDismissInboxErrorIfPresent() {
        if lbInboxError().exists { lbTap("lb-inbox"); lbWait({ !self.lbInboxError().exists }) }
    }

    func lbResumeDecisionAfterReconnect(timeout: TimeInterval = 120) -> Bool {
        guard let before = lbSelection.incoming else { XCTFail("Reconnect has no original captured request"); return false }
        var ready = false
        lbWait({
            self.lbDismissInboxErrorIfPresent()
            if self.lbInboxIdle() { return true }
            guard let head = self.lbReadIncoming(), head.identifier == before.identifier,
                  head.sender == before.sender && head.subject == before.subject && head.count == before.count else { return false }
            _ = self.lbIncoming(LiveboardIncomingExpected(kind: before.kind, subject: before.subject,
                requester: before.sender, requestId: before.requestId, count: before.count), timeout: timeout)
            let approve = self.descendants(matching: .any)["lb-approve-\(before.requestId)"]
            ready = approve.exists && approve.isEnabled && approve.isHittable
            return ready
        }, timeout: timeout)
        if !ready { lbIncomingTerminal(before, timeout: timeout) }
        return ready
    }

    func lbIncomingCount() -> Int {
        XCTAssertLessThanOrEqual(descendants(matching: .any).matching(identifier: "lb-inbox-count").count, 1,
                                 "Ambiguous queue count cannot establish an empty inbox")
        let badge = descendants(matching: .any)["lb-inbox-count"]
        if !badge.exists { return 0 }
        let candidate = badge.label.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let count = Int(candidate), count > 0 else { XCTFail("Incoming badge is not a positive total count"); return -1 }
        return count
    }

    func lbReadIncoming(observed: ((LiveboardIncomingSenderSample) -> Void)? = nil) -> LiveboardIncoming? {
        let query = descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "lb-inbox-head-"))
        let heads = query.allElementsBoundByIndex.filter { lbVisible($0) }
        XCTAssertLessThanOrEqual(heads.count, 1, "Multiple visible request heads cannot establish inbox absence")
        guard heads.count == 1 else { return nil }
        let head = heads[0]
        let kinds = ["Join", "Recover", "DeleteSecret", "RemoveDevice"]
        guard let kind = kinds.first(where: { head.identifier.hasPrefix("lb-inbox-head-\($0)-") }) else { return nil }
        let headIdentifier = head.identifier
        let requestId = String(headIdentifier.dropFirst("lb-inbox-head-\(kind)-".count))
        let uuidValid = UUID(uuidString: requestId) != nil
        guard (kind == "Recover" ? liveboardRecoveryRequestIdIsValid(requestId) : uuidValid) else { return nil }
        let sender = descendants(matching: .any)["lb-request-sender"]
        let subject = descendants(matching: .any)["lb-request-subject"]
        var sample = LiveboardIncomingSenderSample(headIdentifier: headIdentifier, kind: kind, visibleHeadCount: heads.count)
        sample.uuidValid = uuidValid
        let senderVisible = lbVisible(sender, observed: { exists, target, app in
            sample.exists = exists; sample.targetFrame = target; sample.appFrame = app
        })
        sample.visible = senderVisible
        guard senderVisible else { observed?(sample); return nil }
        let guardLabel = sender.label
        sample.labelLength = guardLabel.count; sample.nonempty = !guardLabel.isEmpty
        observed?(sample)
        guard !guardLabel.isEmpty else { return nil }
        let count = lbIncomingCount()
        guard count > 0 else { return nil }
        return LiveboardIncoming(identifier: head.identifier, kind: kind, sender: sender.label,
                                 subject: subject.exists ? subject.label : "", count: count)
    }

    @discardableResult
    func lbObserveIncoming(_ expected: LiveboardIncomingExpected, timeout: TimeInterval = 180) -> LiveboardIncoming {
        lbHome(timeout: timeout)
        var captured: LiveboardIncoming?
        var reveal = LiveboardIncomingRevealState()
        var sample = LiveboardIncomingObserveSample()
        let progress = LiveboardIncomingObserveProgress()
        lbWait({
            sample = LiveboardIncomingObserveSample()
            defer { progress.record(sample) }
            guard let clip = incomingObserveClip(self.lbRevealIncomingInbox(&reveal, observation: sample), sample) else { return false }
            guard let value = self.lbReadIncoming(observed: { self.lbRecordIncomingSenderObservation($0); sample.sender($0) })
            else { sample.flag(false, .readPresent); return false }
            sample.flag(true, .readPresent)
            guard sample.flag(self.lbIncomingSenderContained(in: clip, observation: sample), .contained),
                  sample.flag(value.kind == expected.kind, .kindMatch),
                  sample.flag(expected.subject.map({ $0 == value.subject }) ?? true, .subjectMatch),
                  sample.flag(expected.requester.map({ $0 == value.sender }) ?? true, .requesterMatch),
                  sample.flag(expected.requestId.map({ $0 == value.requestId }) ?? true, .requestIdMatch),
                  sample.flag(expected.count.map({ $0 == value.count }) ?? true, .countMatch) else { return false }
            captured = value; return true
        }, timeout: timeout, message: "Exact current incoming request was not visibly identified", failureDiagnostic: {
            self.lbRecordIncomingObserveFailure(sample)
        })
        guard let captured else {
            return LiveboardIncoming(identifier: "", kind: expected.kind, sender: "", subject: "", count: -1)
        }
        lbSelection.incoming = captured
        return captured
    }

    @discardableResult
    func lbIncoming(_ expected: LiveboardIncomingExpected, timeout: TimeInterval = 180) -> LiveboardIncoming {
        let captured = lbObserveIncoming(expected, timeout: timeout)
        if !descendants(matching: .any)["lb-approve-\(captured.requestId)"].exists { lbTap("lb-inbox") }
        let same = lbReadIncoming()
        XCTAssertEqual(same?.identifier, captured.identifier, "Current request changed before expansion")
        _ = lbRequireExpandedApprove(captured)
        _ = lbRequire("lb-decline-\(captured.requestId)")
        return captured
    }

    @discardableResult
    func lbCurrentIncoming() -> LiveboardIncoming {
        guard let incoming = lbReadIncoming() else {
            XCTFail("No single actual typed visible incoming head")
            return LiveboardIncoming(identifier: "", kind: "", sender: "", subject: "", count: -1)
        }
        lbSelection.incoming = incoming
        return incoming
    }

    func lbDecideCurrent(approve: Bool) {
        guard let before = lbSelection.incoming else { XCTFail("No captured decision target"); return }
        lbSendDecision(approve: approve)
        lbIncomingTerminal(before)
    }

    func lbSendDecision(approve: Bool) {
        guard let expected = lbSelection.incoming else { XCTFail("Decision has no previously identified request"); return }
        guard let current = lbReadIncoming(), current.identifier == expected.identifier,
              current.kind == expected.kind && current.subject == expected.subject && current.sender == expected.sender && current.count == expected.count else {
            XCTFail("Decision target is not the SAME visible request"); return
        }
        lbTap("lb-\(approve ? "approve" : "decline")-\(current.requestId)")
        _ = lbConfirmSystemIfPresent()
    }

    func lbOpenIncomingOrTerminal(_ before: LiveboardIncoming, timeout: TimeInterval = 180) -> LiveboardIncomingOpenResult {
        guard timeout.isFinite, timeout > 0, timeout <= 180 else { XCTFail("Invalid request opening budget"); return .refused }
        let deadline = ProcessInfo.processInfo.systemUptime + timeout
        var refused = false, terminal = false, ready = false
        var actionableTarget: XCUIElement?
        func remaining() -> TimeInterval { max(0, deadline - ProcessInfo.processInfo.systemUptime) }
        func read<T>(_ operation: () -> T) -> T? {
            guard remaining() > 0 else { refused = true; return nil }
            let value = operation()
            guard remaining() > 0 else { refused = true; return nil }
            return value
        }
        func finiteFrame(_ value: CGRect) -> CGRect? {
            let numbers = [value.minX, value.minY, value.width, value.height]
            guard !value.isEmpty, !value.isNull, !value.isInfinite, numbers.allSatisfy({ $0.isFinite }) else { return nil }
            return value
        }
        func matches(_ value: LiveboardIncoming) -> Bool {
            value.identifier == before.identifier && value.kind == before.kind && value.sender == before.sender && value.subject == before.subject && value.count == before.count
        }
        func observe(_ expanded: Bool) -> Bool {
            ready = false
            guard let error = read({ self.lbInboxError().exists }), !error,
                  let heads = read({ self.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "lb-inbox-head-")).count }), heads <= 1,
                  let counters = read({ self.descendants(matching: .any).matching(identifier: "lb-inbox-count").count }), counters <= 1,
                  let oldCount = read({ self.descendants(matching: .any).matching(identifier: before.identifier).count }), oldCount <= 1,
                  let count = read({ self.lbIncomingCount() }), count >= 0,
                  let sampled = read({ self.lbReadIncoming() }) else { refused = true; return true }
            if let current = sampled, current.requestId == before.requestId && !matches(current) { refused = true; return true }
            if heads > 0 && sampled == nil { refused = true; return true }
            if oldCount == 0 && count == before.count - 1 { terminal = true; return true }
            guard let current = sampled, matches(current) else {
                if sampled != nil { refused = true; return true }; return false
            }
            let homeQuery = self.descendants(matching: .any).matching(identifier: "lb-home")
            let inboxQuery = self.descendants(matching: .any).matching(identifier: "lb-inbox")
            let scopeQuery = self.scrollViews.containing(NSPredicate(format: "identifier == %@", "lb-home")).containing(NSPredicate(format: "identifier == %@", "lb-inbox"))
            guard let homes = read({ homeQuery.count }), homes == 1, let inboxes = read({ inboxQuery.count }), inboxes == 1,
                  let scopes = read({ scopeQuery.count }), scopes == 1,
                  let appFrame = read({ self.frame }), let homeFrame = read({ homeQuery.element(boundBy: 0).frame }),
                  let scopeFrame = read({ scopeQuery.element(boundBy: 0).frame }), let inboxFrame = read({ inboxQuery.element(boundBy: 0).frame }),
                  let appBounds = finiteFrame(appFrame), let homeBounds = finiteFrame(homeFrame),
                  let scopeBounds = finiteFrame(scopeFrame), let inboxBounds = finiteFrame(inboxFrame),
                  let clip = finiteFrame(appBounds.intersection(homeBounds).intersection(scopeBounds)),
                  inboxBounds.minX >= clip.minX, inboxBounds.maxX <= clip.maxX,
                  expanded || clip.contains(inboxBounds), let contained = read({ self.lbIncomingSenderContained(in: clip) }), contained else { refused = true; return true }
            if expanded {
                let approve = self.descendants(matching: .any).matching(identifier: "lb-approve-\(before.requestId)")
                let decline = self.descendants(matching: .any).matching(identifier: "lb-decline-\(before.requestId)")
                guard let a = read({ approve.count }), let d = read({ decline.count }), a <= 1, d <= 1 else { refused = true; return true }
                guard a == 1 && d == 1 else { return false }
                guard let av = read({ self.lbVisible(approve.element(boundBy: 0)) }), let dv = read({ self.lbVisible(decline.element(boundBy: 0)) }) else { return true }
                ready = av && dv
            } else {
                let target = inboxQuery.element(boundBy: 0)
                guard let visible = read({ self.lbVisible(target) }), let enabled = read({ target.isEnabled }), let hittable = read({ target.isHittable }) else { return true }
                ready = visible && enabled && hittable
                actionableTarget = ready ? target : nil
            }
            return ready
        }
        func finishTerminal() -> LiveboardIncomingOpenResult {
            guard remaining() > 0, self.lbIncomingTerminal(before, timeout: remaining(), deadline: deadline), remaining() > 0 else { return .refused }
            return .terminal
        }
        guard remaining() > 0 else { return .refused }
        lbWait({ observe(false) }, timeout: remaining(), message: "Current request did not become actionable or terminal")
        guard !refused, remaining() > 0 else { return .refused }
        if terminal { return finishTerminal() }
        guard ready, observe(false), !refused, remaining() > 0 else { return .refused }
        if terminal { return finishTerminal() }
        guard ready, let actionableTarget else { return .refused }
        lbSelection.incoming = before
        guard remaining() > 0 else { return .refused }
        actionableTarget.tap()
        guard remaining() > 0 else { return .refused }
        lbWait({ observe(true) }, timeout: remaining(), message: "SAME request expansion or terminal evidence was not observed")
        guard !refused, remaining() > 0 else { return .refused }
        if terminal { return finishTerminal() }
        return ready ? .opened : .refused
    }

    @discardableResult
    func lbIncomingTerminal(_ before: LiveboardIncoming, timeout: TimeInterval = 180, deadline: TimeInterval? = nil) -> Bool {
        func fresh() -> Bool { deadline.map { ProcessInfo.processInfo.systemUptime < $0 } ?? true }
        func remaining() -> TimeInterval { deadline.map { min(timeout, max(0, $0 - ProcessInfo.processInfo.systemUptime)) } ?? timeout }
        var sample = LiveboardIncomingTerminalSample(), firstProved = false, lastProved = false
        guard remaining() > 0 else { return false }
        lbWait({
            sample = LiveboardIncomingTerminalSample()
            guard fresh() else { return false }
            let errorPresent = self.lbInboxError().exists
            guard fresh() else { return false }
            sample.errorPresent = errorPresent
            guard !errorPresent else { return false }
            let headCount = self.descendants(matching: .any).matching(identifier: before.identifier).count
            guard fresh() else { return false }
            sample.headCount = headCount
            guard headCount == 0 else { return false }
            let badgeCount = self.lbIncomingCount()
            guard fresh() else { return false }
            sample.badgeCount = badgeCount
            if deadline != nil {
                let heads = self.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "lb-inbox-head-")).count
                guard fresh() else { return false }
                let badges = self.descendants(matching: .any).matching(identifier: "lb-inbox-count").count
                guard fresh(), heads <= 1, badges <= 1, before.count > 1 || heads == 0 else { return false }
            }
            firstProved = badgeCount == before.count - 1
            return firstProved
        }, timeout: remaining(), message: "SAME request disappearance and total-count decrement were not observed", failureDiagnostic: {
            self.lbRecordIncomingTerminalFailure(sample, expectedCount: before.count - 1)
        })
        if deadline != nil && (!firstProved || !fresh()) { return false }
        guard remaining() > 0 else { return false }
        if before.count > 1 {
            lbWait({
                guard fresh() else { return false }
                if deadline != nil { let error = self.lbInboxError().exists; guard fresh(), !error else { return false } }
                guard let next = self.lbReadIncoming(), fresh() else { return false }
                lastProved = next.requestId != before.requestId && next.count == before.count - 1
                return lastProved
            }, timeout: remaining())
        } else {
            lbWait({ guard fresh() else { return false }; let idle = self.lbInboxIdle(); guard fresh() else { return false }; lastProved = idle; return idle }, timeout: remaining(), message: "No actual idle inbox after the SAME request terminated")
        }
        return firstProved && lastProved && fresh()
    }

    func lbLoadFifo(cycle: Int, expected: [String: String], expectedRequestCount: Int) -> LiveboardFifoContext? {
        guard (1...2).contains(expectedRequestCount) else {
            XCTFail("FIFO requires the exact supported current-cycle request count"); return nil
        }
        let base = ProcessInfo.processInfo.environment["E2E_APPROVAL_COORDINATOR_URL"] ?? "http://127.0.0.1:5180"
        guard let url = URL(string: "\(base)/fifo?cycle=\(cycle)") else { XCTFail("Invalid coordinator URL"); return nil }
        var context: LiveboardFifoContext?
        lbWait({
            guard let data = liveboardCoordinatorData(url), let value = try? JSONDecoder().decode(LiveboardFifoContext.self, from: data) else { return false }
            guard value.version == 1, value.cycle == cycle,
                  value.runId == expected["runId"], String(value.testNumber) == expected["testNumber"],
                  value.blockId == expected["blockId"], value.requests.count == expectedRequestCount,
                  Set(value.requests.map(\.requestId)).count == expectedRequestCount else { XCTFail("Foreign or incomplete immutable FIFO context"); return false }
            let identities = Array(value.identities.values)
            let ids = Set(identities.map(\.deviceId))
            guard Set(value.identities.keys) == Set(["web", "android", "ios"]), ids.count == 3,
                  identities.allSatisfy({ !$0.deviceId.isEmpty && !$0.deviceName.isEmpty }),
                  !value.approvals.isEmpty, Set(value.approvals.map(\.logicalStep)) == Set(1...value.approvals.count),
                  Set(value.requests.map(\.requesterId)).count == expectedRequestCount,
                  value.requests.allSatisfy({ request in
                      request.kind == "recover" && liveboardRecoveryRequestIdIsValid(request.requestId) && !request.passId.isEmpty && !request.subject.isEmpty &&
                      identities.contains(where: { $0.deviceId == request.requesterId && $0.deviceName == request.requesterName }) &&
                      Set(request.eligiblePeerIds) == ids.subtracting([request.requesterId])
                  }) else { XCTFail("FIFO canonical identity/claim map is incomplete or foreign"); return false }
            context = value; return true
        }, timeout: 180)
        return context
    }

    func lbFifoStep(context: LiveboardFifoContext, completed: Set<String>, remaining: Set<Int>) -> (LiveboardIncoming, Int)? {
        guard let actor = context.identities["ios"] else { XCTFail("Missing canonical iOS identity"); return nil }
        lbHome()
        var matched: (LiveboardIncoming, Int)?
        var reveal = LiveboardIncomingRevealState()
        lbWait({
            guard let clip = self.lbRevealIncomingInbox(&reveal) else { return false }
            guard let head = self.lbReadIncoming(observed: { self.lbRecordIncomingSenderObservation($0) }),
                  head.kind == "Recover" else { return false }
            guard self.lbIncomingSenderContained(in: clip) else { return false }
            let addressed = context.requests.filter { $0.eligiblePeerIds.contains(actor.deviceId) && !completed.contains($0.requestId) }
            guard head.count == addressed.count,
                  let request = addressed.first(where: { $0.requestId == head.requestId }),
                  request.kind == "recover", request.requesterId != actor.deviceId,
                  request.requesterName == head.sender, request.subject == head.subject else { return false }
            let approvals = context.approvals.filter { remaining.contains($0.logicalStep) && $0.approverPlatform == "ios" && $0.passId == request.passId && $0.subject == request.subject && $0.action == "approve" }
            guard let step = approvals.map(\.logicalStep).min() else { XCTFail("Current head has no original assigned approval step"); return false }
            matched = (head, step); return true
        }, timeout: 180)
        if let matched {
            _ = lbIncoming(LiveboardIncomingExpected(kind: "Recover", subject: matched.0.subject,
                requester: matched.0.sender, requestId: matched.0.requestId, count: matched.0.count))
        }
        return matched
    }
}
