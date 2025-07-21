package sharedData.metaSecretCore

import models.apiModels.AppStateModel
import models.apiModels.JoinClusterRequest
import models.apiModels.VaultEvents
import models.apiModels.VaultFullInfo
import models.apiModels.VaultSummary

interface MetaSecretAppManagerInterface {
    suspend fun initWithSavedKey(): InitResult
    suspend fun checkAuth(): AuthState

    fun getStateModel(): AppStateModel?
    fun getVaultFullInfoModel(): VaultFullInfo?
    fun getVaultEventsModel(): VaultEvents?
    fun getJoinRequestsCount(): Int?
    fun getJoinRequestsCandidate(): List<JoinClusterRequest>?
    fun getVaultSummary(): VaultSummary?
}