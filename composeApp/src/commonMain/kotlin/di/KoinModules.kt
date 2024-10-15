package di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import scenes.onboarding.OnboardingViewModel
import scenes.splashscreen.SplashScreenViewModel

val appModule = module {
    single {

    }
//    single{ YouTubeServiceImpl() }
//    single { YoutubeDatabase(DriverFactory().createDriver()) }
    factory { SplashScreenViewModel() }
    factory { OnboardingViewModel() }
}