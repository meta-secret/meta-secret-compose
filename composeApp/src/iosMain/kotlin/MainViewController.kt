import androidx.compose.ui.window.ComposeUIViewController
import di.appModule
import org.koin.core.context.startKoin
import platform.App
import platform.getPlatformModule

fun MainViewController() = run {
    // Initialize Koin for iOS
    startKoin {
        modules(
            appModule,
            getPlatformModule()
        )
    }
    
    ComposeUIViewController { App() }
}