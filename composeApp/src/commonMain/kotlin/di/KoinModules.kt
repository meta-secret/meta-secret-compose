package di

import org.koin.dsl.module
import scenes.devicesscreen.DevicesScreenViewModel
import scenes.mainscreen.MainScreenViewModel
import scenes.onboarding.OnboardingViewModel
import scenes.profilescreen.ProfileScreenViewModel
import scenes.secretsscreen.SecretsScreenViewModel
import scenes.signinscreen.SignInScreenViewModel
import scenes.splashscreen.SplashScreenViewModel
import sharedData.metaSecretCore.MetaSecretAppManager
import sharedData.metaSecretCore.MetaSecretAppManagerInterface
import sharedData.metaSecretCore.MetaSecretSocketHandler
import sharedData.metaSecretCore.MetaSecretSocketHandlerInterface
import sharedData.metaSecretCore.MetaSecretStateResolver
import sharedData.metaSecretCore.MetaSecretStateResolverInterface
import storage.KeyValueStorage
import storage.KeyValueStorageImpl
import ui.dialogs.adddevice.AddDeviceViewModel
import ui.dialogs.addsecret.AddSecretViewModel
import ui.dialogs.removesecret.RemoveSecretViewModel
import ui.dialogs.showsecret.ShowSecretViewModel

val appModule = module {
    single<KeyValueStorage> { KeyValueStorageImpl() }
    single<MetaSecretAppManagerInterface> { MetaSecretAppManager(get(), get()) }
    single<MetaSecretStateResolverInterface> { MetaSecretStateResolver(get()) }
    single<MetaSecretSocketHandlerInterface> { MetaSecretSocketHandler(get(), get()) }

    factory { MainScreenViewModel(get(), get()) }
    factory { SplashScreenViewModel(get(), get(), get(), get()) }
    factory { OnboardingViewModel(get(), get()) }
    factory { SignInScreenViewModel(get(), get(), get(), get(), get(), get()) }
    factory { ProfileScreenViewModel(get()) }
    factory { DevicesScreenViewModel(get()) }
    factory { SecretsScreenViewModel(get()) }
    factory { AddSecretViewModel(get()) }
    factory { RemoveSecretViewModel(get()) }
    factory { AddDeviceViewModel(get()) }
    factory { ShowSecretViewModel(get()) }
}