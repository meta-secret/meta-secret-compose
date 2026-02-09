package di

import org.koin.dsl.module
import core.BiometricAuthenticatorInterface
import core.BiometricAuthenticatorIos
import core.KeyChainInterface
import core.KeyChainManagerIos
import core.MetaSecretCoreServiceIos
import core.metaSecretCore.MetaSecretCoreInterface
import core.BackupCoordinatorInterface
import core.BackupCoordinatorInterfaceIos
import core.StringProviderInterface
import core.StringProviderIos
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
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
val iosPlatformModule = module {
    single<MetaSecretCoreInterface> { MetaSecretCoreServiceIos(get()) }
    single<StringProviderInterface> { StringProviderIos() }
    single<DeviceInfoProviderInterface> { DeviceInfoProviderIos() }
    single<ScreenMetricsProviderInterface> { ScreenMetricsProviderIos() }
    single<BiometricAuthenticatorInterface> { BiometricAuthenticatorIos(get()) }
    single<KeyChainInterface> { KeyChainManagerIos(get()) }
    single<BackupCoordinatorInterface> { BackupCoordinatorInterfaceIos(get(), get(), get(), get()) }
    single<DatabasePathProviderInterface> { DatabasePathProviderIos(get()) }
    single<DebugLoggerInterface> { DebugLoggerIos(get()) }
    single<LogFormatterInterface> { LogFormatterIos() }
}
