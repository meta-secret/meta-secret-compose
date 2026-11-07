import XCTest
import Foundation

final class RecoveryShowPhaseTests: XCTestCase {
    private struct Input {
        let number: Int
        let cycle: Int
        let phase: String
        let scope: String
        init(_ number: Int, _ mode: String, _ cycle: Int) {
            self.number = number; self.cycle = cycle
            phase = #"{"version":1,"runId":"SYNTHETIC_NATIVE_SHOW","testNumber":\#(number),"attempt":1,"blockId":"source-bound-block","cycle":\#(cycle),"logicalStep":1,"mode":"\#(mode)","contextId":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","invocationId":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","platform":"ios","role":"sender"}"#
            scope = #"{"testNumber":\#(number),"role":"sender","nonce":"cccccccccccccccccccccccccccccccc","cycle":\#(cycle),"step":1,"runId":"SYNTHETIC_NATIVE_SHOW","contextId":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","invocationId":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}"#
        }
    }
    private var examples: [(Input, String)] { [
        (Input(26,"android-secondary-creator-lifecycle",16),"test26:android-secondary-creator-lifecycle"),
        (Input(34,"web-secondary-creator-lifecycle",5),"test34:web-secondary-creator-lifecycle"),
        (Input(46,"web-distributed-creator-removal-rejoin-recovery-lifecycle",25),"test46:web-distributed-creator-removal-rejoin-recovery-lifecycle"),
    ] }
    private func entry(_ input: Input, _ selected: String) -> RecoveryShowEntry {
        RecoveryShowEntry(raw: input.scope,
            phase: Test24PhaseEntry(raw: input.phase, required: true, selectedProfile: selected, expected: input.phase),
            current: RecoveryShowCurrent(role: "sender", cycle: String(input.cycle), step: "1", hasLegacy22: false, hasLiteral25: false))
    }
    private func replacing(_ entry: RecoveryShowEntry, raw: String?) -> RecoveryShowEntry {
        RecoveryShowEntry(raw: raw, phase: entry.phase, current: entry.current)
    }

    func testOwnFutureSourceBoundSenderBothProcedureOutcomes() throws {
        for (input, selected) in examples {
            let current = entry(input, selected), accepted = try XCTUnwrap(RecoveryShowPhaseContext.readEntry(current))
            for outcome in ["INPUT_RETURNED", "ALREADY_VISIBLE"] {
                XCTAssertEqual(accepted.line(outcome, current: current, failureCount: 0),
                    "E2E: IOS_RECOVERY_SHOW_PREP_V1 \(input.number) cccccccccccccccccccccccccccccccc aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb \(input.cycle) 1 \(outcome)")
            }
        }
    }

    func testMalformedCanonicalScopeAfterOwnPositive() throws {
        let (input, selected) = examples[0], current = entry(input, selected)
        _ = try XCTUnwrap(RecoveryShowPhaseContext.readEntry(current))
        let prefix = #""testNumber":26,"role":"sender""#
        let invalid = [input.scope+"\n", input.scope+String(repeating:" ",count:2049),
            input.scope.replacingOccurrences(of:"\"testNumber\":26,",with:"\"testNumber\":26,\"testNumber\":26,"),
            input.scope.replacingOccurrences(of:prefix,with:#""role":"sender","testNumber":26"#),
            input.scope.replacingOccurrences(of:"\"testNumber\":26",with:"\"testNumber\":26.0"),
            input.scope.replacingOccurrences(of:"SYNTHETIC_NATIVE_SHOW",with:"SYNTHÉTIC_NATIVE_SHOW"),
            String(input.scope.dropLast())+",\"extra\":1}"]
        for raw in invalid { XCTAssertThrowsError(try RecoveryShowPhaseContext.readEntry(replacing(current,raw:raw))) }
        XCTAssertThrowsError(try RecoveryShowPhaseContext.readEntry(replacing(current,raw:nil)))
    }

    func testIndependentRequiredSelectedExpectedAndFieldConflicts() throws {
        let (input, selected) = examples[0], current = entry(input,selected)
        _ = try XCTUnwrap(RecoveryShowPhaseContext.readEntry(current))
        let invalidPhase = [
            Test24PhaseEntry(raw:input.phase,required:true,selectedProfile:selected,expected:nil),
            Test24PhaseEntry(raw:input.phase,required:false,selectedProfile:selected,expected:input.phase),
            Test24PhaseEntry(raw:input.phase,required:true,selectedProfile:"test25:web-secondary-creator-lifecycle",expected:input.phase),
            Test24PhaseEntry(raw:input.phase+"\n",required:true,selectedProfile:selected,expected:input.phase+"\n"),
        ]
        for phase in invalidPhase {
            XCTAssertThrowsError(try RecoveryShowPhaseContext.readEntry(RecoveryShowEntry(raw:input.scope,phase:phase,current:current.current)))
        }
        for flags in [(true,false),(false,true),(true,true)] {
            let changed = RecoveryShowCurrent(role:"sender",cycle:"16",step:"1",hasLegacy22:flags.0,hasLiteral25:flags.1)
            XCTAssertThrowsError(try RecoveryShowPhaseContext.readEntry(RecoveryShowEntry(raw:input.scope,phase:current.phase,current:changed)))
        }
    }

    func testEncodingRechecksExactEntryAndFailureCount() throws {
        let (input, selected) = examples[0], current = entry(input,selected)
        let accepted = try XCTUnwrap(RecoveryShowPhaseContext.readEntry(current))
        XCTAssertNotNil(accepted.line("INPUT_RETURNED",current:current,failureCount:0))
        XCTAssertNil(accepted.line("UNKNOWN",current:current,failureCount:0))
        XCTAssertNil(accepted.line("INPUT_RETURNED",current:current,failureCount:nil))
        XCTAssertNil(accepted.line("INPUT_RETURNED",current:current,failureCount:1))
        let changedPhase = input.phase.replacingOccurrences(of:"\"attempt\":1",with:"\"attempt\":2")
        let changed = RecoveryShowEntry(raw:input.scope,
            phase:Test24PhaseEntry(raw:changedPhase,required:true,selectedProfile:selected,expected:changedPhase),current:current.current)
        XCTAssertNotNil(try RecoveryShowPhaseContext.readEntry(changed))
        XCTAssertNil(accepted.line("INPUT_RETURNED",current:changed,failureCount:0))
        let changedNonce = replacing(current,raw:input.scope.replacingOccurrences(of:String(repeating:"c",count:32),with:String(repeating:"d",count:32)))
        XCTAssertNotNil(try RecoveryShowPhaseContext.readEntry(changedNonce))
        XCTAssertNil(accepted.line("INPUT_RETURNED",current:changedNonce,failureCount:0))
    }

    func testCancelledTaskCannotReadOrEmit() async throws {
        let (input, selected) = examples[0], current = entry(input,selected)
        let accepted = try XCTUnwrap(RecoveryShowPhaseContext.readEntry(current))
        let child = Task {
            withUnsafeCurrentTask { $0?.cancel() }
            XCTAssertThrowsError(try RecoveryShowPhaseContext.readEntry(current))
            XCTAssertNil(accepted.line("INPUT_RETURNED",current:current,failureCount:0))
        }
        try await child.value
    }
}
