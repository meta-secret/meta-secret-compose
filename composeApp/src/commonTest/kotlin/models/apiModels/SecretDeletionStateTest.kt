package models.apiModels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.encodeToString

class SecretDeletionStateTest {
    @Test
    fun `new deletion follows fresh Core policy after membership changed`() {
        for (requiresApproval in listOf(false, true)) {
            val policy = SecretDeletionState(PassId("id", "label"), "id", requiresApproval = requiresApproval)
            val previous = policy.copy(requestId = "old-uuid", requiresApproval = !requiresApproval,
                clientStatus = SecretDeletionStatus.Declined)
            assertEquals(requiresApproval, deletionPolicyState(listOf(previous, policy), "id")?.requiresApproval)
            assertNull(deletionPolicyState(listOf(previous), "id"))
            val pending = previous.copy(requestId = "current-uuid", clientStatus = SecretDeletionStatus.Pending)
            assertNull(deletionPolicyState(listOf(policy, previous, pending), "id"))
        }
    }

    @Test
    fun `live request takes precedence over old declined outcome for same secret`() {
        val old = SecretDeletionState(PassId("id", "label"), "id", "old", true, SecretDeletionStatus.Declined)
        val live = old.copy(requestId = "new", clientStatus = SecretDeletionStatus.Pending)
        assertEquals("new", currentDeletionState(listOf(live, old), "id")?.requestId)
        assertNull(currentDeletionState(listOf(live), "different-id"))
    }

    @Test
    fun `deletion instructions preserve opaque identity and exact request UUID`() {
        val state = JsonConfig.json.decodeFromString<SecretDeletionState>(
            """{"passId":{"id":"opaque","name":"same label"},"secretId":"opaque","requestId":"uuid-1","requiresApproval":true,"clientStatus":"NeedApprove"}"""
        )
        assertEquals("opaque", state.secretId)
        assertEquals("uuid-1", state.requestId)
        assertEquals(SecretDeletionStatus.NeedApprove, state.clientStatus)
        assertEquals(true, state.requiresApproval)
    }

    @Test
    fun `historical member state defaults to no deletion instructions`() {
        assertNull(JsonConfig.json.decodeFromString<SecretDeletionState>(
            """{"passId":{"id":"opaque","name":"label"},"secretId":"opaque","requiresApproval":false}"""
        ).clientStatus)
    }

    @Test
    fun `decision serializes exact UUID and explicit decline`() {
        assertEquals("""{"requestId":"uuid-2","accepted":false}""",
            JsonConfig.json.encodeToString(SecretDeletionDecision("uuid-2", false)))
    }
}
