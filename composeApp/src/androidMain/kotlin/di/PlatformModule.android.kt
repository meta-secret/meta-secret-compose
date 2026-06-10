package di

import android.content.Context
import androidx.fragment.app.FragmentActivity
import org.koin.dsl.module
import core.BiometricAuthenticatorAndroid
import core.BiometricAuthenticatorInterface
import core.KeyChainInterface
import core.KeyChainManagerAndroid
import core.MetaSecretCoreServiceAndroid
import core.metaSecretCore.MetaSecretCoreInterface
import core.StringProviderAndroid
import core.StringProviderInterface
import core.ClientDeviceInfoProviderAndroid
import core.ClientDeviceInfoProviderInterface
import core.DeviceInfoProviderAndroid
import core.DeviceInfoProviderInterface
import core.ScreenMetricsProviderAndroid
import core.ScreenMetricsProviderInterface
import core.DatabasePathProviderInterface
import core.DatabasePathProviderAndroid
import core.LogFormatterInterface
import core.LogFormatterAndroid
import core.email.AndroidEmailSelectionCoordinator
import core.email.AndroidEmailAuthConfig
import core.email.EmailProvider
import core.email.EmailSelectionCoordinatorInterface
import core.email.EmailSelectionPlatformConfig
import core.email.androidEmailAuthConfig

val androidPlatformModule = module {
    single<MetaSecretCoreInterface> { MetaSecretCoreServiceAndroid() }
    single<EmailSelectionPlatformConfig> {
        EmailSelectionPlatformConfig(
            providerOrder = listOf(
                EmailProvider.GOOGLE,
                EmailProvider.APPLE,
                EmailProvider.MANUAL,
            )
        )
    }
    single<AndroidEmailAuthConfig> { androidEmailAuthConfig() }
    single<EmailSelectionCoordinatorInterface> { AndroidEmailSelectionCoordinator(get(), get()) }

    factory<KeyChainInterface> { (context: Context) ->
        KeyChainManagerAndroid(context, get())
    }
    
    factory<BiometricAuthenticatorInterface> { (activity: FragmentActivity) ->
        BiometricAuthenticatorAndroid(
            context = activity.applicationContext,
            activity = activity,
            stringProvider = get()
        )
    }

    single<StringProviderInterface> { StringProviderAndroid(get()) }
    single<ClientDeviceInfoProviderInterface> { ClientDeviceInfoProviderAndroid() }
    single<DeviceInfoProviderInterface> { DeviceInfoProviderAndroid() }
    single<ScreenMetricsProviderInterface> { ScreenMetricsProviderAndroid() }
    single<DatabasePathProviderInterface> { DatabasePathProviderAndroid(get()) }
    single<LogFormatterInterface> { LogFormatterAndroid() }
} 
