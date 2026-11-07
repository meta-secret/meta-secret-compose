package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class LiveboardPrivacyEpochOrderTest {
    @Test
    fun testNewPrivacyEpochCannotExposeOldValuesOrEraseNestedCurrentInput() = runBlocking {
        withTimeout(45_000) {
            val f = LiveboardFeatureFixture()
            val failures = mutableListOf<String>()
            try {
                val session = f.start()
                f.disclose(session, "pass-a"); f.disclose(session, "pass-b")
                assertEquals(setOf("pass-a", "pass-b"), session.sensitive.state.value.revealed.keys)
                assertNotNull(session.sensitive.copyValue("pass-a"))
                assertNotNull(session.sensitive.copyValue("pass-b"))
                val oldEpoch = session.sensitive.privacyEpoch.value
                var armed = false
                var observed = 0
                var returned = false
                var nestedFinishedInline = false
                var nestedEpoch: Any? = null
                val handled = CompletableDeferred<Unit>()
                val before = f.core.calls.value.size
                val observer = f.track(launch(f.main, CoroutineStart.UNDISPATCHED) {
                    session.sensitive.privacyEpoch.collect { epoch ->
                        if (!armed || epoch === oldEpoch) return@collect
                        armed = false // One exact inline reentry; subsequent nested epochs cannot recurse.
                        observed++
                        if (session.sensitive.state.value.revealed.isNotEmpty()) failures.add("New epoch exposed old public card values")
                        if (session.sensitive.copyValue("pass-a") != null || session.sensitive.copyValue("pass-b") != null) {
                            failures.add("New epoch retained old known-card Copy eligibility")
                        }
                        // A real new-generation user action may synchronously reenter this publication.
                        session.sensitive.routeChanged()
                        nestedEpoch = session.sensitive.privacyEpoch.value
                        session.sensitive.editDraft("New epoch draft", "New epoch fixture input")
                        assertTrue(session.sensitive.openCreate())
                        nestedFinishedInline = !returned
                        handled.complete(Unit)
                    }
                })
                armed = true
                session.sensitive.routeChanged()
                returned = true
                handled.await() // Positive completion of the actual subscribed epoch consumer.
                assertEquals(1, observed)
                assertTrue(nestedFinishedInline, "Harness must finish the actual nested action before outer conceal returns")
                assertSame(nestedEpoch, session.sensitive.privacyEpoch.value)
                assertEquals(before, f.core.calls.value.size, "Privacy publication and authored input do not enter native")
                if (session.sensitive.state.value.draft != LiveboardDraft("New epoch draft", "New epoch fixture input") ||
                    !session.sensitive.state.value.createOpen) {
                    failures.add("Outer conceal erased current nested authored input or its open form")
                }
                observer.cancel(); observer.join()
                // Deliberate current action still works after observation; cleanup is not the only positive tail.
                f.disclose(session, "pass-b")
                assertEquals(before + 1, f.core.calls.value.size)
                assertNotNull(session.sensitive.copyValue("pass-b"))
                session.sensitive.routeChanged()
                assertTrue(f.disclosuresEmpty(session))
                assertEquals(emptyList(), failures, "Inline epoch privacy, nested current generation and actual fresh native controls reached")
            } finally { f.close() }
        }
    }
}
