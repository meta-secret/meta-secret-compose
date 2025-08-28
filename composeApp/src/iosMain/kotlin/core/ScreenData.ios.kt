package core

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIScreen

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun actualWidthFactor(): Float {
    val width = UIScreen.mainScreen.bounds.useContents { size.width }
    val coefficient = width.toFloat() / 375f
    return coefficient
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun actualHeightFactor(): Float {
    val height = UIScreen.mainScreen.bounds.useContents { size.height }
    val coefficient = height.toFloat() / 812f
    return coefficient
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun getScreenWidth(): Int {
    return UIScreen.mainScreen.bounds.useContents { size.width.toInt() }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun getScreenHeight(): Int {
    return UIScreen.mainScreen.bounds.useContents { size.height.toInt() }
}


