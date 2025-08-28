package ui.dialogs.showsecret

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.compose.resources.Font
import core.AppColors
import core.Device
import core.KeyValueStorage

class ShowSecretViewModel(
    private val keyValueStorage: KeyValueStorage
) : ViewModel() {

    private val devicesList: StateFlow<List<Device>> = keyValueStorage.deviceData
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val devicesCount: StateFlow<Int> = devicesList.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    @Composable
    fun textRow(text: String, isPasswordVisible: Boolean) {
        val scrollState = rememberScrollState()
        var copyTriggered by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = AppColors.TextField,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp)
                .heightIn(min = 48.dp, max = 200.dp)
                .verticalScroll(scrollState)
                .clickable { if(isPasswordVisible){ copyTriggered = true }},
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 16.sp,
                fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                color = AppColors.White,
                textAlign = TextAlign.Center
            )
        }
        if (copyTriggered) {
            copyToClipboard(text)
            copyTriggered = false
        }
    }

    @Composable
    fun copyToClipboard(textToCopy: String) {
        val clipboardManager = LocalClipboardManager.current
        clipboardManager.setText(AnnotatedString(textToCopy))
    }
}