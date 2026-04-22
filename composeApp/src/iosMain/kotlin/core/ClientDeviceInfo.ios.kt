package core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.UIDevice
import platform.posix.uname
import platform.posix.utsname
import kotlinx.cinterop.toKString

class ClientDeviceInfoProviderIos : ClientDeviceInfoProviderInterface {
    @OptIn(ExperimentalForeignApi::class)
    override fun current(): ClientDeviceInfo {
        val rawMachine = resolveRawMachineIdentifier()
        val modelIdentifier = if (rawMachine == "x86_64" || rawMachine == "arm64") {
            NSProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] as? String ?: rawMachine
        } else {
            rawMachine
        }
        val displayName = mapIosModelIdentifierToDisplayName(modelIdentifier)
        val isTablet = UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad
        val deviceType = if (isTablet) "Tablet" else "iPhone"
        return ClientDeviceInfo(
            deviceName = displayName,
            deviceType = deviceType,
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun resolveRawMachineIdentifier(): String = memScoped {
        val systemInfo = alloc<utsname>()
        uname(systemInfo.ptr)
        systemInfo.machine.toKString()
    }
}
