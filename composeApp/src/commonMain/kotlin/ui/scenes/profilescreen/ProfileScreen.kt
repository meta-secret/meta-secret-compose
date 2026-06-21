package ui.scenes.profilescreen

import core.AppString

import core.appString

import ui.theme.AppTextStyles

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ui.scenes.signinscreen.SignInScreen
import org.koin.compose.viewmodel.koinViewModel
import core.AppColors
import ui.screenContent.CommonBackground

class ProfileScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: ProfileScreenViewModel = koinViewModel()
        val navigator = LocalNavigator.currentOrThrow
        val navigationEvent by viewModel.navigationEvent.collectAsState()
        var isSettingsMenuExpanded by remember { mutableStateOf(false) }

        LaunchedEffect(navigationEvent) {
            when (navigationEvent) {
                ProfileNavigationEvent.NavigateToSignIn -> {
                    navigator.rootNavigator().replaceAll(SignInScreen())
                    viewModel.consumeNavigationEvent()
                }

                ProfileNavigationEvent.Idle -> Unit
            }
        }

        CommonBackground(
            text = AppString.profile,
            headerTrailingContent = {
                ProfileSettingsMenu(
                    expanded = isSettingsMenuExpanded,
                    onExpandedChange = { isSettingsMenuExpanded = it },
                    onResetAllDataClick = {
                        isSettingsMenuExpanded = false
                        viewModel.handle(ProfileEvents.ResetAllData)
                    }
                )
            }
        ) {
            ProfileBody(viewModel = viewModel)
        }
    }
}

@Composable
fun ProfileBody(viewModel: ProfileScreenViewModel) {
    val secretsCount by viewModel.secretsCount.collectAsState()
    val devicesCount by viewModel.devicesCount.collectAsState()
    val secrets = appString(AppString.secretsHeader)
    val devices = appString(AppString.devicesList)
    val nickname = appString(AppString.nickname)
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
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextCell(
                    (appString(AppString.version) + " " + viewModel.deviceInfoProvider.getAppVersion()),
                    16
                )
                TextCell(appString(AppString.poweredBy), 0)
            }
        }
    }
}

private fun Navigator.rootNavigator(): Navigator {
    return generateSequence(this) { it.parent }.last()
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
            style = AppTextStyles.Paragraph().copy(color = AppColors.White75)
        )
        Text(
            modifier = Modifier
                .height(32.dp),
            text = content,
            style = AppTextStyles.DialogTitle().copy(color = AppColors.White),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TextCell(content: String, padding: Int) {
    Text(
        text = content,
        style = AppTextStyles.Paragraph().copy(color = AppColors.White75),
        modifier = Modifier
            .padding(top = padding.dp)
            .height(22.dp)
    )
}
