package ui.dialogs.showsecret

import kotlin.test.Test
import kotlin.test.assertEquals

class ShowSecretViewModelTest {

    @Test
    fun `parseSecretValue returns seed phrase for 12 words`() {
        val value = (1..12).joinToString(" ") { "word$it" }

        val result = parseSecretValue(value)

        assertEquals(SecretValueType.SEED_PHRASE, result.type)
        assertEquals(12, result.count)
        assertEquals(12, result.words.size)
    }

    @Test
    fun `parseSecretValue returns seed phrase for 24 words with extra spaces`() {
        val value = (1..24).joinToString("   ") { "word$it" }

        val result = parseSecretValue("  $value  ")

        assertEquals(SecretValueType.SEED_PHRASE, result.type)
        assertEquals(24, result.count)
        assertEquals(24, result.words.size)
    }

    @Test
    fun `parseSecretValue returns password for non seed value`() {
        val result = parseSecretValue("my-super-secret-password")

        assertEquals(SecretValueType.PASSWORD, result.type)
        assertEquals(null, result.count)
        assertEquals(0, result.words.size)
    }
}
