import Foundation

enum RecoveryShowError: Error { case invalidEntry }

struct RecoveryShowCurrent: Sendable {
    let role: String?
    let cycle: String?
    let step: String?
    let hasLegacy22: Bool
    let hasLiteral25: Bool
}
struct RecoveryShowEntry: Sendable {
    let raw: String?
    let phase: Test24PhaseEntry
    let current: RecoveryShowCurrent
}

// TEST-only procedure receipt. This is not permission to reveal a Secret and
// never replaces the existing value, Copy, Hide, approval or terminal checks.
struct RecoveryShowPhaseContext: Equatable, Sendable {
    private let fields: [String]
    private let phaseRaw: String
    private static let grammar = #"\A\{"testNumber":([1-9][0-9]{0,2}),"role":"sender","nonce":"([0-9a-f]{32})","cycle":([1-9][0-9]{0,2}),"step":1,"runId":"([A-Za-z0-9][A-Za-z0-9_.-]{0,79})","contextId":"([0-9a-f]{32})","invocationId":"([0-9a-f]{32})"\}\z"#

    static func readEntry(_ entry: RecoveryShowEntry) throws -> Self? {
        let profile = TestRecoveryProfile.future(entry.phase.selectedProfile)
        guard profile != nil || entry.raw != nil else { return nil }
        try Task.checkCancellation()
        guard let profile, entry.phase.required,
              !entry.current.hasLegacy22, !entry.current.hasLiteral25,
              let phase = try Test24RecoveryPhaseContext.readEntry(entry.phase),
              let role = entry.current.role, let cycle = entry.current.cycle,
              let step = entry.current.step else { throw RecoveryShowError.invalidEntry }
        try phase.requireCurrent(role: role, cycle: cycle, step: step)
        if phase.role != "sender" {
            guard entry.raw == nil else { throw RecoveryShowError.invalidEntry }
            return nil
        }
        guard let phaseRaw = entry.phase.raw, let raw = entry.raw, let fields = captures(raw),
              fields[0] == String(profile.number), let ownCycle = Int(fields[2]),
              profile.permitsIosSender(ownCycle), ownCycle == phase.cycle,
              fields[3] == phase.runId, fields[4] == phase.contextId,
              fields[5] == phase.invocationId else { throw RecoveryShowError.invalidEntry }
        return Self(fields: fields, phaseRaw: phaseRaw)
    }

    // The same original nonthrowing callback records XCTFail on nil. Check the
    // full admitted entry again; no new AX/config/network/auth read is performed.
    func line(_ outcome: String, current: RecoveryShowEntry, failureCount: Int?) -> String? {
        guard !Task.isCancelled, failureCount == 0,
              outcome == "INPUT_RETURNED" || outcome == "ALREADY_VISIBLE" else { return nil }
        do {
            guard let accepted = try Self.readEntry(current), accepted == self else { return nil }
        } catch { return nil }
        let text = "E2E: IOS_RECOVERY_SHOW_PREP_V1 \(fields[0]) \(fields[1]) \(fields[4]) \(fields[5]) \(fields[2]) 1 \(outcome)"
        guard text.utf8.count + 1 <= 512 else { return nil }
        return text
    }

    private static func captures(_ raw: String) -> [String]? {
        guard raw.utf8.count <= 2048, raw.utf8.allSatisfy({ $0 < 128 }),
              let expression = try? NSRegularExpression(pattern: grammar),
              let match = expression.firstMatch(in: raw, range: NSRange(raw.startIndex..., in: raw)),
              match.range.length == raw.utf16.count else { return nil }
        return (1..<match.numberOfRanges).map { String(raw[Range(match.range(at: $0), in: raw)!]) }
    }
}
