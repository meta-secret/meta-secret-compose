package ui.scenes.onboarding

import core.AppString
import core.AppImage

sealed class OnBoardingPage(
    val image: AppImage,
    val title: AppString,
    val subTitle: AppString,
    val description: AppString
) {
    data object First: OnBoardingPage(
        image = AppImage.Executioner,
        title = AppString.onBoardingTitle1,
        subTitle = AppString.onBoardingSubTitle1,
        description = AppString.onBoardingDescription1
    )

    data object Second: OnBoardingPage(
        image = AppImage.Executioner,
        title = AppString.onBoardingTitle2,
        subTitle = AppString.onBoardingSubTitle2,
        description = AppString.onBoardingDescription2
    )

    data object Third: OnBoardingPage(
        image = AppImage.Executioner,
        title = AppString.onBoardingTitle3,
        subTitle = AppString.onBoardingSubTitle3,
        description = AppString.onBoardingDescription3
    )
}
