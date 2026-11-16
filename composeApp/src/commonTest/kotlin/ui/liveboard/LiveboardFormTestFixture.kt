package ui.liveboard

import core.metaSecretCore.liveboard.*
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Delegates to the SAME Sensitive owner and actual Root/action/auth paths. */
internal class LiveboardFormTestFixture {
    val f = LiveboardRootTestFixture()
    lateinit var root: LiveboardRootCoordinator
    lateinit var session: LiveboardSession
    suspend fun start() { f.start(); attach() }
    suspend fun attach() { root = f.root(); session = f.session(root) }
    suspend fun fresh() { f.newOwner(); attach() }
    fun draft(mode: LiveboardDraftMode = LiveboardDraftMode.Plain, count: Int = 12): LiveboardDraft =
        LiveboardDraft("selected name", "inactive plain", mode, count, List(24) { "word${it + 1}" })
    fun open(value: LiveboardDraft) { assertTrue(root.openCreate()); root.editForm(value) }
    fun active(value: LiveboardDraft): String = if (value.mode == LiveboardDraftMode.Plain) value.value
        else value.words.take(value.wordCount).joinToString(" ") { it.trim() }
    suspend fun live(value: LiveboardDraft) {
        open(value)
        val before = f.core.calls.value.size
        root.submitCreate().join(); session.actions.awaitSettled()
        assertEquals(before + 1, f.core.calls.value.size)
        assertEquals(NativeCall("create", listOf(value.name, active(value))), f.core.calls.value.last())
    }
    suspend fun error(text: String) {
        f.notifications.showError(text)
        session.inbox.state.first { it.error == text }
    }
    suspend fun refresh() { f.proof(); f.idle() }
    fun sensitive() = session.sensitive.state.value
    suspend fun close() = f.close()
}
