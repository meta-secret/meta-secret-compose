package ui.scenes.signinscreen

import core.ScreenMetricsProviderInterface
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface

class ManualSignInScreenViewModel(
    val screenMetricsProvider: ScreenMetricsProviderInterface,
) : CommonViewModel() {
    override fun handle(event: CommonViewModelEventsInterface) {
        TODO("Not yet implemented")
    }

}

sealed class ManualSignInViewEvents : CommonViewModelEventsInterface {

}