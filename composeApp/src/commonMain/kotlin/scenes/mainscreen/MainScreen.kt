package scenes.mainscreen

import AppColors
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator


class MainScreen : Screen {
    @Composable
    override fun Content() {
       // val viewModel: MainScreenViewModel = koinViewModel()
        TabNavigator(SecretsTab) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    BottomNavigation(
                        modifier = Modifier
                            .height(90.dp),
                        backgroundColor = AppColors.TabBar,
                        contentColor = AppColors.White75,
                    ) {
                        TabItem(SecretsTab)
                        TabItem(DevicesTab)
                        TabItem(ProfileTab)
                    }
                }
            ) {
                    CurrentTab()
            }
        }
    }
}
@Composable
private fun RowScope.TabItem(tab: Tab) {
    val tabNavigator = LocalTabNavigator.current
    val isSelected = tabNavigator.current == tab

        BottomNavigationItem(
            selected = isSelected,
            onClick = {
                tabNavigator.current = tab
            },
            icon = {
                tab.options.icon?.let { icon ->
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp)
                    ) {
                        Icon(
                            icon,
                            contentDescription = tab.options.title,
                        )
                    }
                }
            },
            label = {
                Text(
                    text = tab.options.title,
                    color = AppColors.White75
                )
            }
        )
    }