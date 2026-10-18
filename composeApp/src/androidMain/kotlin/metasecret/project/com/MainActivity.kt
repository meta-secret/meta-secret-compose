package metasecret.project.com

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import android.os.Build
import android.os.Bundle
import android.content.Intent
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
import core.AppleEmailRequesterAndroid
import core.apple.AndroidAppleEmailBrowser
import core.GoogleEmailRequesterAndroid
import core.GoogleEmailRequesterInterface
import core.BiometricAuthenticatorAndroid
import core.BiometricAuthenticatorInterface
import core.KeyChainInterface
import core.KeyChainManagerAndroid
import core.KeyValueStorageInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface


class MainActivity : FragmentActivity() {
    private lateinit var appleEmailRequester: AppleEmailRequesterAndroid
    private lateinit var appleBrowser: AndroidAppleEmailBrowser

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
            single<GoogleEmailRequesterInterface> {
                GoogleEmailRequesterAndroid(this@MainActivity)
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

        val applicationKoin = org.koin.java.KoinJavaComponent.getKoin()
        appleEmailRequester = applicationKoin.get()
        appleBrowser = applicationKoin.get()
        appleEmailRequester.onHostDetached()
        appleBrowser.attach(this)

        val keyValueStorage: KeyValueStorageInterface = org.koin.java.KoinJavaComponent.getKoin().get()
        keyValueStorage.resetKeyValueStorage()

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
            Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                App()
            }
        }
        appleEmailRequester.onReturn(intent?.action, intent?.dataString)
    }

    override fun onDestroy() {
        if (::appleEmailRequester.isInitialized && appleBrowser.detach(this)) appleEmailRequester.onHostDetached()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (appleBrowser.owns(this)) appleEmailRequester.onReturn(intent.action, intent.dataString)
    }

    override fun onPause() {
        if (::appleEmailRequester.isInitialized && appleBrowser.owns(this)) appleEmailRequester.onHostPaused()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::appleEmailRequester.isInitialized && appleBrowser.owns(this)) appleEmailRequester.onHostResumed()
    }

    override fun onStart() {
        super.onStart()
        org.koin.java.KoinJavaComponent.getKoin()
            .get<MetaSecretSocketHandlerInterface>()
            .onAppForeground()
    }

    override fun onStop() {
        org.koin.java.KoinJavaComponent.getKoin()
            .get<MetaSecretSocketHandlerInterface>()
            .onAppBackground()
        super.onStop()
    }
}
