package di

import org.koin.dsl.module
import sharedData.BiometricAuthenticator
import sharedData.`BiometricAuthenticator.ios`
import sharedData.KeyChainInterface
import sharedData.KeyChainManagerIos
import sharedData.MetaSecretCoreInterface
import sharedData.MetaSecretCoreServiceIos

val iosPlatformModule = module {
    single<MetaSecretCoreInterface> { MetaSecretCoreServiceIos() }
    single<BiometricAuthenticator> { `BiometricAuthenticator.ios`() }
    single<KeyChainInterface> { KeyChainManagerIos() }
}