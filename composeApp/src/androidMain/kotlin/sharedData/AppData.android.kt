package sharedData

import android.os.Build
import metasecret.project.com.BuildConfig
import java.util.Locale

actual fun getAppVersion(): String = BuildConfig.APP_VERSION
actual fun getDeviceMake(): String = Build.MANUFACTURER.uppercase(Locale.ROOT)
actual fun getDeviceId(): String = Build.ID.uppercase(Locale.ROOT)