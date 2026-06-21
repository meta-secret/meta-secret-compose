package ui.scenes.mainscreen

import core.AppString

import core.appString

import core.AppImage
import core.ImageProviderInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import org.koin.compose.koinInject
import ui.scenes.devicesscreen.DevicesScreen
import ui.scenes.profilescreen.ProfileScreen
import ui.scenes.secretsscreen.SecretsScreen

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
            val imageProvider: ImageProviderInterface = koinInject()
            val icon = imageProvider.getPainter(AppImage.SecretsLogo)
            val title = appString(AppString.secretsHeader)
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
            val imageProvider: ImageProviderInterface = koinInject()
            val icon = imageProvider.getPainter(AppImage.DevicesLogo)
            val title = appString(AppString.devicesList)
            val index: UShort = 1U

            return TabOptions(
                index, title, icon
            )
        }
        
    @Composable
    fun TabWithBadge(hasJoinRequests: Boolean) {
        val imageProvider: ImageProviderInterface = koinInject()
        Box {
            val icon = imageProvider.getPainter(AppImage.DevicesLogo)
            Icon(
                painter = icon,
                contentDescription = appString(AppString.devicesList),
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
                        painter = imageProvider.getPainter(AppImage.Warning),
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
            val imageProvider: ImageProviderInterface = koinInject()
            val icon = imageProvider.getPainter(AppImage.ProfileLogo)
            val title = appString(AppString.profile)
            val index: UShort = 2U

            return TabOptions(
                index, title, icon
            )
        }
}
