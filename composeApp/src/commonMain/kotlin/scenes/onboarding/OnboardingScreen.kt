package scenes.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalDensity
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
import scenes.mainscreen.MainScreen
import scenes.signinscreen.SignInScreen
import sharedData.AppColors
import ui.ClassicButton

class OnboardingScreen : Screen {
    @Composable
    @OptIn(ExperimentalFoundationApi::class)
    override fun Content() {
        val viewModel: OnboardingViewModel = koinViewModel()
        val navigator = LocalNavigator.currentOrThrow
        val pages = viewModel.pages
        val pagerState = rememberPagerState(pageCount = { pages.size })
        val backgroundMain = painterResource(Res.drawable.background_main)
        val currentPage by viewModel.currentPage.collectAsState()
        val density = LocalDensity.current
        val topInset = with(density) { WindowInsets.systemBars.getTop(this).toDp() }

        LaunchedEffect(currentPage) {
            if (currentPage == -1) {
                navigator.push(SignInScreen())
            } else if (currentPage == -2) {
                navigator.push(MainScreen())
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topInset, bottom = 24.dp)
                    .padding(horizontal = 0.dp),
            ) {
                OnboardingHeader(
                    pagerState = pagerState,
                    viewModel = viewModel,
                    pagesCount = pages.size
                )

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
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            repeat(pagerState.pageCount) { iteration ->
                val color =
                    if (pagerState.currentPage == iteration) AppColors.ActionLink else AppColors.White50
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(color)
                        .size(8.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                fontSize = 15.sp,
                color = AppColors.White75,
                text = "${pagerState.currentPage + 1} / $pagesCount"
            )
            Text(
                modifier = Modifier
                    .clickable {
                        coroutineScope.launch {
                            val result = viewModel.handle(OnboardingViewEvents.COMPLETE_ONBOARDING)
                            println(result)
                        }
                    },
                text = stringResource(Res.string.skip),
                fontSize = 15.sp,
                color = AppColors.ActionLink,
                textAlign = TextAlign.End,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingFooter(pagerState: PagerState, viewModel: OnboardingViewModel, pagesCount: Int) {
    Column(
        modifier = Modifier
            .padding(top = 24.dp)
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
    ) {
        val coroutineScope = rememberCoroutineScope()

        ClassicButton(
            {
                coroutineScope.launch {
                    if (pagerState.currentPage + 1 >= pagesCount) {
                        viewModel.handle(OnboardingViewEvents.COMPLETE_ONBOARDING)
                    } else {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            },
            stringResource(Res.string.next)
        )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(onBoardingPage.title),
                color = AppColors.White,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
            Text(
                text = stringResource(onBoardingPage.subTitle),
                color = AppColors.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
            Text(
                text = stringResource(onBoardingPage.description),
                fontSize = 16.sp,
                color = AppColors.White75,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
        }
    }
}