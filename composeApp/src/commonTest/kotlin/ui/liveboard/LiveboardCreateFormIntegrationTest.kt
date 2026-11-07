package ui.liveboard

import core.metaSecretCore.liveboard.*
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class LiveboardCreateFormIntegrationTest {
    @Test
    fun testFullOwnedFormUsesExactActivePayloadAndRejectsOldSelections() = runBlocking {
        withTimeout(45_000) {
            val f = LiveboardFormTestFixture(); val errors = mutableListOf<String>()
            try {
                f.start()
                for ((mode, count) in listOf(LiveboardDraftMode.Plain to 12, LiveboardDraftMode.Seed to 12,
                    LiveboardDraftMode.Seed to 24)) {
                    val full = f.draft(mode, count)
                    f.open(full)
                    rootCheck(errors, "full roundtrip/$mode/$count") { assertEquals(full, f.sensitive().draft) }
                    f.root.editForm(full.copy(mode = if (mode == LiveboardDraftMode.Plain) LiveboardDraftMode.Seed else LiveboardDraftMode.Plain))
                    f.root.editForm(full)
                    assertEquals(full, f.sensitive().draft)
                    val before = f.f.core.calls.value.size
                    f.root.submitCreate().join(); f.session.actions.awaitSettled()
                    assertEquals(before + 1, f.f.core.calls.value.size)
                    assertEquals(NativeCall("create", listOf(full.name, f.active(full))), f.f.core.calls.value.last())
                    f.fresh()
                }
                val callerWords = MutableList(24) { "owned${it + 1}" }
                val selected = f.draft(LiveboardDraftMode.Seed, 24).copy(words = callerWords)
                val expected = selected.copy(words = callerWords.toList())
                f.open(selected); callerWords[0] = "external mutation"; callerWords[23] = "late external mutation"
                rootCheck(errors, "Sensitive owns defensive full copy") { assertEquals(expected, f.sensitive().draft) }
                val text = f.sensitive().toString() + f.sensitive().draft.toString() + LiveboardDisclosure("pass-b", "private disclosure").toString()
                rootCheck(errors, "redacted containers") {
                    assertFalse(text.contains(expected.value)); assertFalse(text.contains("owned1"))
                    assertFalse(text.contains("private disclosure"))
                }
                f.root.submitCreate().join(); f.session.actions.awaitSettled()
                rootCheck(errors, "selected payload ignores caller alias") {
                    assertEquals(NativeCall("create", listOf(expected.name, f.active(expected))), f.f.core.calls.value.last())
                }
                f.fresh()
                val twelve = f.draft(LiveboardDraftMode.Seed, 12)
                f.open(twelve); f.f.auth.held = true
                val before = f.f.core.calls.value.size
                val old = f.f.track(f.root.submitCreate()); val auth = f.f.auth.take()
                val edited = twelve.copy(words = twelve.words.mapIndexed { index, word -> if (index >= 12) "new$word" else word })
                assertEquals(f.active(twelve), f.active(edited))
                f.root.editForm(edited); auth.success(); old.join(); f.session.actions.awaitSettled()
                rootCheck(errors, "inactive-only edit revokes selected capsule") {
                    assertEquals(before, f.f.core.calls.value.size); assertEquals(edited, f.sensitive().draft)
                }
                f.f.auth.held = false; f.fresh(); f.live(edited)
                f.fresh()
                val emptyActive = f.draft(LiveboardDraftMode.Seed, 12).copy(value = "", words = List(24) { if (it < 12) "" else "inactive$it" })
                f.open(emptyActive)
                val pasteJob = f.f.track(f.root.pasteCreate()); val pending = f.f.platform.takePaste()
                f.root.editForm(emptyActive.copy(mode = LiveboardDraftMode.Plain, value = "new typed value"))
                pending.complete(List(24) { "late$it" }.joinToString(" ")); pasteJob.join()
                rootCheck(errors, "late clipboard cannot restore old version") { assertEquals("new typed value", f.sensitive().draft?.value) }
                f.root.editForm(emptyActive)
                assertTrue(f.root.back())
                rootCheck(errors, "idle close clears inactive-only contents") {
                    assertNull(f.sensitive().draft); assertFalse(f.sensitive().createOpen)
                }
                f.open(f.draft(LiveboardDraftMode.Seed, 24))
                val validPaste = f.f.track(f.root.pasteCreate()); val read = f.f.platform.takePaste()
                val pasted = List(24) { "pasted${it + 1}" }; read.complete(pasted.joinToString(" ")); validPaste.join()
                rootCheck(errors, "current clipboard fills full selected seed") { assertEquals(pasted, f.sensitive().draft?.words) }
                f.root.submitCreate().join(); f.session.actions.awaitSettled()
                assertEquals(NativeCall("create", listOf("selected name", pasted.joinToString(" "))), f.f.core.calls.value.last())
                assertEquals(emptyList(), errors, "Plain/12/24/alias/inactive edit/Paste/empty-active positive tails reached")
            } finally { f.close() }
        }
    }

    @Test
    fun testSoftConcealAndTerminalCleanupBelongToExactAuthoredFormVersion() = runBlocking {
        withTimeout(60_000) {
            val errors = mutableListOf<String>()
            softLifecycle(this, errors)
            for (newer in listOf(false, true)) canonicalTerminal(this, newer, errors)
            hardBoundaries(errors)
            assertEquals(emptyList(), errors, "Sync/error/auth/version/Unknown/hard-boundary tails reached")
        }
    }

    private suspend fun softLifecycle(scope: CoroutineScope, errors: MutableList<String>) {
        val f = LiveboardFormTestFixture()
        try {
            f.start(); val full = f.draft(LiveboardDraftMode.Seed, 24); f.open(full); f.f.idle()
            val gate = f.f.hold("read")
            val reading = f.f.track(scope.async { f.f.manager.getStateModel() }); gate.entered.await()
            val before = f.f.core.calls.value.size
            rootCheck(errors, "ordinary sync preserves open full form") {
                assertTrue(f.sensitive().createOpen); assertEquals(full, f.sensitive().draft)
                assertFalse(f.session.actions.createPresentation().canSubmit)
            }
            f.root.submitCreate().join()
            rootCheck(errors, "no submit during actual read") { assertEquals(before, f.f.core.calls.value.size) }
            gate.release.complete(Unit); reading.await(); f.refresh()
            // Preserve the wrong native entry as semantic failure, but isolate later auth positives
            // from its genuinely issued slot; a correct fence takes no recovery branch.
            if (f.f.core.calls.value.size != before) { f.fresh(); f.open(full) }
            for (round in 0..1) {
                f.error("same known error")
                rootCheck(errors, "error close keeps full draft/$round") { assertFalse(f.sensitive().createOpen); assertEquals(full, f.sensitive().draft) }
                f.session.inbox.dismissError(); f.f.idle()
                // Technical readiness comes from a new actual covered read, never from opening the modal.
                f.refresh(); assertTrue(f.session.inbox.state.value.ready)
                assertTrue(f.root.openCreate())
                rootCheck(errors, "reopen retains full fields/$round") { assertEquals(full, f.sensitive().draft) }
                f.root.editForm(full)
            }
            f.f.auth.held = true
            val beforeAuth = f.f.core.calls.value.size
            val cancelled = f.f.track(f.root.submitCreate()); val prompt = f.f.auth.take()
            prompt.error("Synthetic OS cancellation"); cancelled.join(); f.session.actions.awaitSettled()
            rootCheck(errors, "OS Cancel keeps open input/no Core") {
                assertTrue(f.sensitive().createOpen); assertEquals(full, f.sensitive().draft)
                assertEquals(beforeAuth, f.f.core.calls.value.size)
            }
            f.f.auth.held = false; f.refresh()
            f.root.submitCreate().join(); f.session.actions.awaitSettled()
            assertEquals(NativeCall("create", listOf(full.name, f.active(full))), f.f.core.calls.value.last())
        } finally { f.close() }
    }

    private suspend fun canonicalTerminal(scope: CoroutineScope, newer: Boolean, errors: MutableList<String>) {
        val f = LiveboardFormTestFixture()
        try {
            f.start(); f.f.waitingCreatedSecret(); f.refresh()
            val original = f.draft(); f.open(original)
            val gate = f.f.hold("create")
            val view = f.f.track(f.root.submitCreate()); gate.entered.await()
            val replacement = original.copy(name = "New authored name", value = "new authored value", words = List(24) { "new$it" })
            if (newer) f.root.editForm(replacement)
            gate.release.complete(Unit); gate.exited.await(); view.join(); f.session.actions.awaitSettled()
            rootCheck(errors, "returned attempt is not canonical/$newer") {
                assertEquals(if (newer) replacement else original, f.sensitive().draft)
                assertEquals(LiveboardCreatePhase.Submitted, f.session.actions.createPresentation().phase)
            }
            f.f.core.fresh(); f.refresh()
            rootCheck(errors, "exact terminal only clears unchanged authored version/$newer") {
                assertEquals(if (newer) replacement else null, f.sensitive().draft)
                assertEquals(newer, f.sensitive().createOpen)
                assertEquals(LiveboardCreatePhase.Idle, f.session.actions.createPresentation().phase)
            }
            if (!newer) { assertTrue(f.root.openCreate()); f.root.editForm(replacement) }
            f.root.submitCreate().join(); f.session.actions.awaitSettled()
            assertEquals(2, f.f.core.calls.value.count { it.name == "create" })
            assertEquals(NativeCall("create", listOf(replacement.name, replacement.value)), f.f.core.calls.value.last())
        } finally { f.close() }
    }

    private suspend fun hardBoundaries(errors: MutableList<String>) {
        val f = LiveboardFormTestFixture()
        try {
            f.start(); val full = f.draft(LiveboardDraftMode.Seed, 24); f.open(full)
            val unknown = f.f.hold("create", IllegalStateException("Synthetic uncertain form response"))
            val view = f.f.track(f.root.submitCreate()); unknown.entered.await()
            view.cancel(); view.join(); unknown.release.complete(Unit); unknown.exited.await(); f.session.actions.awaitSettled()
            f.error("Known presentation error")
            rootCheck(errors, "Unknown preserves authored retry input") { assertEquals(full, f.sensitive().draft) }
            val count = f.f.core.calls.value.size
            f.f.socket.onAppBackground()
            rootCheck(errors, "hard background clears full form") { assertNull(f.sensitive().draft); assertFalse(f.sensitive().createOpen) }
            f.f.socket.onAppForeground(); f.refresh()
            f.root.openCreate(); f.root.editForm(full); f.root.submitCreate().join(); f.session.actions.awaitSettled()
            rootCheck(errors, "hard wipe does not unlock private Unknown") {
                assertEquals(count, f.f.core.calls.value.size)
                assertEquals(LiveboardCreatePhase.Unknown, f.session.actions.createPresentation().phase)
            }
            f.fresh(); f.open(full)
            val paste = f.f.track(f.root.pasteCreate()); val pending = f.f.platform.takePaste()
            val old = f.session; f.f.newOwner(); f.attach()
            pending.complete("late key-generation clipboard"); paste.join()
            rootCheck(errors, "key boundary clears ALL old fields") { assertNull(old.sensitive.state.value.draft); assertNull(f.sensitive().draft) }
            f.live(full)
            f.fresh(); f.open(full); val disposed = f.session; disposed.dispose()
            rootCheck(errors, "dispose clears full form/tombstone") {
                assertNull(disposed.sensitive.state.value.draft)
                assertNull(f.f.factory.open(assertNotNull(f.f.reads.frames.value)))
            }
            f.fresh(); f.live(f.draft())
        } finally { f.close() }
    }
}
