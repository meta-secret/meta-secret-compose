package ui.dialogs.showsecret

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.close
import kotlinproject.composeapp.generated.resources.device
import kotlinproject.composeapp.generated.resources.devices_4
import kotlinproject.composeapp.generated.resources.devices_5
import kotlinproject.composeapp.generated.resources.devices_logo
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.show
import kotlinproject.composeapp.generated.resources.showSecret
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import sharedData.AppColors
import sharedData.actualHeightFactor
import storage.Secret

@Composable
fun showSecret(
    secret: Secret,
    dialogVisibility: (Boolean) -> Unit,
) {
    val viewModel: ShowSecretViewModel = koinViewModel()
    val devicesCount by viewModel.devicesCount.collectAsState()
    var isPasswordVisible by remember { mutableStateOf(false) }

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
                .clickable { dialogVisibility(false) }
                .padding(horizontal = 16.dp)
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .heightIn(
                        min = (actualHeightFactor() * 316).dp,
                        max = (actualHeightFactor() * 518).dp
                    )
                    .background(AppColors.PopUp, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp)
                    .clickable(onClick = {}, enabled = false),
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
                                dialogVisibility(false)
                            }
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .padding(vertical = 30.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.showSecret),
                        fontSize = 24.sp,
                        fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                        color = AppColors.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                    )
                    viewModel.textRow(secret.secretName)
                    viewModel.textRow(
                        when (isPasswordVisible) {
                            true -> secret.password
                            false -> "*".repeat(secret.password.length)
                        }
                    )

                    Row(
                        modifier = Modifier.height(24.dp)
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
                            )
                        )
                    }
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = AppColors.ActionMain,
                            contentColor = AppColors.White,
                            disabledBackgroundColor = AppColors.ActionMain.copy(alpha = 0.5f),
                            disabledContentColor = AppColors.White.copy(alpha = 0.5f)
                        ),
                        onClick = {
                            isPasswordVisible = when (isPasswordVisible) {
                                true -> false
                                false -> true
                            }
                        }
                    ) {
                        Text(
                            text = stringResource(Res.string.show),
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }
    }
}