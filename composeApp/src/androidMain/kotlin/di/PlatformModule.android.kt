package di

import androidx.fragment.app.FragmentActivity
import org.koin.dsl.module
import sharedData.BiometricAuthenticatorAndroid
import sharedData.BiometricAuthenticatorInterface
import sharedData.KeyChainInterface
import sharedData.KeyChainManagerAndroid
import sharedData.MetaSecretCoreInterface
import sharedData.MetaSecretCoreServiceAndroid

val androidPlatformModule = module {
    single<MetaSecretCoreInterface> { MetaSecretCoreServiceAndroid() }
    single<KeyChainInterface> { KeyChainManagerAndroid() }
    
    factory<BiometricAuthenticatorInterface> { (activity: FragmentActivity) ->
        BiometricAuthenticatorAndroid(
            context = activity.applicationContext,
            activity = activity
        )
    }
} 