package core.metaSecretCore.liveboard

import core.AppStateCacheProviderInterface
import core.DebugLoggerInterface
import core.KeyChainInterface
import core.KeyValueStorageInterface
import core.LogFormatterInterface
import core.NotificationCoordinatorInterface
import core.StringProviderInterface
import core.errors.ErrorMapper
import core.metaSecretCore.MetaSecretCoreInterface
import core.metaSecretCore.MetaSecretSocketClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.CancellationException

class NativeManagerPorts(
    val core: MetaSecretCoreInterface,
    val keys: KeyChainInterface,
    val storage: KeyValueStorageInterface,
    val logger: DebugLoggerInterface,
)

class NativeKeyPorts(val core: MetaSecretCoreInterface, val keys: KeyChainInterface)

class NativeManagerRuntime(
    val keyLifecycle: NativeKeyLifecycle,
    val operations: NativeOperationPort,
)

class NativeManagerPresentation(
    val notification: NotificationCoordinatorInterface,
    val errorMapper: ErrorMapper,
    val logFormatter: LogFormatterInterface,
    val cache: AppStateCacheProviderInterface,
)

class NativeSocketPorts(
    val core: MetaSecretCoreInterface,
    val logger: DebugLoggerInterface,
    val notification: NotificationCoordinatorInterface,
    val errorMapper: ErrorMapper,
)

class NativeSocketPresentation(
    val cache: AppStateCacheProviderInterface,
    val stringProvider: StringProviderInterface,
)

class NativeSocketRuntime(
    val client: MetaSecretSocketClient,
    val operations: NativeOperationPort,
    val uiDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    val socketDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun authorization(scope: NativeReadScope, native: () -> String): String {
        return when (val entered = operations.invoke(scope, native)) {
            is NativeInvocation.Value -> {
                if (!operations.isCurrent(scope)) throw CancellationException("Native owner retired")
                entered.value
            }
            is NativeInvocation.Failure -> {
                if (!operations.isCurrent(scope)) throw CancellationException("Native owner retired")
                throw entered.cause
            }
            NativeInvocation.Retired -> throw CancellationException("Native owner retired")
        }
    }
}
