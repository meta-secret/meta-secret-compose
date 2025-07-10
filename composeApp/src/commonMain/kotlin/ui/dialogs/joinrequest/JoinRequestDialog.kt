package ui.dialogs.joinrequest

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.accept
import kotlinproject.composeapp.generated.resources.close
import kotlinproject.composeapp.generated.resources.decline
import kotlinproject.composeapp.generated.resources.join_request_title
import kotlinproject.composeapp.generated.resources.join_requests
import kotlinproject.composeapp.generated.resources.manrope_semi_bold
import kotlinproject.composeapp.generated.resources.you_have
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import sharedData.AppColors
import sharedData.actualHeightFactor
import ui.ClassicButton

@Composable
fun JoinRequestDialog(
    requestsCount: Int,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { /* Not dismissible by clicking outside */ }
                .padding(horizontal = 16.dp)
                .background(AppColors.Black30),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .height((actualHeightFactor() * 280).dp)
                    .background(AppColors.PopUp, RoundedCornerShape(12.dp))
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
                                onDecline()
                            }
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 50.dp, bottom = 24.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.join_request_title),
                        fontFamily = FontFamily(Font(Res.font.manrope_semi_bold)),
                        fontSize = 22.sp,
                        color = AppColors.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    val text = "${stringResource(Res.string.you_have)} $requestsCount ${stringResource(Res.string.join_requests)}"
                    Text(
                        text = text,
                        fontFamily = FontFamily(Font(Res.font.manrope_semi_bold)),
                        fontSize = 14.sp,
                        color = AppColors.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            ClassicButton(
                                action = {
                                    onDecline() 
                                },
                                text = stringResource(Res.string.decline)
                            )
                        }
                        
                        Box(modifier = Modifier.weight(1f)) {
                            ClassicButton(
                                action = {
                                    onAccept() 
                                },
                                text = stringResource(Res.string.accept)
                            )
                        }
                    }
                }
            }
        }
    }
} 