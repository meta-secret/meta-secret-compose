package ui.scenes.onboarding

import core.AppString
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.executioner
import org.jetbrains.compose.resources.DrawableResource

sealed class OnBoardingPage(
    val image: DrawableResource,
    val title: AppString,
    val subTitle: AppString,
    val description: AppString
) {
    data object First: OnBoardingPage(
        image = Res.drawable.executioner,
        title = AppString.onBoardingTitle1,
        subTitle = AppString.onBoardingSubTitle1,
        description = AppString.onBoardingDescription1
    )

    data object Second: OnBoardingPage(
        image = Res.drawable.executioner,
        title = AppString.onBoardingTitle2,
        subTitle = AppString.onBoardingSubTitle2,
        description = AppString.onBoardingDescription2
    )

    data object Third: OnBoardingPage(
        image = Res.drawable.executioner,
        title = AppString.onBoardingTitle3,
        subTitle = AppString.onBoardingSubTitle3,
        description = AppString.onBoardingDescription3
    )
}
