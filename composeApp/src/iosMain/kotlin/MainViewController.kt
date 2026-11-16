import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIColor
import di.appModule
import di.iosPlatformModule
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import platform.App

fun MainViewController() = run {
    // Initialize Koin for iOS
    if (KoinPlatform.getKoinOrNull() == null) {
        startKoin {
            modules(
                appModule,
                iosPlatformModule
            )
        }
    }

    ComposeUIViewController { App() }.apply {
        // Orbit 0.2.0 backgroundCenter beneath the first Compose frame.
        view.backgroundColor = UIColor(red = 44.0 / 255, green = 46.0 / 255, blue = 47.0 / 255, alpha = 1.0)
    }
}

fun MetaSecretAppDidEnterForeground() {
    KoinPlatform.getKoinOrNull()
        ?.get<MetaSecretSocketHandlerInterface>()
        ?.onAppForeground()
}

fun MetaSecretAppDidEnterBackground() {
    KoinPlatform.getKoinOrNull()
        ?.get<MetaSecretSocketHandlerInterface>()
        ?.onAppBackground()
}
