package models.appInternalModels

import models.apiModels.MetaSecretCoreStateModel

data class HandleStateModel (
    val stateModel: MetaSecretCoreStateModel,
    val vaultName: String
)