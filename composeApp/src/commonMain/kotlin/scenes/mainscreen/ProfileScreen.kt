package scenes.mainscreen

import AppColors
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
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
import kotlinproject.composeapp.generated.resources.poweredBy
import kotlinproject.composeapp.generated.resources.profile
import kotlinproject.composeapp.generated.resources.signOut
import kotlinproject.composeapp.generated.resources.version
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import scenes.signinscreen.SignInScreenViewModel
import sharedData.getAppVersion
import sharedData.getScreenHeight
import sharedData.getScreenWidth
import ui.ElementsSizing.headerHeightMultiplier
import ui.ElementsSizing.headerWidthMultiplier
import ui.ElementsSizing.signOutHeightMultiplier
import ui.ElementsSizing.signOutWidthMultiplier

class ProfileScreen : Screen {

    @Composable

    override fun Content() {
        val signInScreenViewModel: SignInScreenViewModel = koinViewModel()
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {

            Image(
                painter = painterResource(Res.drawable.background_main),
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
                text = stringResource(Res.string.profile),
                color = AppColors.White,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-100).dp)
            ) {
                Button(
                    modifier = Modifier
                        .height((getScreenHeight() * signOutHeightMultiplier).dp)
                        .width((getScreenWidth() * signOutWidthMultiplier).dp)
                        .padding(bottom = 20.dp)
                        .align(Alignment.CenterHorizontally),
                            colors = ButtonDefaults . buttonColors (
                            backgroundColor = AppColors.RedError,
                    contentColor = AppColors.White,
                    disabledBackgroundColor = AppColors.RedError.copy(alpha = 0.5f),
                    disabledContentColor = AppColors.White.copy(alpha = 0.5f)
                ),
                onClick = {
                    signInScreenViewModel.completeSignIn(false)
                }
                ) {
                Text(text = stringResource(Res.string.signOut), fontSize = 16.sp)
                }
                Text(
                    text = stringResource(Res.string.version)
                            + " " + getAppVersion()
                            + "\n" + stringResource(Res.string.poweredBy),
                    fontSize = 15.sp,
                    color = AppColors.White75,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally),
                            textAlign = TextAlign.Center
                )
            }
        }
    }
}