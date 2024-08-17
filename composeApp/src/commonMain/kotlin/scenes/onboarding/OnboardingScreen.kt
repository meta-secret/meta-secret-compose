package scenes.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.next
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import scenes.signinscreein.SignInScreen

class OnboardingScreen : Screen {
    @Composable
    @OptIn(ExperimentalFoundationApi::class)
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val pages = listOf(
            OnBoardingPage.First,
            OnBoardingPage.Second,
            OnBoardingPage.Third
        )

        val pagerState = rememberPagerState(pageCount = {
            pages.count()
        })

        val coroutineScope = rememberCoroutineScope()

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            HorizontalPager(state = pagerState) { position ->
                PagerScreen(onBoardingPage = pages[position])
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .height(40.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(pagerState.pageCount) { iteration ->
                        val color =
                            if (pagerState.currentPage == iteration) Color.LightGray else Color.DarkGray
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(color)
                                .size(8.dp)
                        )
                    }
                }

                Text(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp),
                    fontSize = 15.sp,
                    color = Color(0xFF90BDFF),
                    text = "${pagerState.currentPage + 1} / ${pages.count()}"
                )

                Button(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp),
                    onClick = {
                        navigator.push(SignInScreen())
                    },
                    colors = ButtonDefaults.buttonColors(
                        Color.Transparent
                    ),
                    elevation = null
                ) {
                    Text(
                        text = "Skip",
                        fontSize = 15.sp,
                        color = Color(0xFF90BDFF),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .fillMaxWidth()
            ) {
                Button(
                    modifier = Modifier
                        .requiredHeight(48.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp),
                    colors = ButtonDefaults.buttonColors(
                        Color(0xFF0368FF),
                        contentColor = Color.White
                    ),
                    onClick = {
                        coroutineScope.launch {
                            if (pagerState.currentPage + 1 >= pages.count()) {
                                //viewModel.saveOnBoardingState(completed = true, context)
                                navigator.push(SignInScreen())
                            } else pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                ) {
                    Text(text = stringResource(Res.string.next), fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun PagerScreen(onBoardingPage: OnBoardingPage) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        Image(
            painter = painterResource(onBoardingPage.image),
            contentDescription = "background_image",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .offset(y = (-100).dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = stringResource(onBoardingPage.title),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
            )
            Text(
                text = stringResource(onBoardingPage.subTitle),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
            )
            Text(
                text = stringResource(onBoardingPage.description),
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
            )
        }
    }
}