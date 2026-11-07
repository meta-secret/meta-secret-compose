package ui.liveboard

import core.DeviceInfoProviderInterface
import core.metaSecretCore.liveboard.NativeReadEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class LiveboardNavigationObservationTest {
    private class Capture(delegate: LiveboardPlatformPort = RootPlatform(), val enabled: () -> Boolean = { true }) :
        LiveboardPlatformPort by delegate {
        val records = mutableListOf<LiveboardNavigationObservationRecord>()
        val closes = mutableListOf<Boolean>()
        var failure: Throwable? = null
        var onEmit: ((LiveboardNavigationObservationRecord) -> Unit)? = null
        override val navigationObservationEnabled get() = enabled()
        override fun emitNavigationObservation(record: LiveboardNavigationObservationRecord) {
            failure?.let { throw it }
            onEmit?.invoke(record)
            records.add(record)
        }
        override fun closeNavigationObservation(continuous: Boolean) { closes.add(continuous) }
    }

    @Test fun closedCodecBoundsAndPrivacyHaveNoPayloadChannel() {
        for (stage in NavigationObservationStage.entries) {
            val seq = if (stage in listOf(NavigationObservationStage.READY, NavigationObservationStage.CLOSE)) 0 else 8
            val record = LiveboardNavigationObservationRecord(seq, stage)
            val row = assertNotNull(record.encodedNavigationObservation())
            assertTrue(row.encodeToByteArray().size + 1 <= 256)
            assertFalse(row.contains("PRIVATE_CANARY"))
        }
        assertNull(LiveboardNavigationObservationRecord(9, NavigationObservationStage.RESULT).encodedNavigationObservation())
        assertNull(LiveboardNavigationObservationRecord(0, NavigationObservationStage.RESULT).encodedNavigationObservation())
        assertNull(LiveboardNavigationObservationRecord(1, NavigationObservationStage.RESULT,
            continuous = NavigationObservationFlag.TRUE).encodedNavigationObservation())
    }

    @Test fun primitiveBufferFlushesOnlyAfterOriginalDecisionAndKeepsThrowable() {
        val events = mutableListOf<String>()
        val port = Capture().apply { onEmit = { events.add("emit") } }
        val owner = LiveboardNavigationObservation(port)
        owner.navigate({ events.add("render"); true }) { ticket ->
            events.add("commit")
            owner.updateRoute(LiveboardRootRoute.Secrets, ticket) {
                events.add("update")
                assertTrue(port.records.isEmpty())
            }
            owner.flushWrites()
            assertTrue(port.records.isEmpty())
            events.add("return")
        }
        assertEquals(listOf("render", "commit", "update", "return"), events.take(4))
        assertEquals(1, port.records.count { it.stage == NavigationObservationStage.ROUTE_WRITE })
        assertEquals(NavigationObservationFlag.TRUE, port.records.single { it.stage == NavigationObservationStage.RESULT }.flags.rendered)
        val before = port.records.size
        owner.navigate({ false }) { error("Vetoed rendered callback cannot enter") }
        assertEquals(NavigationObservationFlag.FALSE, port.records.drop(before).single {
            it.stage == NavigationObservationStage.RESULT }.flags.rendered)
        val original = IllegalStateException("PRIVATE_CANARY")
        val caught = try { owner.navigate({ true }) { throw original }; null } catch (failure: Throwable) { failure }
        assertSame(original, caught)
        assertEquals(1, port.records.count { it.stage == NavigationObservationStage.ROUTE_WRITE })
        owner.close(); assertEquals(listOf(false), port.closes)
    }

    @Test fun actualStateFlowCasRetryProducesOnePostUpdateWriteAndOriginalError() {
        val port = Capture(); val owner = LiveboardNavigationObservation(port)
        val state = MutableStateFlow(LiveboardRootPresentation())
        var evaluations = 0
        owner.navigate({ true }) { ticket ->
            owner.updateRoute(LiveboardRootRoute.Secrets, ticket) {
                state.update {
                    evaluations++
                    if (evaluations == 1) state.value = it.copy(chrome = it.chrome.copy(revision = Any()))
                    assertTrue(port.records.isEmpty())
                    it.copy(createCompletion = null, chrome = it.chrome.copy(route = LiveboardRootRoute.Secrets,
                        overlay = LiveboardRootOverlay.None, revision = Any()))
                }
            }
        }
        assertEquals(2, evaluations)
        assertEquals(LiveboardRootRoute.Secrets, state.value.chrome.route)
        assertEquals(1, port.records.count { it.stage == NavigationObservationStage.ROUTE_WRITE })
        val original = IllegalArgumentException("PRIVATE_CANARY")
        val before = port.records.count { it.stage == NavigationObservationStage.ROUTE_WRITE }
        assertSame(original, try {
            owner.navigate({ true }) { ticket -> owner.updateRoute(LiveboardRootRoute.Home, ticket) { throw original } }
            null
        } catch (failure: Throwable) { failure })
        assertEquals(before, port.records.count { it.stage == NavigationObservationStage.ROUTE_WRITE })
    }

    @Test fun disabledGetterFaultSinkFaultOverflowAndInflightCloseNeverChangeCalls() {
        for (throwGetter in listOf(false, true)) {
            val port = Capture(enabled = { if (throwGetter) error("PRIVATE_CANARY") else false })
            val owner = LiveboardNavigationObservation(port)
            var rendered = 0; var commits = 0
            owner.ready(); owner.navigate({ rendered++; true }) { commits++ }; owner.close()
            assertEquals(1, rendered); assertEquals(1, commits); assertTrue(port.records.isEmpty())
        }
        val broken = Capture().apply { failure = IllegalStateException("PRIVATE_CANARY") }
        val logger = LiveboardNavigationObservation(broken)
        var calls = 0
        logger.ready(); logger.navigate({ true }) { calls++ }; logger.close()
        assertEquals(1, calls); assertEquals(listOf(false), broken.closes)
        val capped = Capture(); val bounded = LiveboardNavigationObservation(capped)
        bounded.ready()
        repeat(9) { bounded.navigate({ true }) { calls++ } }
        bounded.close()
        assertEquals(10, calls)
        assertTrue(capped.records.size <= 49)
        assertEquals(listOf(false), capped.closes)
        val inflight = Capture(); val closing = LiveboardNavigationObservation(inflight)
        closing.navigate({ true }) {
            closing.close(); assertTrue(inflight.closes.isEmpty()); calls++
        }
        assertEquals(listOf(false), inflight.closes)
        closing.navigate({ true }) { calls++ }
        assertEquals(12, calls)
    }

    @Test fun laterRouteWritesRemainBoundedAndNullSessionDoesNotReadCurrent() {
        val port = Capture(); val owner = LiveboardNavigationObservation(port)
        owner.navigate({ true }) { ticket -> owner.updateRoute(LiveboardRootRoute.Secrets, ticket) {} }
        for (route in listOf(LiveboardRootRoute.Home, LiveboardRootRoute.Profile, LiveboardRootRoute.Home)) {
            owner.updateRoute(route, null) {}; owner.flushWrites()
        }
        owner.close()
        assertEquals(listOf(NavigationObservationRoute.SECRETS, NavigationObservationRoute.HOME,
            NavigationObservationRoute.PROFILE), port.records.filter {
            it.stage == NavigationObservationStage.ROUTE_WRITE }.map { it.route })
        assertEquals(listOf(false), port.closes)
        val ticket = LiveboardNavigationObservationTicket(1)
        assertNull(observeNavigationSession(null, ticket) { error("Must not read current without session") })
        assertEquals(NavigationObservationFlag.FALSE, ticket.session)
        assertEquals(NavigationObservationFlag.UNKNOWN, ticket.root)
    }


    @Test fun reentrantSinkCannotReplaceOriginalTicketOrCloseBeforeFinalRows() {
        val port = Capture()
        lateinit var owner: LiveboardNavigationObservation
        owner = LiveboardNavigationObservation(port)
        var outer = 0; var nested = 0; var once = false
        port.onEmit = {
            if (!once) {
                once = true
                owner.navigate({ true }) { ticket ->
                    nested++
                    owner.updateRoute(LiveboardRootRoute.Home, ticket) {}
                    owner.flushWrites()
                }
            }
        }
        owner.navigate({ true }) { ticket ->
            outer++
            owner.updateRoute(LiveboardRootRoute.Secrets, ticket) {}
        }
        assertEquals(1, outer); assertEquals(1, nested)
        val writes = port.records.filter { it.stage == NavigationObservationStage.ROUTE_WRITE }
        assertEquals(listOf(NavigationObservationRoute.SECRETS), writes.map { it.route })
        assertEquals(setOf(1), port.records.map { it.sequence }.toSet())
        owner.close(); assertEquals(listOf(false), port.closes)

        val closing = Capture()
        val other = LiveboardNavigationObservation(closing)
        closing.onEmit = {
            other.close()
            assertTrue(closing.closes.isEmpty(), "Close must wait until all original buffered rows finish")
        }
        other.navigate({ true }) { ticket -> other.updateRoute(LiveboardRootRoute.Secrets, ticket) {} }
        assertEquals(NavigationObservationStage.RETURN, closing.records.last().stage)
        assertEquals(listOf(false), closing.closes)
    }

    @Test fun renderedThrowableAlsoInvalidatesContinuityWithoutCallingCommit() {
        val port = Capture(); val owner = LiveboardNavigationObservation(port)
        val original = IllegalStateException("PRIVATE_CANARY")
        var commits = 0
        assertSame(original, try {
            owner.navigate({ throw original }) { commits++ }; null
        } catch (failure: Throwable) { failure })
        assertEquals(0, commits)
        assertEquals(NavigationObservationFlag.UNKNOWN,
            port.records.single { it.stage == NavigationObservationStage.RESULT }.flags.rendered)
        owner.close(); assertEquals(listOf(false), port.closes)
    }

    @Test fun actualRootHealthyAndRetiredAttachmentRespectNativeOwnership() = runBlocking {
        val f = LiveboardRootTestFixture()
        var owned: LiveboardRootCoordinator? = null
        try {
            f.start()
            val capture = Capture(f.platform)
            val info = object : DeviceInfoProviderInterface {
                override fun getAppVersion() = "fixture"
                override fun getDeviceMake() = "fixture"
                override fun getDeviceId() = "device-a"
            }
            val root = LiveboardRootCoordinator(LiveboardRootDependencies(f.factory, f.reads,
                NativeReadEnvironment(Dispatchers.IO, f.main), LiveboardRootServices(f.reset, f.socket,
                    info, f.notifications), capture)).also { owned = it }
            val session = f.session(root)
            val calls = f.core.calls.value.toList()
            root.navigateSecretsFromRendered { true }
            assertEquals(LiveboardRootRoute.Secrets, root.state.value.chrome.route)
            assertSame(session, f.content(root).session)
            assertEquals(calls, f.core.calls.value)
            val first = capture.records.single { it.stage == NavigationObservationStage.RESULT }
            assertEquals(NavigationObservationFlag.TRUE, first.flags.rootCurrent)
            assertEquals(NavigationObservationFlag.TRUE, first.flags.sessionPresent)
            assertEquals(NavigationObservationFlag.TRUE, first.flags.nativeScope)
            assertEquals(NavigationObservationFlag.TRUE, first.flags.finalCurrent)
            val replacement = f.root(); f.session(replacement)
            val before = capture.records.size
            root.navigateSecretsFromRendered { true }
            assertEquals(LiveboardRootRoute.Secrets, root.state.value.chrome.route)
            val veto = capture.records.drop(before).single { it.stage == NavigationObservationStage.RESULT }
            assertEquals(NavigationObservationFlag.FALSE, veto.flags.rootCurrent)
            assertEquals(NavigationObservationFlag.UNKNOWN, veto.flags.nativeScope)
            assertEquals(NavigationObservationFlag.UNKNOWN, veto.flags.finalCurrent)
        } finally { owned?.close(); f.close() }
    }
}
