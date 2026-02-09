package ui.scenes.common

import androidx.lifecycle.ViewModel
import core.DebugLoggerInterface
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

abstract class CommonViewModel : ViewModel(), KoinComponent {
    protected val logger: DebugLoggerInterface by inject()
    
    abstract fun handle(event: CommonViewModelEventsInterface)
}

interface CommonViewModelEventsInterface {}