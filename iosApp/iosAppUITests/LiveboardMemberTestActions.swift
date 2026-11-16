import XCTest

@MainActor
extension XCUIApplication {
    var lbConnectedSlots: XCUIElementQuery {
        descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@ AND NOT (identifier IN %@)",
            "lb-device-", ["lb-device-0", "lb-device-1", "lb-device-2"]))
    }

    func lbExactVisibleText(_ text: String, in container: XCUIElement) -> Bool {
        guard lbVisible(container) else { return false }
        let candidates = [container] + container.descendants(matching: .staticText).allElementsBoundByIndex
        return candidates.contains { node in
            guard lbVisible(node), container.frame.intersects(node.frame) else { return false }
            return node.label.split(separator: "\n").map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.contains(text)
        }
    }

    func lbDeviceId(name: String, status: String) -> String? {
        let rows = lbConnectedSlots.allElementsBoundByIndex.filter { lbExactVisibleText(name, in: $0) }
        XCTAssertLessThanOrEqual(rows.count, 1, "Duplicate exact Device names cannot establish removal/absence")
        guard rows.count == 1 else { return nil }
        let row = rows[0]
        let current = lbExactVisibleText("THIS DEVICE", in: row) || lbExactVisibleText("ЭТО УСТРОЙСТВО", in: row)
        if status == "Current" && !current { return nil }
        if status == "Member" && current { return nil }
        // A Core-backed member slot remains a member while its live operation status changes.
        return String(row.identifier.dropFirst("lb-device-".count))
    }

    /// Eligibility is checked using the real selected preview, never inferred from member count.
    func lbCanRemove(_ deviceId: String) -> Bool {
        let slot = descendants(matching: .any)["lb-device-\(deviceId)"]
        guard lbVisible(slot) && slot.isHittable else { return false }
        slot.tap()
        let control = descendants(matching: .any)["lb-remove-\(deviceId)"]
        let appeared = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in control.exists }, object: nil)
        _ = XCTWaiter.wait(for: [appeared], timeout: 2)
        XCTAssertLessThanOrEqual(descendants(matching: .any).matching(identifier: "lb-remove-\(deviceId)").count, 1,
                                 "Duplicate Remove controls cannot prove a prohibited action")
        let possible = lbVisible(control) && control.isEnabled
        let current = descendants(matching: .any)["lb-current"]
        if current.exists && current.isEnabled && current.isHittable { current.tap() }
        return possible
    }

    func lbRemoveDevice(_ deviceId: String, name: String) {
        lbHome()
        let slot = lbRequire("lb-device-\(deviceId)")
        XCTAssertTrue(lbExactVisibleText(name, in: slot), "Removal preview must identify the exact selected Device")
        slot.tap()
        let remove = lbRequire("lb-remove-\(deviceId)")
        XCTAssertTrue(remove.isEnabled, "The selected Core-projected Device cannot be removed")
        lbTap("lb-remove-\(deviceId)")
        _ = lbConfirmSystemIfPresent()
        lbWait({
            !self.descendants(matching: .any)["lb-device-\(deviceId)"].exists ||
            (remove.exists && !remove.isEnabled && !self.descendants(matching: .any)["lb-current"].isEnabled)
        }, timeout: 180, message: "Removal did not become pending or canonically remove the exact Device")
    }

    func lbAssertRemovalSubject(_ name: String) {
        guard let incoming = lbReadIncoming(), incoming.kind == "RemoveDevice" else { XCTFail("Expected an actual incoming Device removal"); return }
        XCTAssertTrue(incoming.subject == name, "Incoming Device removal target is not exact")
        lbSelection.incoming = incoming
    }
}
