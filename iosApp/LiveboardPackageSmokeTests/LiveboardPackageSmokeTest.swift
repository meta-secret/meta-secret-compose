import Foundation
import XCTest

/// Real generated Swift/UniFFI in a fresh unhosted runner sandbox; never imports the app/SwiftBridge.
@MainActor
final class LiveboardPackageSmokeTest: XCTestCase {
    private let origin = "http://127.0.0.1:3002"
    private var runId = ""
    private var cursor: Int64 = 0
    private var events: [[String: Any]] = []
    private var phase = "initialized"
    private typealias Object = [String: Any]

    func testRealGeneratedAbi() async {
        do {
            let config = try await http("/config")
            runId = try string(config, "runId")
            try require(runId.range(of: "^[A-Za-z0-9_.-]{1,64}$", options: .regularExpression) != nil)
            try require(string(config, "actor") == "A" && string(config, "vaultName") == "g4-\(runId)")
            _ = try await awaitPhase("host", "capability-negative")
            let key = try string(success(generateMasterKey()), "message")
            _ = try success(initIosWithDevice(masterKey: key, deviceName: "g4-native-A", deviceType: "iPhone"))
            let local = try object(object(success(getState()), "message"), "state")
            try require(local["local"] != nil)
            try await emit("initialized", checks: ["realPublicInit": true, "freshLocal": true])
            _ = try success(generateUserCreds(vaultName: string(config, "vaultName")))
            _ = try success(signUp())
            let first = try await awaitMember { try self.memberCount($0) == 1 }
            let actorId = try string(object(first, "liveboardState"), "currentDeviceId")
            try await emit("registered", ids: ["deviceId": actorId])
            var peers: [String: String] = [:]
            for peer in ["B", "C"] {
                let joined = try await awaitPhase(peer, "join-submitted")
                let incoming = try string(object(joined, "ids"), "deviceId")
                let state = try await awaitMember { try self.operation($0, "join", incoming) != nil }
                let request = try string(required(operation(state, "join", incoming)), "requestId")
                _ = try oldReceipt(decideJoinRequest(requestJson: decision(request)), "join", request: request)
                _ = try success(getState())
                _ = try await awaitPhase(peer, "member")
                peers[peer] = incoming
            }
            _ = try await awaitMember { try self.memberCount($0) == 3 }
            try await emit("member", checks: ["member3": true])
            let secret = "G4 package fixture value"
            let created = try oldReceipt(clusterDistributionChecked(name: string(config, "primaryName"), secret: secret), "create")
            let passId = try string(created, "secretId")
            let deleted = try oldReceipt(clusterDistributionChecked(name: string(config, "deleteName"), secret: UUID().uuidString), "create")
            let deleteId = try string(deleted, "secretId")
            _ = try await awaitMember { try self.secretPresent($0, passId) && self.secretPresent($0, deleteId) }
            try await emit("secret-created", ids: ["passId": passId])
            let recovery = try oldReceipt(recoverChecked(secretId: passId), "recover")
            let firstClaim = try string(recovery, "claimId")
            try await emit("recover1-requested", ids: ["passId": passId, "claimId": firstClaim])
            _ = try await awaitPhase("B", "recover1-approved")
            _ = try await awaitMember { try self.claim($0, firstClaim)?["clientStatus"] as? String == "accepted" }
            try require(string(object(success(showRecovered(claimId: firstClaim)), "message"), "secret") == secret)
            try await emit("recover1-shown", ids: ["claimId": firstClaim], checks: ["plaintextMatchesInRam": true])
            let secondEvent = try await awaitPhase("B", "recover2-requested")
            let second = try string(object(secondEvent, "ids"), "claimId")
            try require(second != firstClaim)
            let before = try await awaitMember { try self.claim($0, second)?["clientStatus"] as? String == "needApprove" }
            try require(claim(before, firstClaim) != nil)
            let exact = try required(claim(before, second))
            try require(string(exact, "id") == second && string(exact, "sender") == peers["B"])
            try require(string(object(object(exact, "distClaimId"), "passId"), "id") == passId)
            _ = try success(acceptRecover(claimId: second))
            try await emit("recover2-approved", ids: ["claimId": second, "firstClaimId": firstClaim], checks: ["exactSecondOpaqueId": true])
            _ = try await awaitPhase("B", "second-selected")
            _ = try await awaitPhase("B", "second-shown")
            let deleteReceipt = try producer(deleteSecretChecked(secretId: deleteId), "deleteSecret")
            try await emit("delete-produced", ids: ["passId": deleteId, "requestId": string(deleteReceipt.value, "requestId"), "referenceJson": deleteReceipt.reference])
            _ = try await awaitPhase("B", "delete-approved")
            _ = try await awaitMember { try !self.secretPresent($0, deleteId) && self.secretPresent($0, passId) }
            try sameSources(deleteReceipt, producer(reconcileProducerReceipt(referenceJson: deleteReceipt.reference), "deleteSecret"))
            let removeEvent = try await awaitPhase("B", "remove3-produced")
            let remove3 = try string(object(removeEvent, "ids"), "requestId")
            let peerB = try required(peers["B"]), peerC = try required(peers["C"])
            let incomingRemove = try await awaitMember { try self.operation($0, "removeDevice", peerB)?["requestId"] as? String == remove3 }
            try require(string(required(operation(incomingRemove, "removeDevice", peerB)), "targetDeviceId") == peerC)
            _ = try oldReceipt(decideRemoveMember(requestJson: decision(remove3)), "removeDevice", request: remove3)
            try await emit("remove3-approved", ids: ["requestId": remove3])
            _ = try await awaitMember { try self.memberCount($0) == 2 && self.secretPresent($0, passId) && self.localReady($0, passId) }
            try require(string(object(success(showLocalSecret(secretId: passId)), "message"), "secret") == secret)
            try await emit("member2", checks: ["member2": true, "localPrimaryReady": true, "plaintextMatchesInRam": true])
            _ = try await awaitPhase("host", "observation-fault-armed")
            let remove = try producer(removeMemberChecked(targetDeviceId: peerB), "removeDevice")
            try require(string(remove.value, "phase") == "unknown")
            try require(string(object(remove.value, "decisionStage"), "state") == "attempted")
            try await emit("remove2-produced", ids: ["referenceJson": remove.reference], checks: ["unknown": true,
                "materialReady": remove.value["materialStage"] as? String == "ready", "materialUnknown": remove.value["materialStage"] as? String == "unknown",
                "materialPending": remove.value["materialStage"] as? String == "pending", "canonicalApplied": remove.value["canonicalState"] as? String == "applied",
                "canonicalUnknown": remove.value["canonicalState"] as? String == "unknown", "knownOperationUnavailable": remove.value["errorCode"] as? String == "OperationUnavailable"])
            let observationBefore = try await http("/evidence")
            let uncertain = try producer(reconcileProducerReceipt(referenceJson: remove.reference), "removeDevice")
            try sameSources(remove, uncertain)
            try require(string(uncertain.value, "phase") == "unknown")
            let observationAfter = try await http("/evidence")
            try readOnlyObservation(observationBefore, observationAfter)
            try await emit("reconciled-unknown", ids: ["referenceJson": remove.reference], checks: ["sameSources": true, "noResubmission": true])
            _ = try await awaitPhase("host", "observation-fault-cleared")
            let clearBefore = try await http("/evidence")
            let applied = try producer(reconcileProducerReceipt(referenceJson: remove.reference), "removeDevice")
            try sameSources(remove, applied)
            try sameSources(uncertain, applied)
            try require(string(applied.value, "canonicalState") == "applied" && !string(applied.value, "requestId").isEmpty)
            let clearAfter = try await http("/evidence")
            try readOnlyObservation(clearBefore, clearAfter)
            try await emit("reconciled-applied", ids: ["referenceJson": remove.reference], checks: ["sameSources": true])
            _ = try await awaitMember { try self.memberCount($0) == 1 && self.secretPresent($0, passId) }
            let continued = try producer(continueLocalRemoveChecked(referenceJson: remove.reference), "removeDevice")
            try sameSources(remove, continued)
            try sameSources(applied, continued)
            try require(string(continued.value, "canonicalState") == "applied" && string(continued.value, "materialStage") == "ready")
            try await emit("continued", ids: ["referenceJson": remove.reference], checks: ["sameSources": true])
            let negativeBefore = try await http("/evidence")
            try fixedFailure(reconcileProducerReceipt(referenceJson: "{}"), "InvalidProducerReference")
            try fixedFailure(decideJoinRequest(requestJson: "{}"), "InvalidDecisionInput")
            let negativeAfter = try await http("/evidence")
            try require(equal(negativeBefore, negativeAfter))
            try await emit("complete", checks: ["generatedNativeAbi": true, "fixedNativeFailureEnvelope": true, "noLegacyFallback": true])
        } catch {
            if !runId.isEmpty { try? await emit(phase, failed: true) }
            XCTFail("G4 native package smoke failed at bounded phase \(phase); inspect redacted host evidence")
        }
    }

    private struct Receipt {
        let value: Object
        let reference: String
        let source: String
        let decision: String?
    }

    private func require(_ condition: @autoclosure () throws -> Bool) throws {
        if try !condition() { throw NSError(domain: "G4PackageSmoke", code: 1) }
    }
    private func required<T>(_ value: T?) throws -> T {
        guard let value else { throw NSError(domain: "G4PackageSmoke", code: 2) }
        return value
    }
    private func object(_ value: Object, _ key: String) throws -> Object { try required(value[key] as? Object) }
    private func string(_ value: Object, _ key: String) throws -> String { try required(value[key] as? String) }
    private func decode(_ raw: String) throws -> Object { try required(JSONSerialization.jsonObject(with: Data(raw.utf8)) as? Object) }
    private func json(_ value: Any) throws -> String { String(decoding: try JSONSerialization.data(withJSONObject: value, options: [.sortedKeys, .fragmentsAllowed]), as: UTF8.self) }
    private func equal(_ left: Any, _ right: Any) throws -> Bool { try json(left) == json(right) }
    private func success(_ raw: String) throws -> Object {
        let outer = try decode(raw)
        try require(outer["success"] as? Bool == true)
        return outer
    }
    private func oldReceipt(_ raw: String, _ kind: String, request: String? = nil) throws -> Object {
        let outer = try success(raw)
        try require(Set(outer.keys) == ["success", "message"])
        let receipt = try object(outer, "message")
        try require(string(receipt, "kind") == kind && ["queued", "unknown"].contains(string(receipt, "phase")))
        try require(Set(receipt.keys).isSubset(of: ["kind", "secretId", "claimId", "requestId", "phase", "errorCode"]))
        if let request { try require(string(receipt, "requestId") == request) }
        return receipt
    }
    private func producer(_ raw: String, _ kind: String) throws -> Receipt {
        let outer = try success(raw)
        try require(Set(outer.keys) == ["success", "message"])
        let receipt = try object(outer, "message")
        try require(Set(receipt.keys).isSubset(of: ["version", "kind", "phase", "secretId", "targetDeviceId", "requestId", "requestStage", "decisionStage", "materialStage", "canonicalState", "readiness", "errorCode"]))
        try require(receipt["version"] as? Int == 1 && string(receipt, "kind") == kind)
        try require(["queued", "unknown"].contains(string(receipt, "phase")))
        let exact = try rawField(raw, "message")
        let source = try rawField(rawField(exact, "requestStage"), "source")
        var decisionSource: String?
        if let stage = receipt["decisionStage"] as? Object, stage["state"] as? String == "attempted" {
            decisionSource = try rawField(rawField(exact, "decisionStage"), "source")
        }
        let uuid = receipt["requestId"] == nil ? "" : ",\"requestId\":\(try rawField(exact, "requestId"))"
        let decisionPart = decisionSource.map { ",\"decisionStage\":\($0)" } ?? ""
        let reference = "{\"version\":1,\"kind\":\(try json(kind))\(uuid),\"requestStage\":\(source)\(decisionPart)}"
        return Receipt(value: receipt, reference: reference, source: source, decision: decisionSource)
    }
    private func sameSources(_ original: Receipt, _ observed: Receipt) throws {
        try require(original.source == observed.source && original.decision == observed.decision)
        if let known = original.value["requestId"] as? String { try require(observed.value["requestId"] as? String == known) }
        try require(original.value["targetDeviceId"] as? String == observed.value["targetDeviceId"] as? String)
    }
    private func readOnlyObservation(_ before: Object, _ after: Object) throws {
        let old = try required(before["observations"] as? Int), new = try required(after["observations"] as? Int)
        try require(new == old + 1 && equal(required(before["writes"]), required(after["writes"])))
        try require(equal(required(before["ordinaryReads"]), required(after["ordinaryReads"])))
        try require(equal(required(before["business"]), required(after["business"])))
    }
    private func fixedFailure(_ raw: String, _ code: String) throws {
        let value = try decode(raw)
        try require(Set(value.keys) == ["success", "errorCode"] && value["success"] as? Bool == false && string(value, "errorCode") == code)
    }
    private func member() throws -> Object { try object(object(object(success(getState()), "message"), "state"), "vault").flatMember() }
    private func memberCount(_ member: Object) throws -> Int { try object(object(member, "member"), "vault").flatUsers().values.filter { ($0 as? Object)?["member"] != nil }.count }
    private func secretPresent(_ member: Object, _ id: String) throws -> Bool { try (object(member, "liveboardState")["secrets"] as? [Object] ?? []).contains { $0["secretId"] as? String == id } }
    private func localReady(_ member: Object, _ id: String) throws -> Bool {
        try (object(member, "liveboardState")["secrets"] as? [Object] ?? []).contains {
            $0["secretId"] as? String == id && $0["primaryAction"] as? String == "showLocal" && $0["readiness"] as? String == "ready"
        }
    }
    private func claim(_ member: Object, _ id: String) throws -> Object? { try object(object(member, "ssClaims"), "claims")[id] as? Object }
    private func operation(_ member: Object, _ kind: String, _ requester: String) throws -> Object? {
        let list = try required(object(member, "liveboardState")["operations"] as? [Object]).filter { $0["kind"] as? String == kind && $0["requesterId"] as? String == requester && $0["canApprove"] as? Bool == true }
        try require(list.count <= 1)
        return list.first
    }
    private func decision(_ id: String) throws -> String { try json(["requestId": id, "accepted": true]) }
    private func awaitMember(_ ready: (Object) throws -> Bool) async throws -> Object {
        let deadline = ProcessInfo.processInfo.systemUptime + 120
        while ProcessInfo.processInfo.systemUptime < deadline {
            let state = try member()
            if try ready(state) { return state }
            try await pull()
        }
        throw NSError(domain: "G4PackageSmoke", code: 3)
    }
    private func awaitPhase(_ actor: String, _ wanted: String) async throws -> Object {
        phase = wanted
        let deadline = ProcessInfo.processInfo.systemUptime + 120
        while ProcessInfo.processInfo.systemUptime < deadline {
            if let event = events.first(where: { $0["actor"] as? String == actor && $0["phase"] as? String == wanted }) { return event }
            try await pull()
        }
        throw NSError(domain: "G4PackageSmoke", code: 4)
    }
    private func pull() async throws {
        let response = try await http("/phase?runId=\(runId)&after=\(cursor)")
        try require(string(response, "runId") == runId)
        for event in try required(response["events"] as? [Object]) {
            let next = try required(event["cursor"] as? Int64)
            try require(next > cursor && string(event, "status") == "ready")
            cursor = next
            events.append(event)
        }
    }
    private func emit(_ name: String, ids: [String: String] = [:], checks: [String: Bool] = [:], failed: Bool = false) async throws {
        phase = name
        _ = try await http("/phase", body: ["runId": runId, "actor": "A", "phase": name, "status": failed ? "failed" : "ready", "ids": ids, "checks": checks])
    }
    private func http(_ path: String, body: Object? = nil) async throws -> Object {
        var request = URLRequest(url: try required(URL(string: origin + path)))
        request.timeoutInterval = 25
        if let body {
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        try require((response as? HTTPURLResponse)?.statusCode == 200 && data.count <= 1_048_576)
        return try required(JSONSerialization.jsonObject(with: data) as? Object)
    }
    /// Extract untouched Core source tokens, including full u64 integers, into reference JSON.
    private func rawField(_ json: String, _ key: String) throws -> String {
        let text = Array(json); var i = 0
        func white() { while i < text.count && text[i].isWhitespace { i += 1 } }
        func value() -> String {
            white(); let start = i; var depth = 0, quoted = false, escaped = false
            while i < text.count {
                let ch = text[i]
                if quoted {
                    if escaped { escaped = false } else if ch == "\\" { escaped = true } else if ch == "\"" { quoted = false }
                } else if ch == "\"" { quoted = true }
                else if ch == "{" || ch == "[" { depth += 1 }
                else if ch == "}" || ch == "]" { if depth == 0 { break }; depth -= 1 }
                else if ch == "," && depth == 0 { break }
                i += 1
            }
            return String(text[start..<i]).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        white(); try require(i < text.count && text[i] == "{"); i += 1
        while i < text.count {
            white(); if text[i] == "}" { break }
            let start = i; i += 1
            while i < text.count && text[i] != "\"" { if text[i] == "\\" { i += 1 }; i += 1 }
            i += 1
            let field = try required(decode("{\(String(text[start..<i])):0}").keys.first)
            white(); try require(text[i] == ":"); i += 1
            let raw = value()
            if field == key { return raw }
            white(); if text[i] == "," { i += 1 } else { break }
        }
        throw NSError(domain: "G4PackageSmoke", code: 5)
    }
}

private extension Dictionary where Key == String, Value == Any {
    func flatMember() throws -> [String: Any] {
        guard let value = self["member"] as? [String: Any] else { throw NSError(domain: "G4PackageSmoke", code: 6) }
        return value
    }
    func flatUsers() throws -> [String: Any] {
        guard let value = self["users"] as? [String: Any] else { throw NSError(domain: "G4PackageSmoke", code: 7) }
        return value
    }
}
