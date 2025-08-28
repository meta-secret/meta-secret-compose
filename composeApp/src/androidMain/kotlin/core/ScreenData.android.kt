package core

import android.os.Build
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity


@Composable
@RequiresApi(Build.VERSION_CODES.R)
actual fun actualWidthFactor(): Float {
    val context = LocalContext.current
    val windowMetrics = context.getSystemService(WindowManager::class.java).currentWindowMetrics
    val density = LocalDensity.current.density
    val coefficient = (windowMetrics.bounds.width() / density) / 375
    return coefficient
}

@Composable
@RequiresApi(Build.VERSION_CODES.R)
actual fun getScreenWidth(): Int {
    val context = LocalContext.current
    val windowMetrics = context.getSystemService(WindowManager::class.java).currentWindowMetrics
    val density = LocalDensity.current.density
    return (windowMetrics.bounds.width() / density).toInt()
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
actual fun actualHeightFactor(): Float {
    val context = LocalContext.current
    val windowMetrics = context.getSystemService(WindowManager::class.java).currentWindowMetrics
    val density = LocalDensity.current.density
    val coefficient = (windowMetrics.bounds.height() / density) / 812
    return coefficient
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
actual fun getScreenHeight(): Int {
    val context = LocalContext.current
    val windowMetrics = context.getSystemService(WindowManager::class.java).currentWindowMetrics
    val density = LocalDensity.current.density
    return  (windowMetrics.bounds.height() / density).toInt()

}
