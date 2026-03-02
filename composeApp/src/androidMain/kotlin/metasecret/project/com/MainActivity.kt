package metasecret.project.com

import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import di.appModule
import di.androidPlatformModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.App
import core.BiometricAuthenticatorAndroid
import core.BiometricAuthenticatorInterface
import core.KeyChainInterface
import core.KeyChainManagerAndroid
import core.BackupCoordinatorInterface
import core.BackupCoordinatorInterfaceAndroid
import core.KeyValueStorageInterface


class MainActivity : FragmentActivity() {

    companion object {
        private var loadedActivityModule: Module? = null
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activityModule = module {
            single<BiometricAuthenticatorInterface> {
                BiometricAuthenticatorAndroid(
                    this@MainActivity.applicationContext,
                    this@MainActivity,
                    get()
                )
            }
            single<KeyChainInterface> {
                KeyChainManagerAndroid(this@MainActivity.applicationContext, get())
            }
            single<BackupCoordinatorInterface> {
                BackupCoordinatorInterfaceAndroid(this@MainActivity, get(), get(), get())
            }
        }

        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(applicationContext)
                modules(appModule, androidPlatformModule, activityModule)
            }
            loadedActivityModule = activityModule
        } else {
            loadedActivityModule?.let { unloadKoinModules(it) }
            loadKoinModules(activityModule)
            loadedActivityModule = activityModule
        }

        val keyValueStorage: KeyValueStorageInterface = org.koin.java.KoinJavaComponent.getKoin().get()
        keyValueStorage.resetKeyValueStorage()

        org.koin.java.KoinJavaComponent.getKoin().get<BackupCoordinatorInterface>()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY
            ) {
            }
        } else {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                }
            })
        }

        setContent {
            App()
        }
    }
}
