package ui.notifications

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.close
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.warning
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import core.AppColors

@Composable
fun warningContent(
    text: AnnotatedString,
    mainAction: () -> Unit?,
    closeAction: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 14.dp)
            .background(AppColors.White5, RoundedCornerShape(10.dp))
            .height(92.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .height(60.dp)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painter = painterResource(Res.drawable.warning),
                contentDescription = null,
                contentScale = ContentScale.FillBounds
            )
            ClickableText(
                text = text,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                    color = AppColors.White75
                ),
                onClick = { offset ->
                    text.getStringAnnotations("addText", offset, offset)
                        .firstOrNull()
                        ?.let {
                            mainAction()
                        }
                },
                modifier = Modifier
                    .padding(end = 30.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .padding(end = 12.dp)
        ) {
            Image(
                painter = painterResource(Res.drawable.close),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable {
                        closeAction()
                    }
            )
        }
    }
}