package di

import org.koin.dsl.module
import sharedData.BiometricAuthenticatorInterface
import sharedData.BiometricAuthenticatorIos
import sharedData.KeyChainInterface
import sharedData.KeyChainManagerIos
import sharedData.MetaSecretCoreInterface
import sharedData.MetaSecretCoreServiceIos

val iosPlatformModule = module {
    single<MetaSecretCoreInterface> { MetaSecretCoreServiceIos() }
    single<BiometricAuthenticatorInterface> { BiometricAuthenticatorIos() }
    single<KeyChainInterface> { KeyChainManagerIos() }
}