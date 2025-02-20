package ui.dialogs.removesecret

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.cancel
import kotlinproject.composeapp.generated.resources.close
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.remove
import kotlinproject.composeapp.generated.resources.removeSecret
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import sharedData.AppColors
import sharedData.actualHeightFactor
import storage.Secret

@Composable
fun removeSecret(
    text: AnnotatedString,
    secret: Secret,
    buttonVisibility: (Boolean) -> Unit,
    dialogVisibility: (Boolean) -> Unit,
) {
    val viewModel: RemoveSecretViewModel = koinViewModel()

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { dialogVisibility(false); buttonVisibility(false) }
                .padding(horizontal = 16.dp)
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .height((actualHeightFactor() * 280).dp)
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
                                buttonVisibility(false)
                                dialogVisibility(false)
                            }
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.removeSecret) + "?",
                        fontSize = 24.sp,
                        fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                        color = AppColors.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 30.dp)

                    )
                    Text(
                        text = text,
                        fontSize = 15.sp,
                        fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                        color = AppColors.White75,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 24.dp)
                    )
                    Button(
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = AppColors.RedError,
                            contentColor = AppColors.White,
                            disabledBackgroundColor = AppColors.ActionMain.copy(alpha = 0.5f),
                            disabledContentColor = AppColors.White.copy(alpha = 0.5f)
                        ),
                        onClick = {
                            viewModel.removeSecret(secret)
                            buttonVisibility(false)
                            dialogVisibility(false)
                        }
                    ) {
                        Text(
                            text = stringResource(Res.string.remove),
                            fontSize = 16.sp,
                            color = AppColors.White
                        )
                    }
                    Button(
                        modifier = Modifier
                            .padding(top = 14.dp)
                            .fillMaxWidth()
                            .height(48.dp)
                            .border(
                                width = 1.dp,
                                color = AppColors.White50,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clip(RoundedCornerShape(10.dp)),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color.Transparent,
                            contentColor = AppColors.White,
                            disabledBackgroundColor = AppColors.ActionMain.copy(alpha = 0.5f),
                            disabledContentColor = AppColors.White.copy(alpha = 0.5f)
                        ),
                        onClick = {
                            buttonVisibility(false)
                            dialogVisibility(false)
                        }
                    ) {
                        Text(
                            text = stringResource(Res.string.cancel),
                            fontSize = 16.sp,
                            color = AppColors.White
                        )
                    }
                }
            }
        }
    }
}