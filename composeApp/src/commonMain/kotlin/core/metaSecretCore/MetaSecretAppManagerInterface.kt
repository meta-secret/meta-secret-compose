package core.metaSecretCore

import models.apiModels.AppStateModel
import models.apiModels.CommonResponseModel
import models.apiModels.JoinClusterRequest
import models.apiModels.UserData
import models.apiModels.VaultEvents
import models.apiModels.VaultFullInfo
import models.apiModels.VaultSummary
import models.appInternalModels.ClaimModel
import models.appInternalModels.SecretModel

interface MetaSecretAppManagerInterface {
    suspend fun initWithSavedKey(): InitResult
    suspend fun checkAuth(): AuthState

    fun getStateModel(): AppStateModel?
    fun getVaultFullInfoModel(): VaultFullInfo?
    fun getVaultEventsModel(): VaultEvents?
    fun getJoinRequestsCount(): Int?
    fun getJoinRequestsCandidate(): List<JoinClusterRequest>?
    fun getVaultSummary(): VaultSummary?
    fun updateMember(candidate: UserData, actionUpdate: String): CommonResponseModel?
    fun getUserDataBy(deviceId: String): UserData?
    fun splitSecret(secretModel: SecretModel): CommonResponseModel?
    fun findClaim(secretModel: SecretModel): ClaimModel?
    fun recover(secretModel: SecretModel): CommonResponseModel?
    fun acceptRecover(claim: ClaimModel): CommonResponseModel?
    fun showRecovered(secretModel: SecretModel): String?
}