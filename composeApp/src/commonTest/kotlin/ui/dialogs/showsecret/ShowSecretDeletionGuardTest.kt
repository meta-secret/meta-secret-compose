package ui.dialogs.showsecret

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import core.DebugLoggerInterface
import core.NotificationCoordinatorInterface
import core.errors.ErrorMapper
import core.metaSecretCore.MetaSecretAppManager
import core.metaSecretCore.MetaSecretAppManagerInterface
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import models.appInternalModels.SecretModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import testutils.*
import kotlin.test.Test
import kotlin.test.assertNull

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ShowSecretDeletionGuardTest {
    @Test
    fun `late reveal result cannot restore plaintext after dialog closes during sync`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        startKoin { modules(module {
            single<DebugLoggerInterface> { FakeDebugLogger() }
            single<NotificationCoordinatorInterface> { FakeNotificationCoordinator() }
        }) }
        try {
            val release = CompletableDeferred<String?>()
            val started = CompletableDeferred<Unit>()
            val base = MetaSecretAppManager(FakeMetaSecretCore(), FakeKeyChain(), FakeKeyValueStorage(),
                FakeDebugLogger(), FakeNotificationCoordinator(), ErrorMapper(FakeStringProvider()),
                FakeLogFormatter(), FakeAppStateCacheProvider())
            val manager = object : MetaSecretAppManagerInterface by base {
                override suspend fun showRecovered(secretModel: SecretModel): String? {
                    started.complete(Unit)
                    return release.await()
                }
            }
            val vm = ShowSecretViewModel(manager, FakeVaultStatsProvider(), FakeSecretSocketHandler(), FakeStringProvider())
            vm.handle(ShowSecretEvents.ShowSecret("label", forceRecovery = false, secretId = "opaque-id"))
            runCurrent()
            started.await()
            vm.handle(ShowSecretEvents.HideSecret)
            release.complete("synthetic-value")
            advanceUntilIdle()
            vm.isLoading.first { !it }
            assertNull(vm.revealedSecret.value)
            vm.viewModelScope.cancel()
            runCurrent()
        } finally {
            stopKoin()
            Dispatchers.resetMain()
        }
    }
}
