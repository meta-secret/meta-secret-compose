package core.metaSecretCore

import models.apiModels.AppStateModel
import models.apiModels.CommonResponseModel
import models.apiModels.JoinClusterRequest
import models.apiModels.SecretApiModel
import models.apiModels.UserData
import models.apiModels.VaultEvents
import models.apiModels.VaultFullInfo
import models.apiModels.VaultSummary
import models.appInternalModels.ClaimModel
import models.appInternalModels.SecretModel

interface MetaSecretAppManagerInterface {
    suspend fun initWithSavedKey(): InitResult
    suspend fun checkAuth(): AuthState

    suspend fun getStateModel(): AppStateModel?
    suspend fun getVaultFullInfoModel(): VaultFullInfo?
    suspend fun getVaultEventsModel(): VaultEvents?
    suspend fun getJoinRequestsCount(): Int?
    suspend fun getJoinRequestsCandidate(): List<JoinClusterRequest>?
    suspend fun getVaultSummary(): VaultSummary?
    suspend fun updateMember(candidate: UserData, actionUpdate: String): CommonResponseModel?
    suspend fun getUserDataBy(deviceId: String): UserData?
    suspend fun splitSecret(secretModel: SecretModel): CommonResponseModel?
    suspend fun findClaim(secretId: String): ClaimModel?
    suspend fun recover(secretModel: SecretModel): CommonResponseModel?
    suspend fun acceptRecover(claim: ClaimModel): CommonResponseModel?
    suspend fun showRecovered(secretModel: SecretModel): String?
    suspend fun getSecretsFromVault(): List<SecretApiModel>?
}
