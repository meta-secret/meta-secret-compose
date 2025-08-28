package core

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.UIKit.UIDevice
import platform.Foundation.NSUUID

@OptIn(ExperimentalForeignApi::class)
actual fun getAppVersion(): String {
    val mainBundle = NSBundle.mainBundle
    val version = mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
    val build = mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String
    return "${version ?: "1.0.0"}${if (build != null) " ($build)" else ""}"
}

@OptIn(ExperimentalForeignApi::class)
actual fun getDeviceMake(): String {
    return UIDevice.currentDevice.model.uppercase()
}

@OptIn(ExperimentalForeignApi::class)
actual fun getDeviceId(): String {
    // Используем identifierForVendor или создаем случайный UUID
    return UIDevice.currentDevice.identifierForVendor?.UUIDString() ?: NSUUID().UUIDString()
}