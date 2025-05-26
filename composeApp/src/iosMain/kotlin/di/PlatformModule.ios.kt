package di

import org.koin.dsl.module
import sharedData.MetaSecretCoreInterface
import sharedData.MetaSecretCoreServiceIos

val iosPlatformModule = module {
    single<MetaSecretCoreInterface> { MetaSecretCoreServiceIos() }
} 