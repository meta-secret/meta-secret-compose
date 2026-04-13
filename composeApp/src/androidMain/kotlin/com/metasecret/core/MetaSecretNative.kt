package com.metasecret.core

object MetaSecretNative {
    @JvmStatic
    fun generateMasterKey(): String = uniffi.mobile_uniffi.generateMasterKey()

    @JvmStatic
    fun init(masterKey: String): String = uniffi.mobile_uniffi.initAndroid(masterKey)

    @JvmStatic
    fun getState(): String = uniffi.mobile_uniffi.getState()

    @JvmStatic
    fun generate_user_creds(vaultName: String): String =
        uniffi.mobile_uniffi.generateUserCreds(vaultName)

    @JvmStatic
    fun signUp(): String = uniffi.mobile_uniffi.signUp()

    @JvmStatic
    fun update_membership(candidate: String, actionUpdate: String): String =
        uniffi.mobile_uniffi.updateMembership(candidate, actionUpdate)

    @JvmStatic
    fun clean_up_database(): String = uniffi.mobile_uniffi.cleanUpDatabase()

    @JvmStatic
    fun split(secretId: String, secret: String): String =
        uniffi.mobile_uniffi.splitSecret(secretId, secret)

    @JvmStatic
    fun find_claim_by_(secretId: String): String =
        uniffi.mobile_uniffi.findClaimBy(secretId)

    @JvmStatic
    fun recover(secretId: String): String = uniffi.mobile_uniffi.recover(secretId)

    @JvmStatic
    fun acceptRecover(claimId: String): String =
        uniffi.mobile_uniffi.acceptRecover(claimId)

    @JvmStatic
    fun declineRecover(claimId: String): String =
        uniffi.mobile_uniffi.declineRecover(claimId)

    @JvmStatic
    fun sendDeclineCompletion(claimId: String): String =
        uniffi.mobile_uniffi.sendDeclineCompletion(claimId)

    @JvmStatic
    fun showRecovered(secretId: String): String =
        uniffi.mobile_uniffi.showRecovered(secretId)

    @JvmStatic
    fun metaWsStart(): String = uniffi.mobile_uniffi.metaWsStart()

    @JvmStatic
    fun metaWsStop(): String = uniffi.mobile_uniffi.metaWsStop()

    @JvmStatic
    fun metaWsWaitNextEvent(timeoutMs: UInt): Boolean =
        uniffi.mobile_uniffi.metaWsWaitNextEvent(timeoutMs)
}
