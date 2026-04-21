package ui.dialogs.showsecret

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import core.AppColors
import core.ScreenMetricsProviderInterface
import core.Secret
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.close
import kotlinproject.composeapp.generated.resources.copyPhrase
import kotlinproject.composeapp.generated.resources.copySecret
import kotlinproject.composeapp.generated.resources.device
import kotlinproject.composeapp.generated.resources.devices_4
import kotlinproject.composeapp.generated.resources.devices_5
import kotlinproject.composeapp.generated.resources.devices_logo
import kotlinproject.composeapp.generated.resources.icon_copy
import kotlinproject.composeapp.generated.resources.icon_eye_off
import kotlinproject.composeapp.generated.resources.icon_lock
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.manrope_semi_bold
import kotlinproject.composeapp.generated.resources.secretEncryptedSubtitle
import kotlinproject.composeapp.generated.resources.secretEncryptedTitle
import kotlinproject.composeapp.generated.resources.show
import kotlinproject.composeapp.generated.resources.showSecret
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.platform.LocalClipboardManager
import ui.ClassicButton

@Composable
fun ShowSecret(
    secret: Secret,
    secretIdToShow: String?,
    onDismiss: () -> Unit,
    onClearSecretId: () -> Unit,
) {
    val viewModel: ShowSecretViewModel = koinViewModel()
    val screenMetricsProvider: ScreenMetricsProviderInterface = koinInject()
    val clipboardManager = LocalClipboardManager.current

    val devicesCount by viewModel.devicesCount.collectAsState()
    val revealedSecret by viewModel.revealedSecret.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(secretIdToShow) {
        if (secretIdToShow != null) {
            viewModel.handle(ShowSecretEvents.SecretReadyToShow(secretIdToShow))
            onClearSecretId()
        }
    }

    val deviceText = when {
        devicesCount == 0 || devicesCount > 4 -> stringResource(Res.string.devices_5)
        devicesCount in 2..4 -> stringResource(Res.string.devices_4)
        else -> stringResource(Res.string.device)
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Black30),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .heightIn(
                        min = (screenMetricsProvider.heightFactor() * 316).dp,
                        max = (screenMetricsProvider.heightFactor() * 560).dp
                    )
                    .background(Color(0xFF111527), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp)
                    .clickable(
                        indication = null,
                        interactionSource = MutableInteractionSource()
                    ) { },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Image(
                        painter = painterResource(Res.drawable.close),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clickable {
                                viewModel.handle(ShowSecretEvents.HideSecret)
                                onDismiss()
                            }
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .padding(top = 44.dp, bottom = 20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(Res.string.showSecret),
                        fontSize = 24.sp,
                        fontFamily = FontFamily(Font(Res.font.manrope_semi_bold)),
                        color = AppColors.White,
                        textAlign = TextAlign.Center
                    )

                    SecretNameField(
                        secretName = secret.secretName,
                        seedCount = (revealedSecret as? RevealedSecretContent.SeedPhrase)?.count,
                    )

                    AnimatedContent(
                        targetState = revealedSecret,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(200)) +
                                slideInVertically(animationSpec = tween(200)) { fullHeight -> fullHeight / 4 }) togetherWith
                                fadeOut(animationSpec = tween(200))
                        },
                        label = "show_secret_transition"
                    ) { content ->
                        when (content) {
                            null -> LockedSecretStub()
                            is RevealedSecretContent.Password -> PasswordSecretField(content.value)
                            is RevealedSecretContent.SeedPhrase -> SeedPhraseGrid(content.words)
                        }
                    }

                    DevicesRow(devicesCount = devicesCount, deviceText = deviceText)

                    if (revealedSecret == null) {
                        ClassicButton(
                            action = {
                                if (!isLoading) {
                                    viewModel.handle(ShowSecretEvents.ShowSecret(secret.secretName))
                                }
                            },
                            text = stringResource(Res.string.show)
                        )
                    } else {
                        val copyText = when (val content = revealedSecret) {
                            is RevealedSecretContent.Password -> content.value
                            is RevealedSecretContent.SeedPhrase -> content.words.joinToString(" ")
                            null -> ""
                        }
                        val buttonText = when (revealedSecret) {
                            is RevealedSecretContent.Password -> stringResource(Res.string.copySecret)
                            is RevealedSecretContent.SeedPhrase -> stringResource(Res.string.copyPhrase)
                            null -> stringResource(Res.string.copySecret)
                        }

                        CopyButton(
                            text = buttonText,
                            onClick = {
                                if (copyText.isNotBlank()) {
                                    clipboardManager.setText(AnnotatedString(copyText))
                                }
                            }
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(10f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AppColors.ActionMain)
                }
            }
        }
    }
}

@Composable
private fun SecretNameField(secretName: String, seedCount: Int?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF1C2138),
                shape = RoundedCornerShape(8.dp)
            )
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = secretName,
                fontSize = 16.sp,
                fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                color = AppColors.White,
            )

            if (seedCount == 12 || seedCount == 24) {
                Text(
                    text = "SEED · $seedCount",
                    fontSize = 11.sp,
                    fontFamily = FontFamily(Font(Res.font.manrope_semi_bold)),
                    color = Color(0xFF3A7BFF),
                    modifier = Modifier
                        .background(
                            color = Color(0x263A7BFF),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .border(1.dp, Color(0x4D3A7BFF), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun LockedSecretStub() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C2138), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Color(0x1A3A7BFF), CircleShape)
                    .border(1.dp, Color(0x333A7BFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.icon_lock),
                    contentDescription = null,
                    tint = Color(0xFF3A7BFF),
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = stringResource(Res.string.secretEncryptedTitle),
                fontSize = 14.sp,
                fontFamily = FontFamily(Font(Res.font.manrope_semi_bold)),
                color = AppColors.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(Res.string.secretEncryptedSubtitle),
                fontSize = 13.sp,
                fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                color = Color(0x8CFFFFFF),
                textAlign = TextAlign.Center
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0.70f, 0.50f, 0.60f, 0.45f).forEach { width ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(width)
                            .height(8.dp)
                            .background(Color(0x40FFFFFF), RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordSecretField(value: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C2138), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xB33A7BFF), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = value,
                fontSize = 16.sp,
                fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                color = AppColors.White,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            )

            Icon(
                painter = painterResource(Res.drawable.icon_eye_off),
                contentDescription = null,
                tint = Color(0xFF3A7BFF),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SeedPhraseGrid(words: List<String>) {
    val columns = if (words.size == 24) 3 else 2
    val rows = words.chunked(columns)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEachIndexed { rowIndex, rowWords ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowWords.forEachIndexed { columnIndex, word ->
                    val index = rowIndex * columns + columnIndex + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF1C2138), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xB33A7BFF), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$index",
                                fontSize = 11.sp,
                                fontFamily = FontFamily(Font(Res.font.manrope_semi_bold)),
                                color = Color(0x4DFFFFFF),
                                textAlign = TextAlign.End,
                                modifier = Modifier.widthIn(min = 18.dp)
                            )
                            Text(
                                text = word,
                                fontSize = 14.sp,
                                fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                                color = AppColors.White,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DevicesRow(devicesCount: Int, deviceText: String) {
    Row(
        modifier = Modifier.height(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(Res.drawable.devices_logo),
            contentDescription = null,
            tint = AppColors.White75
        )
        Text(
            text = "$devicesCount $deviceText",
            style = TextStyle(
                fontSize = 15.sp,
                fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                color = AppColors.White75
            ),
            modifier = Modifier.height(20.dp)
        )
    }
}

@Composable
private fun CopyButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .border(width = 1.dp, color = AppColors.White5, shape = shape),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.ActionMain,
            contentColor = AppColors.White,
        ),
        shape = shape,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
            hoveredElevation = 0.dp,
            focusedElevation = 0.dp,
        ),
    ) {
        Icon(
            painter = painterResource(Res.drawable.icon_copy),
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            fontSize = 16.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
