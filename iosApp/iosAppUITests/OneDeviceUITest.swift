import XCTest

final class OneDeviceUITest: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    private func assertExists(_ element: XCUIElement, timeout: TimeInterval, file: StaticString = #filePath, line: UInt = #line) {
        if !element.waitForExistence(timeout: timeout) {
            XCTFail("Element not found. Accessibility hierarchy:\n\(XCUIApplication().debugDescription)", file: file, line: line)
        }
    }

    private func dismissKeyboard(in app: XCUIApplication) {
        let doneButton = app.keyboards.buttons["Done"]
        if doneButton.exists {
            doneButton.tap()
            return
        }

        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.12)).tap()
    }

    func testOneDeviceSignupFlow() throws {
        let app = XCUIApplication()
        app.launchEnvironment["METASECRET_UI_TEST_MODE"] = "true"
        app.launch()

        let runNumber = Int.random(in: 1000..<9999)
        let email = "ios_\(runNumber)@test.com"

        // STEP 1: Skip Onboarding
        print("TEST STEP 1: Click Skip on Onboarding")
        sleep(3)
        let skipButton = app.buttons["onboarding-skip"]
        XCTAssertTrue(skipButton.waitForExistence(timeout: 10))
        skipButton.tap()
        sleep(1)

        // STEP 2: Click "Enter email manually"
        print("TEST STEP 2: Click Enter email manually")
        let emailManualButton = app.buttons["signin-email-manual"]
        XCTAssertTrue(emailManualButton.waitForExistence(timeout: 10))
        emailManualButton.tap()
        sleep(1)

        // STEP 3: Enter email and continue
        print("TEST STEP 3: Enter email and click Continue")
        let emailField = app.descendants(matching: .any)["email-input"]
        assertExists(emailField, timeout: 10)
        emailField.tap()
        emailField.typeText(email)
        dismissKeyboard(in: app)
        sleep(1)

        let continueButton = app.buttons["manual-signin-continue"]
        assertExists(continueButton, timeout: 10)
        continueButton.tap()
        sleep(1)

        // STEP 4: Email Confirmation - Click Continue
        print("TEST STEP 4: Click Continue on confirmation")
        let confirmButton = app.buttons["email-confirmation-continue"]
        assertExists(confirmButton, timeout: 10)
        confirmButton.tap()
        sleep(2)

        // STEP 5: Wait for Secrets screen
        print("TEST STEP 5: Verify Secrets screen loaded")
        let fabButton = app.buttons["add-secret-fab"]
        XCTAssertTrue(fabButton.waitForExistence(timeout: 30))

        // STEP 6: Tap Add Secret FAB
        print("TEST STEP 6: Tap Add Secret FAB")
        fabButton.tap()
        sleep(3)

        // STEP 7: Wait for Add Secret dialog
        print("TEST STEP 7: Wait for Add Secret dialog")
        let secretNameInput = app.descendants(matching: .any)["secret-name-input"]
        assertExists(secretNameInput, timeout: 45)
        print("TEST: secret-name-input found!")

        // STEP 8: Enter secret details
        print("TEST STEP 8: Enter secret name and value")
        secretNameInput.tap()
        secretNameInput.typeText("MySecret\(runNumber)")
        sleep(1)

        let secretValueInput = app.descendants(matching: .any)["secret-value-input"]
        assertExists(secretValueInput, timeout: 10)
        secretValueInput.tap()
        secretValueInput.typeText("SecretValue\(runNumber)")
        dismissKeyboard(in: app)
        sleep(1)

        // STEP 9: Submit secret
        print("TEST STEP 9: Submit secret")
        let submitButton = app.buttons["add-secret-submit"]
        submitButton.tap()

        // STEP 10: Wait for success toast
        print("TEST STEP 10: Wait for success toast")
        let toastElement = app.staticTexts["A secret successfully added"]
        XCTAssertTrue(toastElement.waitForExistence(timeout: 45))
        print("TEST: Toast appeared!")

        // STEP 11: Wait for secret in list
        print("TEST STEP 11: Wait for Secrets screen with new secret")
        let secretInList = app.staticTexts["MySecret\(runNumber)"]
        XCTAssertTrue(secretInList.waitForExistence(timeout: 30))
        print("TEST: Secret in list!")

        sleep(3)

        // STEP 12: Click on secret
        print("TEST STEP 12: Click on secret to open ShowSecret dialog")
        secretInList.tap()

        // STEP 13: Wait for Show button
        print("TEST STEP 13: Wait for ShowSecret dialog with Show button")
        let showButton = app.buttons["Show"]
        XCTAssertTrue(showButton.waitForExistence(timeout: 15))
        print("TEST: ShowSecret dialog opened!")

        // STEP 14: Click Show to reveal
        print("TEST STEP 14: Click Show button to reveal secret")
        showButton.tap()

        // STEP 15: Wait for secret value
        print("TEST STEP 15: Wait for secret value to appear")
        let secretValue = app.staticTexts["SecretValue\(runNumber)"]
        XCTAssertTrue(secretValue.waitForExistence(timeout: 15))

        // STEP 16: Verify secret value
        print("TEST STEP 16: Verify secret value is correct")
        XCTAssertTrue(secretValue.exists)

        print("TEST: ✅ SUCCESS - Secret revealed and verified!")
    }
}
