package scenes.profilescreen

import AppColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.poweredBy
import kotlinproject.composeapp.generated.resources.profile
import kotlinproject.composeapp.generated.resources.signOut
import kotlinproject.composeapp.generated.resources.version
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import scenes.signinscreen.SignInScreenViewModel
import sharedData.getAppVersion
import ui.CommonBackground

class ProfileScreen : Screen {

    @Composable
    override fun Content() {
        val signInScreenViewModel: SignInScreenViewModel = koinViewModel()
        CommonBackground(Res.string.profile)

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 16.dp),
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
                    fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                    color = AppColors.White75,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally),
                            textAlign = TextAlign.Center
                )
            }
        }
    }
}