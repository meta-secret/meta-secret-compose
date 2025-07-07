package sharedData.metaSecretCore

import models.apiModels.AppStateModel
import models.apiModels.State
import models.apiModels.UserDataOutsiderStatus
import models.apiModels.VaultFullInfo
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
        val coreStateModel = AppStateModel.fromJson(jsonResult)

        val isSuccess = coreStateModel.success
        val stateModel = coreStateModel.getAppState()

        val result: LocalState? = if (isSuccess && stateModel is State.Local) {
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
        val coreStateModel = AppStateModel.fromJson(jsonResult)

        val isSuccess = coreStateModel.success
        val stateModel = coreStateModel.getAppState()
        val vaultInfo = coreStateModel.getVaultState()

        val result: VaultState? = if (isSuccess && stateModel is State.Local) {
            println("✅ AppState: Current state is VAULT")
            VaultState(metaSecretCore)
        } else if (isSuccess && vaultInfo is VaultFullInfo.Outsider) {
            println("✅ AppState: Current state is OUTSIDER")
            when (vaultInfo.outsider.status) {
                UserDataOutsiderStatus.NON_MEMBER -> {
                    println("✅ AppState: Current state is NON_MEMBER")
                    VaultState(metaSecretCore)
                }
                UserDataOutsiderStatus.PENDING -> {
                    println("✅ AppState: Current state is PENDING")
                    // TODO: #47 Show alert that tells user to accept the request
                    null
                }
                UserDataOutsiderStatus.DECLINED -> {
                    println("✅ AppState: Current state is DECLINED")
                    //  TODO: #47 Show alert that request has been declined
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
        val coreStateModel = AppStateModel.fromJson(jsonResult)

        val isSuccess = coreStateModel.success
        val vaultInfo = coreStateModel.getVaultState()

        val result: AppState? = if (isSuccess && vaultInfo is VaultFullInfo.Member) {
            println("✅ AppState: Current state is MEMBER")
            MemberState()
        } else if (isSuccess && vaultInfo is VaultFullInfo.Outsider) {
            println("✅ AppState: Current state is OUTSIDER")
            OutsiderState(coreStateModel)
        } else {
            println("⛔AppState: SWW with MEMBER state")
            null
        }

        return result
    }
}

class  MemberState : AppState

class  OutsiderState(val coreStateModel: AppStateModel) : AppState
