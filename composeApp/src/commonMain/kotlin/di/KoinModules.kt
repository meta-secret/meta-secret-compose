package di

import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import scenes.onboarding.OnboardingViewModel
import scenes.splashscreen.SplashScreenViewModel

expect fun platformModule(): Module

fun vmModule(): Module {
    return module {
//        single { TaskRepository(get()) }
//        factory { AddTodoViewModel(get()) }
//        factory { TodoViewModel(get()) }
        factory { SplashScreenViewModel() }
        factory { OnboardingViewModel() }
    }
}

fun initKoin(appDeclaration: KoinAppDeclaration = {}) =
    startKoin {
        appDeclaration()
        modules(platformModule(), vmModule())

    }

object KoinF {
    var di: Koin? = null

    fun setupKoin(appDeclaration: KoinAppDeclaration = {}) {
        if (di == null) {
            di = initKoin(appDeclaration).koin
        }
    }
}