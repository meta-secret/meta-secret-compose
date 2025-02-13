package ui

import kotlinx.coroutines.flow.MutableStateFlow

object WarningStateHolder {
    val isWarningVisible = MutableStateFlow(true)
    fun setVisibility(state: Boolean) {
        isWarningVisible.value = state
    }
}

