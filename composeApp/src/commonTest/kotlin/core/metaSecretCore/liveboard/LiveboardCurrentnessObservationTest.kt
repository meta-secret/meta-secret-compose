package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class LiveboardCurrentnessObservationTest {
    @Test
    fun testActualHiddenSelectionCapturesConsultedLeaf() = runBlocking {
        withTimeout(TIMEOUT_MS) {
            val fixture = LiveboardFeatureFixture()
            try {
                val session = fixture.start()
                val selection = LiveboardEntrySelection(
                    session,
                    session.sensitive.generation(),
                    LiveboardEntrySubject.Decision(fixture.identity(LiveboardKind.Recover), true),
                )
                assertTrue(selection.isCurrent(), "The real Recover selection must initially be current")
                val trace = LiveboardDecisionRejectionTrace()

                session.sensitive.background()
                assertFalse(selection.isCurrentObserved(trace))
                assertEquals(0, fixture.auth.calls)
                assertEquals(0, fixture.core.calls.value.count { it.name == "accept" })
                // No command was issued: this checks idle only, not journal/native settlement.
                session.actions.awaitSettled()
                assertTrue(session.actions.presentation().isEmpty())
                assertFalse(session.isDisposed, "The fixture must remain open through the diagnostic assertion")

                assertEquals("POST_CAS_SENSITIVE_HIDDEN", trace.currentnessSource().name,
                    "Expected the actual hidden selection check to capture its first consulted false leaf")
            } finally {
                fixture.close()
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 45_000L
    }
}
