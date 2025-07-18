package scenes.mainscreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import cafe.adriel.voyager.transitions.SlideTransition
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.devicesList
import kotlinproject.composeapp.generated.resources.devices_logo
import kotlinproject.composeapp.generated.resources.profile
import kotlinproject.composeapp.generated.resources.profile_logo
import kotlinproject.composeapp.generated.resources.secretsHeader
import kotlinproject.composeapp.generated.resources.secrets_logo
import kotlinproject.composeapp.generated.resources.warning
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
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
            val title = stringResource(Res.string.secretsHeader)
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
        
    @Composable
    fun tabWithBadge(hasJoinRequests: Boolean) {
        Box {
            val icon = painterResource(Res.drawable.devices_logo)
            Icon(
                painter = icon,
                contentDescription = stringResource(Res.string.devicesList),
                tint = Color.White.copy(alpha = 0.75f)
            )
            
            if (hasJoinRequests) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .offset(x = 8.dp, y = (-8).dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                        .align(Alignment.TopEnd)
                ) {
                    Image(
                        painter = painterResource(Res.drawable.warning),
                        contentDescription = null,
                        modifier = Modifier.size(8.dp).align(Alignment.Center)
                    )
                }
            }
        }
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

