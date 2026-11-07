import XCTest

@MainActor
extension XCUIApplication {
    /// One selector-anchored gesture experiment; true means dispatch only, never business PASS.
    func lbDispatchPrimaryGesture(_ identifier: String, target: XCUIElement, beforeInput: (() -> Bool)? = nil) -> Bool {
        func refuse() -> Bool {
            XCTFail("Primary gesture admission failed; no action dispatched")
            return false
        }
        func finite(_ value: CGRect) -> Bool {
            !value.isNull && !value.isInfinite && value.width > 0 && value.height > 0 &&
                [value.minX, value.minY, value.maxX, value.maxY, value.width, value.height].allSatisfy { $0.isFinite }
        }
        guard let selected = lbSelection.secretId, !selected.isEmpty,
              identifier == "lb-primary-" + selected else { return refuse() }
        let viewport: (root: any XCUIElementSnapshot, bounds: CGRect)
        do { viewport = try lbSecretsViewportSnapshot() } catch { return refuse() }
        let fixed = [identifier, "lb-secrets", "lb-secrets-list"]
        var nodes: [String: (frame: CGRect, window: CGRect)] = [:]
        var stack: [(any XCUIElementSnapshot, CGRect?)] = [(viewport.root, nil)]
        var visited = 0
        while let (node, inheritedWindow) = stack.popLast() {
            visited += 1
            guard visited <= 8192 else { return refuse() }
            let window: CGRect? = node.elementType == .window ? node.frame : inheritedWindow
            if fixed.contains(node.identifier) {
                guard nodes[node.identifier] == nil, let window,
                      finite(window), finite(node.frame) else { return refuse() }
                nodes[node.identifier] = (node.frame, window)
            }
            stack.append(contentsOf: node.children.map { ($0, window) })
        }
        guard let primary = nodes[identifier], let sheet = nodes["lb-secrets"],
              let list = nodes["lb-secrets-list"], primary.window == sheet.window,
              sheet.window == list.window else { return refuse() }
        let query = descendants(matching: .any).matching(identifier: identifier)
        guard query.count == 1, target.exists, target.identifier == identifier,
              target.elementType == .button, target.isEnabled,
              lbSelection.secretId == selected else { return refuse() }
        let current = target.frame
        let contexts = [viewport.root.frame, sheet.frame, list.frame, primary.window, viewport.bounds]
        guard finite(current), current == primary.frame,
              contexts.allSatisfy({ finite($0) && $0.contains(current) }) else { return refuse() }
        // Context checks and this dynamic element-relative coordinate are sequential, not atomic.
        // Rectangles do not establish the eventual UIKit hit receiver or unobscuredness.
        let coordinate = target.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        if let beforeInput, !beforeInput() { return refuse() }
        coordinate.tap()
        return true
    }
}
