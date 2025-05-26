package com.metasecret.core

object MetaSecretNative {
    @JvmStatic
    external fun generateMasterKey(): String

    @JvmStatic
    external fun initAppManager(masterKey: String): String

    @JvmStatic
    external fun getAppState(): String

    @JvmStatic
    external fun signUp(name: String)
} 