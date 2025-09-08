package ui.scenes.secretsscreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.executioner
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.manrope_semi_bold
import kotlinproject.composeapp.generated.resources.noSecrets
import kotlinproject.composeapp.generated.resources.noSecretsHeader
import kotlinproject.composeapp.generated.resources.secretAdded
import kotlinproject.composeapp.generated.resources.secretRemoved
import kotlinproject.composeapp.generated.resources.secretsHeader
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ui.scenes.mainscreen.DevicesTab
import core.AppColors
import ui.AddButton
import ui.dialogs.addsecret.AddSecret
import ui.notifications.InAppNotification
import ui.screenContent.CommonBackground
import ui.screenContent.SecretsContent

class SecretsScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: SecretsScreenViewModel = koinViewModel()
        val secretsCount by viewModel.secretsCount.collectAsState()
        var previousCount by remember { mutableStateOf(secretsCount) }
        val secretsList by viewModel.secrets.collectAsState()
        var isDialogVisible by remember { mutableStateOf(false) }
        val isRedirected by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            previousCount = secretsCount
        }
        if (isRedirected) {
            LocalTabNavigator.current.current = DevicesTab
            viewModel.setTabIndex(1)
        }

        CommonBackground(Res.string.secretsHeader) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(secretsList) { index, secret ->
                    SecretsContent(index, secret, viewModel.screenMetricsProvider)
                }
            }
        }

        AddButton { isDialogVisible = it }

        AnimatedVisibility(
            visible = isDialogVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 1500)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 1000)
            )
        ) {
            AddSecret(
                viewModel.screenMetricsProvider,
                dialogVisibility = { isDialogVisible = it }
            )
        }

        var message = ""
        if (previousCount < secretsCount) {
            message = stringResource(Res.string.secretAdded)
        } else if (previousCount > secretsCount) {
            message = stringResource(Res.string.secretRemoved)
        }

        InAppNotification(
            viewModel.screenMetricsProvider,
            true, //TODO if failed
            message,
            { previousCount = secretsCount }
        )
        LaunchedEffect(Unit) { delay(2000); previousCount = secretsCount }

        if (secretsCount < 1) {
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
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.executioner),
                            contentDescription = null,
                            modifier = Modifier
                                .size((viewModel.screenMetricsProvider.heightFactor() * 220).dp)
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