package di

import org.koin.dsl.module
import core.metaSecretCore.MetaSecretAppManager
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretSocketHandler
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import core.metaSecretCore.MetaSecretStateResolver
import core.metaSecretCore.MetaSecretStateResolverInterface
import core.KeyValueStorage
import core.KeyValueStorageImpl
import ui.dialogs.adddevice.AddDeviceViewModel
import ui.dialogs.addsecret.AddSecretViewModel
import ui.dialogs.removesecret.RemoveSecretViewModel
import ui.dialogs.showsecret.ShowSecretViewModel
import ui.scenes.devicesscreen.DevicesScreenViewModel
import ui.scenes.mainscreen.MainScreenViewModel
import ui.scenes.onboarding.OnboardingViewModel
import ui.scenes.profilescreen.ProfileScreenViewModel
import ui.scenes.secretsscreen.SecretsScreenViewModel
import ui.scenes.signinscreen.SignInScreenViewModel
import ui.scenes.splashscreen.SplashScreenViewModel

val appModule = module {
    single<KeyValueStorage> { KeyValueStorageImpl() }
    single<MetaSecretAppManagerInterface> { MetaSecretAppManager(get(), get()) }
    single<MetaSecretStateResolverInterface> { MetaSecretStateResolver(get()) }
    single<MetaSecretSocketHandlerInterface> { MetaSecretSocketHandler(get(), get()) }

    factory { MainScreenViewModel(get(), get(), get(), get()) }
    factory { SplashScreenViewModel(get(), get(), get(), get(), get()) }
    factory { OnboardingViewModel(get(), get()) }
    factory { SignInScreenViewModel(get(), get(), get(), get(), get(), get()) }
    factory { ProfileScreenViewModel(get()) }
    factory { DevicesScreenViewModel(get(), get()) }
    factory { SecretsScreenViewModel(get()) }
    factory { AddSecretViewModel(get()) }
    factory { RemoveSecretViewModel(get()) }
    factory { AddDeviceViewModel(get()) }
    factory { ShowSecretViewModel(get()) }
}