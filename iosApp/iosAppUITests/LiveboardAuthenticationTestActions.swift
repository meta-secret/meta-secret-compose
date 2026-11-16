import XCTest

@MainActor
extension XCUIApplication {
    @discardableResult
    func lbConfirmSystemIfPresent() -> Bool {
#if targetEnvironment(simulator)
        let environment = ProcessInfo.processInfo.environment
        let profile = environment["E2E_AUTH_PROFILE"]
        if launchEnvironment["METASECRET_UI_TEST_MODE"] == "true",
           lbSelection.devicePasscode == nil,
           environment["E2E_IOS_DEVICE_PASSCODE"] == nil,
           profile == nil || profile == "controlled-business" {
            return false
        }
#endif
        let system = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let field = system.secureTextFields.firstMatch
        if field.waitForExistence(timeout: 2) {
            guard let passcode = lbSelection.devicePasscode else {
                XCTFail("Actual OS PIN controls require a declared devicePasscode; no default is guessed"); return false
            }
            field.tap(); field.typeText(passcode); return true
        }
        let digit = system.buttons["1"]
        if digit.waitForExistence(timeout: 1) {
            guard let passcode = lbSelection.devicePasscode else {
                XCTFail("Actual OS PIN controls require a declared devicePasscode; no default is guessed"); return false
            }
            for character in passcode { system.buttons[String(character)].tap() }
            return true
        }
        return false
    }

    func lbCancelSystemAuthentication() {
        XCTAssertNil(launchEnvironment["METASECRET_UI_TEST_MODE"], "Real system Cancel requires an entire non-bypassed actor launch")
        let system = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let cancel = system.buttons.matching(NSPredicate(format: "label IN %@", ["Cancel", "Отмена"])).firstMatch
        lbWait({ cancel.exists && cancel.isHittable }, timeout: 30, message: "Actual SpringBoard authentication Cancel did not appear")
        XCTAssertEqual(system.state, .runningForeground, "Cancel target is not owned by the system authentication UI")
        cancel.tap()
        lbWait({ !cancel.exists }, timeout: 30)
    }
}
