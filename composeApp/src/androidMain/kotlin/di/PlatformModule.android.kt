package di

import androidx.fragment.app.FragmentActivity
import org.koin.dsl.module
import sharedData.BiometricAuthenticator
import sharedData.BiometricAuthenticatorAndroid
import sharedData.MetaSecretCoreInterface
import sharedData.MetaSecretCoreServiceAndroid

val androidPlatformModule = module {
    single<MetaSecretCoreInterface> { MetaSecretCoreServiceAndroid() }
    
    factory<BiometricAuthenticator> { (activity: FragmentActivity) ->
        BiometricAuthenticatorAndroid(
            context = activity.applicationContext,
            activity = activity
        )
    }
} 