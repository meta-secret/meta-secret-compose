package scenes.onboarding

import AppColors
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import kotlinproject.composeapp.generated.resources.background_main
import kotlinproject.composeapp.generated.resources.next
import kotlinproject.composeapp.generated.resources.skip
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import scenes.signinscreein.SignInScreen

class OnboardingScreen : Screen {
    @Composable
    @OptIn(ExperimentalFoundationApi::class)
    override fun Content() {
        val backgroundMain = painterResource(Res.drawable.background_main)

        val viewModel: OnboardingViewModel = koinViewModel()
        val navigator = LocalNavigator.currentOrThrow

        val currentPage by viewModel.currentPage.collectAsState()
        val pages = viewModel.pages

        val pagerState = rememberPagerState(pageCount = { pages.size })

        LaunchedEffect(currentPage) {
            if (currentPage == -1) {
                navigator.push(SignInScreen())
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = backgroundMain,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
            )

            Column(modifier = Modifier
                .fillMaxSize()
                .padding(top = 44.dp, bottom = 12.dp)
                .padding(horizontal = 16.dp),
            ) {
                OnboardingHeader(pagerState = pagerState, viewModel = viewModel, pagesCount = pages.size)

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    PagerScreen(onBoardingPage = pages[page])
                }

                OnboardingFooter(
                    pagerState = pagerState,
                    viewModel = viewModel,
                    pagesCount = pages.size
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingHeader(pagerState: PagerState, viewModel: OnboardingViewModel, pagesCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            repeat(pagerState.pageCount) { iteration ->
                val color = if (pagerState.currentPage == iteration) AppColors.ActionLink else AppColors.White50
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
            color = AppColors.White75,
            text = "${pagerState.currentPage + 1} / $pagesCount"
        )

        Button(
            onClick = { viewModel.completeOnboarding() },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent),
            elevation = null
        ) {
            Text(
                text = stringResource(Res.string.skip),
                fontSize = 15.sp,
                color = AppColors.ActionLink,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingFooter(pagerState: PagerState, viewModel: OnboardingViewModel, pagesCount: Int) {
    Column(
        modifier = Modifier
            .padding(vertical = 24.dp)
            .fillMaxWidth()
    ) {
        val coroutineScope = rememberCoroutineScope()

        Button(
            onClick = {
                coroutineScope.launch {
                    if (pagerState.currentPage + 1 >= pagesCount) {
                        viewModel.completeOnboarding()
                    } else {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            },
            modifier = Modifier
                .requiredHeight(48.dp)
                .fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF0368FF), contentColor = Color.White)
        ) {
            Text(text = stringResource(Res.string.next), fontSize = 16.sp)
        }
    }
}

@Composable
fun PagerScreen(onBoardingPage: OnBoardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Image(
                painter = painterResource(onBoardingPage.image),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .aspectRatio(1.0f)
                    .padding(top = 32.dp)
                    .fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = stringResource(onBoardingPage.title),
                color = AppColors.White,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(onBoardingPage.subTitle),
                color = AppColors.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(onBoardingPage.description),
                fontSize = 16.sp,
                color = AppColors.White75,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}