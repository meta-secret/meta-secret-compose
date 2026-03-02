package com.metasecret.core

object MetaSecretNative {
    @JvmStatic
    external fun generateMasterKey(): String

    @JvmStatic
    external fun init(masterKey: String): String

    @JvmStatic
    external fun getState(): String

    @JvmStatic
    external fun generate_user_creds(vaultName: String): String

    @JvmStatic
    external fun signUp(): String

    @JvmStatic
    external fun update_membership(candidate: String, actionUpdate: String): String

    @JvmStatic
    external fun clean_up_database(): String

    @JvmStatic
    external fun split(secretId: String, secret: String): String

    @JvmStatic
    external fun find_claim_by_(secretId: String): String

    @JvmStatic
    external fun recover(secretId: String): String

    @JvmStatic
    external fun acceptRecover(claimId: String): String

    @JvmStatic
    external fun declineRecover(claimId: String): String

    @JvmStatic
    external fun sendDeclineCompletion(claimId: String): String

    @JvmStatic
    external fun showRecovered(secretId: String): String
} 