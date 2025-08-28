package core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIScreen

class ScreenMetricsProviderIos : ScreenMetricsProviderInterface {
    @OptIn(ExperimentalForeignApi::class)
    override fun widthFactor(): Float {
        val width = UIScreen.mainScreen.bounds.useContents { size.width }
        return width.toFloat() / 360f
    }
    @OptIn(ExperimentalForeignApi::class)
    override fun heightFactor(): Float {
        val height = UIScreen.mainScreen.bounds.useContents { size.height }
        return height.toFloat() / 800f
    }
    @OptIn(ExperimentalForeignApi::class)
    override fun screenWidth(): Int {
        return UIScreen.mainScreen.bounds.useContents { size.width.toInt() }
    }
    @OptIn(ExperimentalForeignApi::class)
    override fun screenHeight(): Int {
        return UIScreen.mainScreen.bounds.useContents { size.height.toInt() }
    }
}


