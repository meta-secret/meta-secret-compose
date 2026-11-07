package metasecret.project.com

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.content.res.Configuration
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
    private val phoneOrientationPolicy = PhoneOrientationPolicy()
    private lateinit var appleEmailRequester: AppleEmailRequesterAndroid
    private lateinit var appleBrowser: AndroidAppleEmailBrowser
    private var inactiveCover: android.view.View? = null
    private var contentAccessibility: Int? = null

    companion object {
        private var loadedActivityModule: Module? = null
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyPhoneOrientation(resources.configuration)

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

        // Register the fallback before Compose's scoped dialog handlers; Orbit retains its old no-op Back.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                applicationKoin.get<ui.liveboard.LiveboardPlatformAndroid>().dispatchBack()
            }
        })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY
            ) { onBackPressedDispatcher.onBackPressed() }
        }

        setContent {
            Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                App()
            }
        }
        appleEmailRequester.onReturn(intent?.action, intent?.dataString)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyPhoneOrientation(newConfig)
    }

    private fun applyPhoneOrientation(configuration: Configuration) {
        phoneOrientationPolicy.apply(configuration.smallestScreenWidthDp, requestedOrientation) {
            requestedOrientation = it
        }
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
        // Suppress the underlying tree too; hiding the cover alone does not hide its sibling.
        findViewById<android.view.View>(android.R.id.content)?.let { content ->
            if (contentAccessibility == null) contentAccessibility = content.importantForAccessibility
            content.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        // Native task-switcher cover is separate from onStop's actual-background hard conceal.
        if (inactiveCover == null) {
            inactiveCover = android.view.View(this).apply {
                setBackgroundColor(android.graphics.Color.rgb(INACTIVE_COVER_RED, INACTIVE_COVER_GREEN,
                    INACTIVE_COVER_BLUE))
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }.also { (window.decorView as android.view.ViewGroup).addView(it,
                android.view.ViewGroup.LayoutParams(-1, -1)) }
        }
        if (::appleEmailRequester.isInitialized && appleBrowser.owns(this)) appleEmailRequester.onHostPaused()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        inactiveCover?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        inactiveCover = null
        contentAccessibility?.let { previous -> findViewById<android.view.View>(android.R.id.content)
            ?.importantForAccessibility = previous }
        contentAccessibility = null
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

private const val INACTIVE_COVER_RED: Int = 44
private const val INACTIVE_COVER_GREEN: Int = 46
private const val INACTIVE_COVER_BLUE: Int = 47
