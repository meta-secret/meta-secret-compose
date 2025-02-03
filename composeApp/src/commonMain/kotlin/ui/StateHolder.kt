package ui

import kotlinx.coroutines.flow.MutableStateFlow

object WarningStateHolder {
    val isWarningVisible = MutableStateFlow(true)
    fun setVisibility(state: Boolean) {
        isWarningVisible.value = state
    }
}

object SecretsStateHolder {
    val isDialogVisible = MutableStateFlow(false)
    fun setVisibility(state: Boolean) {
        isDialogVisible.value = state
    }
}

object DevicesStateHolder {
    val isDialogVisible = MutableStateFlow(false)
    fun setVisibility(state: Boolean) {
        isDialogVisible.value = state
    }
}

