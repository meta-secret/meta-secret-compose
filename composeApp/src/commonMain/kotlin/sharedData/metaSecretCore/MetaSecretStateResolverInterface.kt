package sharedData.metaSecretCore

import models.appInternalModels.HandleStateModel

interface MetaSecretStateResolverInterface {
    fun handleState(model: HandleStateModel)
}