package core

interface ScreenMetricsProviderInterface {
    fun widthFactor(): Float
    fun heightFactor(): Float
    fun screenWidth(): Int
    fun screenHeight(): Int
    fun topSafeAreaInset(): Int
    fun bottomSafeAreaInset(): Int
}


