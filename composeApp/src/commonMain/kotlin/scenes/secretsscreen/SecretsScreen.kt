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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import kotlinproject.composeapp.generated.resources.secretNotAdded
import kotlinproject.composeapp.generated.resources.secretsHeader
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import sharedData.AppColors
import sharedData.actualHeightFactor
import sharedData.enums.NotificationType
import ui.AddButton
import ui.NotificationStateHolder
import ui.SecretsDialogStateHolder
import ui.WarningStateHolder
import ui.dialogs.popUpSecret
import ui.notifications.InAppNotification
import ui.notifications.warningContent
import ui.screenContent.CommonBackground
import ui.screenContent.ContentCell
import ui.screenContent.SecretsContent

class SecretsScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: SecretsScreenViewModel = koinViewModel()

        val notificationVisibility by NotificationStateHolder.isNotificationVisible.collectAsState()

        val error = stringResource(Res.string.secretNotAdded)
        val success = stringResource(Res.string.secretAdded)

        CommonBackground(Res.string.secretsHeader) {
            warningContent(
                text = viewModel.getWarningText(),
                action = {},
                closeAction = { WarningStateHolder.setVisibility(false) },
                isVisible = WarningStateHolder.isWarningVisible,
                viewModel.devicesSize
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(viewModel.secretsSize) { index ->
                    ContentCell { SecretsContent(viewModel.data(), index) }
                }
            }
        }
        if (notificationVisibility) {
            InAppNotification(
                success,
                NotificationType.Blue,
                { NotificationStateHolder.setVisibility(false) }
            )
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2000)
                NotificationStateHolder.setVisibility(false)
            }
        }
        AddButton {
            SecretsDialogStateHolder.setVisibility(true)
        }

        popUpSecret()

        if (viewModel.secretsSize < 1) {
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



