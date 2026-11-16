import Foundation

enum Test25ShowError: Error { case invalidEntry }

struct Test25ShowCurrent: Sendable {
    let role: String?
    let cycle: String?
    let step: String?
    let hasLegacy22: Bool
}

struct Test25ShowEntry: Sendable {
    let raw: String?
    let phase: Test24PhaseEntry
    let current: Test25ShowCurrent
}

// TEST-only literal25 procedure metadata. This never grants Show permission,
// changes a deadline or substitutes for the original value/Copy/Hide assertions.
struct Test25ShowPhaseContext: Equatable, Sendable {
    private let nonce: String
    private let cycle: Int
    private let phaseRaw: String
    private let contextId: String
    private let invocationId: String
    private static let selected = "test25:web-secondary-creator-lifecycle"
    private static let grammar = #"\A\{"testNumber":25,"role":"sender","nonce":"([0-9a-f]{32})","cycle":(19|20|21|28|29|30|37|38|39),"step":1,"runId":"([A-Za-z0-9][A-Za-z0-9_.-]{0,79})","contextId":"([0-9a-f]{32})","invocationId":"([0-9a-f]{32})"\}\z"#

    static func readEntry(_ entry: Test25ShowEntry) throws -> Self? {
        let selected25 = entry.phase.selectedProfile == selected
        guard selected25 || entry.raw != nil else { return nil }
        try Task.checkCancellation()
        guard selected25, entry.phase.required, !entry.current.hasLegacy22,
              let phase = try Test24RecoveryPhaseContext.readEntry(entry.phase),
              let role = entry.current.role, let cycle = entry.current.cycle,
              let step = entry.current.step else { throw Test25ShowError.invalidEntry }
        try phase.requireCurrent(role: role, cycle: cycle, step: step)
        if phase.role != "sender" {
            guard entry.raw == nil else { throw Test25ShowError.invalidEntry }
            return nil
        }
        guard let phaseRaw = entry.phase.raw, let raw = entry.raw, let fields = captures(raw),
              fields[2] == phase.runId, fields[3] == phase.contextId,
              fields[4] == phase.invocationId, let currentCycle = Int(fields[1]),
              currentCycle == phase.cycle else { throw Test25ShowError.invalidEntry }
        return Self(nonce: fields[0], cycle: currentCycle, phaseRaw: phaseRaw,
                    contextId: fields[3], invocationId: fields[4])
    }

    // The existing callback cannot throw. Its actual caller records XCTFail and
    // emits nothing on nil; continueAfterFailure=false remains the case policy.
    func line(_ outcome: String, current: Test25ShowEntry, failureCount: Int?) -> String? {
        guard !Task.isCancelled, failureCount == 0,
              outcome == "INPUT_RETURNED" || outcome == "ALREADY_VISIBLE" else { return nil }
        do {
            guard let accepted = try Self.readEntry(current), accepted == self else { return nil }
        } catch { return nil }
        let text = "E2E: IOS_TEST25_SHOW_PREP_V1 \(nonce) \(contextId) \(invocationId) \(cycle) 1 \(outcome)"
        guard text.utf8.count + 1 <= 512 else { return nil }
        return text
    }

    private static func captures(_ raw: String) -> [String]? {
        guard raw.utf8.count <= 2048, raw.utf8.allSatisfy({ $0 < 128 }),
              let regex = try? NSRegularExpression(pattern: grammar),
              let match = regex.firstMatch(in: raw, range: NSRange(raw.startIndex..., in: raw)),
              match.range.length == raw.utf16.count else { return nil }
        return (1..<match.numberOfRanges).map { String(raw[Range(match.range(at: $0), in: raw)!]) }
    }
}
