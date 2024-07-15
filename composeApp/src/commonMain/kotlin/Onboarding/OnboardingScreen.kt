package Onboarding

import Scenes.splash.SplashScreen
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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


class OnboardingScreen : Screen {
    @Composable
    @OptIn(ExperimentalFoundationApi::class)
    override fun Content () {
        //val viewModel = OnboardingViewModel()
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
        HorizontalPager(state = pagerState) { position ->
            PagerScreen(onBoardingPage = pages[position])
        }
        Row(
            modifier = Modifier
                .padding(top = 24.dp)
                .height(40.dp) // Adjusted height to fit contents
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier
                    .width(100.dp),
                fontSize = 15.sp,
                color = Color(0xFF90BDFF),
                text = "${pagerState.currentPage + 1} / ${pages.count()}", // Display current page and total pages
                textAlign = TextAlign.Start
            )
//            HorizontalPagerIndicator(
//                pagerState = pagerState,
//                modifier = Modifier
//                    .padding(horizontal = 8.dp) // Space around the indicator
//                    .height(8.dp)
//                    .width(120.dp), // Adjust width as needed
//                activeColor = Color.Blue,
//                inactiveColor = Color.Gray
//            )
            Button(
                modifier = Modifier
                    .width(88.dp),
                onClick = {
                    //navigator.push(SignInScreen())
                },
                colors = ButtonDefaults.buttonColors(
                    Color.Transparent,
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
                .fillMaxWidth()
                .offset(y = 750.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                modifier = Modifier
                .size(343.dp, 48.dp),
                colors = ButtonDefaults.buttonColors(
                    Color(0xFF0368FF),
                            contentColor = Color.White
                ),
                onClick = {
                    coroutineScope.launch {
                        if (pagerState.currentPage + 1 >= pages.count()) {
//                       viewModel.saveOnBoardingState(completed = true, context)
//                       navController.popBackStack()
                            navigator.push(SplashScreen())
                        } else {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                }
            )
            {
                Text(text = stringResource(Res.string.next), fontSize = 20.sp)
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
                contentScale = ContentScale.Crop,
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