package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel

class SocketClaimNativeAdmissionTest {
    @Test
    fun testActualSocketClaimLookupDrainsBeforeProfileResetAndFreshClaimStillCompletes() = runBlocking {
        withTimeout(20_000) {
            val f = NativeBusinessAdmissionFixture()
            try {
                f.initialize()
                val original = assertNotNull(f.initialization.currentScope())
                val profile = f.profile()
                val gate = f.hold("find")
                f.socket.setProcessingSecretName("secret-a")
                f.socket.actionsToFollow(add = listOf(SocketRequestModel.SHOW_SECRET), exclude = null)
                gate.entered.await()
                assertEquals(listOf(NativeCall("find", listOf("secret-a"))), f.core.calls.value)
                f.requestReset(profile)
                assertTrue(f.resetEntered.isCompleted)
                assertNull(f.initialization.currentScope(), "Actual Profile transition closes native admission")
                assertTrue(!gate.exited.isCompleted)
                val clearedBeforeLookupReturned = f.keys.clears.value != 0
                gate.release.complete(Unit)
                gate.exited.await()
                f.awaitReset(profile)
                f.awaitSocketIdle()
                val failures = mutableListOf<String>()
                if (clearedBeforeLookupReturned) failures.add("actual SHOW_SECRET findClaim was not drained before key clear")
                if (f.socket.socketActionType.value is SocketActionModel.RECOVER_ACCEPTED) {
                    failures.add("retired claim lookup published its old approval")
                }
                val fresh = f.freshSuccessor()
                assertNotSame(original.owner, fresh.scope?.owner)
                f.core.claimId = "claim-b"
                f.core.secretName = "secret-b"
                val beforeFreshLookup = f.core.calls.value.size
                f.socket.setProcessingSecretName("secret-b")
                f.socket.refreshAppState()
                val action = f.socket.socketActionType.first {
                    it is SocketActionModel.RECOVER_ACCEPTED && it.claimId == "claim-b" && it.secretId == "secret-b"
                } as SocketActionModel.RECOVER_ACCEPTED
                f.awaitSocketIdle()
                assertEquals("claim-b", action.claimId)
                assertEquals(beforeFreshLookup + 1, f.core.calls.value.size)
                assertEquals(NativeCall("find", listOf("secret-b")), f.core.calls.value.last())
                assertEquals(f.initialization.currentScope(), assertNotNull(f.cache.canonicalFrames.value).scope)
                assertTrue(f.notifications.errorMessages.isEmpty())
                assertEquals(emptyList(), failures, "Actual A lookup/Reset completed and unique fresh-B claim/action positive reached")
            } finally { f.close() }
        }
    }
}
