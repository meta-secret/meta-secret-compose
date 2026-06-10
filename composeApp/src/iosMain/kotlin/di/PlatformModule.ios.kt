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
import core.email.EmailProvider
import core.email.EmailSelectionCoordinatorInterface
import core.email.EmailSelectionPlatformConfig
import core.email.EmailSelectionCoordinatorIos
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
val iosPlatformModule = module {
    single<MetaSecretCoreInterface> { MetaSecretCoreServiceIos(get(), get()) }
    single<EmailSelectionPlatformConfig> {
        EmailSelectionPlatformConfig(
            providerOrder = listOf(
                EmailProvider.APPLE,
                EmailProvider.GOOGLE,
                EmailProvider.MANUAL,
            )
        )
    }
    single<EmailSelectionCoordinatorInterface> { EmailSelectionCoordinatorIos() }
    single<StringProviderInterface> { StringProviderIos() }
    single<ClientDeviceInfoProviderInterface> { ClientDeviceInfoProviderIos() }
    single<DeviceInfoProviderInterface> { DeviceInfoProviderIos() }
    single<ScreenMetricsProviderInterface> { ScreenMetricsProviderIos() }
    single<BiometricAuthenticatorInterface> { BiometricAuthenticatorIos(get()) }
    single<KeyChainInterface> { KeyChainManagerIos(get()) }
    single<BackupCoordinatorInterface> { BackupCoordinatorInterfaceIos(get(), get(), get(), get()) }
    single<DatabasePathProviderInterface> { DatabasePathProviderIos(get()) }
    single<DebugLoggerInterface> { DebugLoggerIos(get()) }
    single<LogFormatterInterface> { LogFormatterIos() }
}
