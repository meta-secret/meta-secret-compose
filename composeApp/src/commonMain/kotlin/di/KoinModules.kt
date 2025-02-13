package di

import org.koin.dsl.module
import scenes.devicesscreen.DevicesScreenViewModel
import scenes.onboarding.OnboardingViewModel
import scenes.profilescreen.ProfileScreenViewModel
import scenes.secretsscreen.SecretsScreenViewModel
import scenes.signinscreen.SignInScreenViewModel
import scenes.splashscreen.SplashScreenViewModel
import sharedData.Repository
import storage.KeyValueStorage
import storage.KeyValueStorageImpl
import ui.dialogs.adddevice.AddDeviceViewModel
import ui.dialogs.addsecret.AddSecretViewModel

val appModule = module {
    single<KeyValueStorage> { KeyValueStorageImpl() }
    single { Repository(get()) }

    factory { SplashScreenViewModel(get()) }
    factory { OnboardingViewModel(get()) }
    factory { SignInScreenViewModel(get()) }
    factory { ProfileScreenViewModel(get()) }
    factory { DevicesScreenViewModel(get()) }
    factory { SecretsScreenViewModel(get()) }
    factory { AddSecretViewModel(get()) }
    factory { AddDeviceViewModel(get()) }
}