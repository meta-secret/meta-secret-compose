package com.metasecret.core

object MetaSecretNative {
    @JvmStatic
    external fun generateMasterKey(): String

    @JvmStatic
    external fun initAppManager(masterKey: String): String

    @JvmStatic
    external fun getAppState(): String

    @JvmStatic
    external fun generateUserCreds(vaultName: String): String

    @JvmStatic
    external fun signUp(): String

    @JvmStatic
    external fun updateMembership(candidate: String, actionUpdate: String): String

    @JvmStatic
    external fun cleanUpDatabase(): String
} 