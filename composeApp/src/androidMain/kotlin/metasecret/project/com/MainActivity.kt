package metasecret.project.com

import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentActivity
import di.appModule
import org.koin.core.context.startKoin
import org.koin.dsl.module
import platform.App
import platform.getPlatformModule
import sharedData.BiometricAuthenticatorAndroid
import sharedData.BiometricAuthenticatorInterface
import sharedData.KeyChainInterface
import sharedData.KeyChainManagerAndroid
import storage.KeyValueStorage
import storage.KeyValueStorageImpl


class MainActivity : FragmentActivity() {

    private val keyValueStorage: KeyValueStorage by lazy { KeyValueStorageImpl() }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keyValueStorage.resetKeyValueStorage()

        //Background extension through the status bar
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY
            ) {
                // Handle custom back logic here without animation
            }
        } else {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {

                }
            })
        }

        val activityModule = module {
            single<BiometricAuthenticatorInterface> { 
                BiometricAuthenticatorAndroid(this@MainActivity.applicationContext, this@MainActivity)
            }
            single<KeyChainInterface> { 
                KeyChainManagerAndroid(this@MainActivity.applicationContext)
            }
        }

        // Initialize Koin
        startKoin {
            modules(
                appModule,
                getPlatformModule(),
                activityModule
            )
        }

        setContent {
            App()
        }
    }
}
