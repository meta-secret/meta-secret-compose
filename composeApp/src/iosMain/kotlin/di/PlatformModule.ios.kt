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

val iosPlatformModule = module {
    single<MetaSecretCoreInterface> { MetaSecretCoreServiceIos() }
    single<StringProviderInterface> { StringProviderIos() }
    single<DeviceInfoProviderInterface> { DeviceInfoProviderIos() }
    single<ScreenMetricsProviderInterface> { ScreenMetricsProviderIos() }
    single<BiometricAuthenticatorInterface> { BiometricAuthenticatorIos(get()) }
    single<KeyChainInterface> { KeyChainManagerIos(get()) }
    single<BackupCoordinatorInterface> { BackupCoordinatorInterfaceIos(get(), get(), get()) }
    single<DatabasePathProviderInterface> { DatabasePathProviderIos(get(), get()) }
}