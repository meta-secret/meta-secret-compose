import androidx.compose.ui.window.ComposeUIViewController
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

    ComposeUIViewController { App() }
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
