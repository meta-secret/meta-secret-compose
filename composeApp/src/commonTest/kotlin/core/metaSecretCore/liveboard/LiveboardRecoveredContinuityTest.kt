package core.metaSecretCore.liveboard

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import testutils.FakeStringProvider
import ui.liveboard.LiveboardRootTestFixture

class LiveboardRecoveredContinuityTest {
    private val wire = RecoveredRevealFixture

    @Test
    fun testOriginalFrameThenPhysicalDoneRestoresSamePubliclyDeliveredRootCard() = runBlocking {
        withTimeout(45_000) {
            val f = LiveboardRootTestFixture()
            try {
                wire.install(f); f.start()
                assertNotNull(f.factory.open(assertNotNull(f.reads.frames.value)), "R32_CONTEXT_GATE_BEFORE_ROOT")
                val root = f.root(); val session = f.session(root)
                root.show("pass-b").join()
                val disclosure = assertNotNull(session.sensitive.state.value.revealed["pass-b"], "Already publicly delivered")
                assertEquals(wire.value, session.sensitive.copyValue("pass-b"), "Original Accepted frame remains current before read")
                root.copy("pass-b"); assertEquals(listOf(wire.value), f.platform.copies)
                val calls = f.core.calls.value.toList(); val auth = f.auth.calls; val reads = f.core.reads.value
                val target = assertNotNull(f.reads.requireFreshRead())
                val held = f.hold("read")
                val reading = f.track(async(Dispatchers.IO) { f.manager.getStateModel() })
                held.entered.await()
                assertTrue(session.sensitive.state.value.revealed.isEmpty())
                assertNull(session.sensitive.copyValue("pass-b")); root.copy("pass-b")
                assertEquals(listOf(wire.value), f.platform.copies)
                held.release.complete(Unit); held.exited.await(); reading.await(); f.idle()
                assertEquals(NativeReadOutcome.SUCCESS, target.completion.await().outcome)
                assertTrue(session.inbox.state.value.ready)
                assertSame(disclosure, session.sensitive.state.value.revealed["pass-b"], "R32_SEMANTIC_DONE_SAME_DELIVERED_CARD")
                assertEquals(wire.value, session.sensitive.copyValue("pass-b")); root.copy("pass-b")
                assertEquals(listOf(wire.value, wire.value), f.platform.copies)
                f.proof(); f.idle()
                assertSame(disclosure, session.sensitive.state.value.revealed["pass-b"])
                assertEquals(calls, f.core.calls.value); assertEquals(auth, f.auth.calls)
                assertEquals(reads + 2, f.core.reads.value)
                assertFalse(session.sensitive.show("pass-b"), "Done is not new Show permission")
                assertEquals(calls, f.core.calls.value); assertEquals(auth, f.auth.calls)
            } finally { f.close() }
        }
    }

    @Test
    fun testLegacyRecoveredWithoutReceiptCannotRestoreUnchangedAccepted() = runBlocking {
        val f = LiveboardRootTestFixture()
        var session: LiveboardSession? = null
        try {
            f.core.state.value = wire.member(proof = null)
            f.core.recoveredFixture = RecoveredFixtureReply(FeatureIds.claim, wire.response(null), null)
            f.start()
            val current = assertNotNull(f.factory.open(assertNotNull(f.reads.frames.value))).also { session = it }
            assertTrue(current.sensitive.show("pass-b"))
            assertEquals(wire.value, current.sensitive.copyValue("pass-b"))
            val calls = f.core.calls.value.toList(); val auth = f.auth.calls; val reads = f.core.reads.value
            f.proof(); f.idle()
            assertEquals(reads + 1, f.core.reads.value)
            assertEquals(calls, f.core.calls.value); assertEquals(auth, f.auth.calls)
            assertNull(current.sensitive.copyValue("pass-b"), "R32_LEGACY_UNCHANGED_ACCEPTED_NOT_RESTORABLE")
            assertTrue(current.sensitive.state.value.revealed.isEmpty())
        } finally { session?.dispose(); f.close() }
    }

    @Test
    fun testLegacyMissingProofAndStaleAcceptedNeverRestoreAfterPhysicalRead() = runBlocking {
        withTimeout(45_000) {
            val failures = mutableListOf<String>()
            for (mode in listOf("legacy-missing", "receipt-missing", "stale-accepted", "receipt-mismatch")) {
                val f = LiveboardRootTestFixture()
                var session: LiveboardSession? = null
                try {
                    if (mode == "legacy-missing") {
                        f.core.state.value = wire.member(proof = null)
                        f.core.recoveredFixture = RecoveredFixtureReply(FeatureIds.claim, wire.response(null), null)
                    } else {
                        wire.install(f, receiptPresent = mode != "receipt-missing", completes = mode != "stale-accepted")
                        if (mode == "receipt-mismatch") f.core.recoveredFixture = RecoveredFixtureReply(FeatureIds.claim,
                            wire.response(wire.receipt(proof = wire.context(wire.freshClaim))), wire.member(done = true))
                    }
                    f.start(); val current = assertNotNull(f.factory.open(assertNotNull(f.reads.frames.value))).also { session = it }
                    assertTrue(current.sensitive.show("pass-b"), "Legacy initial Show checks remain available/$mode")
                    val calls = f.core.calls.value.toList(); val auth = f.auth.calls
                    f.proof(); f.idle()
                    if (current.sensitive.copyValue("pass-b") != null || current.sensitive.state.value.revealed.isNotEmpty()) failures.add(mode)
                    f.core.state.value = wire.member(done = true); f.proof(); f.idle()
                    if (current.sensitive.copyValue("pass-b") != null) failures.add("later-matching/$mode")
                    assertEquals(calls, f.core.calls.value); assertEquals(auth, f.auth.calls)
                } finally { session?.dispose(); f.close() }
            }
            assertEquals(emptyList(), failures, "R32_POST_READ_REQUIRES_ORIGINAL_RECEIPT_AND_DONE")
        }
    }

    @Test
    fun testHardBarriersAndFailedReadCannotReviveMatchingDone() = runBlocking {
        withTimeout(60_000) {
            for (barrier in listOf("hide", "search", "route", "error", "failed", "background", "member", "key", "disposed")) {
                val f = LiveboardRootTestFixture()
                try {
                    wire.install(f); f.start()
                    assertNotNull(f.factory.open(assertNotNull(f.reads.frames.value)), "R32_CONTEXT_GATE_BEFORE_ROOT")
                    val root = f.root(); val session = f.session(root)
                    root.show("pass-b").join(); assertNotNull(session.sensitive.state.value.revealed["pass-b"])
                    val calls = f.core.calls.value.toList(); val auth = f.auth.calls
                    val held = f.hold("read")
                    val reading = f.track(async(Dispatchers.IO) {
                        try { f.manager.getStateModel(); null } catch (failure: IllegalStateException) { failure }
                    })
                    held.entered.await(); assertNull(session.sensitive.copyValue("pass-b"))
                    when (barrier) {
                        "hide" -> root.hide("pass-b")
                        "search" -> root.searchChanged("new query")
                        "route" -> session.sensitive.routeChanged()
                        "error" -> f.notifications.showError("Synthetic continuity error")
                        "failed" -> f.core.failRead = true
                        "background" -> f.socket.onAppBackground()
                        "disposed" -> session.dispose()
                    }
                    held.release.complete(Unit); held.exited.await()
                    val failure = reading.await(); f.core.failRead = false; f.idle()
                    if (barrier == "failed") assertNotNull(failure) else assertNull(failure)
                    if (barrier == "member") {
                        val retiredScope = assertNotNull(f.initialization.currentScope())
                        f.core.state.value = f.removedState(true); f.manager.getStateModel()
                        val navigation = assertNotNull(root.state.first { it.chrome.navigation != null }.chrome.navigation)
                        assertEquals(retiredScope, navigation.receipt.retiredScope)
                        assertNull(f.initialization.currentScope()); f.newOwner()
                    }
                    if (barrier == "key") f.newOwner()
                    if (barrier == "background") { f.socket.onAppForeground(); f.socket.pauseRefreshesAndAwait() }
                    f.core.state.value = wire.member(done = true); f.proof(); f.idle()
                    assertNull(session.sensitive.copyValue("pass-b"), "Terminal barrier/$barrier")
                    assertTrue(session.sensitive.state.value.revealed.isEmpty(), "No matching-Done revival/$barrier")
                    assertEquals(calls, f.core.calls.value); assertEquals(auth, f.auth.calls)
                } finally { f.core.failRead = false; f.close() }
            }
        }
    }

    @Test
    fun testChangedProofAndRemainingSecretFieldsConcealPermanently() = runBlocking {
        withTimeout(60_000) {
            val changes = listOf("source-body", "vault-revision", "claim-scope", "name", "type", "wordCount", "canDelete", "deletion", "new-claim")
            for (change in changes) {
                val f = LiveboardRootTestFixture()
                var session: LiveboardSession? = null
                try {
                    wire.install(f); f.start()
                    val current = assertNotNull(f.factory.open(assertNotNull(f.reads.frames.value))).also { session = it }
                    assertTrue(current.sensitive.show("pass-b"))
                    val calls = f.core.calls.value.toList(); val auth = f.auth.calls
                    val done = Json.parseToJsonElement(wire.member(done = true))
                    val path = listOf("message", "state", "vault", "member", "liveboardState")
                    val changed = wire.edit(done, path) { metadata ->
                        val map = metadata.jsonObject.toMutableMap()
                        if (change in listOf("source-body", "vault-revision", "claim-scope")) {
                            val operation = map.getValue("operations").jsonArray.single()
                            val context = operation.jsonObject.getValue("revealContext")
                            val proof = when (change) {
                                "source-body" -> wire.edit(context, listOf("localDistributionSource")) { wire.field(it, "bodyCommitment", JsonPrimitive("34".repeat(32))) }
                                "vault-revision" -> wire.field(context, "vaultSource", wire.source("Vault", "vault-source", "2"))
                                else -> wire.field(context, "claimScopeCommitment", JsonPrimitive("56".repeat(32)))
                            }
                            map["operations"] = JsonArray(listOf(wire.field(operation, "revealContext", proof)))
                        } else map["secrets"] = JsonArray(map.getValue("secrets").jsonArray.map { item ->
                            if (item.jsonObject["secretId"] != JsonPrimitive("pass-b")) item else when (change) {
                                "canDelete" -> wire.field(item, "canDelete", JsonPrimitive(true))
                                "deletion" -> wire.field(item, "deletionRequestId", JsonPrimitive("deletion-identity"))
                                "new-claim" -> wire.field(wire.field(item, "primaryAction", JsonPrimitive("showRecovered")), "recoveryClaimId", JsonPrimitive(wire.freshClaim))
                                else -> item
                            }
                        })
                        JsonObject(map)
                    }
                    f.core.state.value = if (change in listOf("name", "type", "wordCount")) {
                        wire.edit(changed, listOf("message", "state", "vault", "member", "member", "vault", "secrets")) { secrets ->
                            JsonArray(secrets.jsonArray.map { secret ->
                                if (secret.jsonObject["id"] != JsonPrimitive("pass-b")) secret else
                                    wire.field(secret, change, if (change == "wordCount") JsonPrimitive(24) else JsonPrimitive("changed-$change"))
                            })
                        }.toString()
                    } else changed.toString()
                    f.proof(); f.idle(); assertNull(current.sensitive.copyValue("pass-b"), change)
                    f.core.state.value = wire.member(done = true); f.proof(); f.idle()
                    assertNull(current.sensitive.copyValue("pass-b"), "No later matching proof/$change")
                    assertEquals(calls, f.core.calls.value); assertEquals(auth, f.auth.calls)
                } finally { session?.dispose(); f.close() }
            }
        }
    }

    @Test
    fun testHeldNativeOrUiReturnIsNeverAReadRestorableCard() = runBlocking {
        withTimeout(45_000) {
            for (mode in listOf("native", "ui")) {
                val f = LiveboardRootTestFixture()
                val ui = NativeCheckedIo()
                var session: LiveboardSession? = null
                try {
                    wire.install(f); f.start()
                    val factory = LiveboardSessionFactory(f.checked, f.reads, f.operations,
                        NativeReadEnvironment(Dispatchers.IO, ui), LiveboardClientPorts(f.privacy, f.auth, f.socket, f.notifications, FakeStringProvider()))
                    val current = assertNotNull(factory.open(assertNotNull(f.reads.frames.value))).also { session = it }
                    val held = f.hold("recovered")
                    val showing = f.track(async(f.main, CoroutineStart.UNDISPATCHED) { current.sensitive.show("pass-b") })
                    held.entered.await()
                    if (mode == "ui") ui.queue()
                    if (mode == "native") current.sensitive.hide("pass-b")
                    held.release.complete(Unit); held.exited.await()
                    val heldUi = if (mode == "ui") ui.take() else null
                    f.proof(); f.idle()
                    assertTrue(current.sensitive.state.value.revealed.isEmpty())
                    ui.resumeInline(); if (heldUi != null) f.publish(heldUi, this)
                    assertFalse(showing.await(), "Undelivered result cannot be revived/$mode")
                    assertNull(current.sensitive.copyValue("pass-b"))
                    assertEquals(1, f.auth.calls)
                    assertEquals(listOf(NativeCall("recovered", listOf(FeatureIds.claim))), f.core.calls.value)
                } finally { f.finishControlled(ui); session?.dispose(); f.close() }
            }
        }
    }
}
