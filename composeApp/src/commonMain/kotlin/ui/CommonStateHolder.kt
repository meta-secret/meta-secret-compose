package ui

import kotlinx.coroutines.flow.MutableStateFlow

object WarningStateHolder {
    val isWarningVisible = MutableStateFlow(true)
    fun setVisibility(state: Boolean) {
        isWarningVisible.value = state
    }
}

object SecretsDialogStateHolder {
    val isDialogVisible = MutableStateFlow(false)
    fun setVisibility(state: Boolean) {
        isDialogVisible.value = state
    }
}

object DevicesDialogStateHolder {
    val isDialogVisible = MutableStateFlow(false)
    fun setVisibility(state: Boolean) {
        isDialogVisible.value = state
    }
}

object DevicesMainDialogStateHolder {
    val isDialogVisible = MutableStateFlow(false)
    fun setVisibility(state: Boolean) {
        isDialogVisible.value = state
    }
}

object NotificationStateHolder {
    val isNotificationVisible = MutableStateFlow(false)
    fun setVisibility(state: Boolean) {
        isNotificationVisible.value = state
    }
}


