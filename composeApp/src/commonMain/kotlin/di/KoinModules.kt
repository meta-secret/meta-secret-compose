package di

import org.koin.dsl.module
import scenes.devicesscreen.DevicesScreenViewModel
import scenes.onboarding.OnboardingViewModel
import scenes.profilescreen.ProfileScreenViewModel
import scenes.secretsscreen.SecretsScreenViewModel
import scenes.signinscreen.SignInScreenViewModel
import scenes.splashscreen.SplashScreenViewModel
import sharedData.DeviceRepository
import storage.KeyValueStorage
import storage.KeyValueStorageImpl

val appModule = module {
    single {
    }
    single<KeyValueStorage> { KeyValueStorageImpl() }
    single<DeviceRepository> { DeviceRepository(get()) }

    factory { SplashScreenViewModel(get()) }
    factory { OnboardingViewModel(get()) }
    factory { SignInScreenViewModel(get()) }
    factory { ProfileScreenViewModel(get()) }
    factory { DevicesScreenViewModel(get()) }
    factory { SecretsScreenViewModel(get()) }

}