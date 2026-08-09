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
import core.metaSecretCore.AndroidMetaSecretSocketClient
import core.metaSecretCore.MetaSecretSocketClient
import metasecret.project.com.BuildConfig

val androidPlatformModule = module {
    single<MetaSecretCoreInterface> { MetaSecretCoreServiceAndroid() }
    single<MetaSecretSocketClient> {
        AndroidMetaSecretSocketClient(
            endpoint = BuildConfig.META_SECRET_SOCKET_URL,
            environment = BuildConfig.META_SECRET_ENV,
        )
    }
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
