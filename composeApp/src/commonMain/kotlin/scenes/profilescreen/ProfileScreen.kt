package scenes.profilescreen

import AppColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.manrope_bold
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.nickname
import kotlinproject.composeapp.generated.resources.poweredBy
import kotlinproject.composeapp.generated.resources.profile
import kotlinproject.composeapp.generated.resources.secrets
import kotlinproject.composeapp.generated.resources.signOut
import kotlinproject.composeapp.generated.resources.version
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import sharedData.getAppVersion
import sharedData.getScreenWidthPx
import ui.CommonBackground
import ui.DrawLine


class ProfileScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: ProfileScreenViewModel = koinViewModel()
        val navigator = LocalNavigator.currentOrThrow
        val secrets = stringResource(Res.string.secrets)
        val secretsCount = viewModel.getSecretsCount().toString()
        val devices = stringResource(Res.string.secrets)
        val devicesCount = viewModel.getDevicesCount().toString()
        val nickname = stringResource(Res.string.nickname)
        val nicknameField = viewModel.getNickName().toString()
        CommonBackground(Res.string.profile)

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column {
                Column(
                    modifier = Modifier
                        .padding(top = 100.dp, start = 16.dp, end = 16.dp)
                ) {
                    ProfileTextCell(nickname, nicknameField)
                }
                Box(
                    modifier = Modifier
                        .height(120.dp)
                        .fillMaxWidth()
                ) {
                    DrawLine(16f, 359f, 20f, 20f, false)
                    DrawLine(getScreenWidthPx() / 2f, getScreenWidthPx() / 2f, 40f, 92f, true)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 30.dp)
                            .align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(
                            40.dp,
                            Alignment.CenterHorizontally
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ProfileTextCell(secrets, secretsCount)
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ProfileTextCell(devices, devicesCount)
                        }
                    }
                    DrawLine(16f, 359f, 112f, 112f, false)
                }

            }
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
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = AppColors.RedError,
                        contentColor = AppColors.White,
                        disabledBackgroundColor = AppColors.RedError.copy(alpha = 0.5f),
                        disabledContentColor = AppColors.White.copy(alpha = 0.5f)
                    ),
                    onClick = {
                        viewModel.completeSignIn(false)
                        navigator.popUntilRoot()
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

@Composable
fun ProfileTextCell(header: String, content: String) {
    Text(
        text = header,
        color = AppColors.White75,
        fontSize = 15.sp,
        fontFamily = FontFamily(Font(Res.font.manrope_regular))
    )
    Text(
        text = content,
        color = AppColors.White,
        fontSize = 24.sp,
        fontFamily = FontFamily(Font(Res.font.manrope_bold)),
        textAlign = TextAlign.Center
    )
}