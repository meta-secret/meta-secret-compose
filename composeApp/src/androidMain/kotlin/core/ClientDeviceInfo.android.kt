package core

import android.content.Context
import android.provider.Settings
import android.content.res.Resources
import android.os.Build

class ClientDeviceInfoProviderAndroid(private val context: Context) : ClientDeviceInfoProviderInterface {
    override fun current(): ClientDeviceInfo {
        val displayName = Settings.Global.getString(context.contentResolver, "device_name")
            ?.trim()?.takeIf { it.isNotEmpty() }
            ?: Build.MODEL.orEmpty().trim().ifBlank { "Android Device" }

        val isTablet = Resources.getSystem().configuration.smallestScreenWidthDp >= 600
        val deviceType = if (isTablet) "Tablet" else "Android"

        return ClientDeviceInfo(
            deviceName = displayName,
            deviceType = deviceType,
        )
    }
}
