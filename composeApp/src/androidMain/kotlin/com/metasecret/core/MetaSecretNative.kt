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

    @JvmStatic
    external fun splitSecret(secretId: String, secret: String): String

    @JvmStatic
    external fun findClaim(secretId: String): String

    @JvmStatic
    external fun recover(secretId: String): String

    @JvmStatic
    external fun acceptRecover(claimId: String): String

    @JvmStatic
    external fun showRecovered(secretId: String): String
} 