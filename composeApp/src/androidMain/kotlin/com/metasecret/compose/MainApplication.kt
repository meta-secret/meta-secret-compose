package com.metasecret.compose

import android.app.Application
import android.util.Log
import com.metasecret.core.MetaSecretNative

class MainApplication : Application() {

    companion object {
        lateinit var INSTANCE: MainApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        try {
            // UniFFI Kotlin uses JNA (Native.load). JNA must load libjnidispatch.so before
            // libmetasecret_mobile.so — loading Rust first caused SIGSEGV in jnidispatch JNI_OnLoad.
            Class.forName("com.sun.jna.Native")

            System.setProperty("uniffi.component.mobile_uniffi.libraryOverride", "metasecret_mobile")
            System.loadLibrary("metasecret_mobile")

            val verifierInitOk = MetaSecretNative.initRustlsPlatformVerifier(applicationContext)
            if (!verifierInitOk) {
                Log.e("MainApplication", "rustls-platform-verifier initialization returned false")
            }
        } catch (t: Throwable) {
            Log.e("MainApplication", "UniFFI/JNA preload failed", t)
        }
    }
}
