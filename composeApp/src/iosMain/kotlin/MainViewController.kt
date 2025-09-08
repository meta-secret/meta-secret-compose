import androidx.compose.ui.window.ComposeUIViewController
import di.appModule
import di.iosPlatformModule
import org.koin.core.context.startKoin
import platform.App

fun MainViewController() = run {
    // Initialize Koin for iOS
    startKoin {
        modules(
            appModule,
            iosPlatformModule
        )
    }
    
    ComposeUIViewController { App() }
}