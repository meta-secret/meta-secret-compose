package ui.scenes.registration

import core.AppString
import core.AppleEmailRequesterInterface
import core.GoogleEmailRequesterInterface
import core.metaSecretCore.VaultAvailability
import core.registration.RegistrationSessionInterface
import core.registration.RegistrationSnapshot
import core.registration.RegistrationStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationJoinAvailabilityTest {
    @Test
    fun testAvailableNameRejectsJoinWithoutMutationOrServiceCalls() = verify(
        FakeSession().apply { availability = VaultAvailability.AVAILABLE },
    ) { vm, session ->
        prepareName(vm)
        assertEquals(VaultAvailability.AVAILABLE, vm.state.value.identity.availability)
        assertJoinIgnored(vm, session)
    }

    @Test
    fun testExistingNameOpensJoinWithoutSubmissionThenRequestUsesCorePort() = verify { vm, session ->
        prepareName(vm)
        vm.handle(RegistrationIntent.Create)
        assertEquals(emptyList(), session.submissions)
        vm.handle(RegistrationIntent.Join)
        assertEquals(RegistrationScene.Join, vm.state.value.scene)
        assertEquals(RegistrationPhase.Idle, vm.state.value.phase)
        assertEquals(1, vm.state.value.direction)
        assertEquals(emptyList(), session.submissions)
        assertFalse(vm.openMain.value)
        vm.handle(RegistrationIntent.Request)
        advanceUntilIdle()
        assertEquals(listOf(true), session.submissions)
        assertEquals(RegistrationPhase.Pending, vm.state.value.phase)
        assertFalse(vm.openMain.value)
    }

    @Test
    fun testUnknownNameRejectsJoinWithoutMutationOrServiceCalls() = verify(
        FakeSession().apply { availability = null },
    ) { vm, session ->
        prepareName(vm)
        assertEquals(null, vm.state.value.identity.availability)
        assertJoinIgnored(vm, session)
    }

    @Test
    fun testCheckingNameRejectsJoinUntilCorePreparationCompletes() = verify(
        FakeSession().apply { preparation = CompletableDeferred() },
    ) { vm, session ->
        enterName(vm)
        runCurrent()
        assertEquals(RegistrationPhase.Checking, vm.state.value.phase)
        assertJoinIgnored(vm, session)
        session.preparation!!.complete(RegistrationSnapshot(NAME, VaultAvailability.EXISTS, RegistrationStatus.Prepared))
        advanceUntilIdle()
        assertEquals(RegistrationScene.Name, vm.state.value.scene)
        assertEquals(RegistrationPhase.Idle, vm.state.value.phase)
        assertEquals(emptyList(), session.submissions)
    }

    @Test
    fun testFailedPreparationCannotOpenJoinFromStaleExistingAvailability() = verify(
        FakeSession().apply { status = RegistrationStatus.Failed },
    ) { vm, session ->
        prepareName(vm)
        assertEquals(RegistrationPhase.Error, vm.state.value.phase)
        assertEquals(AppString.orbitNetworkError, vm.state.value.error)
        assertJoinIgnored(vm, session)
    }

    @Test
    fun testBusyCreateRejectsJoinWithoutAnotherSubmission() = verify(
        FakeSession().apply {
            availability = VaultAvailability.AVAILABLE
            submission = CompletableDeferred()
        },
    ) { vm, session ->
        prepareName(vm)
        vm.handle(RegistrationIntent.Create)
        runCurrent()
        assertEquals(RegistrationPhase.Creating, vm.state.value.phase)
        assertEquals(listOf(false), session.submissions)
        assertJoinIgnored(vm, session)
        session.submission!!.complete(RegistrationSnapshot(NAME, VaultAvailability.AVAILABLE, RegistrationStatus.Ready))
        advanceUntilIdle()
    }

    @Test
    fun testJoinOutsideNameRejectsEvenRestoredExistingAvailability() = verify(
        FakeSession().apply { restored = RegistrationSnapshot(NAME, VaultAvailability.EXISTS, RegistrationStatus.Prepared) },
    ) { vm, session ->
        assertEquals(RegistrationScene.Providers, vm.state.value.scene)
        assertJoinIgnored(vm, session)
        vm.handle(RegistrationIntent.Manual)
        assertEquals(RegistrationScene.Manual, vm.state.value.scene)
        assertJoinIgnored(vm, session)
    }

    private fun TestScope.assertJoinIgnored(vm: RegistrationViewModel, session: FakeSession) {
        val before = vm.state.value
        val calls = session.calls.toList()
        val submissions = session.submissions.toList()
        vm.handle(RegistrationIntent.Join)
        runCurrent()
        assertEquals(before, vm.state.value)
        assertEquals(calls, session.calls)
        assertEquals(submissions, session.submissions)
        assertFalse(vm.openMain.value)
    }

    private suspend fun TestScope.prepareName(vm: RegistrationViewModel) {
        enterName(vm)
        advanceUntilIdle()
        assertEquals(RegistrationScene.Name, vm.state.value.scene)
    }

    private fun enterName(vm: RegistrationViewModel) {
        vm.handle(RegistrationIntent.Manual)
        vm.handle(RegistrationIntent.Email(NAME))
        vm.handle(RegistrationIntent.ConfirmEmail)
    }

    private fun verify(
        session: FakeSession = FakeSession(),
        assertions: suspend TestScope.(RegistrationViewModel, FakeSession) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = RegistrationViewModel(session, RegistrationProviders(
            object : AppleEmailRequesterInterface {
                override suspend fun requestAppleEmail(): Nothing {
                    session.calls += "apple"
                    error("Join must not invoke Apple")
                }
            },
            object : GoogleEmailRequesterInterface {
                override suspend fun requestGoogleEmail(): Nothing {
                    session.calls += "google"
                    error("Join must not invoke Google")
                }
            },
        ))
        try {
            advanceUntilIdle()
            assertions(vm, session)
        } finally {
            vm.stopSession()
            Dispatchers.resetMain()
        }
    }

    private class FakeSession : RegistrationSessionInterface {
        override val changes = MutableSharedFlow<RegistrationSnapshot>()
        val calls = mutableListOf<String>()
        val submissions = mutableListOf<Boolean>()
        var availability: VaultAvailability? = VaultAvailability.EXISTS
        var status = RegistrationStatus.Prepared
        var restored = RegistrationSnapshot()
        var preparation: CompletableDeferred<RegistrationSnapshot>? = null
        var submission: CompletableDeferred<RegistrationSnapshot>? = null
        override suspend fun resume(): RegistrationSnapshot { calls += "resume"; return restored }
        override suspend fun prepare(email: String): RegistrationSnapshot {
            calls += "prepare:$email"
            return preparation?.await() ?: RegistrationSnapshot(email, availability, status,
                if (status == RegistrationStatus.Failed) AppString.orbitNetworkError else null)
        }
        override suspend fun submit(join: Boolean): RegistrationSnapshot {
            calls += "submit:$join"
            submissions += join
            return submission?.await() ?: RegistrationSnapshot(NAME, availability, RegistrationStatus.Pending)
        }
        override suspend fun cleanupIncomplete(): RegistrationSnapshot {
            calls += "cleanup"
            error("Join must not clean credentials")
        }
        override suspend fun reset(): Boolean {
            calls += "reset"
            error("Join must not reset credentials")
        }
        override fun stop() = Unit
    }

    private companion object { const val NAME = "family@example.org" }
}
