import XCTest

final class ThreeDeviceUITest: XCTestCase {

    private struct TestParams {
        let email: String
        let secretName: String
        let secretValue: String
    }

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    private func assertExists(_ element: XCUIElement, timeout: TimeInterval, file: StaticString = #filePath, line: UInt = #line) {
        if !element.waitForExistence(timeout: timeout) {
            XCTFail("Element not found. Accessibility hierarchy:\n\(XCUIApplication().debugDescription)", file: file, line: line)
        }
    }

    private func waitForNonExistence(_ element: XCUIElement, timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if !element.exists {
                return true
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }
        return !element.exists
    }

    private func dismissKeyboard(in app: XCUIApplication) {
        let doneButton = app.keyboards.buttons["Done"]
        if doneButton.exists {
            doneButton.tap()
            return
        }

        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.12)).tap()
    }

    private func waitForOnboardingOrSignUp(in app: XCUIApplication, timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        let skipButton = app.buttons["onboarding-skip"]
        let emailManualButton = app.buttons["signin-email-manual"]

        while Date() < deadline {
            if skipButton.exists {
                print("TEST: Onboarding screen found, clicking Skip")
                skipButton.tap()
                return emailManualButton.waitForExistence(timeout: 10)
            }

            if emailManualButton.exists {
                print("TEST: SignUp screen found")
                return true
            }

            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }

        return false
    }

    private func readTestParams() -> TestParams {
        let paramsPath = "/tmp/metasecret-three-device-ui-test.json"

        if let data = FileManager.default.contents(atPath: paramsPath),
           let json = try? JSONSerialization.jsonObject(with: data) as? [String: String] {
            return TestParams(
                email: json["email"] ?? "ios_test@test.com",
                secretName: json["secretName"] ?? "TestSecret",
                secretValue: json["secretValue"] ?? "test"
            )
        }

        let env = ProcessInfo.processInfo.environment
        return TestParams(
            email: env["EMAIL"] ?? "ios_test@test.com",
            secretName: env["SECRET_NAME"] ?? "TestSecret",
            secretValue: env["SECRET_VALUE"] ?? "test"
        )
    }

    func testThreeDeviceJoinFlow() throws {
        let app = XCUIApplication()

        let params = readTestParams()
        let email = params.email
        let secretName = params.secretName
        let secretValue = params.secretValue

        // Set launchEnvironment for the app
        app.launchEnvironment["METASECRET_UI_TEST_MODE"] = "true"
        app.launchEnvironment["EMAIL"] = email
        app.launchEnvironment["SECRET_NAME"] = secretName
        app.launchEnvironment["SECRET_VALUE"] = secretValue

        print("TEST: Three-Device join flow starting")
        print("TEST: email='\(email)', secretName='\(secretName)', secretValue='\(secretValue)'")

        if email == "ios_test@test.com" {
            print("TEST: WARNING - Using default email! Environment EMAIL not set!")
        }

        app.launch()

        // STEP 1: The app may start on Onboarding for a fresh install or directly on SignUp otherwise.
        print("TEST STEP 1: Wait for Onboarding or SignUp screen")
        XCTAssertTrue(waitForOnboardingOrSignUp(in: app, timeout: 30), "Expected onboarding skip or email manual button")

        // STEP 2: Click "Enter email manually"
        print("TEST STEP 2: Click Enter email manually")
        let emailManualButton = app.buttons["signin-email-manual"]
        if !emailManualButton.waitForExistence(timeout: 30) {
            print("TEST: ERROR - 'signin-email-manual' button not found after 30s")
            print("TEST: App hierarchy: \(app.debugDescription)")
            XCTFail("Email manual button not found")
        }
        emailManualButton.tap()
        sleep(2)

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

        // STEP 3.5: Email Confirmation - Confirm email (click Continue)
        print("TEST STEP 3.5: Confirm email (click Continue on confirmation screen)")
        let emailConfirmContinue = app.buttons["email-confirmation-continue"]
        XCTAssertTrue(emailConfirmContinue.waitForExistence(timeout: 10))
        emailConfirmContinue.tap()
        sleep(1)

        // STEP 4: Wait for join prompt (VaultExists state)
        print("TEST STEP 4: Wait for join prompt")
        let joinPrompt = app.staticTexts["This name is already taken. Do you still want to join?"]
        XCTAssertTrue(joinPrompt.waitForExistence(timeout: 30))
        print("TEST: Join prompt appeared!")

        // STEP 5: Click Join button
        print("TEST STEP 5: Click Join button")
        let joinButton = app.buttons["Join"]
        XCTAssertTrue(joinButton.waitForExistence(timeout: 10))
        joinButton.tap()
        sleep(1)

        // STEP 6: Wait for Secrets screen (blocks until web approves + biometry auto-bypasses)
        print("TEST STEP 6: Wait for Secrets screen (web approval + biometry bypass)")
        let fabButton = app.buttons["add-secret-fab"]
        XCTAssertTrue(fabButton.waitForExistence(timeout: 120))
        print("TEST: Secrets screen loaded!")

        let iosApproves = Set([1, 3])

        for round in 1...4 {
            // STEP 7: Wait for recovery request alert from Web.
            print("TEST STEP 7.\(round): Wait for recovery request alert #\(round)")
            let recoveryAlertTitle = app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", "Wanna show the secret?")).firstMatch
            XCTAssertTrue(recoveryAlertTitle.waitForExistence(timeout: 120))
            print("TEST: Recovery request alert #\(round) appeared")

            if iosApproves.contains(round) {
                // STEP 8: Accept recovery request.
                print("TEST STEP 8.\(round): Tap Accept on iOS for recovery request #\(round)")
                let acceptButton = app.buttons["Accept"]
                XCTAssertTrue(acceptButton.waitForExistence(timeout: 10))
                acceptButton.tap()
                print("TEST: Recovery request #\(round) accepted on iOS")
            } else {
                print("TEST STEP 8.\(round): Wait for Android to accept recovery request #\(round)")
            }

            // STEP 9: Wait for recovery request alert to disappear.
            print("TEST STEP 9.\(round): Wait for recovery request alert #\(round) to disappear")
            XCTAssertTrue(waitForNonExistence(recoveryAlertTitle, timeout: 120))
            print("TEST: Recovery request alert #\(round) disappeared")
        }

        // Keep the app alive long enough for the accepted recovery request to sync.
        print("TEST STEP 10: Keep iOS app alive for final recovery accept sync")
        sleep(20)
        print("TEST: iOS recovery accept sync wait finished")

        print("TEST: ✅ SUCCESS - Four recovery request alerts observed, iOS accepted #1 and #3!")
    }
}
