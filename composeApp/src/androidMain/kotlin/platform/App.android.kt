package platform

import di.androidPlatformModule
import org.koin.core.module.Module

actual fun getPlatformModule(): Module = androidPlatformModule 