package ui.scenes.secretsscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import core.Device
import core.KeyValueStorageInterface
import core.ScreenMetricsProviderInterface
import core.Secret
import ui.TabStateHolder
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface

class SecretsScreenViewModel(
    keyValueStorage: KeyValueStorageInterface,
    val screenMetricsProvider: ScreenMetricsProviderInterface
) : ViewModel(), CommonViewModel {

    private val secretsList: StateFlow<List<Secret>> = keyValueStorage.secretData
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val devicesList: StateFlow<List<Device>> = keyValueStorage.deviceData
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val secrets: StateFlow<List<Secret>> = secretsList

    val secretsCount: StateFlow<Int> = secretsList.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val devicesCount: StateFlow<Int> = devicesList.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    init {
        // TODO: If needed
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

    private fun getSecret(index: Int): Secret {
        return secretsList.value[index]
    }

    private fun setTabIndex(index: Int) {
        TabStateHolder.setTabIndex(index)
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