package ui

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow

object TabStateHolder {
    val selectedTabIndex = mutableStateOf(0)
    fun setTabIndex(index: Int) {
        selectedTabIndex.value = index
    }
}