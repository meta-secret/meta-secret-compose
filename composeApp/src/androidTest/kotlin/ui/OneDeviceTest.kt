package ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import metasecret.project.com.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class OneDeviceTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val runNumber = Random.nextInt(1000, 9999)

    @Test
    fun testSignupFlow() {
        val email = "android_${runNumber}@test.com"

        // STEP 1: Onboarding - Click Skip
        println("TEST STEP 1: Click Skip on Onboarding")
        Thread.sleep(3000)
        composeTestRule.onNodeWithTag("onboarding-skip").performClick()
        Thread.sleep(1500)

        // STEP 2: SignUp - Click "Enter email manually"
        println("TEST STEP 2: Click Enter email manually")
        composeTestRule.onNodeWithTag("signin-email-manual").performClick()
        Thread.sleep(1500)

        // STEP 3: ManualSignIn - Enter email and click Continue
        println("TEST STEP 3: Enter email and click Continue")
        composeTestRule.onNodeWithTag("email-input").performTextInput(email)
        Thread.sleep(500)
        composeTestRule.onNodeWithTag("manual-signin-continue").performClick()
        Thread.sleep(1500)

        // STEP 4: EmailConfirmation - Click Continue
        println("TEST STEP 4: Click Continue on confirmation")
        composeTestRule.onNodeWithTag("email-confirmation-continue").performClick()
        Thread.sleep(2000)

        // STEP 5: Wait for Secrets screen
        println("TEST STEP 5: Verify Secrets screen loaded")
        composeTestRule.waitUntil(timeoutMillis = 30000) {
            runCatching {
                composeTestRule.onNodeWithTag("add-secret-fab").fetchSemanticsNode() != null
            }.getOrDefault(false)
        }

        // STEP 6: Add secret - tap FAB
        println("TEST STEP 6: Tap Add Secret FAB")
        composeTestRule.onNodeWithTag("add-secret-fab").performClick()
        Thread.sleep(3000)

        // Wait for dialog
        println("TEST STEP 7: Wait for Add Secret dialog")
        composeTestRule.waitUntil(timeoutMillis = 45000) {
            runCatching {
                val found = composeTestRule.onNodeWithTag("secret-name-input").fetchSemanticsNode() != null
                if (found) println("TEST: secret-name-input found!")
                found
            }.getOrDefault(false)
        }

        // Enter secret details
        println("TEST STEP 8: Enter secret name and value")
        composeTestRule.onNodeWithTag("secret-name-input").performTextInput("MySecret${runNumber}")
        Thread.sleep(300)
        composeTestRule.onNodeWithTag("secret-value-input").performTextInput("SecretValue${runNumber}")
        Thread.sleep(300)

        // Submit secret
        println("TEST STEP 9: Submit secret")
        composeTestRule.onNodeWithTag("add-secret-submit").performClick()

        // Wait for success toast
        println("TEST STEP 10: Wait for success toast")
        composeTestRule.waitUntil(timeoutMillis = 45000) {
            runCatching {
                composeTestRule.onNodeWithText("A secret successfully added").fetchSemanticsNode() != null
            }.getOrDefault(false)
        }
        println("TEST: Toast appeared!")

        // Wait for app to return to Secrets screen (FAB visible + secret in list)
        println("TEST STEP 11: Wait for Secrets screen with new secret")
        composeTestRule.waitUntil(timeoutMillis = 30000) {
            runCatching {
                val fabVisible = composeTestRule.onNodeWithTag("add-secret-fab").fetchSemanticsNode() != null
                val secretInList = composeTestRule.onNodeWithText("MySecret${runNumber}").fetchSemanticsNode() != null
                fabVisible && secretInList
            }.getOrDefault(false)
        }
        println("TEST: Secret in list!")

        // Wait 3 seconds for UI to settle
        Thread.sleep(3000)

        // Click on secret to open ShowSecret dialog
        println("TEST STEP 12: Click on secret to open ShowSecret dialog")
        composeTestRule.onNodeWithText("MySecret${runNumber}").performClick()

        // Wait for ShowSecret dialog with Show button
        println("TEST STEP 13: Wait for ShowSecret dialog with Show button")
        composeTestRule.waitUntil(timeoutMillis = 15000) {
            runCatching {
                composeTestRule.onNodeWithText("Show").fetchSemanticsNode() != null
            }.getOrDefault(false)
        }
        println("TEST: ShowSecret dialog opened!")

        // Click Show button to reveal secret
        println("TEST STEP 14: Click Show button to reveal secret")
        composeTestRule.onNodeWithText("Show").performClick()

        // Wait for secret value to appear
        println("TEST STEP 15: Wait for secret value to appear")
        composeTestRule.waitUntil(timeoutMillis = 15000) {
            runCatching {
                composeTestRule.onNodeWithText("SecretValue${runNumber}").fetchSemanticsNode() != null
            }.getOrDefault(false)
        }

        // Verify secret value is correct
        println("TEST STEP 16: Verify secret value is correct")
        composeTestRule.onNodeWithText("SecretValue${runNumber}").assertIsDisplayed()

        println("TEST: ✅ SUCCESS - Secret revealed and verified!")
    }
}
