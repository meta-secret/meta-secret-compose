package core

import android.content.res.Resources
import android.os.Build

class ClientDeviceInfoProviderAndroid : ClientDeviceInfoProviderInterface {
    override fun current(): ClientDeviceInfo {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val displayName = listOf(manufacturer, model)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android Device" }

        val isTablet = Resources.getSystem().configuration.smallestScreenWidthDp >= 600
        val deviceType = if (isTablet) "Tablet" else "Android"

        return ClientDeviceInfo(
            deviceName = displayName,
            deviceType = deviceType,
        )
    }
}
