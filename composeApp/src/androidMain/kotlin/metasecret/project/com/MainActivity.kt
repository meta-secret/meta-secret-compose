package metasecret.project.com

import App
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import di.KoinF
import org.koin.android.ext.koin.androidContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KoinF.setupKoin {
            androidContext(applicationContext)
        }
        setContent {
            App()
        }
    }

    @Preview
    @Composable
    fun AppAndroidPreview() {
        App()
    }
}
