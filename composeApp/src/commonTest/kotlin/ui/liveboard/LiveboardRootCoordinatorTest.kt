package ui.liveboard

import core.metaSecretCore.liveboard.*
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import models.appInternalModels.SocketRequestModel

@OptIn(ExperimentalCoroutinesApi::class)
class LiveboardRootCoordinatorTest {
    @Test
    fun testPhysicalProofPrivateSettlementAndRemountUseOneSession() = runBlocking {
        withTimeout(45_000) {
            val errors = mutableListOf<String>()
            for (uncertain in listOf(false, true)) privateCreate(this, uncertain, errors)
            snapshotSettlement(this, errors)
            privateRemove(this, errors)
            recoverDecline(errors)
            assertEquals(emptyList(), errors, "All private settlement/remount and exact Recover tails reached")
        }
    }

    private suspend fun privateCreate(scope: CoroutineScope, uncertain: Boolean, errors: MutableList<String>) {
        val f = LiveboardRootTestFixture()
        try {
            f.start(); f.waitingCreatedSecret(); f.proof()
            val prior = assertNotNull(f.reads.frames.value)
            f.cache.updateCache(assertNotNull(prior.state))
            assertNull(f.factory.open(prior), "Generic cache is not physical Member authority")
            f.proof()
            val root = f.root(); val session = f.session(root); f.idle()
            val flags = setOf(SocketRequestModel.GET_STATE, SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN)
            rootCheck(errors, "mount flags") { assertTrue(f.follows.value.flatMap { it.add }.containsAll(flags)) }
            val inits = f.trace.count("core.init")
            for (route in LiveboardRootRoute.entries) {
                root.selectRoute(route); assertSame(session, f.content(root).session)
            }
            assertEquals(inits, f.trace.count("core.init"))
            assertTrue(root.openCreate()); root.editForm(LiveboardDraft("Private A", "private value A"))
            val gate = f.hold("create", if (uncertain) IllegalStateException("Synthetic root response loss") else null)
            val view = f.track(root.submitCreate()); gate.entered.await()
            rootCheck(errors, "admitted Busy") { assertEquals(LiveboardCreatePhase.Busy, session.actions.createPresentation().phase) }
            view.cancel(); view.join(); assertFalse(gate.exited.isCompleted)
            root.close()
            val ui = StandardTestDispatcher()
            val next = f.root(ui); ui.scheduler.runCurrent()
            assertSame(session, f.session(next)); f.idle(); ui.scheduler.runCurrent()
            val before = next.state.value.chrome.revision
            // Register the test drain while native is held, AFTER remount's own pending observer.
            // Its completion dispatch follows the earlier Root waiter; consume owned UI tasks,
            // without requiring a missing Root event to arrive before recording semantic failure.
            val settled = f.track(scope.async(start = CoroutineStart.UNDISPATCHED) { session.actions.awaitSettled() })
            gate.release.complete(Unit); gate.exited.await(); settled.await()
            ui.scheduler.runCurrent()
            val expected = if (uncertain) LiveboardCreatePhase.Unknown else LiveboardCreatePhase.Submitted
            rootCheck(errors, "private Create phase/$uncertain") { assertEquals(expected, session.actions.createPresentation().phase) }
            rootCheck(errors, "root settlement observer/$uncertain") { assertNotSame(before, next.state.value.chrome.revision) }
            val attached = f.root(); assertSame(session, f.session(attached))
            val calls = f.core.calls.value.size
            attached.openCreate(); attached.editForm(LiveboardDraft("Private A", "new retry payload"))
            attached.submitCreate().join(); session.actions.awaitSettled()
            rootCheck(errors, "remount no resubmit/$uncertain") { assertEquals(calls, f.core.calls.value.size) }
            assertEquals(1, f.core.calls.value.count { it.name == "create" })
            f.newOwner(); val fresh = f.root(); val live = f.session(fresh)
            assertNotSame(session, live)
            assertTrue(fresh.openCreate()); fresh.editForm(LiveboardDraft("Fresh", "fresh value"))
            fresh.submitCreate().join(); live.actions.awaitSettled()
            assertEquals(NativeCall("create", listOf("Fresh", "fresh value")), f.core.calls.value.last())
            assertEquals(calls + 1, f.core.calls.value.size)
        } finally { f.close() }
    }

    private suspend fun snapshotSettlement(scope: CoroutineScope, errors: MutableList<String>) {
        val f = LiveboardRootTestFixture()
        try {
            f.start(); f.core.primary("recover"); f.proof(); val session = f.session(f.root())
            session.sensitive.editDraft("Snapshot", "snapshot payload")
            val create = assertNotNull(session.actions.createIntent())
            val recover = assertNotNull(session.actions.recoverIntent("pass-b"))
            val first = f.hold("create"); val second = f.hold("recover")
            val creating = f.track(scope.async { session.actions.create(create) })
            val recovering = f.track(scope.async { session.actions.recover(recover) })
            first.entered.await(); second.entered.await()
            val drain = f.track(scope.async(start = CoroutineStart.UNDISPATCHED) { session.actions.awaitSettled() })
            first.release.complete(Unit); creating.await()
            rootCheck(errors, "snapshot includes unrelated captured worker") { assertFalse(drain.isCompleted) }
            second.release.complete(Unit); recovering.await(); drain.await()
            assertEquals(setOf("create", "recover"), f.core.calls.value.map { it.name }.toSet())
        } finally { f.close() }
    }

    private suspend fun privateRemove(scope: CoroutineScope, errors: MutableList<String>) {
        val f = LiveboardRootTestFixture()
        try {
            f.start(); val root = f.root(); val session = f.session(root)
            f.socket.pauseRefreshesAndAwait()
            val intent = assertNotNull(session.removeDeviceIntent("device-b"))
            f.auth.held = true
            val view = f.track(scope.async { session.actions.remove(intent) })
            val prompt = f.auth.take()
            rootCheck(errors, "OS Busy metadata") { assertEquals(LiveboardActionPhase.Busy,
                session.actions.presentation().single { it.target == LiveboardActionTarget.Remove("device-b") }.phase) }
            val gate = f.hold("remove"); prompt.success(); gate.entered.await(); f.auth.held = false
            view.cancel(); view.join(); root.close()
            val next = f.root(); assertSame(session, f.session(next))
            gate.release.complete(Unit); gate.exited.await(); session.awaitSettled()
            rootCheck(errors, "typed-success producer Unknown") { assertEquals(LiveboardActionPhase.Unknown,
                session.actions.presentation().single { it.target == LiveboardActionTarget.Remove("device-b") }.phase) }
            assertIs<NativeResult.Success<*>>(assertNotNull(session.retainedProducer(intent)).result)
            assertNull(session.removeDeviceIntent("device-b"))
            assertEquals(1, f.core.calls.value.count { it.name == "remove" })
            val pendingProof = f.proof()
            assertTrue(pendingProof.coveredRequirements.size >= 2, "Actual pending read consumes original Remove target plus explicit probe")
            for (wrong in listOf("request", "target")) {
                f.removeDeclined(wrong); f.proof()
                rootCheck(errors, "wrong Remove $wrong stays locked") { assertNull(session.removeDeviceIntent("device-b")) }
            }
            f.removeDeclined(); val terminal = f.proof()
            assertNotEquals(pendingProof.attempt, terminal.attempt)
            assertEquals(1, terminal.coveredRequirements.size, "Original fulfilled target is absent from later physical coverage")
            val retry = session.removeDeviceIntent("device-b")
            rootCheck(errors, "fulfilled proof permits later exact canonical Remove retry") { assertNotNull(retry) }
            if (retry != null) {
                assertIs<NativeResult.Success<*>>(assertIs<NativeLiveboardOutcome.Entered<*>>(session.actions.remove(retry)).result)
                assertEquals(2, f.core.calls.value.count { it.name == "remove" })
            }
            val beforeFresh = f.core.calls.value.count { it.name == "remove" }
            f.newOwner(); val current = f.session(f.root())
            val positive = assertNotNull(current.removeDeviceIntent("device-b"))
            assertIs<NativeResult.Success<*>>(assertIs<NativeLiveboardOutcome.Entered<*>>(current.actions.remove(positive)).result)
            assertEquals(beforeFresh + 1, f.core.calls.value.count { it.name == "remove" })
        } finally { f.close() }
    }

    private suspend fun recoverDecline(errors: MutableList<String>) {
        val f = LiveboardRootTestFixture()
        try {
            f.start(); f.core.primary("recover"); f.proof()
            val session = f.session(f.root())
            val first = assertNotNull(session.actions.recoverIntent("pass-b"))
            assertIs<NativeResult.Success<*>>(assertIs<NativeLiveboardOutcome.Entered<*>>(session.actions.recover(first)).result)
            for (wrong in listOf("claim", "requester")) {
                f.recoverDeclined(wrong); f.proof()
                rootCheck(errors, "wrong $wrong cannot release") { assertNull(session.actions.recoverIntent("pass-b")) }
            }
            f.recoverDeclined(); f.proof()
            val retry = session.actions.recoverIntent("pass-b")
            rootCheck(errors, "verified canonical decline permits retry") { assertNotNull(retry) }
            if (retry != null) {
                assertIs<NativeResult.Success<*>>(assertIs<NativeLiveboardOutcome.Entered<*>>(session.actions.recover(retry)).result)
                assertEquals(2, f.core.calls.value.count { it.name == "recover" })
            }
            f.newOwner(); f.core.primary("recover"); f.proof()
            val successorRoot = f.root()
            val fresh = f.session(successorRoot)
            assertNotSame(session, fresh)
            combine(successorRoot.state, f.reads.frames, f.cache.synchronization, fresh.inbox.state) {
                    root, frame, sync, inbox ->
                val ready = root.content?.session === fresh && frame != null && frame.scope == fresh.scope &&
                    frame.outcome == NativeReadOutcome.SUCCESS && fresh.isFrameCurrent(frame) &&
                    !sync.loading && !sync.failed && sync.activeRefreshes == 0 && inbox.ready &&
                    fresh.isReadyForEntry()
                if (!ready) false else {
                    val primary = fresh.currentMember()?.secrets?.singleOrNull { it.identity.passId == "pass-b" }?.primary
                    primary?.action == "recover" && primary.readiness == "ready" &&
                        fresh.isFrameCurrent(checkNotNull(frame)) && successorRoot.state.value.content?.session === fresh
                }
            }.first { it }
            val live = assertNotNull(fresh.actions.recoverIntent("pass-b"))
            assertIs<NativeResult.Success<*>>(assertIs<NativeLiveboardOutcome.Entered<*>>(fresh.actions.recover(live)).result)
            assertEquals(NativeCall("recover", listOf("pass-b")), f.core.calls.value.last())
        } finally { f.close() }
    }

    @Test
    fun testRealRemovalProfileRetryAndLateResetCannotNavigateSuccessor() = runBlocking {
        withTimeout(45_000) {
            val errors = mutableListOf<String>()
            for (declined in listOf(false, true)) removal(declined, errors)
            profileRetry(errors)
            staleReset(this, errors)
            assertEquals(emptyList(), errors, "Removal/false-clear/retry/late-A and fresh B controls reached")
        }
    }

    private suspend fun removal(declined: Boolean, errors: MutableList<String>) {
        val f = LiveboardRootTestFixture()
        try {
            f.start(); val root = f.root(); f.session(root); f.idle()
            val before = f.clears()
            f.core.failRead = true
            assertFailsWith<IllegalStateException> { f.manager.getStateModel() }
            f.core.failRead = false
            rootCheck(errors, "failed read is not removal") { assertEquals(before, f.clears()); assertNull(root.state.value.chrome.navigation) }
            f.core.fresh(); f.proof()
            f.keys.clearReturns = false; f.core.state.value = f.removedState(declined)
            val originScope = assertNotNull(f.initialization.currentScope())
            val reads = f.core.reads.value
            f.manager.getStateModel()
            root.state.first { it.chrome.navigation != null }
            assertEquals(before + 1, f.clears())
            val ticket = assertNotNull(root.state.value.chrome.navigation)
            assertTrue(f.core.reads.value > reads)
            assertEquals(originScope, ticket.receipt.retiredScope)
            assertFalse(ticket.receipt.value); assertNull(ticket.receipt.failure)
            assertTrue(f.reset.isCurrent(ticket.receipt)); assertNull(f.initialization.currentScope())
            assertTrue(root.consumeNavigation(ticket)); assertNull(root.state.value.chrome.navigation)
            f.keys.clearReturns = true; f.newOwner()
            val fresh = f.root(); assertNotNull(f.content(fresh).member)
            assertNull(fresh.state.value.chrome.navigation)
        } finally { f.close() }
    }

    private suspend fun profileRetry(errors: MutableList<String>) {
        val f = LiveboardRootTestFixture()
        try {
            f.start(); val root = f.root(); f.session(root); root.selectRoute(LiveboardRootRoute.Profile)
            val owner = f.initialization.capture(); val clears = f.clears()
            f.keys.clearReturns = false; root.resetProfile().join()
            rootCheck(errors, "false clear stays retryable Profile") {
                assertEquals(LiveboardRootReset.Failed, root.state.value.chrome.reset)
                assertNull(root.state.value.chrome.navigation); assertEquals(clears + 1, f.clears())
            }
            assertNotEquals(owner, f.initialization.capture()); assertNull(f.initialization.currentScope())
            f.keys.clearReturns = true; root.resetProfile().join()
            rootCheck(errors, "retry owns receipt C") { assertEquals(clears + 2, f.clears()); assertNotNull(root.state.value.chrome.navigation) }
            f.start(); assertNotNull(f.content(f.root()).member)
        } finally { f.close() }
    }

    private suspend fun staleReset(scope: CoroutineScope, errors: MutableList<String>) {
        val f = LiveboardRootTestFixture()
        try {
            f.start(); val ui = NativeCheckedIo(); val old = f.root(ui); f.session(old); f.idle()
            val clears = f.clears(); val origin = f.initialization.capture()
            f.reads.invalidate() // Consume null-frame observer while inline, before the Reset UI queue.
            assertTrue(f.initialization.isCurrent(origin)); ui.queue()
            val view = f.track(old.resetProfile())
            f.publish(ui.take(), scope)
            f.trace.calls.first { calls -> calls.count { it.name == "key.clear" } > clears }
            val receiptOwner = f.initialization.capture()
            while (!f.initialization.isCurrent(receiptOwner)) yield()
            assertNotSame(origin, receiptOwner)
            val lateUi = ui.take() // Actual post-receipt Root task is queued while C is still current.
            // Real old key work is fully finished before saving B; only Root publication stays held.
            f.start(); assertTrue(f.currentOwnerFinished())
            val current = f.root(); val session = f.session(current); val frame = assertNotNull(f.reads.frames.value)
            val before = f.clears(); val reads = f.core.reads.value; val inits = f.trace.count("core.init")
            val late = assertNotNull(f.reads.requireFreshRead())
            f.publish(lateUi, scope); f.finishControlled(ui); view.join()
            rootCheck(errors, "late A cannot affect B") {
                assertNull(old.state.value.chrome.navigation); assertNull(current.state.value.chrome.navigation)
                assertSame(frame, f.reads.frames.value); assertEquals(before, f.clears())
                assertEquals(reads, f.core.reads.value); assertEquals(inits, f.trace.count("core.init"))
                assertFalse(late.completion.isCompleted)
            }
            f.proof(); assertTrue(late.completion.isCompleted)
            assertSame(session, f.content(current).session)
        } finally { f.close() }
    }

    @Test
    fun testBackAndOldCloseBelongOnlyToExactCurrentAttachment() = runBlocking {
        withTimeout(30_000) {
            val f = LiveboardRootTestFixture(); val errors = mutableListOf<String>()
            try {
                f.start(); val old = f.root(); val session = f.session(old)
                old.selectRoute(LiveboardRootRoute.Secrets); assertTrue(old.openCreate())
                old.editForm(LiveboardDraft("Old", "old form")); val staleBack = f.platform.attachments.last().back
                val current = f.root(); assertSame(session, f.session(current))
                current.selectRoute(LiveboardRootRoute.Profile); assertTrue(current.openCreate())
                val draft = LiveboardDraft("Current", "current form"); current.editForm(draft)
                val follows = f.follows.value.size
                rootCheck(errors, "stale Back") { assertFalse(staleBack()); assertEquals(draft, session.sensitive.state.value.draft) }
                old.close()
                rootCheck(errors, "stale close") {
                    assertEquals(draft, session.sensitive.state.value.draft)
                    assertEquals(LiveboardRootOverlay.Create, current.state.value.chrome.overlay)
                    assertTrue(f.platform.attachments.last().isCurrent())
                    assertTrue(f.follows.value.drop(follows).flatMap { it.exclude }.none {
                        it in listOf(SocketRequestModel.GET_STATE, SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN)
                    })
                }
                val target = assertNotNull(f.reads.requireFreshRead()); f.socket.refreshAppState(target); f.idle()
                rootCheck(errors, "B subscription still refreshes") { assertTrue(target.completion.isCompleted) }
                if (target.completion.isCompleted) assertEquals(NativeReadOutcome.SUCCESS, target.completion.await().outcome)
                assertTrue(current.back()); assertEquals(LiveboardRootOverlay.None, current.state.value.chrome.overlay)
                assertEquals(LiveboardRootRoute.Profile, current.state.value.chrome.route)
                assertTrue(current.openInvite()); assertTrue(current.back())
                assertEquals(LiveboardRootOverlay.None, current.state.value.chrome.overlay)
                assertTrue(current.back()); assertEquals(LiveboardRootRoute.Home, current.state.value.chrome.route)
                assertFalse(current.back())
                f.proof(); assertTrue(current.openCreate()); current.editForm(LiveboardDraft("Positive", "positive payload"))
                current.submitCreate().join(); session.actions.awaitSettled()
                assertEquals(NativeCall("create", listOf("Positive", "positive payload")), f.core.calls.value.last())
                assertEquals(emptyList(), errors)
            } finally { f.close() }
        }
    }
}
