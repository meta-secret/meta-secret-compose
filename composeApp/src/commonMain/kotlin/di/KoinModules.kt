package di

import org.koin.dsl.module
import scenes.onboarding.OnboardingViewModel
import scenes.signinscreen.SignInScreenViewModel
import scenes.splashscreen.SplashScreenViewModel
import storage.KeyValueStorage
import storage.KeyValueStorageImpl

val appModule = module {
    single {

    }
    single<KeyValueStorage> { KeyValueStorageImpl() }

    factory { SplashScreenViewModel(get()) }
    factory { OnboardingViewModel(get()) }
    factory { SignInScreenViewModel(get()) }
}