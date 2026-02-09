package ui

import androidx.compose.runtime.mutableStateOf

object TabStateHolder {
    val selectedTabIndex = mutableStateOf(0)
    fun setTabIndex(index: Int) {
        selectedTabIndex.value = index
    }
}