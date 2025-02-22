package ui.dialogs.addsecret

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import sharedData.AppColors
import storage.KeyValueStorage
import storage.Secret

class AddSecretViewModel(
    private val keyValueStorage: KeyValueStorage
) : ViewModel() {

    fun addSecret(secretName: String, password: String) {
        keyValueStorage.addSecret(Secret(secretName, password))
    }

    @Composable
    fun textInput(
        placeholderText: String,
        onTextChange: (String) -> Unit
    ) {
        var text by remember { mutableStateOf("") }
        val isError by remember { mutableStateOf(false) }
        var isFocused by remember { mutableStateOf(false) }
        val focusRequester = FocusRequester()

        TextField(
            value = text,
            onValueChange = { newText ->
                text = newText
                onTextChange(newText)
            },
            shape = RoundedCornerShape(8.dp),
            placeholder = {
                Text(
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    text = placeholderText
                )
            },
            modifier = Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .heightIn(min = 52.dp, max = 200.dp)
                .border(
                    width = 2.dp,
                    color =
                    if (isError) {
                        AppColors.RedError
                    } else {
                        if (isFocused) {
                            AppColors.ActionPremium
                        } else {
                            Color.Transparent
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                },
            maxLines = Int.MAX_VALUE,
            singleLine = false,
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            textStyle = TextStyle(fontSize = 16.sp, color = Color.White),
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = AppColors.White5,
                cursorColor = AppColors.White,
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent
            )
        )
    }
}