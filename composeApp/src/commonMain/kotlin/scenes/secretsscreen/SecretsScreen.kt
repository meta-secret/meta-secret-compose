package scenes.secretsscreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.executioner
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.manrope_semi_bold
import kotlinproject.composeapp.generated.resources.noSecrets
import kotlinproject.composeapp.generated.resources.noSecretsHeader
import kotlinproject.composeapp.generated.resources.secretAdded
import kotlinproject.composeapp.generated.resources.secretsHeader
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import sharedData.AppColors
import sharedData.actualHeightFactor
import ui.AddButton
import ui.dialogs.popUpSecret
import ui.notifications.InAppNotification
import ui.notifications.warningContent
import ui.screenContent.CommonBackground
import ui.screenContent.SecretsContent

class SecretsScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: SecretsScreenViewModel = koinViewModel()
        var isDialogVisible by remember { mutableStateOf(false) }
        var isNotificationVisibility by remember { mutableStateOf(false) }

        CommonBackground(Res.string.secretsHeader) {
            warningContent(
                text = viewModel.getWarningText(),
                action = {},
                closeAction = { viewModel.changeWarningVisibilityTo(false) },
                viewModel.devicesCount
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(viewModel.secretsCount) { index ->
                    SecretsContent(index)
                }
            }
        }
        if (isNotificationVisibility) {
            InAppNotification(
                stringResource(Res.string.secretAdded),
                AppColors.ActionMain,
                { isNotificationVisibility = false }
            )
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2000)
                isNotificationVisibility = false
            }
        }
        AddButton { isDialogVisible = it }

        if (isDialogVisible) {
            popUpSecret(
                dialogVisibility = { isDialogVisible = it },
                notificationVisibility = { isNotificationVisibility = it }
            )
        }

        if (viewModel.secretsCount < 1) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
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
                                .size((actualHeightFactor() * 220).dp)
                                .align(Alignment.Center),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Text(
                        text = stringResource(Res.string.noSecretsHeader),
                        color = AppColors.White,
                        fontSize = 22.sp,
                        fontFamily = FontFamily(Font(Res.font.manrope_semi_bold)),
                        modifier = Modifier
                            .padding(top = 14.dp)
                    )
                    Text(
                        text = stringResource(Res.string.noSecrets),
                        color = AppColors.White75,
                        textAlign = TextAlign.Center,
                        fontSize = 15.sp,
                        fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                        modifier = Modifier
                            .padding(vertical = 7.dp)
                    )
                }
            }
        }
    }
}



