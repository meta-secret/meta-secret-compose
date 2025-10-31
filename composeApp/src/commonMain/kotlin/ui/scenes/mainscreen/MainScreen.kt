package ui.scenes.mainscreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.addText
import kotlinproject.composeapp.generated.resources.goto_devices_tab
import kotlinproject.composeapp.generated.resources.lackOfDevices_end
import kotlinproject.composeapp.generated.resources.lackOfDevices_start
import kotlinproject.composeapp.generated.resources.manrope_regular
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import core.AppColors
import kotlinproject.composeapp.generated.resources.wanna_recover
import ui.TabStateHolder
import ui.dialogs.YesNoDialog
import ui.notifications.WarningContent

class MainScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: MainScreenViewModel = koinViewModel()
        val tabs = listOf(SecretsTab, DevicesTab, ProfileTab)
        val selectedTabIndex by TabStateHolder.selectedTabIndex
        val tabSize = viewModel.screenMetricsProvider.screenWidth() / tabs.size
        val joinRequestsCount by viewModel.joinRequestsCount.collectAsState()
        val hasJoinRequestsBadge by viewModel.hasJoinRequestsBadge.collectAsState()
        val devicesCount by viewModel.devicesCount.collectAsState()
        val isWarningShown by viewModel.isWarningShown.collectAsState()
        val recoverDialog by viewModel.recoverDialog.collectAsState()

        TabNavigator(tabs[selectedTabIndex]) {
            val tabNavigator = LocalTabNavigator.current
            tabNavigator.current = tabs[selectedTabIndex]
            Scaffold(
                modifier = Modifier
                    .fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .background(AppColors.TabBar)
                    ) {
                        val animatedOffset by animateDpAsState(
                            targetValue = tabSize.dp * selectedTabIndex
                        )
                        Box(
                            modifier = Modifier
                                .offset(x = animatedOffset)
                                .width(tabSize.dp)
                                .height(4.dp)
                                .background(AppColors.ActionMain)
                        )
                        BottomNavigation(
                            modifier = Modifier
                                .height(68.dp),
                        ) {
                            tabs.forEachIndexed { index, tab ->
                                BottomNavigationItem(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(AppColors.TabBar),
                                    selected = selectedTabIndex == index,
                                    onClick = {
                                        viewModel.handle(MainViewEvents.SetTabIndex(index))
                                        tabNavigator.current = tab
                                    },
                                    icon = {
                                        if (index == 1 && hasJoinRequestsBadge && selectedTabIndex != index) {
                                            DevicesTab.TabWithBadge(hasJoinRequests = true)
                                        } else {
                                            tab.options.icon?.let { icon ->
                                                Icon(
                                                    icon,
                                                    contentDescription = tab.options.title,
                                                    tint = AppColors.White75
                                                )
                                            }
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = tab.options.title,
                                            color = AppColors.White75,
                                            fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()) {
                    CurrentTab()

                    if (devicesCount < 3 || (joinRequestsCount != null && selectedTabIndex != 1)) {
                        viewModel.handle(MainViewEvents.ShowWarning(true))
                    } else {
                        viewModel.handle(MainViewEvents.ShowWarning(false))
                    }

                    val computedWarningText = if (selectedTabIndex == 1 && joinRequestsCount != null && joinRequestsCount!! > 0) {
                        null
                    } else {
                        getWarningText(joinRequestsCount, devicesCount)
                    }

                    computedWarningText?.let { warningText ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                                .padding(horizontal = 16.dp)
                                .padding(top = 0.dp)
                                .padding(bottom = 14.dp)
                        ) {
                            WarningContent(
                                text = warningText,
                                mainAction = {
                                    viewModel.handle(MainViewEvents.SetTabIndex(1))
                                },
                                closeAction = {
                                    viewModel.handle(MainViewEvents.ShowWarning(false))
                                },
                                isVisible = isWarningShown
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = recoverDialog != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 1500)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 1000)
            )
        ) {
            YesNoDialog(
                stringResource(Res.string.wanna_recover),
                viewModel.screenMetricsProvider,
                onDismiss = {
                    if (it != null) {
                        viewModel.handle(MainViewEvents.RecoverDecision(it))
                    } else {
                        viewModel.handle(MainViewEvents.DismissRecoverDialog)
                    }
                }
            )
        }
    }

    @Composable
    fun getWarningText(joinRequestsCount: Int? = null, devicesCount: Int? = null): AnnotatedString? {
        if (joinRequestsCount != null && joinRequestsCount > 0) {
            return buildAnnotatedString {
                append(stringResource(Res.string.goto_devices_tab))
            }
        } else {
            return if (devicesCount != null) {
                buildAnnotatedString {
                    append(stringResource(Res.string.lackOfDevices_start))
                    append((3 - devicesCount).toString())
                    append(stringResource(Res.string.lackOfDevices_end))
                    pushStringAnnotation(tag = "addText", annotation = "")
                    withStyle(style = SpanStyle(color = AppColors.ActionLink)) {
                        append(stringResource(Res.string.addText))
                    }
                    pop()
                }
            } else {
                null
            }
        }
    }
}