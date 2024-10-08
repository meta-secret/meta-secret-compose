package scenes.signinscreein

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.advice
import kotlinproject.composeapp.generated.resources.background_logo
import kotlinproject.composeapp.generated.resources.background_main
import kotlinproject.composeapp.generated.resources.forward
import kotlinproject.composeapp.generated.resources.logo
import kotlinproject.composeapp.generated.resources.placeholder
import kotlinproject.composeapp.generated.resources.scan
import kotlinproject.composeapp.generated.resources.start
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

class SignInScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        var text by remember { mutableStateOf("") }
        var isFocused by remember { mutableStateOf(false) }
        val focusRequester = FocusRequester()
        val focusManager = LocalFocusManager.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    focusManager.clearFocus()
                }
        ) {
            Image( // background
                painter = painterResource(Res.drawable.background_main),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
            Column( //page content
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .align(Alignment.TopCenter),
                verticalArrangement = Arrangement.SpaceBetween

            ) {
                Box(        //logo
                    modifier = Modifier
                       .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                    ) {
                    Image(
                        painter = painterResource(Res.drawable.background_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(200.dp)
                    )
                    Image(
                        painter = painterResource(Res.drawable.logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(70.dp)
                    )
                }
                Column(     //text
                    modifier = Modifier
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.start),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                    )
                    Text(
                        text = stringResource(Res.string.advice),
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .padding(vertical = 5.dp)
                    )
                }
                Column(     //scan + input + forward
                    verticalArrangement = Arrangement.spacedBy(30.dp)
                ) {
                    Column(     //scan + input
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                Color.Transparent,
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.dp),
                            elevation = null,
                            onClick = {
                            }
                        ) {
                            Text(text = stringResource(Res.string.scan), fontSize = 16.sp)
                        }

                        TextField(
                            value = text,
                            onValueChange = { newText -> text = newText },
                            shape = RoundedCornerShape(8.dp),
                            placeholder = {
                                Text(
                                    color = Color.White.copy(alpha = 0.5f),
                                    text = stringResource(Res.string.placeholder)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .border(
                                    width = 2.dp,
                                    color = if (isFocused) Color(0xFF3C8AFF) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .focusRequester(focusRequester)
                                .onFocusChanged { focusState ->
                                    isFocused = focusState.isFocused
                                },
                            keyboardOptions = KeyboardOptions.Default.copy(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done
                            ),
                            textStyle = TextStyle(fontSize = 16.sp, color = Color.White)
                        )
                    }
                    Button(     //forward
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            Color(0xFF0368FF),
                            contentColor = Color.White
                        ),
                        onClick = {
                        }
                    ) {
                        Text(text = stringResource(Res.string.forward), fontSize = 16.sp)
                    }
                }
            }
        }
    }
}