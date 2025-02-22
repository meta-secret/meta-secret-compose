package ui

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow

object WarningStateHolder {
    val isWarningVisible = MutableStateFlow(true)
    fun setVisibility(state: Boolean) {
        isWarningVisible.value = state
    }
}
object TabStateHolder {
    val selectedTabIndex = mutableStateOf(0)
    fun setTabIndex(index: Int) {
        selectedTabIndex.value = index
    }
}