package ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.AppColors

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
    val shape = RoundedCornerShape(8.dp)
    Button(
        modifier = modifier
            .let { if (fillMaxWidth) it.fillMaxWidth() else it }
            .padding(horizontal = horizontalPadding.dp)
            .height(48.dp)
            .border(width = 1.dp, color = borderColor, shape = shape),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = AppColors.White,
            disabledContainerColor = AppColors.ActionMain.copy(alpha = 0.5f),
            disabledContentColor = AppColors.White.copy(alpha = 0.5f),
        ),
        enabled = isEnabled,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
            hoveredElevation = 0.dp,
            focusedElevation = 0.dp,
        ),
        onClick = { action() }
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            color = AppColors.White,
            modifier = Modifier.height(22.dp)
        )
    }
}