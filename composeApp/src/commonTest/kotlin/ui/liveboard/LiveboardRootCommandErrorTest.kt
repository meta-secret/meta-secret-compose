package ui.liveboard

import core.metaSecretCore.liveboard.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class LiveboardRootCommandErrorTest {
    @Test
    fun testEnteredRootFailurePinsOwnSafeErrorWhileCancelAndLateOwnerStaySilent() = runBlocking {
        withTimeout(45_000) {
            val f = LiveboardRootTestFixture()
            val failures = mutableListOf<String>()
            try {
                f.start()
                val root = f.root()
                val session = f.session(root)
                val draft = fullDraft("Own failed Create")
                assertTrue(root.openCreate()); root.editForm(draft)
                val own = f.hold("create", IllegalStateException("Synthetic private native detail must never be displayed"))
                val first = root.submitCreate()
                own.entered.await(); own.release.complete(Unit); own.exited.await()
                first.join(); session.awaitSettled()
                assertEquals(1, f.core.calls.value.count { it.name == "create" })
                assertEquals(1, f.core.failures.value, "The actual synchronous native route threw")
                assertEquals(LiveboardCreatePhase.Unknown, session.actions.createPresentation().phase)
                rootCheck(failures, "own fixed error") { assertEquals("errorInternal", session.inbox.state.value.error) }
                rootCheck(failures, "own close") { assertFalse(session.sensitive.state.value.createOpen) }
                rootCheck(failures, "own full draft") { assertEquals(draft, session.sensitive.state.value.draft) }
                rootCheck(failures, "own Unknown no replay") { assertEquals(null, session.actions.createIntent()) }
                assertEquals(1, f.core.calls.value.count { it.name == "create" })

                // A genuine fresh native owner isolates OS Cancel from the unresolved failed write.
                f.newOwner()
                val cancelledRoot = f.root()
                val cancelledSession = f.session(cancelledRoot)
                val cancelDraft = fullDraft("Cancelled selection")
                assertTrue(cancelledRoot.openCreate()); cancelledRoot.editForm(cancelDraft)
                f.auth.held = true
                val beforeCancel = f.core.calls.value.size
                val cancelled = cancelledRoot.submitCreate()
                f.auth.take().error("Synthetic OS Cancel detail")
                cancelled.join(); cancelledSession.awaitSettled()
                rootCheck(failures, "NotEntered cancel silent") { assertEquals(null, cancelledSession.inbox.state.value.error) }
                rootCheck(failures, "NotEntered cancel open") { assertTrue(cancelledSession.sensitive.state.value.createOpen) }
                rootCheck(failures, "NotEntered cancel full draft") { assertEquals(cancelDraft, cancelledSession.sensitive.state.value.draft) }
                assertEquals(beforeCancel, f.core.calls.value.size)
                f.auth.held = false
                cancelledRoot.submitCreate().join(); cancelledSession.awaitSettled()
                assertEquals(beforeCancel + 1, f.core.calls.value.size, "Fresh approved selection really entered native")
                assertEquals(null, cancelledSession.inbox.state.value.error)

                // Hold only the old Root UI, never a native lease, across the real successor transition.
                f.newOwner()
                val oldUi = NativeCheckedIo()
                val oldRoot = f.root(oldUi)
                val oldSession = f.session(oldRoot)
                assertTrue(oldRoot.openCreate()); oldRoot.editForm(fullDraft("Retired failed Create"))
                val late = f.hold("create", IllegalStateException("Synthetic retired private native detail"))
                val oldView = oldRoot.submitCreate()
                late.entered.await(); oldUi.queue()
                late.release.complete(Unit); late.exited.await(); oldSession.awaitSettled()
                assertEquals(2, f.core.failures.value)
                val oldScope = assertNotNull(f.reads.frames.value?.scope)
                val current = f.newOwner()
                assertTrue(current.scope != oldScope)
                val liveRoot = f.root()
                val liveSession = f.session(liveRoot)
                val liveDraft = fullDraft("Current B draft")
                assertTrue(liveRoot.openCreate()); liveRoot.editForm(liveDraft)
                f.finishControlled(oldUi); oldView.join()
                rootCheck(failures, "late A cannot error B") { assertEquals(null, liveSession.inbox.state.value.error) }
                rootCheck(failures, "late A cannot close B") { assertTrue(liveSession.sensitive.state.value.createOpen) }
                rootCheck(failures, "late A cannot change B draft") { assertEquals(liveDraft, liveSession.sensitive.state.value.draft) }
                val beforeB = f.core.calls.value.size
                liveRoot.submitCreate().join(); liveSession.awaitSettled()
                assertEquals(beforeB + 1, f.core.calls.value.size)
                assertEquals(null, liveSession.inbox.state.value.error)
                assertEquals(emptyList(), failures, "Own entered failure, silent unentered Cancel, private Unknown, late A and genuine fresh native controls reached")
            } finally { f.close() }
        }
    }

    private fun fullDraft(name: String) = LiveboardDraft(name, "Synthetic active input", LiveboardDraftMode.Plain,
        12, List(24) { "inactive${it + 1}" })
}
