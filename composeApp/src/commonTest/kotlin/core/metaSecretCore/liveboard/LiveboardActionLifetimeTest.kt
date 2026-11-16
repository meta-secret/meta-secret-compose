package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LiveboardActionLifetimeTest {
    private data class Probe(val route: String, val expected: NativeResult<*>, val invoke: suspend () -> NativeLiveboardOutcome<*>, val arguments: List<String>? = null)

    @Test
    fun testTypedCommandsKeepPrivateTruthAndExactReferencesWhileQueuedCreateOwnsOnlyItsSelectedDraft() = runBlocking {
        withTimeout(45_000) {
            val failures = mutableListOf<String>()
            val reached = mutableListOf<String>()
            for (route in listOf("create", "recover", "delete", "remove", "join", "decideRemove", "decideDelete", "accept", "decline", "reconcile", "continue", "uncertain")) {
                admitted(this, route, failures, reached)
            }
            queuedCreate(this, failures)
            changedDraftDuringAuth(this, failures)
            for (cancel in listOf(false, true)) formErrorDuringAuth(this, cancel, failures)
            assertEquals(12, reached.size)
            assertEquals(emptyList(), failures, "All typed/raw attempts, actual settlement, references and fresh native controls reached: $reached")
        }
    }

    private suspend fun admitted(scope: CoroutineScope, route: String, failures: MutableList<String>, reached: MutableList<String>) {
        val f = LiveboardFeatureFixture()
        try {
            val session = f.start()
            val profile = f.profile()
            val probe = choose(f, session, if (route == "uncertain") "create" else route)
            val owner = assertNotNull(f.initialization.currentScope())
            val native = f.hold(probe.route, if (route == "uncertain") IllegalStateException("Synthetic lost feature response") else null)
            f.io.queue()
            val view = f.track(scope.async(f.main, CoroutineStart.UNDISPATCHED) { probe.invoke() })
            val worker = f.run(f.io.take(), scope)
            native.entered.await(); f.io.resumeInline()
            view.cancel()
            assertTrue(!native.exited.isCompleted)
            native.release.complete(Unit); native.exited.await(); worker.join(); view.join(); session.awaitSettled()
            assertTrue(view.isCancelled)
            if (route in listOf("create", "uncertain")) {
                session.sensitive.dismissCreate()
                session.sensitive.editDraft("Selected fixture name", "replacement retry payload")
                val beforeSelection = f.core.calls.value.size
                if (session.actions.createIntent() != null) failures.add("$route: closing an admitted Unknown form unlocked retry")
                assertEquals(beforeSelection, f.core.calls.value.size)
            }
            val beforeRepeat = f.core.calls.value.size
            val repeated = probe.invoke()
            val allowedObservation = if (route == "reconcile") 1 else 0
            if (f.core.calls.value.size != beforeRepeat + allowedObservation) failures.add("$route: wrong native repeat count after cancelled view")
            if (route == "reconcile") assertEquals(1, f.core.calls.value.count { it.name == "remove" }, "Observation cannot replay its producer")
            if (repeated !is NativeLiveboardOutcome.Entered) failures.add("$route: actual admitted private outcome was not retained")
            else {
                assertEquals(owner, repeated.scope)
                if (repeated.result != if (route == "uncertain") NativeResult.Uncertain else probe.expected) failures.add("$route: original typed outcome changed after cancelled view")
                assertTrue(!repeated.toString().contains("selected fixture payload"))
            }
            checkArguments(f.core.calls.value.first { it.name == probe.route }, probe)
            assertEquals(if (route == "uncertain") 1 else 0, f.core.failures.value)
            val fresh = f.successor(profile)
            val positive = choose(f, fresh, probe.route)
            val count = f.core.calls.value.size
            val result = assertIs<NativeLiveboardOutcome.Entered<*>>(positive.invoke())
            assertEquals(f.initialization.currentScope(), result.scope)
            assertEquals(positive.expected, result.result)
            assertEquals(count + 1, f.core.calls.value.size)
            checkArguments(f.core.calls.value.last(), positive)
            reached.add("$route/entered/view-cancel/settled/fresh-B")
        } finally { f.close() }
    }

    private suspend fun choose(f: LiveboardFeatureFixture, session: LiveboardSession, route: String): Probe {
        val actions = session.actions
        return when (route) {
            "create" -> {
                session.sensitive.editDraft("Selected fixture name", "selected fixture payload")
                val intent = assertNotNull(actions.createIntent())
                Probe(route, LiveboardReceiptDecoder.decodeChecked(f.core.checked("create")), { actions.create(intent) },
                    listOf("Selected fixture name", "selected fixture payload"))
            }
            "recover" -> {
                f.core.primary("recover"); f.proof()
                val intent = assertNotNull(actions.recoverIntent("pass-b"))
                Probe(route, LiveboardReceiptDecoder.decodeChecked(f.core.checked("recover")), { actions.recover(intent) }, listOf("pass-b"))
            }
            "delete" -> {
                val intent = assertNotNull(actions.deleteIntent("pass-b"))
                Probe(route, LiveboardReceiptDecoder.decodeProducer(f.core.producer("deleteSecret")), { actions.delete(intent) }, listOf("pass-b"))
            }
            "remove" -> {
                val intent = assertNotNull(session.removeDeviceIntent("device-b"))
                Probe(route, LiveboardReceiptDecoder.decodeProducer(f.core.producer()), { actions.remove(intent) }, listOf("device-b"))
            }
            "reconcile", "continue" -> {
                val original = assertNotNull(session.removeDeviceIntent("device-b"))
                val seed = assertIs<NativeLiveboardOutcome.Entered<LiveboardProducerReceipt>>(session.removeDevice(original))
                val receipt = assertIs<NativeResult.Success<LiveboardProducerReceipt>>(seed.result).value
                val reference = LiveboardProducerReference.fromReceipt(receipt).toJson()
                val invoke: suspend () -> NativeLiveboardOutcome<*> = if (route == "reconcile") ({ actions.reconcile(original) })
                else {
                    val intent = assertNotNull(actions.continueRemoveIntent(original))
                    ({ actions.continueRemove(intent) })
                }
                Probe(route, LiveboardReceiptDecoder.decodeProducer(f.core.producer(continued = true)), invoke, listOf(reference))
            }
            else -> {
                val kind = when (route) {
                    "join" -> LiveboardKind.Join
                    "decideRemove" -> LiveboardKind.RemoveDevice
                    "decideDelete" -> LiveboardKind.DeleteSecret
                    else -> LiveboardKind.Recover
                }
                val accepted = route != "decline"
                val intent = assertNotNull(actions.decisionIntent(f.identity(kind), accepted))
                val expected = if (kind in listOf(LiveboardKind.Join, LiveboardKind.RemoveDevice)) {
                    val receipt = assertIs<NativeResult.Success<LiveboardCheckedReceipt>>(LiveboardReceiptDecoder.decodeChecked(
                        f.core.checked(FeatureIds.wire(kind), FeatureIds.request(kind)),
                    )).value
                    NativeResult.Success(LiveboardDecisionAttempt.Checked(receipt))
                } else NativeResult.Success(LiveboardDecisionAttempt.Returned(kind, FeatureIds.request(kind), accepted))
                Probe(route, expected, { actions.decide(intent) }, if (kind == LiveboardKind.Recover) listOf(FeatureIds.claim) else null)
            }
        }
    }

    private fun checkArguments(call: NativeCall, probe: Probe) {
        assertEquals(probe.route, call.name)
        if (probe.arguments != null) assertEquals(probe.arguments, call.arguments)
        else {
            val request = Json.parseToJsonElement(call.arguments.single()).jsonObject
            assertEquals(setOf("requestId", "accepted"), request.keys)
            val expected = when (probe.route) { "join" -> FeatureIds.join; "decideRemove" -> FeatureIds.remove; else -> FeatureIds.delete }
            assertEquals(expected, request.getValue("requestId").jsonPrimitive.content)
            assertEquals("true", request.getValue("accepted").jsonPrimitive.content)
        }
    }

    private suspend fun queuedCreate(scope: CoroutineScope, failures: MutableList<String>) {
        val f = LiveboardFeatureFixture()
        try {
            val session = f.start(); val profile = f.profile()
            session.sensitive.editDraft("Queued A", "queued A payload")
            val original = assertNotNull(session.actions.createIntent())
            f.io.queue()
            val view = f.track(scope.async(f.main, CoroutineStart.UNDISPATCHED) { session.actions.create(original) })
            val old = f.io.take()
            view.cancel(); f.io.resumeInline()
            session.sensitive.dismissCreate()
            if (session.sensitive.state.value.draft != null) failures.add("Queued cancelled Create prevented idle form close")
            session.sensitive.editDraft("Fresh B", "fresh B payload")
            val fresh = session.actions.createIntent()
            if (fresh == null) failures.add("Queued cancelled Create still owns the draft selection")
            else {
                val result = assertIs<NativeLiveboardOutcome.Entered<*>>(session.actions.create(fresh))
                assertIs<NativeResult.Success<*>>(result.result)
                assertEquals(NativeCall("create", listOf("Fresh B", "fresh B payload")), f.core.calls.value.last())
            }
            val count = f.core.calls.value.size
            val liveB = f.successor(profile) // Must finish while the old queued runnable remains held.
            f.run(old, scope).join(); view.join(); session.awaitSettled()
            assertEquals(count, f.core.calls.value.size)
            assertTrue(f.core.calls.value.none { "queued A payload" in it.arguments })
            liveB.sensitive.editDraft("Actual B", "actual B payload")
            val actualB = assertNotNull(liveB.actions.createIntent())
            assertIs<NativeResult.Success<*>>(assertIs<NativeLiveboardOutcome.Entered<*>>(liveB.actions.create(actualB)).result)
            assertEquals(NativeCall("create", listOf("Actual B", "actual B payload")), f.core.calls.value.last())
        } finally { f.close() }
    }

    private suspend fun changedDraftDuringAuth(scope: CoroutineScope, failures: MutableList<String>) {
        val f = LiveboardFeatureFixture()
        try {
            val session = f.start()
            val profile = f.profile()
            session.sensitive.editDraft("Draft A", "payload A")
            val original = assertNotNull(session.actions.createIntent())
            f.auth.held = true
            val action = f.track(scope.async(f.main, CoroutineStart.UNDISPATCHED) { session.actions.create(original) })
            val callback = f.auth.take()
            session.sensitive.editDraft("Draft B", "payload B")
            callback.success(); action.await(); session.awaitSettled()
            if (f.core.calls.value.isNotEmpty()) failures.add("Old Create auth used an invalidated draft version/payload")
            f.auth.held = false
            session.inbox.dismissError(); f.proof()
            val fresh = session.actions.createIntent()
            val count = f.core.calls.value.size
            if (fresh == null) failures.add("Invalidated original Create still prevented fresh same-owner draft B selection")
            else {
                assertIs<NativeResult.Success<*>>(assertIs<NativeLiveboardOutcome.Entered<*>>(session.actions.create(fresh)).result)
                assertEquals(listOf(NativeCall("create", listOf("Draft B", "payload B"))), f.core.calls.value.drop(count))
            }
            session.dispose()
            if (f.factory.open(assertNotNull(f.reads.frames.value)) != null) failures.add("Disposed same-key factory reopened an empty command journal")
            val liveB = f.successor(profile)
            liveB.sensitive.editDraft("Live retry", "live retry payload")
            val liveIntent = assertNotNull(liveB.actions.createIntent())
            assertIs<NativeLiveboardOutcome.Entered<*>>(liveB.actions.create(liveIntent))
            assertEquals(NativeCall("create", listOf("Live retry", "live retry payload")), f.core.calls.value.last())
        } finally { f.close() }
    }

    private suspend fun formErrorDuringAuth(scope: CoroutineScope, cancel: Boolean, failures: MutableList<String>) {
        val f = LiveboardFeatureFixture()
        try {
            val session = f.start(); val profile = f.profile()
            f.disclose(session)
            session.sensitive.editDraft("Retry form", "authored retry payload")
            val selected = assertNotNull(session.actions.createIntent())
            f.auth.held = true
            val action = f.track(scope.async(f.main, CoroutineStart.UNDISPATCHED) { session.actions.create(selected) })
            val callback = f.auth.take()
            if (cancel) callback.error("Fixture OS cancellation")
            else {
                f.notifications.showError("Queued form fixture error")
                f.seenError(session, "Queued form fixture error")
                callback.success()
            }
            if (!f.disclosuresEmpty(session)) failures.add("form/$cancel: ordinary error or auth Cancel retained revealed content")
            if (action.isCancelled) action.join() else action.await()
            session.awaitSettled()
            if (f.core.calls.value.any { it.name == "create" }) failures.add("form/$cancel: revoked selected payload entered Core")
            if (session.sensitive.state.value.draft != LiveboardDraft("Retry form", "authored retry payload")) failures.add("form/$cancel: ordinary error or auth Cancel erased the authored retry draft")
            f.notifications.dismiss()
            val live = f.successor(profile)
            live.sensitive.editDraft("Fresh retry", "fresh retry payload")
            val retry = assertNotNull(live.actions.createIntent())
            assertIs<NativeLiveboardOutcome.Entered<*>>(live.actions.create(retry))
            assertEquals(NativeCall("create", listOf("Fresh retry", "fresh retry payload")), f.core.calls.value.last())
        } finally { f.close() }
    }
}
