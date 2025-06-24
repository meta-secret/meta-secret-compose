package sharedData.metaSecretCore

import models.apiModels.MetaSecretCoreStateModel
import models.apiModels.StateType
import models.appInternalModels.HandleStateModel

class MetaSecretStateResolver(
    private val metaSecretCoreInterface: MetaSecretCoreInterface
) : MetaSecretStateResolverInterface {
    override fun handleState(model: HandleStateModel) {
        when (model.stateModel.message.state) {
            StateType.LOCAL -> handleLocalState(model.vaultName)
            StateType.VAULT -> handleVaultState()
        }
    }
    
    private fun handleLocalState(vaultName: String) {
        val result = metaSecretCoreInterface.generateUserCreds(vaultName)
        println("DEBUG: $result")
        val resultS = metaSecretCoreInterface.signUp()
        println("DEBUG: $resultS")
    }
    
    private fun handleVaultState() {
        println("DEBUG: Handling VAULT state")
    }
}