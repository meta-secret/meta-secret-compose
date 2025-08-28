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

val iosPlatformModule = module {
    single<MetaSecretCoreInterface> { MetaSecretCoreServiceIos() }
    single<BiometricAuthenticatorInterface> { BiometricAuthenticatorIos() }
    single<KeyChainInterface> { KeyChainManagerIos() }
    single<BackupCoordinatorInterface> { BackupCoordinatorInterfaceIos() }
}