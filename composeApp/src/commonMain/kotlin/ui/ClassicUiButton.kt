package ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sharedData.AppColors

@Composable
fun ClassicButton(
    action: () -> Unit,
    text: String,
    isEnabled: Boolean = true,
    color: Color = AppColors.ActionMain,
    borderColor: Color = AppColors.White5,
    fillMaxWidth: Boolean = true,
    horizontalPadding: Int = 0,
    modifier: Modifier = Modifier
) {
    Button(
        modifier = modifier
            .let { if (fillMaxWidth) it.fillMaxWidth() else it }
            .padding(horizontal = horizontalPadding.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            ),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = color,
            contentColor = AppColors.White,
            disabledBackgroundColor = AppColors.ActionMain.copy(alpha = 0.5f),
            disabledContentColor = AppColors.White.copy(alpha = 0.5f),
        ),
        enabled = isEnabled,
        elevation = null,
        onClick = { action() }
    ) {
        Text(text = text, fontSize = 16.sp, modifier = Modifier.height(22.dp))
    }
}