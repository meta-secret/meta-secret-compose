package ui.scenes.secretsscreen

import kotlin.test.Test
import kotlin.test.assertEquals
import models.apiModels.ClientStatus

class SecretPrimaryActionStateTest {
    @Test
    fun `accepted recovery exposes show action with exact claim id`() {
        assertEquals(
            SecretPrimaryActionState(SecretPrimaryAction.Show, "claim-123"),
            secretPrimaryActionState(ClientStatus.ACCEPTED, "claim-123"),
        )
    }

    @Test
    fun `non accepted claim remains a recover action`() {
        assertEquals(
            SecretPrimaryActionState(SecretPrimaryAction.Recover),
            secretPrimaryActionState(ClientStatus.PENDING, "claim-123"),
        )
    }

    @Test
    fun `accepted claim without id does not route to local show`() {
        assertEquals(
            SecretPrimaryActionState(SecretPrimaryAction.Recover),
            secretPrimaryActionState(ClientStatus.ACCEPTED, null),
        )
    }
}
