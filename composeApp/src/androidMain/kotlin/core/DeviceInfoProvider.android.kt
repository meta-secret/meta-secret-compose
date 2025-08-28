package core

import android.os.Build
import metasecret.project.com.BuildConfig
import java.util.Locale

class DeviceInfoProviderAndroid : DeviceInfoProviderInterface {
    override fun getAppVersion(): String = BuildConfig.APP_VERSION
    override fun getDeviceMake(): String = Build.MANUFACTURER.uppercase(Locale.ROOT)
    override fun getDeviceId(): String = Build.ID.uppercase(Locale.ROOT)
}


