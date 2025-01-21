package scenes.mainscreen

import AppColors
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.background_main
import kotlinproject.composeapp.generated.resources.devicesList
import kotlinproject.composeapp.generated.resources.executioner
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource


class DevicesScreen : Screen {
    @Composable
    override fun Content() {
        val backgroundMain = painterResource(Res.drawable.background_main)
        val logo = painterResource(Res.drawable.executioner)
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {

            Image(
                painter = backgroundMain,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            Text(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 50.dp, start = 16.dp),
                text = stringResource(Res.string.devicesList),
                color = AppColors.White,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
            )
        }
    }
}

