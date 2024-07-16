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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
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
        //val viewModel = SignInScreenViewModel()
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
            Image(
                painter = painterResource(Res.drawable.background_main),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 77.dp)
            ) {

                Image(
                    painter = painterResource(Res.drawable.background_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(200.dp)
                        .align(Alignment.Center)
                        .offset(y = (-70).dp)
                )

                Image(
                    painter = painterResource(Res.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(70.dp)
                        .align(Alignment.Center)
                        .offset(y = (-70).dp)
                )
                Button(
                    modifier = Modifier
                        .size(343.dp, 48.dp)
                        .offset(y = 70.dp)
                        .align(Alignment.BottomEnd),
                    colors = ButtonDefaults.buttonColors(
                        Color.Transparent,
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, color = Color.White.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp),
                    elevation = null,
                    onClick = {}
                )
                {
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
                        .size(width = 343.dp, height = 52.dp)
                        .offset(y = 135.dp)
                        .align(Alignment.BottomEnd)
                        .border(
                            width = 2.dp,
                            color = if (isFocused) Color(0xFF3C8AFF) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            isFocused = focusState.isFocused
                        },
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = Color.White.copy(alpha = 0.05f),
                        textColor = Color.White
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    textStyle = TextStyle(fontSize = 16.sp)
                )
                Button(
                    modifier = Modifier
                        .size(343.dp, 48.dp)
                        .offset(y = 220.dp)
                        .align(Alignment.BottomEnd),
                    colors = ButtonDefaults.buttonColors(
                        Color(0xFF0368FF),
                        contentColor = Color.White
                    ),
                    onClick = {
//                    coroutineScope.launch {
//                        if (pagerState.currentPage + 1 >= pages.count()) {
//                            //viewModel.saveOnBoardingState(completed = true, context)
//                            navigator.push(SignInScreen())
//                        } else pagerState.animateScrollToPage(pagerState.currentPage + 1)
//                    }
                    }
                )
                {
                    Text(text = stringResource(Res.string.forward), fontSize = 16.sp)
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .offset(y = 160.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = stringResource(Res.string.start),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
            )
            Text(
                text = stringResource(Res.string.advice),
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
            )
        }
    }
}

