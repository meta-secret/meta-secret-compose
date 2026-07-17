package ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import metasecret.project.com.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThreeDeviceTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun hasNodeWithTag(tag: String): Boolean {
        return runCatching {
            composeTestRule.onNodeWithTag(tag).fetchSemanticsNode()
            true
        }.getOrDefault(false)
    }

    @Test
    fun testThreeDeviceJoinFlow() {
        // Read parameters from instrumentation
        val args = InstrumentationRegistry.getArguments()
        val email = args.getString("email") ?: run {
            throw IllegalArgumentException("Missing required parameter: email")
        }
        val secretName = args.getString("secretName") ?: run {
            throw IllegalArgumentException("Missing required parameter: secretName")
        }
        val secretValue = args.getString("secretValue") ?: run {
            throw IllegalArgumentException("Missing required parameter: secretValue")
        }

        println("TEST: Three-Device join flow starting")
        println("TEST: email='$email', secretName='$secretName', secretValue='$secretValue'")

        // STEP 1: The app may start on Onboarding for a fresh install or directly on SignUp otherwise.
        println("TEST STEP 1: Wait for Onboarding or SignUp screen")
        composeTestRule.waitUntil(timeoutMillis = 30000) {
            hasNodeWithTag("onboarding-skip") || hasNodeWithTag("signin-email-manual")
        }

        if (hasNodeWithTag("onboarding-skip")) {
            println("TEST: Onboarding screen detected, clicking Skip")
            composeTestRule.onNodeWithTag("onboarding-skip").performClick()
            Thread.sleep(1500)
        }

        composeTestRule.waitUntil(timeoutMillis = 10000) {
            hasNodeWithTag("signin-email-manual")
        }

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

        // STEP 3.5: EmailConfirmation - Confirm email by clicking Continue
        println("TEST STEP 3.5: Confirm email (click Continue on confirmation screen)")
        composeTestRule.onNodeWithTag("email-confirmation-continue").performClick()
        Thread.sleep(1500)

        // STEP 5: EmailConfirmation - Wait for join prompt (VaultExists state)
        println("TEST STEP 5: Wait for vault-exists prompt")
        composeTestRule.waitUntil(timeoutMillis = 30000) {
            runCatching {
                composeTestRule.onNodeWithText("This name is already taken. Do you still want to join?")
                    .fetchSemanticsNode() != null
            }.getOrDefault(false)
        }
        println("TEST: Join prompt appeared!")

        // STEP 6: Click Join button
        println("TEST STEP 6: Click Join button")
        composeTestRule.onNodeWithTag("email-confirmation-join").performClick()
        Thread.sleep(1500)

        // STEP 7: Wait for add-secret-fab (blocks until web approves + biometry auto-bypasses)
        println("TEST STEP 7: Wait for Secrets screen (web approval + biometry bypass)")
        composeTestRule.waitUntil(timeoutMillis = 120000) {
            runCatching {
                composeTestRule.onNodeWithTag("add-secret-fab").fetchSemanticsNode() != null
            }.getOrDefault(false)
        }
        println("TEST: Secrets screen loaded!")

        // STEP 8: Wait for secret in list (synced from vault)
        println("TEST STEP 8: Wait for secret in list")
        composeTestRule.waitUntil(timeoutMillis = 30000) {
            runCatching {
                composeTestRule.onNodeWithText(secretName).fetchSemanticsNode() != null
            }.getOrDefault(false)
        }
        println("TEST: Secret '$secretName' found in list!")

        // STEP 9: Verify protection level shows "Normal" (2 devices = level_2)
        println("TEST STEP 9: Verify protection level is Normal")
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            runCatching {
                composeTestRule.onNodeWithText("Normal").fetchSemanticsNode() != null
            }.getOrDefault(false)
        }
        println("TEST: Protection level is Normal!")

        // STEP 10: Click on secret to open ShowSecret dialog
        println("TEST STEP 10: Click on secret")
        composeTestRule.onNodeWithText(secretName).performClick()

        // STEP 11: Wait for ShowSecret dialog with Show button
        println("TEST STEP 11: Wait for ShowSecret dialog")
        composeTestRule.waitUntil(timeoutMillis = 15000) {
            runCatching {
                composeTestRule.onNodeWithText("Show").fetchSemanticsNode() != null
            }.getOrDefault(false)
        }
        println("TEST: ShowSecret dialog opened!")

        // STEP 12: Click Show button to reveal secret
        println("TEST STEP 12: Click Show button")
        composeTestRule.onNodeWithText("Show").performClick()

        // STEP 13: Wait for secret value to appear
        println("TEST STEP 13: Wait for secret value")
        composeTestRule.waitUntil(timeoutMillis = 15000) {
            runCatching {
                composeTestRule.onNodeWithText(secretValue).fetchSemanticsNode() != null
            }.getOrDefault(false)
        }

        // STEP 14: Verify secret value is correct
        println("TEST STEP 14: Verify secret value is correct")
        composeTestRule.onNodeWithText(secretValue).assertIsDisplayed()

        println("TEST: Secret revealed and verified: '$secretName' = '$secretValue'")

        println("TEST STEP 15: Close ShowSecret dialog")
        composeTestRule.onNodeWithTag("show-secret-close").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeTestRule.onNodeWithText(secretValue).fetchSemanticsNode()
                false
            }.getOrDefault(true)
        }
        println("TEST: ShowSecret dialog closed")

        val androidApproves = setOf(2, 4)
        for (round in 1..4) {
            println("TEST STEP 16.$round: Wait for recovery request alert #$round")
            composeTestRule.waitUntil(timeoutMillis = 120000) {
                runCatching {
                    composeTestRule.onNodeWithText("Wanna show the secret?", substring = true)
                        .fetchSemanticsNode()
                    true
                }.getOrDefault(false)
            }
            println("TEST: Recovery request alert #$round appeared")

            if (round in androidApproves) {
                println("TEST STEP 17.$round: Tap Accept on Android for recovery request #$round")
                composeTestRule.onNodeWithText("Accept").performClick()
                println("TEST: Recovery request #$round accepted on Android")
            } else {
                println("TEST STEP 17.$round: Wait for iOS to accept recovery request #$round")
            }

            println("TEST STEP 18.$round: Wait for recovery request alert #$round to disappear")
            composeTestRule.waitUntil(timeoutMillis = 120000) {
                runCatching {
                    composeTestRule.onNodeWithText("Wanna show the secret?", substring = true)
                        .fetchSemanticsNode()
                    false
                }.getOrDefault(true)
            }
            println("TEST: Recovery request alert #$round disappeared")
        }

        println("TEST STEP 19: Keep Android app alive for final recovery accept sync")
        Thread.sleep(20000)
        println("TEST: Android recovery sync wait finished")

        println("TEST: ✅ SUCCESS - Four recovery request alerts observed, Android accepted #2 and #4!")
    }
}
