package scenes.mainscreen

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.manrope_regular
import org.jetbrains.compose.resources.Font
import org.koin.compose.viewmodel.koinViewModel
import sharedData.AppColors
import sharedData.getScreenWidth
import ui.TabStateHolder
import ui.dialogs.joinrequest.JoinRequestDialog

class MainScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: MainScreenViewModel = koinViewModel()
        val tabs = listOf(SecretsTab, DevicesTab, ProfileTab)
        val selectedTabIndex by TabStateHolder.selectedTabIndex
        val tabSize = getScreenWidth() / tabs.size
        val showJoinRequestDialog by viewModel.showJoinRequestDialog.collectAsState()

        TabNavigator(tabs[selectedTabIndex]) {
            val tabNavigator = LocalTabNavigator.current
            tabNavigator.current = tabs[selectedTabIndex]
            Scaffold(
                modifier = Modifier.fillMaxSize(),
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
                                        viewModel.setTabIndex(index)
                                        tabNavigator.current = tab
                                    },
                                    icon = {
                                        tab.options.icon?.let { icon ->
                                            Icon(
                                                icon,
                                                contentDescription = tab.options.title,
                                                tint = AppColors.White75
                                            )
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
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CurrentTab()
                    
                    if (showJoinRequestDialog) {
                        JoinRequestDialog(
                            onAccept = {
                                viewModel.hideJoinRequestDialog()
                                viewModel.acceptJoinRequest()
                            },
                            onDecline = {
                                viewModel.hideJoinRequestDialog()
                                viewModel.declineJoinRequest()
                            }
                        )
                    }
                }
            }
        }
    }
}