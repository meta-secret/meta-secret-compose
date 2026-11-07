package ui.liveboard

import core.metaSecretCore.liveboard.*
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

@OptIn(ExperimentalCoroutinesApi::class)
class LiveboardDecisionReadSchedulingTest {
    @Test
    fun testClosedPreparationIsFailedBeforeDeferredWake() = runBlocking {
        withTimeout(TIMEOUT_MS) {
            val fixture = LiveboardRootTestFixture()
            var ownedScope: NativeReadScope? = null
            try {
                fixture.start()
                val scope = assertNotNull(fixture.initialization.currentScope())
                ownedScope = scope
                val frame = assertNotNull(fixture.reads.frames.value)
                for (close in listOf("owner", "scope")) {
                    val coordination = NativeReadEntryCoordination()
                    val required = assertNotNull(fixture.reads.requireFreshRead(scope))
                    val read = coordination.watch(required, frame.attempt)
                    assertFalse(coordination.failed(read))
                    val closed = if (close == "owner") coordination.closeOwner(scope.owner)
                        else coordination.closeScope(scope)
                    try {
                        assertFalse(read.closed.isCompleted, "Wake must remain outside the close metadata CAS")
                        assertTrue(coordination.failed(read),
                            "Removed preparation must fail immediately before deferred wake")
                    } finally {
                        coordination.wakeClosed(closed)
                        coordination.unwatch(read)
                    }
                    assertTrue(read.closed.isCompleted)
                }
            } finally {
                withContext(NonCancellable) {
                    ownedScope?.let { fixture.reads.retireScope(it) }
                    fixture.close()
                }
            }
        }
    }

    @Test
    fun testPendingSocketReadRunsBetweenTwoAlreadyQueuedEntries() = runBlocking {
        withTimeout(TIMEOUT_MS) {
            val fixture = LiveboardRootTestFixture()
            val scheduler = TestCoroutineScheduler()
            val dispatcher = StandardTestDispatcher(scheduler)
            val entries = mutableListOf<Deferred<NativeReadTurn?>>()
            val watches = mutableListOf<NativeDecisionRead>()
            val turns = mutableSetOf<NativeReadTurn>()
            try {
                fixture.start()
                val root = fixture.root(io = dispatcher)
                fixture.session(root)
                val scope = assertNotNull(fixture.initialization.currentScope())
                val first = assertNotNull(fixture.reads.registerDecision(scope)).also { watches.add(it) }
                val second = assertNotNull(fixture.reads.registerDecision(scope)).also { watches.add(it) }
                assertTrue(fixture.reads.demandDecision(first))
                assertTrue(fixture.reads.demandDecision(second))
                val readsBeforeA = fixture.core.reads.value
                val readA = fixture.hold("read")
                fixture.socket.refreshAppState()
                readA.entered.await()
                assertTrue(fixture.cache.synchronization.value.loading)
                val entry1 = fixture.track(async(dispatcher) {
                    fixture.reads.beginDecision(first).also { if (it != null) turns.add(it) }
                }).also { entries.add(it) }
                scheduler.runCurrent()
                assertFalse(entry1.isCompleted)

                val readB = fixture.hold("read")
                fixture.socket.refreshAppState()
                val entry2 = fixture.track(async(dispatcher) {
                    fixture.reads.beginDecision(second).also { if (it != null) turns.add(it) }
                }).also { entries.add(it) }
                scheduler.runCurrent()
                assertFalse(entry2.isCompleted)
                // A later duplicate signal may coalesce B, but may not move its original queue position.
                fixture.socket.refreshAppState()
                readA.release.complete(Unit)
                readA.exited.await()
                while (!entry1.isCompleted) {
                    scheduler.runCurrent()
                    yield()
                }
                val firstTurn = assertNotNull(entry1.await())
                val proofA = first.target.completion.await()
                assertEquals(NativeReadOutcome.SUCCESS, proofA.outcome)
                assertEquals(scope, proofA.scope)
                assertTrue(first.target in proofA.coveredRequirements)
                assertTrue(second.target in proofA.coveredRequirements)
                assertFalse(readB.entered.isCompleted)
                assertFalse(entry2.isCompleted)

                fixture.reads.finishDecision(firstTurn)
                scheduler.runCurrent()
                val readWon = select<Boolean> {
                    readB.entered.onAwait { true }
                    entry2.onAwait { false }
                }
                assertTrue(readWon, "Pending Socket read B must physically enter before later entry E2 grants")
                assertFalse(entry2.isCompleted)
                assertEquals(readsBeforeA + 2, fixture.core.reads.value)
                readB.release.complete(Unit)
                readB.exited.await()
                while (!entry2.isCompleted) {
                    scheduler.runCurrent()
                    yield()
                }
                val secondTurn = assertNotNull(entry2.await())
                fixture.reads.finishDecision(secondTurn)
                fixture.idle()
                val proofB = assertNotNull(fixture.reads.frames.value)
                assertEquals(NativeReadOutcome.SUCCESS, proofB.outcome)
                assertEquals(scope, proofB.scope)
                assertTrue(proofB.attempt > proofA.attempt)
                assertEquals(0, fixture.cache.synchronization.value.activeRefreshes)
                assertFalse(fixture.cache.synchronization.value.failed)
                assertEquals(readsBeforeA + 2, fixture.core.reads.value)
                assertEquals(0, fixture.auth.calls)
                assertTrue(fixture.core.calls.value.isEmpty(), "Logical entry turns must not invoke a business route")
            } finally {
                withContext(NonCancellable) {
                    entries.forEach { it.cancel() }
                    scheduler.runCurrent()
                    turns.forEach { fixture.reads.finishDecision(it) }
                    scheduler.runCurrent()
                    watches.forEach { fixture.reads.releaseDecision(it) }
                    fixture.close()
                }
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 45_000L
    }
}
