package di

import org.koin.dsl.module
import core.metaSecretCore.MetaSecretAppManager
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretSocketHandler
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import core.metaSecretCore.MetaSecretStateResolver
import core.metaSecretCore.MetaSecretStateResolverInterface
import core.KeyValueStorageImpl
import core.KeyValueStorageInterface
import core.ImageProvider
import core.ImageProviderInterface
import ui.dialogs.adddevice.AddDeviceViewModel
import ui.dialogs.addsecret.AddSecretViewModel
import ui.dialogs.removesecret.RemoveSecretViewModel
import ui.dialogs.showsecret.ShowSecretViewModel
import ui.scenes.devicesscreen.DevicesScreenViewModel
import ui.scenes.mainscreen.MainScreenViewModel
import ui.scenes.onboarding.OnboardingViewModel
import ui.scenes.profilescreen.ProfileScreenViewModel
import ui.scenes.secretsscreen.SecretsScreenViewModel
import ui.scenes.splashscreen.SplashScreenViewModel
import core.VaultStatsProvider
import core.VaultStatsProviderInterface
import core.AlertCoordinator
import core.AlertCoordinatorInterface
import core.NotificationCoordinator
import core.NotificationCoordinatorInterface
import core.DebugLogger
import core.DebugLoggerInterface
import core.AppStateCacheProvider
import core.AppStateCacheProviderInterface
import core.errors.ErrorMapper
import core.metaSecretCore.liveboard.LiveboardPrivacyOwner
import core.metaSecretCore.liveboard.NativeCanonicalReadOwner
import core.metaSecretCore.liveboard.NativeInitializationOwner
import core.metaSecretCore.liveboard.NativeRegistrationLifecycle
import ui.scenes.mainscreen.MainScreenDependencies
import core.metaSecretCore.liveboard.NativeKeyLifecycle
import core.metaSecretCore.liveboard.NativeKeyPorts
import core.metaSecretCore.liveboard.NativeLocalReset
import core.metaSecretCore.liveboard.NativeManagerPorts
import core.metaSecretCore.liveboard.NativeManagerRuntime
import core.metaSecretCore.liveboard.NativeManagerPresentation
import core.metaSecretCore.liveboard.NativeOperationPort
import core.metaSecretCore.liveboard.NativeReadDependencies
import core.metaSecretCore.liveboard.NativeReadEnvironment
import core.metaSecretCore.liveboard.NativeReadRuntime
import core.metaSecretCore.liveboard.NativeSocketPorts
import core.metaSecretCore.liveboard.NativeSocketPresentation
import core.metaSecretCore.liveboard.NativeSocketRuntime

val appModule = module {
    single<KeyValueStorageInterface> { KeyValueStorageImpl(get()) }
    single<ImageProviderInterface> { ImageProvider() }
    single<ErrorMapper> { ErrorMapper(get()) }
    single { LiveboardPrivacyOwner() }
    single<NotificationCoordinatorInterface> { NotificationCoordinator(get()) }
    single<DebugLoggerInterface> { DebugLogger(get()) }
    single<AppStateCacheProviderInterface> { AppStateCacheProvider(get(), get()) }
    single { NativeReadEnvironment() }
    single { NativeInitializationOwner(get(), get()) }
    single { NativeOperationPort(get(), get()) }
    single { NativeKeyLifecycle(NativeKeyPorts(get(), get()), get(), get(), get()) }
    single {
        NativeCanonicalReadOwner(
            NativeReadDependencies(get(), get(), get(), get()), NativeReadRuntime(get(), get(), get()),
        )
    }
    single<MetaSecretAppManagerInterface> {
        MetaSecretAppManager(
            NativeManagerPorts(get(), get(), get(), get()),
            NativeManagerPresentation(get(), get(), get(), get()), get(), NativeManagerRuntime(get(), get()),
        )
    }
    single { NativeRegistrationLifecycle(get()) }
    single<MetaSecretStateResolverInterface> { MetaSecretStateResolver(get(), get(), get(), get()) }
    single<MetaSecretSocketHandlerInterface> {
        MetaSecretSocketHandler(
            NativeSocketPorts(get(), get(), get(), get()), NativeSocketPresentation(get(), get()),
            NativeSocketRuntime(get(), get()), get(),
        )
    }
    factory { NativeLocalReset(get(), get(), get()) }
    single { core.metaSecretCore.liveboard.LiveboardCoreAdapter(get()) }
    single { core.metaSecretCore.liveboard.NativeLiveboardOperations(get(), get(), get()) }
    single {
        val currentAuth = { get<core.BiometricAuthenticatorInterface>() }
        val auth = object : core.BiometricAuthenticatorInterface {
            override fun isBiometricAvailable() = currentAuth().isBiometricAvailable()
            override fun authenticate(onSuccess: () -> Unit, onError: (String) -> Unit, onFallback: () -> Unit) =
                currentAuth().authenticate(onSuccess, onError, onFallback)
            override fun openAppSettings() = currentAuth().openAppSettings()
        }
        core.metaSecretCore.liveboard.LiveboardClientPorts(get(), auth, get(), get(), get())
    }
    single { core.metaSecretCore.liveboard.LiveboardSessionFactory(get(), get(), get(), get(), get()) }
    factory { ui.liveboard.LiveboardRootServices(get(), get(), get(), get()) }
    factory { ui.liveboard.LiveboardRootDependencies(get(), get(), get(), get(), get()) }

    single<VaultStatsProviderInterface> { VaultStatsProvider(get(), get(), get())
        } // appStateCacheProvider, socketHandler, logger
    single<AlertCoordinatorInterface> { AlertCoordinator(get(), get(), get()) }

    single { MainScreenViewModel(get(), get(), get(), get(), MainScreenDependencies(get(), get(), get())) }
    factory { SplashScreenViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { OnboardingViewModel(get(), get()) }
    factory<core.registration.RegistrationSessionInterface> {
        core.registration.CoreRegistrationSession(
            core.registration.RegistrationCorePorts(get(), get(), get(), get()),
            core.registration.RegistrationStoragePorts(get(), get(), get()), get(),
        )
    }
    factory { ui.scenes.registration.RegistrationProviders(get(), get()) }
    factory { ui.scenes.registration.RegistrationViewModel(get(), get()) }
    factory { ProfileScreenViewModel(get(), get(), get(), get()) }
    factory { DevicesScreenViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { SecretsScreenViewModel(get(), get(), get(), get(), get()) }
    factory { AddSecretViewModel(get(), get(), get(), get()) }
    factory { RemoveSecretViewModel(get(), get(), get()) }
    factory { AddDeviceViewModel() }
    factory { ShowSecretViewModel(get(), get(), get(), get()) }
}
