package sharedData

import metasecret.project.com.BuildConfig


actual fun getAppVersion(): String = BuildConfig.APP_VERSION
