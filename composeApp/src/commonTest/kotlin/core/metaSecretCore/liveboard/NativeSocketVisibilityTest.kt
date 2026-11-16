package core.metaSecretCore.liveboard

import core.metaSecretCore.MetaSecretSocketEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class NativeSocketVisibilityTest {
    @Test
    fun testBackgroundReadFinallyCannotLoseForegroundReadOrItsActualPauseDrain() = runBlocking {
        withTimeout(30_000) {
            val f = NativeSocketVisibilityFixture()
            val failures = mutableListOf<String>()
            try {
                f.start(this)
                val initialized = assertNotNull(f.initialization.currentScope())
                val old = f.hold("read")
                f.socket.onAppForeground()
                old.entered.await()
                val foreground = f.hold("read")
                f.socket.onAppBackground()
                f.socket.onAppForeground()
                assertTrue(!old.exited.isCompleted, "Foreground is requested while the old physical native call is still entered")
                assertEquals(initialized, f.initialization.currentScope())
                old.release.complete(Unit)
                old.exited.await()
                foreground.entered.await()
                f.cache.synchronization.first { it.activeRefreshes == 1 && it.loading }
                assertTrue(!foreground.exited.isCompleted)

                // UND reaches the real public pause's first suspension without a time/idle oracle.
                val paused = f.track(async(f.immediate, CoroutineStart.UNDISPATCHED) {
                    f.socket.pauseRefreshesAndAwait()
                })
                if (paused.isCompleted) failures.add("Old background finally lost the entered foreground job: actual pause returned before its native exit")
                foreground.release.complete(Unit)
                foreground.exited.await()
                paused.await()
                f.idle()

                val fresh = assertNotNull(f.reads.requireFreshRead())
                f.socket.resumeRefreshes()
                f.idle()
                assertSocketProof(f, fresh, initialized)
                assertEquals(3, f.trace.count("core.read"))
                assertEquals(1, f.trace.count("core.init"))
                assertEquals(0, f.trace.count("core.generate"))
                assertEquals(0, f.trace.count("core.signUp"))
                assertEquals("visibility-key-a", f.keys.getString("master_key"))
                assertEquals(emptyList(), failures, "Held A, actual B drain and fresh same-epoch foreground controls all completed")
            } finally { f.close() }
        }
    }

    @Test
    fun testAcknowledgedHiddenInvalidationDoesNotReadAndForegroundBootstrapsWithoutTransportEvent() = runBlocking {
        withTimeout(30_000) {
            val f = NativeSocketVisibilityFixture()
            val failures = mutableListOf<String>()
            try {
                f.start(this)
                val initialized = assertNotNull(f.initialization.currentScope())
                // Preserve legacy explicit refresh before the first lifecycle foreground event.
                f.socket.refreshAppState()
                f.idle()
                assertEquals(1, f.trace.count("core.read"))
                assertEquals(initialized, assertNotNull(f.cache.canonicalFrames.value).scope)
                f.socket.onAppBackground()
                val before = f.trace.count("core.read")
                f.client.events.emitAndAwait(MetaSecretSocketEvent.StateInvalidated(scope = "vault"))
                // ACK proves actual handler settlement; idle only drains any work it scheduled.
                f.idle()
                val hidden = f.trace.count("core.read")
                if (hidden != before) failures.add("Acknowledged hidden invalidation entered native read: before=$before after=$hidden")

                val target = assertNotNull(f.reads.requireFreshRead())
                f.socket.onAppForeground()
                f.idle()
                assertSocketProof(f, target, initialized)
                assertEquals(hidden + 1, f.trace.count("core.read"), "Foreground itself performs one actual fresh read without a Connected/SSE event")
                assertTrue("connect" in f.client.effects.value)
                assertEquals(1, f.trace.count("core.init"))
                assertEquals(0, f.trace.count("core.generate"))
                assertEquals(0, f.trace.count("core.signUp"))
                assertEquals(emptyList(), failures, "Initial compatibility, actual hidden callback, fresh foreground and same-key epoch controls completed")
            } finally { f.close() }
        }
    }

    private suspend fun assertSocketProof(
        f: NativeSocketVisibilityFixture,
        target: NativeReadRequirement,
        initialized: NativeReadScope,
    ) {
        val frame = assertNotNull(f.cache.canonicalFrames.value)
        assertEquals(NativeReadOutcome.SUCCESS, frame.outcome)
        assertEquals(initialized, frame.scope)
        assertEquals(initialized, f.initialization.currentScope())
        assertSame(frame.state, f.cache.appState.value)
        assertTrue(target in frame.coveredRequirements)
        assertTrue(target.completion.isCompleted)
        assertSame(frame, target.completion.await())
    }
}
