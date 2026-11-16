import Foundation

enum Test24PhaseError: Error { case invalidContext, invalidResponse, expired, transport, recordedFailure }

struct Test24PhaseEntry: Sendable {
    let raw: String?
    let required: Bool
    let selectedProfile: String?
    let expected: String?
}

// Closed canonical test grammar; not a general JSON parser or Core authority.
struct Test24RecoveryPhaseContext: Sendable {
    private let fields: [String]
    private let selectedProfile: String
    var runId: String { fields[0] }
    var cycle: Int { Int(fields[3])! }
    var contextId: String { fields[4] }
    var invocationId: String { fields[5] }
    var platform: String { fields[6] }
    var role: String { fields[7] }
    private static let profile24 = "test24:ios-multi-secret-lifecycle"
    private static let profile25 = "test25:web-secondary-creator-lifecycle"
    private static let grammar = #"\A\{"version":1,"runId":"([A-Za-z0-9][A-Za-z0-9_.-]{0,79})","testNumber":24,"attempt":([1-9][0-9]{0,8}),"blockId":"([A-Za-z0-9][A-Za-z0-9_.-]{0,79})","cycle":(1[3-9]|2[0-9]|3[0-9]),"logicalStep":1,"mode":"ios-multi-secret-lifecycle","contextId":"([0-9a-f]{32})","invocationId":"([0-9a-f]{32})","platform":"(android|ios|web)","role":"(sender|receiver)"\}\z"#
    private static let grammar25 = #"\A\{"version":1,"runId":"([A-Za-z0-9][A-Za-z0-9_.-]{0,79})","testNumber":25,"attempt":([1-9][0-9]{0,8}),"blockId":"([A-Za-z0-9][A-Za-z0-9_.-]{0,79})","cycle":(1[3-9]|2[0-9]|3[0-9]),"logicalStep":1,"mode":"web-secondary-creator-lifecycle","contextId":"([0-9a-f]{32})","invocationId":"([0-9a-f]{32})","platform":"(android|ios|web)","role":"(sender|receiver)"\}\z"#
    static func readEntry(_ entry: Test24PhaseEntry) throws -> Self? {
        if !entry.required {
            guard entry.selectedProfile == nil, entry.expected == nil else { throw Test24PhaseError.invalidContext }
            return try read(entry.raw, required: false)
        }
        guard let selected = entry.selectedProfile, TestRecoveryProfile.selected(selected) != nil else {
            throw Test24PhaseError.invalidContext
        }
        if selected == profile24 {
            guard entry.expected == nil else { throw Test24PhaseError.invalidContext }
        } else {
            guard let expected = entry.expected, !expected.isEmpty else { throw Test24PhaseError.invalidContext }
        }
        guard let context = try read(entry.raw, required: true), context.selectedProfile == selected else {
            throw Test24PhaseError.invalidContext
        }
        if let rawExpected = entry.expected {
            guard let expected = try read(rawExpected, required: true),
                  expected.selectedProfile == selected, entry.raw == rawExpected else { throw Test24PhaseError.invalidContext }
        }
        return context
    }

    static func read(_ raw: String?, required: Bool) throws -> Self? {
        if !required {
            guard raw == nil || raw == "" else { throw Test24PhaseError.invalidContext }
            return nil
        }
        guard let raw else { throw Test24PhaseError.invalidContext }
        let literal24 = captures(raw, grammar)
        let literal25 = captures(raw, grammar25)
        let future = literal24 == nil && literal25 == nil ? TestRecoveryProfile.readFuture(raw) : nil
        guard let fields = literal24 ?? literal25 ?? future?.fields, fields[4] != fields[5] else {
            throw Test24PhaseError.invalidContext
        }
        let selected = literal24 != nil ? profile24 : literal25 != nil ? profile25 : future!.selected
        return Self(fields: fields, selectedProfile: selected)
    }
    func requireCurrent(role: String, cycle: String, step: String) throws {
        guard platform == "ios", self.role == role, String(self.cycle) == cycle, step == "1" else {
            throw Test24PhaseError.invalidContext
        }
    }
    func emit(_ phase: String, failureCount: Int?) throws {
        try Task.checkCancellation()
        guard failureCount == 0, ["READY", "REQUEST_SENT", "INCOMING_VISIBLE"].contains(phase) else {
            throw Test24PhaseError.recordedFailure
        }
        print("E2E: RECOVERY_PHASE_V1 context=\(contextId) invocation=\(invocationId) platform=\(platform) phase=\(phase)")
    }
    fileprivate static func captures(_ text: String, _ pattern: String) -> [String]? {
        guard text.utf8.count <= 2048, text.utf8.allSatisfy({ $0 < 128 }),
              let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              match.range.length == text.utf16.count else { return nil }
        return (1..<match.numberOfRanges).map { String(text[Range(match.range(at: $0), in: text)!]) }
    }
}

struct Test24PhaseReply {
    let state: String
    let deadlineMs: Int
    let badgeMs: Int
    private static let grammar = #"\A\{"version":1,"contextId":"([0-9a-f]{32})","invocationId":"([0-9a-f]{32})","view":"(dispatch|request-sent)","state":"(WAITING|DISPATCH_PERMITTED|REQUEST_SENT)","deadlineRemainingMs":([1-9][0-9]{0,5}),"badgeRemainingMs":(0|[1-9][0-9]{0,5})\}\z"#
    static func read(_ data: Data, context: Test24RecoveryPhaseContext, view: String) throws -> Self {
        guard view == "dispatch" && context.role == "sender" || view == "request-sent" && context.role == "receiver" else {
            throw Test24PhaseError.invalidResponse
        }
        guard data.count <= 2048, let text = String(data: data, encoding: .ascii),
              let fields = Test24RecoveryPhaseContext.captures(text, grammar),
              fields[0] == context.contextId, fields[1] == context.invocationId, fields[2] == view,
              let deadline = Int(fields[4]), let badge = Int(fields[5]),
              (1...600_000).contains(deadline), (0...180_000).contains(badge) else {
            throw Test24PhaseError.invalidResponse
        }
        guard fields[3] == "REQUEST_SENT"
            ? view == "request-sent" && badge > 0 && badge <= deadline
            : badge == 0 && (fields[3] == "WAITING" || view == "dispatch") else {
            throw Test24PhaseError.invalidResponse
        }
        return Self(state: fields[3], deadlineMs: deadline, badgeMs: badge)
    }
}

// Real URLSession delegate: cap bytes while receiving, deny redirects/errors,
// finish once, cancel session/task/timer and suppress every late completion.
final class Test24PhaseCall: NSObject, URLSessionDataDelegate, @unchecked Sendable {
    private let lock = NSLock()
    private var done = false, accepted = false
    private var data = Data()
    private var expectedLength: Int64 = -1
    private var deadline: UInt64 = 0
    private var continuation: CheckedContinuation<Data, Error>?
    private var session: URLSession?
    private var task: URLSessionDataTask?
    private var timer: DispatchWorkItem?

    func start(_ next: CheckedContinuation<Data, Error>, url: URL, budgetMs: Int, configuration: URLSessionConfiguration) {
        lock.lock()
        if done { lock.unlock(); next.resume(throwing: Test24PhaseError.expired); return }
        continuation = next
        deadline = DispatchTime.now().uptimeNanoseconds + UInt64(budgetMs) * 1_000_000
        let config = configuration
        config.timeoutIntervalForRequest = Double(budgetMs) / 1000
        config.timeoutIntervalForResource = Double(budgetMs) / 1000
        config.urlCache = nil
        let session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
        var request = URLRequest(url: url)
        request.timeoutInterval = Double(budgetMs) / 1000
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        let task = session.dataTask(with: request)
        let timer = DispatchWorkItem { [weak self] in self?.finish(.failure(Test24PhaseError.expired)) }
        self.session = session; self.task = task; self.timer = timer
        lock.unlock()
        DispatchQueue.global().asyncAfter(deadline: .now() + .milliseconds(budgetMs), execute: timer)
        task.resume()
    }
    func cancel() { finish(.failure(Test24PhaseError.expired)) }
    private func finish(_ outcome: Result<Data, Error>) {
        lock.lock()
        if done { lock.unlock(); return }
        done = true
        let next = continuation, session = session, task = task, timer = timer
        let expired = deadline != 0 && DispatchTime.now().uptimeNanoseconds >= deadline
        continuation = nil; self.session = nil; self.task = nil; self.timer = nil
        lock.unlock()
        timer?.cancel(); task?.cancel(); session?.invalidateAndCancel()
        next?.resume(with: expired ? .failure(Test24PhaseError.expired) : outcome)
    }
    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask,
                    didReceive response: URLResponse, completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        guard let http = response as? HTTPURLResponse, http.statusCode == 200,
              http.value(forHTTPHeaderField: "Content-Type")?.lowercased() == "application/json",
              http.value(forHTTPHeaderField: "Content-Encoding") == nil || http.value(forHTTPHeaderField: "Content-Encoding") == "identity",
              response.expectedContentLength <= 2048 else {
            completionHandler(.cancel); finish(.failure(Test24PhaseError.transport)); return
        }
        lock.lock(); let live = !done; accepted = live; expectedLength = response.expectedContentLength; lock.unlock()
        completionHandler(live ? .allow : .cancel)
    }
    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive chunk: Data) {
        lock.lock()
        guard !done else { lock.unlock(); return }
        guard data.count + chunk.count <= 2048 else {
            lock.unlock(); finish(.failure(Test24PhaseError.invalidResponse)); return
        }
        data.append(chunk); lock.unlock()
    }
    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        lock.lock()
        let bytes = data, valid = accepted && error == nil && (expectedLength < 0 || Int64(data.count) == expectedLength)
        lock.unlock()
        finish(valid ? .success(bytes) : .failure(Test24PhaseError.transport))
    }
    func urlSession(_ session: URLSession, task: URLSessionTask,
                    willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
                    completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil); finish(.failure(Test24PhaseError.transport))
    }
    @MainActor
    static func fetch(_ url: URL, budgetMs: Int, configuration: URLSessionConfiguration? = nil) async throws -> Data {
        guard (1...1000).contains(budgetMs) else { throw Test24PhaseError.expired }
        let call = Test24PhaseCall(), config = configuration ?? .ephemeral
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { call.start($0, url: url, budgetMs: budgetMs, configuration: config) }
        } onCancel: { call.cancel() }
    }
}

@MainActor
final class Test24RecoveryPhaseClient {
    let context: Test24RecoveryPhaseContext
    private let base: String
    private let clock: @MainActor () -> Double
    private let exchange: @MainActor (URL, Int) async throws -> Data
    private let pause: @MainActor (Int) async throws -> Void
    private var terminal = false, dispatched = false
    private var localDeadline = Double.infinity
    private var lastClock = -Double.infinity

    init(context: Test24RecoveryPhaseContext, base: String,
         clock: @escaping @MainActor () -> Double = { ProcessInfo.processInfo.systemUptime * 1000 },
         exchange: @escaping @MainActor (URL, Int) async throws -> Data = { try await Test24PhaseCall.fetch($0, budgetMs: $1) },
         pause: @escaping @MainActor (Int) async throws -> Void = { try await Task.sleep(nanoseconds: UInt64($0) * 1_000_000) }) throws {
        guard base == "http://127.0.0.1:5180" else { throw Test24PhaseError.invalidContext }
        self.context = context; self.base = base; self.clock = clock; self.exchange = exchange; self.pause = pause
    }
    func awaitDispatch() async throws {
        guard context.role == "sender", !dispatched else { throw Test24PhaseError.invalidContext }
        _ = try await wait("dispatch", ceilingMs: 240_000); dispatched = true
    }
    func awaitRequestSent() async throws -> Int {
        guard context.role == "receiver" else { throw Test24PhaseError.invalidContext }
        return try await wait("request-sent", ceilingMs: 600_000)
    }
    private func wait(_ view: String, ceilingMs: Int) async throws -> Int {
        guard !terminal else { throw Test24PhaseError.expired }
        localDeadline = min(localDeadline, try now() + Double(ceilingMs))
        do {
            while true {
                try Task.checkCancellation()
                let start = try now(), budget = min(1000, floor(localDeadline - start))
                guard start.isFinite, budget > 0,
                      let url = URL(string: "\(base)/recovery-phase?v=1&context=\(context.contextId)&participant=\(context.invocationId)&view=\(view)") else {
                    throw Test24PhaseError.expired
                }
                let bytes = try await exchange(url, Int(budget))
                try Task.checkCancellation()
                let reply = try Test24PhaseReply.read(bytes, context: context, view: view)
                let end = try now()
                guard end.isFinite, end >= start else { throw Test24PhaseError.expired }
                let rtt = end - start
                localDeadline = min(localDeadline, end + Double(reply.deadlineMs) - rtt)
                let remaining = floor(localDeadline - end)
                guard remaining > 0 else { throw Test24PhaseError.expired }
                try Task.checkCancellation()
                if reply.state == "DISPATCH_PERMITTED" { return Int(remaining) }
                if reply.state == "REQUEST_SENT" {
                    let badge = floor(min(remaining, Double(reply.badgeMs) - rtt))
                    guard badge > 0 else { throw Test24PhaseError.expired }
                    terminal = true; return Int(badge)
                }
                try await pause(Int(min(200, remaining)))
            }
        } catch { terminal = true; throw error }
    }
    private func now() throws -> Double {
        let value = clock()
        guard value.isFinite, value >= lastClock else { throw Test24PhaseError.expired }
        lastClock = value; return value
    }
}
