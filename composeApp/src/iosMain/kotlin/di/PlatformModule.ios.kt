package di

import org.koin.dsl.module
import core.BiometricAuthenticatorInterface
import core.BiometricAuthenticatorIos
import core.AppleEmailRequesterInterface
import core.AppleEmailRequesterIos
import core.KeyChainInterface
import core.KeyChainManagerIos
import core.MetaSecretCoreServiceIos
import core.metaSecretCore.MetaSecretCoreInterface
import core.StringProviderInterface
import core.StringProviderIos
import core.GoogleEmailRequesterInterface
import core.GoogleEmailRequesterIos
import core.ClientDeviceInfoProviderInterface
import core.ClientDeviceInfoProviderIos
import core.DeviceInfoProviderInterface
import core.DeviceInfoProviderIos
import core.ScreenMetricsProviderInterface
import core.ScreenMetricsProviderIos
import core.DatabasePathProviderInterface
import core.DatabasePathProviderIos
import core.DebugLoggerInterface
import core.DebugLoggerIos
import core.LogFormatterInterface
import core.LogFormatterIos
import core.metaSecretCore.IosMetaSecretSocketClient
import core.metaSecretCore.MetaSecretSocketClient
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle

@OptIn(ExperimentalForeignApi::class)
val iosPlatformModule = module {
    single<MetaSecretCoreInterface> { MetaSecretCoreServiceIos(get(), get()) }
    single<MetaSecretSocketClient> {
        IosMetaSecretSocketClient(
            endpoint = NSBundle.mainBundle.objectForInfoDictionaryKey("META_SECRET_SOCKET_URL") as? String ?: "",
            environment = NSBundle.mainBundle.objectForInfoDictionaryKey("META_SECRET_ENV") as? String ?: "remote",
        )
    }
    single<StringProviderInterface> { StringProviderIos() }
    single<ClientDeviceInfoProviderInterface> { ClientDeviceInfoProviderIos() }
    single<DeviceInfoProviderInterface> { DeviceInfoProviderIos() }
    single<ScreenMetricsProviderInterface> { ScreenMetricsProviderIos() }
    single<BiometricAuthenticatorInterface> { BiometricAuthenticatorIos(get()) }
    single<AppleEmailRequesterInterface> { AppleEmailRequesterIos() }
    single<GoogleEmailRequesterInterface> { GoogleEmailRequesterIos() }
    single<KeyChainInterface> { KeyChainManagerIos(get()) }
    single<DatabasePathProviderInterface> { DatabasePathProviderIos(get()) }
    single<DebugLoggerInterface> { DebugLoggerIos(get()) }
    single<LogFormatterInterface> { LogFormatterIos() }
}
