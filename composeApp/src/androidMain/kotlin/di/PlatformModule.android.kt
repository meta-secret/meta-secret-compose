package di

import org.koin.dsl.module
import sharedData.MetaSecretCoreInterface
import sharedData.MetaSecretCoreServiceAndroid

val androidPlatformModule = module {
    single<MetaSecretCoreInterface> { MetaSecretCoreServiceAndroid() }
} 