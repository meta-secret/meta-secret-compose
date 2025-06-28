package sharedData.metaSecretCore

import models.apiModels.MetaSecretCoreStateModel
import models.apiModels.StateType
import models.appInternalModels.AppErrors

data class AppStateResult (
    val appState: AppState?,
    val error: AppErrors?
)

interface MetaSecretStateResolverInterface {
    fun startFirstSignUp(
        vaultName: String
    ): AppStateResult
}

interface AppState

open class LocalState(
    private val vaultName: String,
    private val metaSecretCore: MetaSecretCoreInterface
) : AppState {
    fun new(): LocalState? {
        println("✅ Start get app state")
        val jsonResult = metaSecretCore.getAppState()
        val coreStateModel = MetaSecretCoreStateModel.fromJson(jsonResult)

        val isSuccess = coreStateModel.success
        val stateModel = coreStateModel.getState()

        val result: LocalState? = if (isSuccess && stateModel == StateType.LOCAL) {
            println("✅ Current state is LOCAL")
            LocalState(vaultName, metaSecretCore)
        } else {
            println("⛔ SWW with LOCAL state")
            null
        }

        return result
    }

    fun generateNewCreds(): VaultState? {
        println("✅ Start generate new creds")
        var jsonResult = metaSecretCore.generateUserCreds(vaultName)
        var coreStateModel = MetaSecretCoreStateModel.fromJson(jsonResult)

        var isSuccess = coreStateModel.success
        if (isSuccess) {
            jsonResult = metaSecretCore.getAppState()
            println("Debug: State Model jsonResult $jsonResult")
        }

        coreStateModel = MetaSecretCoreStateModel.fromJson(jsonResult)
        isSuccess = coreStateModel.success
        val stateModel = coreStateModel.getState()

        println("Debug: State Model $stateModel")
        val result: VaultState? = if (isSuccess && stateModel == StateType.VAULT_NOT_EXISTS) {
            println("✅ Current state is VAULT")
            VaultState(metaSecretCore)
        } else if (isSuccess && stateModel == StateType.MEMBER) {
            println("⛔ VAULT is already MEMBER")
            null
        } else {
            println("⛔ SWW with VAULT state")
            null
        }

        return result
    }
}

class  VaultState(
    private val metaSecretCore: MetaSecretCoreInterface
) : AppState {
    fun signUp(): MemberState? {
        println("✅ Start SignUp")
        val jsonResult = metaSecretCore.signUp()
        val coreStateModel = MetaSecretCoreStateModel.fromJson(jsonResult)

        val isSuccess = coreStateModel.success
        val stateModel = coreStateModel.getState()

        val result: MemberState? = if (isSuccess && stateModel == StateType.MEMBER) {
            println("✅ Current state is MEMBER")
            MemberState()
        } else {
            println("⛔ SWW with MEMBER state")
            null
        }

        return result
    }
}

class  MemberState : AppState

class  OutsiderState : AppState
