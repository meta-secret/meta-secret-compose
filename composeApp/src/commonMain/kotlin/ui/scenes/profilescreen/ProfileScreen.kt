package ui.scenes.profilescreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import kotlinproject.composeapp.generated.resources.devicesList
import kotlinproject.composeapp.generated.resources.manrope_bold
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.nickname
import kotlinproject.composeapp.generated.resources.poweredBy
import kotlinproject.composeapp.generated.resources.profile
import kotlinproject.composeapp.generated.resources.secretsHeader
import kotlinproject.composeapp.generated.resources.version
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import core.AppColors
import ui.screenContent.CommonBackground
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize

class ProfileScreen : Screen {
    @Composable
    override fun Content() {
        CommonBackground(Res.string.profile) {
            ProfileBody()
        }
    }
}

@Composable
fun ProfileBody() {
    val navigator = LocalNavigator.currentOrThrow
    val viewModel: ProfileScreenViewModel = koinViewModel()
    val secretsCount by viewModel.secretsCount.collectAsState()
    val devicesCount by viewModel.devicesCount.collectAsState()
    val secrets = stringResource(Res.string.secretsHeader)
    val devices = stringResource(Res.string.devicesList)
    val nickname = stringResource(Res.string.nickname)
    val vaultName by viewModel.vaultName.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.handle(ProfileEvents.LoadProfileData)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 15.dp)
                .fillMaxSize()
        ) {
            ProfileTextCell(nickname, vaultName ?: "n/a", Alignment.Start)
            DrawBoxLine(20)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp)
                    .height(92.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally)
            ) {
                ProfileTextCell(secrets, secretsCount.toString(), Alignment.CenterHorizontally)
                Box(
                    modifier = Modifier
                        .padding(vertical = 20.dp)
                        .fillMaxHeight()
                        .background(color = AppColors.White10)
                        .width(1.dp)
                )
                ProfileTextCell(devices, devicesCount.toString(), Alignment.CenterHorizontally)
            }
            DrawBoxLine(0)
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp),
        ) {
//            ClassicButton({
//                    viewModel.completeSignIn(false)
//                    navigator.popUntilRoot()
//                },
//                stringResource(Res.string.signOut),
//                color = AppColors.RedError
//            )
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextCell(
                    (stringResource(Res.string.version) + " " + viewModel.deviceInfoProvider.getAppVersion()),
                    16
                )
                TextCell(stringResource(Res.string.poweredBy), 0)
            }
        }
    }
}

@Composable
fun DrawBoxLine(padding: Int) {
    Box(
        modifier = Modifier
            .padding(top = padding.dp)
            .fillMaxWidth()
            .background(color = AppColors.White10)
            .height(1.dp)
    )
}

@Composable
fun ProfileTextCell(header: String, content: String, alignment: Alignment.Horizontal) {

    Column(horizontalAlignment = alignment) {
        Text(
            modifier = Modifier
                .height(22.dp),
            text = header,
            color = AppColors.White75,
            fontSize = 15.sp,
            fontFamily = FontFamily(Font(Res.font.manrope_regular))
        )
        Text(
            modifier = Modifier
                .height(32.dp),
            text = content,
            color = AppColors.White,
            fontSize = 24.sp,
            fontFamily = FontFamily(Font(Res.font.manrope_bold)),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TextCell(content: String, padding: Int) {
    Text(
        text = content,
        fontSize = 15.sp,
        fontFamily = FontFamily(Font(Res.font.manrope_regular)),
        color = AppColors.White75,
        modifier = Modifier
            .padding(top = padding.dp)
            .height(22.dp)
    )
}