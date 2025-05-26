package platform

import di.iosPlatformModule
import org.koin.core.module.Module

actual fun getPlatformModule(): Module = iosPlatformModule 