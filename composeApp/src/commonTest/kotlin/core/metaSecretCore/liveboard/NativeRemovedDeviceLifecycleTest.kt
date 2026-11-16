package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import models.appInternalModels.SecretModel
import models.appInternalModels.SocketActionModel
import ui.scenes.mainscreen.MainNavigationEvent

class NativeRemovedDeviceLifecycleTest {
    @Test
    fun testActualRemovedDeviceHandlerDrainsNativeAndCannotNavigateOverSuccessor() = runBlocking {
        withTimeout(30_000) {
            val failures = mutableListOf<String>()
            val reached = mutableListOf<String>()
            val held = NativeRegistrationTestFixture()
            try {
                held.initialize(held.core.member())
                val main = held.main()
                val prime = assertNotNull(held.manager.getStateModel())
                assertSame(prime, held.cache.appState.value)
                val native = held.hold("split")
                val operation = held.track(async(Dispatchers.IO) { held.manager.splitSecret(SecretModel("fixture-secret", "fixture-value")) })
                native.entered.await()
                val clears = held.trace.count("key.clear")
                val signal = held.remove(main)
                // The barrier was positive closed admission OR actual completed navigation, never elapsed absence.
                if (held.trace.count("key.clear") != clears) failures.add("Actual Main removal cleared keys before entered native exited")
                if (held.cache.appState.value == null) failures.add("Actual Main removal cleared canonical state before native drain")
                if (main.navigationEvent.value == MainNavigationEvent.NavigateToSignIn) failures.add("Main navigated before native drain")
                assertTrue(!native.exited.isCompleted)
                native.release.complete(Unit)
                operation.await()
                held.finishMainSignal(signal)
                assertEquals(MainNavigationEvent.NavigateToSignIn, main.navigationEvent.value)
                assertEquals(clears + 1, held.trace.count("key.clear"))
                val fresh = held.successor()
                assertSame(fresh, held.cache.canonicalFrames.value)
                assertEquals("stored-key-b", held.keys.getString("master_key"))
                reached.add("entered-native/main-removal/drain/fresh-B")
            } finally { held.close() }

            val delayed = NativeRegistrationTestFixture()
            try {
                delayed.initialize(delayed.core.member())
                val origin = delayed.initialization.capture()
                val main = delayed.main()
                delayed.queueMainUi()
                val signal = delayed.socket.socketActions.emit(SocketActionModel.CURRENT_DEVICE_REMOVED)
                delayed.mainCompleted.first { it == 1 }
                // This is the actual queued Main UI task after real cleanup, not an invented navigation callback.
                val oldNavigation = delayed.mainUi.take()
                assertEquals(MainNavigationEvent.Idle, main.navigationEvent.value)
                assertEquals(1, delayed.trace.count("key.clear"))
                delayed.immediateMainUi() // Owner/Profile UI stays available while the old Main task remains held.
                val profile = delayed.profile()
                delayed.reset(profile)
                delayed.awaitReset(profile)
                val fresh = delayed.successor()
                assertNotSame(origin, fresh.scope?.owner)
                val clears = delayed.trace.count("key.clear")
                oldNavigation.publish()
                delayed.track(delayed.mainUi.drain(this))
                delayed.finishMainSignal(signal)
                if (main.navigationEvent.value != MainNavigationEvent.Idle) failures.add("Old Main completion navigated over actual B")
                if (delayed.cache.canonicalFrames.value !== fresh) failures.add("Old Main completion changed B canonical proof")
                assertEquals(clears, delayed.trace.count("key.clear"))
                assertEquals("stored-key-b", delayed.keys.getString("master_key"))
                reached.add("actual-cleanup/held-Main-UI/Profile-reset/B/old-finally")
            } finally { delayed.close() }

            for (cleared in listOf(true, false)) {
                val f = NativeRegistrationTestFixture()
                try {
                    f.initialize(f.core.member())
                    val main = f.main()
                    f.keys.clearReturns = cleared
                    val first = f.socket.socketActions.emit(SocketActionModel.CURRENT_DEVICE_REMOVED)
                    f.finishMainSignal(first)
                    assertEquals(MainNavigationEvent.NavigateToSignIn, main.navigationEvent.value,
                        "Removed-device Main policy still navigates when key clear returns false")
                    val duplicate = f.socket.socketActions.emit(SocketActionModel.CURRENT_DEVICE_REMOVED)
                    f.finishMainSignal(duplicate) // Positive acknowledgment from that real Main collector.
                    assertEquals(1, f.trace.count("key.clear"))
                    assertEquals(1, f.trace.count("socket.background"))
                    assertEquals(1, f.mainCompleted.value)
                    assertEquals(!cleared, f.keys.containsKey("master_key"))
                    reached.add("clear=$cleared/actual-Main-nav/duplicate-consumed-once")
                } finally { f.close() }
            }
            assertEquals(4, reached.size)
            assertEquals(emptyList(), failures, "All Main drain/stale-finally/current policy controls reached: $reached")
        }
    }
}
