import XCTest

private enum LiveboardObservationStatus: String { case captured, inconclusive }
private enum LiveboardObservationReason: String {
    case none, count, absent, snapshotUnavailable, noChildren, unknown, nodeLimit, depthLimit, outputLimit, serialization
}

@MainActor
extension XCUIApplication {
    /// One sequential, test-owned observation. It never admits, retries or performs an action.
    func lbObserveActionabilityTransition(_ identifier: String, target: XCUIElement) {
        let prefix = "lb-primary-"
        guard identifier.hasPrefix(prefix), identifier != prefix else { return }
        let selection = lbSelection
        guard !selection.actionabilityObservationAttempted else { return }
        selection.actionabilityObservationAttempted = true
        let primaryHittable = target.isHittable
        guard !primaryHittable else { return }
        let suffix = String(identifier.dropFirst(prefix.count))
        let roles = [("primary", identifier), ("delete", "lb-delete-" + suffix), ("back", "lb-secrets-back"),
                     ("shade", "lb-overlay-shade"), ("sheet", "lb-secrets"), ("list", "lb-secrets-list")]
        var status = LiveboardObservationStatus.captured
        var reason = LiveboardObservationReason.none
        func incomplete(_ value: LiveboardObservationReason) {
            status = .inconclusive
            if reason == .none { reason = value }
        }
        func rectangle(_ value: CGRect) -> Any {
            let numbers = [value.minX, value.minY, value.width, value.height]
            guard !value.isNull, !value.isInfinite, numbers.allSatisfy({ $0.isFinite }) else {
                incomplete(.unknown); return NSNull()
            }
            return numbers.map { Double($0) }
        }
        let appFrame = rectangle(frame)
        var rows: [[String: Any]] = []
        var sheet: XCUIElement?
        for (role, exactIdentifier) in roles {
            let query = descendants(matching: .any).matching(identifier: exactIdentifier)
            let count = query.count
            var row: [String: Any] = ["role": role, "count": count, "exists": NSNull(), "enabled": NSNull(),
                                      "hittable": NSNull(), "frame": NSNull(), "type": NSNull()]
            if count == 1 {
                let element = role == "primary" ? target : query.element(boundBy: 0)
                let exists = element.exists
                row["exists"] = exists
                if exists {
                    row["enabled"] = element.isEnabled
                    row["hittable"] = role == "primary" ? primaryHittable : element.isHittable
                    row["frame"] = rectangle(element.frame)
                    row["type"] = element.elementType.rawValue
                    if role == "sheet" { sheet = element }
                } else { incomplete(.absent) }
            } else { incomplete(.count) }
            rows.append(row)
        }
        var nodes: [[String: Any]] = []
        var visited = 0
        if let sheet, let root = try? sheet.snapshot() {
            var stack: [(any XCUIElementSnapshot, [Int])] = [(root, [])]
            while let (node, path) = stack.popLast() {
                visited += 1
                let nodeIdentifier = node.identifier
                if let role = roles.first(where: { $0.1 == nodeIdentifier })?.0 {
                    nodes.append(["role": role, "path": path, "type": node.elementType.rawValue,
                                  "frame": rectangle(node.frame)])
                } else { incomplete(.unknown) }
                let children = node.children
                if path.isEmpty && children.isEmpty { incomplete(.noChildren) }
                if path.count >= 8 && !children.isEmpty { incomplete(.depthLimit); continue }
                let remaining = max(0, 256 - visited - stack.count)
                if children.count > remaining { incomplete(.nodeLimit) }
                for (index, child) in children.prefix(remaining).enumerated().reversed() {
                    stack.append((child, path + [index]))
                }
            }
        } else { incomplete(.snapshotUnavailable) }
        let payload: [String: Any] = ["version": 1, "status": status.rawValue, "reason": reason.rawValue,
                                     "scope": "sequential-known-roles-not-hit-owner", "appFrame": appFrame,
                                     "roles": rows, "sheetNodes": nodes, "visited": visited]
        func attach(_ data: Data) {
            XCTContext.runActivity(named: "Liveboard primary tap observation") { activity in
                let attachment = XCTAttachment(data: data, uniformTypeIdentifier: "public.json")
                attachment.name = "liveboard-primary-tap-observation.json"
                attachment.lifetime = .keepAlways
                activity.add(attachment)
            }
        }
        guard let encoded = try? JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys]) else {
            attach(Data(#"{"version":1,"status":"inconclusive","reason":"serialization"}"#.utf8)); return
        }
        if encoded.count <= 4096 { attach(encoded) }
        else { attach(Data(#"{"version":1,"status":"inconclusive","reason":"outputLimit"}"#.utf8)) }
    }
}
