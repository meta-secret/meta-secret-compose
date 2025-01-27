package scenes.mainscreen

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import cafe.adriel.voyager.transitions.SlideTransition
import kotlinproject.composeapp.generated.resources.*
import org.jetbrains.compose.resources.*
import scenes.devicesscreen.DevicesScreen
import scenes.profilescreen.ProfileScreen
import scenes.secretsscreen.SecretsScreen


object SecretsTab : Tab {
    @Composable
    override fun Content() {
        Navigator(SecretsScreen()){ navigator ->
           SlideTransition(navigator)
       }
    }

    override val options: TabOptions
        @Composable
        get() {
            val icon = painterResource(Res.drawable.secrets_logo)
            val title = stringResource(Res.string.secrets)
            val index: UShort = 0U


            return TabOptions(
                index, title, icon
            )
        }
}

object DevicesTab : Tab{
    @Composable
    override fun Content() {
        Navigator(DevicesScreen()){ navigator ->
            SlideTransition(navigator)
        }
    }

    override val options: TabOptions
        @Composable
        get() {
            val icon = painterResource(Res.drawable.devices_logo)
            val title = stringResource(Res.string.devicesList)
            val index: UShort = 1U

            return TabOptions(
                index, title, icon
            )
        }
}

object ProfileTab : Tab{
    @Composable
    override fun Content() {
        Navigator(ProfileScreen()){ navigator ->
            SlideTransition(navigator)
        }
    }

    override val options: TabOptions
        @Composable
        get() {
            val icon = painterResource(Res.drawable.profile_logo)
            val title = stringResource(Res.string.profile)
            val index: UShort = 2U

            return TabOptions(
                index, title, icon
            )
        }
}

