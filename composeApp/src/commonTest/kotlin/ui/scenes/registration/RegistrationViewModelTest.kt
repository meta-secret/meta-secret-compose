package ui.scenes.registration

import core.AppString
import core.AppleEmailAuthResult
import core.AppleEmailRequesterInterface
import core.GoogleEmailAuthResult
import core.GoogleEmailRequesterInterface
import core.registration.RegistrationSessionInterface
import core.registration.RegistrationSnapshot
import core.registration.RegistrationStatus
import core.metaSecretCore.VaultAvailability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import models.appInternalModels.EmailProvider

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationViewModelTest {
    @Test
    fun testProviderCancelStaysAtFirstStep() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = model(FakeSession())
            advanceUntilIdle()
            vm.handle(RegistrationIntent.Provider(EmailProvider.APPLE))
            advanceUntilIdle()
            assertEquals(RegistrationScene.Providers, vm.state.value.scene)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun testCheckingNameDoesNotSubmit() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = FakeSession()
            val vm = model(session)
            advanceUntilIdle()
            vm.handle(RegistrationIntent.Manual)
            vm.handle(RegistrationIntent.Email("person@example.org"))
            vm.handle(RegistrationIntent.ConfirmEmail)
            advanceUntilIdle()
            assertEquals(RegistrationScene.Name, vm.state.value.scene)
            assertEquals(0, session.submissions)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun testQrAutomaticallyRequestsButNeverNavigatesToMain() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = FakeSession()
            val vm = model(session)
            advanceUntilIdle()
            vm.handle(RegistrationIntent.QrDecoded("Family Vault"))
            advanceUntilIdle()
            assertEquals(1, session.submissions)
            assertEquals("Family Vault", vm.state.value.identity.email)
            assertEquals(RegistrationPhase.Pending, vm.state.value.phase)
            assertFalse(vm.openMain.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun testDeclinePersistsUntilBackThenClearsIdentityToFirstStep() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = FakeSession().apply {
                restored = RegistrationSnapshot("family@example.org", status = RegistrationStatus.Declined)
            }
            val vm = model(session)
            advanceUntilIdle()
            assertEquals(RegistrationPhase.Declined, vm.state.value.phase)
            assertEquals(0, session.incompleteCleanups)
            vm.handle(RegistrationIntent.Back)
            advanceUntilIdle()
            assertEquals(RegistrationScene.Providers, vm.state.value.scene)
            assertEquals("", vm.state.value.identity.email)
            assertEquals(RegistrationPhase.Idle, vm.state.value.phase)
            assertEquals(-1, vm.state.value.direction)
            assertEquals(1, session.incompleteCleanups)
            assertEquals(0, session.resets)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun testKnownCreateFailureHasNoActionDuringThreeSecondMessage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = FakeSession().apply {
                availability = VaultAvailability.AVAILABLE
                next = RegistrationSnapshot("person@example.org", VaultAvailability.AVAILABLE,
                    RegistrationStatus.Failed, AppString.orbitNetworkError, true)
            }
            val vm = model(session)
            advanceUntilIdle()
            vm.handle(RegistrationIntent.Manual)
            vm.handle(RegistrationIntent.Email("person@example.org"))
            vm.handle(RegistrationIntent.ConfirmEmail)
            advanceUntilIdle()
            vm.handle(RegistrationIntent.Create)
            runCurrent()
            assertEquals(RegistrationPhase.CreateError, vm.state.value.phase)
            assertEquals(true, vm.state.value.busy)
            advanceTimeBy(2999)
            assertEquals(RegistrationPhase.CreateError, vm.state.value.phase)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(RegistrationPhase.Idle, vm.state.value.phase)
            assertEquals(RegistrationScene.Name, vm.state.value.scene)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun testIncompleteCleanupFailureNeverShowsFalseFirstStep() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            for (initial in listOf(RegistrationStatus.Pending, RegistrationStatus.Declined)) {
            val session = incompleteSession(initial).apply {
                cleanupResult = RegistrationSnapshot("family@example.org", status = RegistrationStatus.Failed,
                    error = AppString.orbitResetFailed)
            }
            val vm = model(session)
            advanceUntilIdle()
            vm.handle(RegistrationIntent.Back)
            advanceUntilIdle()
            assertEquals(RegistrationScene.Join, vm.state.value.scene)
            assertEquals("family@example.org", vm.state.value.identity.email)
            assertEquals(AppString.orbitResetFailed, vm.state.value.error)
            assertFalse(vm.openMain.value)
            assertEquals(0, session.resets)
            vm.stopSession()
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun testMemberAppearingBeforeIncompleteCleanupUsesExistingMainFlow() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            for (initial in listOf(RegistrationStatus.Pending, RegistrationStatus.Declined)) {
            val session = incompleteSession(initial).apply {
                cleanupResult = RegistrationSnapshot("family@example.org", status = RegistrationStatus.Ready)
            }
            val vm = model(session)
            advanceUntilIdle()
            vm.handle(RegistrationIntent.Back)
            advanceUntilIdle()
            assertTrue(vm.openMain.value)
            assertEquals(RegistrationPhase.Syncing, vm.state.value.phase)
            assertEquals(0, session.resets)
            vm.stopSession()
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun testPendingBackCleansIdentityToFirstStep() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = FakeSession().apply {
                restored = RegistrationSnapshot("family@example.org", status = RegistrationStatus.Pending)
            }
            val vm = model(session)
            advanceUntilIdle()
            vm.handle(RegistrationIntent.Back)
            advanceUntilIdle()
            assertEquals(RegistrationScene.Providers, vm.state.value.scene)
            assertEquals("", vm.state.value.identity.email)
            assertEquals(0, session.resets)
            assertEquals(1, session.incompleteCleanups)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun testPreRequestBackAndManualBackKeepPreviousStepBehavior() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = FakeSession()
            val vm = model(session)
            advanceUntilIdle()
            vm.handle(RegistrationIntent.Manual)
            vm.handle(RegistrationIntent.Back)
            advanceUntilIdle()
            assertEquals(RegistrationScene.Providers, vm.state.value.scene)
            vm.handle(RegistrationIntent.Manual)
            vm.handle(RegistrationIntent.Email("family@example.org"))
            vm.handle(RegistrationIntent.ConfirmEmail)
            advanceUntilIdle()
            vm.handle(RegistrationIntent.Join)
            vm.handle(RegistrationIntent.Back)
            advanceUntilIdle()
            assertEquals(RegistrationScene.Name, vm.state.value.scene)
            vm.handle(RegistrationIntent.Manual)
            vm.handle(RegistrationIntent.Back)
            advanceUntilIdle()
            assertEquals(RegistrationScene.Name, vm.state.value.scene)
            assertEquals("family@example.org", vm.state.value.identity.email)
            assertEquals(0, session.incompleteCleanups)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun testNonDeclinedErrorBackKeepsNameAndNeverCleansDeclined() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = FakeSession().apply {
                restored = RegistrationSnapshot("family@example.org", status = RegistrationStatus.Pending)
                next = RegistrationSnapshot("family@example.org", status = RegistrationStatus.Failed,
                    error = AppString.orbitNetworkError)
            }
            val vm = model(session)
            advanceUntilIdle()
            session.changes.emit(session.next!!)
            advanceUntilIdle()
            assertEquals(RegistrationPhase.Error, vm.state.value.phase)
            vm.handle(RegistrationIntent.Back)
            advanceUntilIdle()
            assertEquals(RegistrationScene.Name, vm.state.value.scene)
            assertEquals("family@example.org", vm.state.value.identity.email)
            assertEquals(1, session.resets)
            assertEquals(0, session.incompleteCleanups)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun testIncompleteCleanupStopsOldCollectorAndSerializesDuplicateBack() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            for (initial in listOf(RegistrationStatus.Pending, RegistrationStatus.Declined)) {
            val completion = CompletableDeferred<RegistrationSnapshot>()
            val session = incompleteSession(initial).apply { cleanupCompletion = completion }
            val vm = model(session)
            advanceUntilIdle()
            vm.handle(RegistrationIntent.Back)
            runCurrent()
            vm.handle(RegistrationIntent.Back)
            assertEquals(1, session.incompleteCleanups)
            assertEquals(0, session.changes.subscriptionCount.value)
            session.changes.emit(RegistrationSnapshot("family@example.org", status = RegistrationStatus.Ready))
            completion.complete(RegistrationSnapshot())
            advanceUntilIdle()
            assertEquals(RegistrationScene.Providers, vm.state.value.scene)
            vm.handle(RegistrationIntent.Manual)
            vm.handle(RegistrationIntent.Email("family@example.org"))
            vm.handle(RegistrationIntent.ConfirmEmail)
            advanceUntilIdle()
            vm.handle(RegistrationIntent.Join)
            vm.handle(RegistrationIntent.Request)
            advanceUntilIdle()
            assertEquals(RegistrationPhase.Pending, vm.state.value.phase)
            assertFalse(vm.openMain.value)
            vm.stopSession()
            }
        } finally { Dispatchers.resetMain() }
    }

    private fun declinedSession() = incompleteSession(RegistrationStatus.Declined)
    private fun incompleteSession(status: RegistrationStatus) = FakeSession().apply {
        restored = RegistrationSnapshot("family@example.org", status = status)
    }

    private fun model(session: FakeSession) = RegistrationViewModel(
        session,
        RegistrationProviders(
            object : AppleEmailRequesterInterface {
                override suspend fun requestAppleEmail() = AppleEmailAuthResult.Cancelled
            },
            object : GoogleEmailRequesterInterface {
                override suspend fun requestGoogleEmail() = GoogleEmailAuthResult.Cancelled
            },
        ),
    )

    private class FakeSession : RegistrationSessionInterface {
        override val changes = MutableSharedFlow<RegistrationSnapshot>()
        var restored = RegistrationSnapshot()
        var availability = VaultAvailability.EXISTS
        var next: RegistrationSnapshot? = null
        var selected = ""
        var submissions = 0
        var resets = 0
        var incompleteCleanups = 0
        var cleanupResult = RegistrationSnapshot()
        var cleanupCompletion: CompletableDeferred<RegistrationSnapshot>? = null
        override suspend fun resume() = restored
        override suspend fun prepare(email: String): RegistrationSnapshot {
            selected = email
            return RegistrationSnapshot(email, availability, RegistrationStatus.Prepared)
        }
        override suspend fun submit(join: Boolean): RegistrationSnapshot {
            submissions++
            return next ?: RegistrationSnapshot(selected, status = RegistrationStatus.Pending)
        }
        override suspend fun reset(): Boolean { resets++; return true }
        override suspend fun cleanupIncomplete(): RegistrationSnapshot {
            incompleteCleanups++
            return cleanupCompletion?.await() ?: cleanupResult
        }
        override fun stop() = Unit
    }
}
