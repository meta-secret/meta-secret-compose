package core

import android.content.res.Resources

class ScreenMetricsProviderAndroid : ScreenMetricsProviderInterface {
    override fun widthFactor(): Float = Resources.getSystem().displayMetrics.widthPixels / 360f
    override fun heightFactor(): Float = Resources.getSystem().displayMetrics.heightPixels / 800f
    override fun screenWidth(): Int = Resources.getSystem().displayMetrics.widthPixels
    override fun screenHeight(): Int = Resources.getSystem().displayMetrics.heightPixels
}


