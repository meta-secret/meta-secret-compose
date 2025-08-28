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

val androidPlatformModule = module {
    single<MetaSecretCoreInterface> { MetaSecretCoreServiceAndroid() }

    factory<KeyChainInterface> { (context: Context) ->
        KeyChainManagerAndroid(context)
    }
    
    factory<BiometricAuthenticatorInterface> { (activity: FragmentActivity) ->
        BiometricAuthenticatorAndroid(
            context = activity.applicationContext,
            activity = activity
        )
    }
} 