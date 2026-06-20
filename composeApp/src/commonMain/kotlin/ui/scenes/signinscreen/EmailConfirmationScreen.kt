package ui.scenes.signinscreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import core.AppColors
import core.AppString
import core.appString
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.apple
import kotlinproject.composeapp.generated.resources.background_logo
import kotlinproject.composeapp.generated.resources.background_main
import kotlinproject.composeapp.generated.resources.email_received_check
import kotlinproject.composeapp.generated.resources.google
import kotlinproject.composeapp.generated.resources.icon_lock
import kotlinproject.composeapp.generated.resources.logo
import models.appInternalModels.EmailProvider
import org.jetbrains.compose.resources.painterResource
import ui.ClassicButton
import ui.NakedButton
import ui.theme.AppTextStyles

class EmailConfirmationScreen(
    private val email: String,
    private val provider: EmailProvider
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val focusManager = LocalFocusManager.current

        val backgroundMain = painterResource(Res.drawable.background_main)
        val backgroundLogo = painterResource(Res.drawable.background_logo)
        val logo = painterResource(Res.drawable.logo)
        val lockIcon = painterResource(Res.drawable.icon_lock)
        val checkIcon = painterResource(Res.drawable.email_received_check)

        val providerIcon = when (provider) {
            EmailProvider.APPLE -> painterResource(Res.drawable.apple)
            EmailProvider.GOOGLE -> painterResource(Res.drawable.google)
            else -> null
        }
        val providerLabel = when (provider) {
            EmailProvider.APPLE -> "APPLE ID"
            EmailProvider.GOOGLE -> "GOOGLE"
            else -> ""
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { focusManager.clearFocus() }
        ) {
            Image(
                painter = backgroundMain,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 75.dp)
                        .aspectRatio(1f)
                ) {
                    Image(
                        painter = backgroundLogo,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .align(Alignment.Center),
                        contentScale = ContentScale.Fit
                    )
                    Image(
                        painter = logo,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 55.dp)
                            .aspectRatio(1f)
                            .align(Alignment.Center),
                        contentScale = ContentScale.Fit
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = appString(AppString.emailSelectionConfirmTitle),
                        style = AppTextStyles.ScreenTitle(),
                        color = AppColors.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                    Text(
                        text = appString(AppString.emailSelectionConfirmDescription),
                        style = AppTextStyles.Caption(),
                        color = AppColors.White75,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )

                    Spacer(modifier = Modifier.size(28.dp))

                    val cardShape = RoundedCornerShape(14.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AppColors.White50, cardShape)
                            .background(AppColors.White5, cardShape)
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (providerIcon != null) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(Color(0xFF0D0D0D), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = providerIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = providerLabel,
                                    style = AppTextStyles.Tiny(),
                                    color = AppColors.White75,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = email,
                                    style = AppTextStyles.BodyStrong(),
                                    color = AppColors.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Image(
                                painter = checkIcon,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.size(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 6.dp)
                    ) {
                        Image(
                            painter = lockIcon,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(AppColors.White30),
                            modifier = Modifier.padding(top = 3.dp)
                        )
                        Text(
                            text = appString(AppString.emailSelectionManualHintLine2),
                            style = AppTextStyles.Caption(),
                            color = AppColors.White30,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ClassicButton(
                            action = { },
                            text = appString(AppString.emailSelectionContinue),
                            modifier = Modifier.fillMaxWidth()
                        )

                        NakedButton(
                            title = appString(AppString.emailSelectionChange),
                            onClick = {
                                focusManager.clearFocus()
                                navigator?.pop()
                            }
                        )
                    }
                }
            }
        }
    }
}
