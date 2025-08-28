package core

interface ScreenMetricsProviderInterface {
    fun widthFactor(): Float
    fun heightFactor(): Float
    fun screenWidth(): Int
    fun screenHeight(): Int
}


