package ui.dialogs.removesecret

import core.BiometricAuthenticatorInterface
import core.Secret
import core.errors.ErrorMapper
import core.metaSecretCore.MetaSecretAppManager
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import models.apiModels.PassId
import models.apiModels.SecretDeletionState
import models.apiModels.SecretDeletionStatus
import models.appInternalModels.RestoreData
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import testutils.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RemoveSecretViewModelTest {
    @Test
    fun `cancelled biometric does not submit destructive request`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val core = FakeMetaSecretCore()
            val auth = TestAuthenticator()
            val vm = createViewModel(core, auth)
            vm.removeSecret(Secret("opaque-id", "label")) {}
            auth.onError?.invoke("cancelled")
            advanceUntilIdle()
            assertEquals(emptyList(), core.deletionCalls)
            assertTrue(vm.failed.value)
            assertEquals(false, vm.busy.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun `approve waits for biometric and decline submits exact UUID without local erasure`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val core = FakeMetaSecretCore().apply { appStateJson = "{\"success\":true}" }
            val auth = TestAuthenticator()
            val vm = createViewModel(core, auth)
            val request = SecretDeletionState(PassId("opaque-id", "label"), "opaque-id", "exact-uuid", true, SecretDeletionStatus.NeedApprove)
            vm.decide(request, true) {}
            assertEquals(emptyList(), core.deletionDecisions)
            auth.onSuccess?.invoke()
            advanceUntilIdle()
            vm.busy.first { !it }
            vm.decide(request.copy(requestId = "decline-uuid"), false) {}
            advanceUntilIdle()
            vm.busy.first { !it }
            assertEquals(listOf("{\"requestId\":\"exact-uuid\",\"accepted\":true}",
                "{\"requestId\":\"decline-uuid\",\"accepted\":false}"), core.deletionDecisions)
            assertEquals(1, auth.calls)
            assertEquals(emptyList(), core.deletionCalls)
        } finally { Dispatchers.resetMain() }
    }

    private suspend fun createViewModel(core: FakeMetaSecretCore, auth: TestAuthenticator): RemoveSecretViewModel {
        val manager = MetaSecretAppManager(core, FakeKeyChain(mutableMapOf("master_key" to "test-key")),
            FakeKeyValueStorage(), FakeDebugLogger(), FakeNotificationCoordinator(), ErrorMapper(FakeStringProvider()),
            FakeLogFormatter(), FakeAppStateCacheProvider())
        manager.initWithSavedKey()
        return RemoveSecretViewModel(manager, auth, FakeSecretSocketHandler())
    }

    private class TestAuthenticator : BiometricAuthenticatorInterface {
        var calls = 0
        var onSuccess: (() -> Unit)? = null
        var onError: ((String) -> Unit)? = null
        override fun isBiometricAvailable() = true
        override fun openAppSettings() = Unit
        override fun authenticate(onSuccess: () -> Unit, onError: (String) -> Unit, onFallback: () -> Unit) {
            calls += 1
            this.onSuccess = onSuccess
            this.onError = onError
        }
    }


}
