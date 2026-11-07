import Foundation

// TEST-only closed source catalog. Test numbers in the gaps are never admitted.
struct TestRecoveryProfile: Sendable {
    let number: Int
    let mode: String
    private let cycles: ClosedRange<Int>
    private let iosSenders: [Int]
    var tag: String { "test\(number):\(mode)" }
    var future: Bool { Self.futureNumbers.contains(number) }

    private init(_ number: Int, _ mode: String, _ cycles: ClosedRange<Int>, _ iosSenders: [Int]) {
        self.number = number; self.mode = mode; self.cycles = cycles; self.iosSenders = iosSenders
    }
    private static let futureNumbers = [26,27,28,29,30,31,32,33,34,35,36,46,47,48,49]
    private static let catalog: [Self] = [
        .init(24,"ios-multi-secret-lifecycle",13...39,[]),
        .init(25,"web-secondary-creator-lifecycle",13...39,[]),
        .init(26,"android-secondary-creator-lifecycle",13...39,[16,17,18,25,26,27,34,35,36]),
        .init(27,"ios-secondary-creator-lifecycle",13...39,[13,14,15,22,23,24,31,32,33]),
        .init(28,"web-secondary-creator-lifecycle",1...27,[7,8,9,16,17,18,25,26,27]),
        .init(29,"android-distributed-creator-lifecycle",1...27,[4,5,6,13,14,15,22,23,24]),
        .init(30,"ios-distributed-creator-lifecycle",1...27,[1,2,3,10,11,12,19,20,21]),
        .init(31,"web-secondary-creator-lifecycle",1...27,[7,8,9,16,17,18,25,26,27]),
        .init(32,"android-owner-creator-lifecycle",1...27,[4,5,6,13,14,15,22,23,24]),
        .init(33,"ios-two-secret-owner-lifecycle",1...27,[1,2,3,10,11,12,19,20,21]),
        .init(34,"web-secondary-creator-lifecycle",1...18,[5,6,11,12,17,18]),
        .init(35,"android-secondary-creator-lifecycle",1...27,[4,5,6,13,14,15,22,23,24]),
        .init(36,"ios-distributed-creator-lifecycle",1...27,[1,2,3,10,11,12,19,20,21]),
        .init(46,"web-distributed-creator-removal-rejoin-recovery-lifecycle",7...33,[25,26,27,28,29,30,31,32,33]),
        .init(47,"web-distributed-creator-declined-removal-recovery-lifecycle",1...27,[7,8,9,16,17,18,25,26,27]),
        .init(48,"android-distributed-creator-declined-removal-recovery-lifecycle",1...27,[7,8,9,16,17,18,25,26,27]),
        .init(49,"ios-distributed-creator-declined-removal-recovery-lifecycle",1...27,[7,8,9,16,17,18,25,26,27]),
    ]
    static func selected(_ tag: String?) -> Self? {
        guard let tag else { return nil }
        return catalog.first { $0.tag == tag }
    }
    static func future(_ tag: String?) -> Self? {
        guard let profile = selected(tag), profile.future else { return nil }
        return profile
    }
    func permitsIosSender(_ cycle: Int) -> Bool { future && iosSenders.contains(cycle) }

    // Identical field order/capture positions to the preserved literal24/25
    // grammar. Full source block/role ownership is independently bound by the
    // real Core test writer before both raw and expected private strings.
    static func readFuture(_ raw: String) -> (fields: [String], selected: String)? {
        for profile in catalog where profile.future {
            let pattern = #"\A\{"version":1,"runId":"([A-Za-z0-9][A-Za-z0-9_.-]{0,79})","testNumber":\#(profile.number),"attempt":([1-9][0-9]{0,8}),"blockId":"([A-Za-z0-9][A-Za-z0-9_.-]{0,79})","cycle":([1-9][0-9]{0,2}),"logicalStep":1,"mode":"\#(profile.mode)","contextId":"([0-9a-f]{32})","invocationId":"([0-9a-f]{32})","platform":"(android|ios|web)","role":"(sender|receiver)"\}\z"#
            guard let fields = captures(raw, pattern), let cycle = Int(fields[3]),
                  profile.cycles.contains(cycle) else { continue }
            return (fields, profile.tag)
        }
        return nil
    }
    private static func captures(_ raw: String, _ pattern: String) -> [String]? {
        guard raw.utf8.count <= 2048, raw.utf8.allSatisfy({ $0 < 128 }),
              let expression = try? NSRegularExpression(pattern: pattern),
              let match = expression.firstMatch(in: raw, range: NSRange(raw.startIndex..., in: raw)),
              match.range.length == raw.utf16.count else { return nil }
        return (1..<match.numberOfRanges).map { String(raw[Range(match.range(at: $0), in: raw)!]) }
    }
}
