package ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.trashbox
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import sharedData.AppColors
import sharedData.actualWidthFactor

@Composable
fun RemoveButton(action: (Boolean) -> Unit, description: String) {
    Box(
        modifier = Modifier
            .width((actualWidthFactor() * 80).dp)
            .height(96.dp)
            .background(AppColors.RedError, RoundedCornerShape(12.dp))
            .clickable { action(true) },
        Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(Res.drawable.trashbox),
                contentDescription = null,
            )
            Text(
                text = description,
                textAlign = TextAlign.Center,
                color = AppColors.White,
                fontSize = 11.sp,
                fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                modifier = Modifier
                    .padding(horizontal = 18.5.dp)
            )
        }
    }
}