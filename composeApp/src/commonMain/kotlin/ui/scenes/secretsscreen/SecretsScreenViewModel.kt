package ui.scenes.secretsscreen

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import core.KeyValueStorageInterface
import core.ScreenMetricsProviderInterface
import core.Secret
import core.AppColors
import core.LogTags
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.manrope_bold
import kotlinproject.composeapp.generated.resources.removeSecretConfirmation
import kotlinproject.composeapp.generated.resources.fromAllDevices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import ui.TabStateHolder
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface

class SecretsScreenViewModel(
    private val keyValueStorage: KeyValueStorageInterface,
    val screenMetricsProvider: ScreenMetricsProviderInterface,
    private val socketHandler: MetaSecretSocketHandlerInterface,
    private val metaSecretAppManager: MetaSecretAppManagerInterface,
) : ViewModel(), CommonViewModel {

    private val secretsList: StateFlow<List<Secret>> = keyValueStorage.secretData
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val secrets: StateFlow<List<Secret>> = secretsList

    private val _devicesCount = MutableStateFlow(0)
    val devicesCount: StateFlow<Int> = _devicesCount.asStateFlow()

    val secretsCount: StateFlow<Int> = secretsList.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    init {
        CoroutineScope(Dispatchers.IO).launch {
            println("✅${LogTags.SECRETS_VM}: Start to follow UPDATE_STATE")
            socketHandler.actionsToFollow(
                add = listOf(SocketRequestModel.GET_STATE),
                exclude = null
            )
        }

        viewModelScope.launch {
            socketHandler.socketActions.collect { actionType ->
                println("✅${LogTags.SECRETS_VM}: Socket action type is $actionType")
                if (actionType == SocketActionModel.UPDATE_STATE) {
                    println("✅${LogTags.SECRETS_VM}: New state for secrets been gotten")
                    loadSecretsFromVault()
                }
            }
        }
        
        loadSecretsFromVault()
    }

    override fun handle(event: CommonViewModelEventsInterface) {
        if (event is SecretsEvents) {
            when (event) {
                is SecretsEvents.GetSecret -> {
                    getSecret(event.index)
                }

                is SecretsEvents.SetTabIndex -> {
                    setTabIndex(event.index)
                }
            }
        }
    }

    fun getSecret(index: Int): Secret {
        return secretsList.value[index]
    }

    private fun setTabIndex(index: Int) {
        TabStateHolder.setTabIndex(index)
    }

    private fun loadSecretsFromVault() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                println("✅${LogTags.SECRETS_VM}: Loading secrets from vault in background")
                val secretsFromVault = metaSecretAppManager.getSecretsFromVault()
                if (secretsFromVault != null) {
                    keyValueStorage.syncSecretsFromVault(secretsFromVault)
                    println("✅${LogTags.SECRETS_VM}: Secrets synced successfully")
                } else {
                    println("❌${LogTags.SECRETS_VM}: Failed to get secrets from vault")
                }
                
                val vaultSummary = withContext(Dispatchers.Default) {
                    metaSecretAppManager.getVaultSummary()
                }
                
                if (vaultSummary != null) {
                    _devicesCount.value = vaultSummary.users.size
                    println("✅${LogTags.SECRETS_VM}: Devices count updated to ${_devicesCount.value}")
                } else {
                    println("❌${LogTags.SECRETS_VM}: VaultSummary is null")
                }
            } catch (e: Exception) {
                println("❌${LogTags.SECRETS_VM}: Error loading secrets from vault: ${e.message}")
            }
        }
    }

    // TODO: For the future
//    @Composable
//    fun deleteSecretText(secretName: String): AnnotatedString {
//        return buildAnnotatedString {
//            append(stringResource(Res.string.removeSecretConfirmation))
//            withStyle(
//                style = SpanStyle(
//                    fontFamily = FontFamily(Font(Res.font.manrope_bold)),
//                    fontSize = 18.sp,
//                    color = AppColors.White
//                )
//            ) {
//                append(secretName)
//            }
//            append(stringResource(Res.string.fromAllDevices))
//        }
//    }
}

sealed class SecretsEvents : CommonViewModelEventsInterface {
    data class GetSecret(val index: Int) : SecretsEvents()
    data class SetTabIndex(val index: Int) : SecretsEvents()
}