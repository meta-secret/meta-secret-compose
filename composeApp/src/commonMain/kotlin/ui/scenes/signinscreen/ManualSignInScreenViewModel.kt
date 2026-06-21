package ui.scenes.signinscreen

import androidx.lifecycle.viewModelScope
import core.ScreenMetricsProviderInterface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface
import ui.scenes.mainscreen.MainViewEvents

class ManualSignInScreenViewModel(
    val screenMetricsProvider: ScreenMetricsProviderInterface,
) : CommonViewModel() {

    override fun handle(event: CommonViewModelEventsInterface) {
        when (event) {
            is ManualSignInViewEvents.ShowError -> {
                val message = event.errorMessage ?: return
                viewModelScope.launch {
                    delay(500)
                    showNotification(message, isError = true)
                }
            }
        }
    }
}

sealed class ManualSignInViewEvents : CommonViewModelEventsInterface {
    data class ShowError(val errorMessage: String?) : ManualSignInViewEvents()
}
