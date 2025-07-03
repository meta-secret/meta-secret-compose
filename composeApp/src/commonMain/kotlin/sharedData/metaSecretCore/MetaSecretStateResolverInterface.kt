package sharedData.metaSecretCore

import models.apiModels.MetaSecretCoreStateModel
import models.apiModels.OutsiderStatus
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
        println("✅ AppState: Start get app state")
        val jsonResult = metaSecretCore.getAppState()
        val coreStateModel = MetaSecretCoreStateModel.fromJson(jsonResult)

        val isSuccess = coreStateModel.success
        val stateModel = coreStateModel.getState()

        val result: LocalState? = if (isSuccess && stateModel == StateType.LOCAL) {
            println("✅ AppState: Current state is LOCAL")
            LocalState(vaultName, metaSecretCore)
        } else {
            println("⛔ AppState: SWW with LOCAL state")
            null
        }

        return result
    }

    fun generateNewCreds(): VaultState? {
        println("✅ AppState: Start generate new creds")
        val jsonResult = metaSecretCore.generateUserCreds(vaultName)
        val coreStateModel = MetaSecretCoreStateModel.fromJson(jsonResult)

        val isSuccess = coreStateModel.success
        val stateModel = coreStateModel.getState()
        val vaultInfo = coreStateModel.getVaultInfo()

        val result: VaultState? = if (isSuccess && stateModel == StateType.VAULT) {
            println("✅ AppState: Current state is VAULT")
            VaultState(metaSecretCore)
        } else if (isSuccess && stateModel == StateType.OUTSIDER) {
            println("✅ AppState: Current state is OUTSIDER")
            when (vaultInfo?.outsider?.status) {
                OutsiderStatus.NON_MEMBER -> {
                    println("✅ AppState: Current state is NON_MEMBER")
                    VaultState(metaSecretCore)
                }
                OutsiderStatus.PENDING -> {
                    println("✅ AppState: Current state is PENDING")
                    // TODO: #47 Show alert that tells user to accept the request
                    null
                }
                OutsiderStatus.DECLINED -> {
                    println("✅ AppState: Current state is DECLINED")
                    //  TODO: #47 Show alert that request has been declined
                    null
                }
                null -> {
                    println("⛔ AppState: SWW with OUTSIDER state")
                    null
                }
            }

        } else {
            println("⛔AppState: SWW with VAULT state")
            null
        }

        return result
    }
}

class VaultState(
    private val metaSecretCore: MetaSecretCoreInterface
) : AppState {
    fun signUp(): AppState? {
        println("✅ AppState: Start SignUp")
        val jsonResult = metaSecretCore.signUp()
        val coreStateModel = MetaSecretCoreStateModel.fromJson(jsonResult)

        val isSuccess = coreStateModel.success
        val stateModel = coreStateModel.getState()

        val result: AppState? = if (isSuccess && stateModel == StateType.MEMBER) {
            println("✅ AppState: Current state is MEMBER")
            MemberState()
        } else if (isSuccess && stateModel == StateType.OUTSIDER) {
            println("✅ AppState: Current state is OUTSIDER")
            OutsiderState()
        } else {
            println("⛔AppState: SWW with MEMBER state")
            null
        }

        return result
    }
}

class  MemberState : AppState

class  OutsiderState : AppState
