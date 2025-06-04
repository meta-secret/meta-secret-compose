package di

import org.koin.dsl.module
import sharedData.BiometricAuthenticator
import sharedData.BiometricAuthenticatorIos
import sharedData.MetaSecretCoreInterface
import sharedData.MetaSecretCoreServiceIos

val iosPlatformModule = module {
    single<MetaSecretCoreInterface> { MetaSecretCoreServiceIos() }
    single<BiometricAuthenticator> { BiometricAuthenticatorIos() }
} 