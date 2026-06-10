package core.email

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailSelectionModelsTest {
    @Test
    fun `normalizeEmailInput trims outer whitespace`() {
        assertEquals("alex.morgan@icloud.com", normalizeEmailInput("  alex.morgan@icloud.com  "))
    }

    @Test
    fun `isValidEmail accepts a standard email address`() {
        assertTrue(isValidEmail("alex.morgan@icloud.com"))
    }

    @Test
    fun `isValidEmail rejects malformed addresses`() {
        assertFalse(isValidEmail("alex.morgan@icloud"))
        assertFalse(isValidEmail("not-an-email"))
        assertFalse(isValidEmail("alex morgan@icloud.com"))
    }

    @Test
    fun `isApplePrivateRelayEmail matches private relay addresses`() {
        assertTrue(isApplePrivateRelayEmail("hide.me@privaterelay.appleid.com"))
        assertTrue(isApplePrivateRelayEmail("HIDE.ME@PRIVATERELAY.APPLEID.COM"))
    }

    @Test
    fun `isApplePrivateRelayEmail rejects normal emails`() {
        assertFalse(isApplePrivateRelayEmail("alex.morgan@icloud.com"))
    }
}
