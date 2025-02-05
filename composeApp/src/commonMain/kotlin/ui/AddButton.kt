package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.addbutton
import org.jetbrains.compose.resources.painterResource


@Composable
fun Addbutton(popUpHeader: String, boxHeight: Int) {
    var showPopup by remember { mutableStateOf(false) }
    val imgSize = 80
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 113.dp, end = 45.dp),
        Alignment.BottomEnd
    ) {
        Image(
            painter = painterResource(Res.drawable.addbutton),
            contentDescription = null,
            modifier = Modifier
                .size(imgSize.dp)
                .clickable {
                    showPopup = true
                }
        )
    }
    if (showPopup) {
        popup(onDismiss = { showPopup = false }, popUpHeader, boxHeight)

        AnimatedVisibility(
            visible = showPopup,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
        }
    }
}