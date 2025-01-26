package scenes.mainscreen

import AppColors
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.background_main
import kotlinproject.composeapp.generated.resources.executioner
import kotlinproject.composeapp.generated.resources.noSecrets
import kotlinproject.composeapp.generated.resources.noSecretsHeader
import kotlinproject.composeapp.generated.resources.secrets
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import sharedData.getScreenHeight
import ui.ElementsSizing.executionerSizeMultiplier
import ui.ElementsSizing.headerHeightMultiplier
import ui.ElementsSizing.headerWidthMultiplier

class SecretsScreen : Screen {
    @Composable
    override fun Content() {
        val backgroundMain = painterResource(Res.drawable.background_main)
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
                    .padding(top = 50.dp, start = 16.dp)
                    .height((getScreenHeight() * headerHeightMultiplier).dp)
                    .width((getScreenHeight() * headerWidthMultiplier).dp),
                text = stringResource(Res.string.secrets),
                color = AppColors.White,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
            )


            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 75.dp)
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(Res.drawable.executioner),
                        contentDescription = null,
                        modifier = Modifier
                            .size((getScreenHeight() * executionerSizeMultiplier).dp)
                            .align(Alignment.Center),
                        contentScale = ContentScale.Fit
                    )
                }
                Text(
                    text = stringResource(Res.string.noSecretsHeader),
                    color = AppColors.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(top = 14.dp)
                )
                Text(
                    text = stringResource(Res.string.noSecrets),
                    color = AppColors.White75,
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .padding(vertical = 7.dp)
                )
            }
        }
    }
}


