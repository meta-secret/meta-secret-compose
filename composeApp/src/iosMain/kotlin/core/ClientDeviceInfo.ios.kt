package core

import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.UIDevice

class ClientDeviceInfoProviderIos : ClientDeviceInfoProviderInterface {
    override fun current(): ClientDeviceInfo {
        val device = UIDevice.currentDevice
        // iOS supplies a generic name when the user-assigned-name entitlement is unavailable.
        val name = device.name.trim().ifBlank { device.model }
        return ClientDeviceInfo(name, if (device.userInterfaceIdiom == UIUserInterfaceIdiomPad) "Tablet" else "iPhone")
    }
}
