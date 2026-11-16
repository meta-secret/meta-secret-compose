package ui.dialogs.adddevice

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import core.AppColors
import core.AppImage
import core.AppString
import core.ImageProviderInterface
import core.ScreenMetricsProviderInterface
import core.appString
import org.koin.compose.koinInject
import qrgenerator.qrkitpainter.QrPainter
import ui.theme.AppTextStyles

@Composable
fun AddingDevice(
    screenMetricsProvider: ScreenMetricsProviderInterface,
    mainDialogVisibility: (Boolean) -> Unit,
    vaultName: String,
) {
    if (vaultName.isBlank()) return
    val painter = remember(vaultName) { runCatching { QrPainter(vaultName) }.getOrNull() }
    val imageProvider: ImageProviderInterface = koinInject()
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("add-device-dialog")
                .background(AppColors.Black30)
                .clickable { mainDialogVisibility(false) },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .height((screenMetricsProvider.heightFactor() * 560).dp)
                    .fillMaxWidth()
                    .background(AppColors.PopUp, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp)
                    .clickable(onClick = {}, enabled = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .zIndex(1f)
                ) {
                    Image(
                        painter = imageProvider.getPainter(AppImage.Close),
                        contentDescription = appString(AppString.cancel),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clickable { mainDialogVisibility(false) }
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                ) {
                    Text(
                        text = appString(AppString.addDevice),
                        style = AppTextStyles.SectionTitle(),
                        color = AppColors.White,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(top = 30.dp, bottom = 24.dp)
                    )
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((screenMetricsProvider.heightFactor() * 180).dp)
                                .background(AppColors.White5, RoundedCornerShape(12.dp))
                        )
                        Text(
                            text = appString(AppString.downloadMetasecret),
                            style = AppTextStyles.Paragraph().copy(color = AppColors.White75),
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 24.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppColors.White5)
                            .height(1.dp)
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = appString(AppString.orUseQR),
                            style = AppTextStyles.Paragraph().copy(color = AppColors.White75),
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 10.dp)
                        )
                        Text(
                            text = vaultName,
                            style = AppTextStyles.Paragraph(),
                            color = AppColors.White75,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().testTag("vault-qr-name"),
                        )
                        if (painter != null) {
                            Box(Modifier.background(AppColors.White).padding(32.dp)) {
                                Image(
                                    painter = painter,
                                    contentDescription = appString(AppString.orbitQr),
                                    modifier = Modifier.size(164.dp).testTag("vault-name-qr"),
                                )
                            }
                        } else {
                            Text(
                                text = appString(AppString.errorInternal),
                                style = AppTextStyles.Paragraph(),
                                color = AppColors.RedError,
                            )
                        }
                    }
                }
            }
        }
    }
}
