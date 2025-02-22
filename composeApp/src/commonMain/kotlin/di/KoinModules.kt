package di

import org.koin.dsl.module
import scenes.devicesscreen.DevicesScreenViewModel
import scenes.mainscreen.MainScreenViewModel
import scenes.onboarding.OnboardingViewModel
import scenes.profilescreen.ProfileScreenViewModel
import scenes.secretsscreen.SecretsScreenViewModel
import scenes.signinscreen.SignInScreenViewModel
import scenes.splashscreen.SplashScreenViewModel
import storage.KeyValueStorage
import storage.KeyValueStorageImpl
import ui.dialogs.adddevice.AddDeviceViewModel
import ui.dialogs.addsecret.AddSecretViewModel
import ui.dialogs.removesecret.RemoveSecretViewModel
import ui.dialogs.showsecret.ShowSecretViewModel

val appModule = module {
    single<KeyValueStorage> { KeyValueStorageImpl() }

    factory { MainScreenViewModel(get()) }
    factory { SplashScreenViewModel(get()) }
    factory { OnboardingViewModel(get()) }
    factory { SignInScreenViewModel(get()) }
    factory { ProfileScreenViewModel(get()) }
    factory { DevicesScreenViewModel(get()) }
    factory { SecretsScreenViewModel(get()) }
    factory { AddSecretViewModel(get()) }
    factory { RemoveSecretViewModel(get()) }
    factory { AddDeviceViewModel(get()) }
    factory { ShowSecretViewModel(get()) }
}