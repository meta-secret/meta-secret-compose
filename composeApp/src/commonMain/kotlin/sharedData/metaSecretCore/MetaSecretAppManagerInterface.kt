package sharedData.metaSecretCore

import models.apiModels.AppStateModel
import models.apiModels.VaultFullInfo

interface MetaSecretAppManagerInterface {
    suspend fun initWithSavedKey(): InitResult
    suspend fun checkAuth(): AuthState

    fun getStateModel(): AppStateModel?
    fun getVaultInfoModel(): VaultFullInfo?
}