package di

import org.koin.dsl.module
import core.metaSecretCore.FfiSynchronizer
import core.metaSecretCore.FfiSynchronizerInterface
import core.metaSecretCore.MetaSecretAppManager
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretSocketHandler
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import core.metaSecretCore.MetaSecretStateResolver
import core.metaSecretCore.MetaSecretStateResolverInterface
import core.KeyValueStorageImpl
import core.KeyValueStorageInterface
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
import core.VaultStatsProvider
import core.VaultStatsProviderInterface
import core.AlertCoordinator
import core.AlertCoordinatorInterface
import core.NotificationCoordinator
import core.NotificationCoordinatorInterface
import core.DebugLogger
import core.DebugLoggerInterface
import core.errors.ErrorMapper
import core.StringProviderInterface

val appModule = module {
    single<KeyValueStorageInterface> { KeyValueStorageImpl(get()) }
    single<FfiSynchronizerInterface> { FfiSynchronizer(get()) }
    single<ErrorMapper> { ErrorMapper(get()) }
    single<NotificationCoordinatorInterface> { NotificationCoordinator() }
    single<MetaSecretAppManagerInterface> { MetaSecretAppManager(get(), get(), get(), get(), get(), get(), get(), get()) }
    single<MetaSecretStateResolverInterface> { MetaSecretStateResolver(get(), get()) }
    single<MetaSecretSocketHandlerInterface> { MetaSecretSocketHandler(get(), get(), get(), get(), get(), get()) }
    single<VaultStatsProviderInterface> { VaultStatsProvider(get(), get(), get()) }
    single<AlertCoordinatorInterface> { AlertCoordinator() }
    single<DebugLoggerInterface> { DebugLogger(get()) }

    single { MainScreenViewModel(get(), get(), get(), get(), get(), get(), get()) }
    factory { SplashScreenViewModel(get(), get(), get(), get(), get(), get(), get()) }
    factory { OnboardingViewModel(get(), get()) }
    factory { SignInScreenViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { ProfileScreenViewModel(get(), get(), get(), get(), get()) }
    factory { DevicesScreenViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { SecretsScreenViewModel(get(), get(), get(), get(), get()) }
    factory { AddSecretViewModel(get(), get(), get(), get(), get(), get()) }
    factory { RemoveSecretViewModel(get()) }
    factory { AddDeviceViewModel(get()) }
    factory { ShowSecretViewModel(get(), get()) }
}