package core.metaSecretCore

import kotlin.test.Test
import kotlin.test.assertTrue
import models.apiModels.UserData

class MetaSecretCoreInterfaceSegregationTest {

    @Test
    fun `aggregate interface exposes account and secret contracts`() {
        val core = FakeMetaSecretCore()

        assertTrue(core is MetaSecretAccountInterface)
        assertTrue(core is MetaSecretSecretOperationsInterface)
    }

    private class FakeMetaSecretCore : MetaSecretCoreInterface {
        override fun generateMasterKey(): String = "master-key"
        override fun initAppManager(masterKey: String): String = "init:$masterKey"
        override fun getAppState(): String = "state"
        override fun generateUserCreds(vaultName: String): String = vaultName
        override fun signUp(): String = "signup"
        override fun updateMembership(candidate: UserData, actionUpdate: String): String = actionUpdate
        override fun splitSecret(secretName: String, secret: String): String = "$secretName:$secret"
        override fun findClaim(secretId: String): String = secretId
        override fun recover(secretId: String): String = secretId
        override fun acceptRecover(claimId: String): String = claimId
        override fun declineRecover(claimId: String): String = claimId
        override fun sendDeclineCompletion(claimId: String): String = claimId
        override fun showRecovered(secretId: String): String = secretId
    }
}
