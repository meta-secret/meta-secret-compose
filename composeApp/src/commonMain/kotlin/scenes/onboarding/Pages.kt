package scenes.onboarding

import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.empty
import kotlinproject.composeapp.generated.resources.executioner
import kotlinproject.composeapp.generated.resources.onBoardingDescription1
import kotlinproject.composeapp.generated.resources.onBoardingDescription2
import kotlinproject.composeapp.generated.resources.onBoardingDescription3
import kotlinproject.composeapp.generated.resources.onBoardingSubTitle1
import kotlinproject.composeapp.generated.resources.onBoardingSubTitle2
import kotlinproject.composeapp.generated.resources.onBoardingSubTitle3
import kotlinproject.composeapp.generated.resources.onBoardingTitle1
import kotlinproject.composeapp.generated.resources.onBoardingTitle2
import kotlinproject.composeapp.generated.resources.onBoardingTitle3
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

sealed class OnBoardingPage(
    val image: DrawableResource,
    val title: StringResource,
    val subTitle: StringResource,
    val description: StringResource
) {
    data object First: OnBoardingPage(
        image = Res.drawable.executioner,
        title = Res.string.onBoardingTitle1,
        subTitle = Res.string.onBoardingSubTitle1,
        description = Res.string.onBoardingDescription1
    )

    data object Second: OnBoardingPage(
        image = Res.drawable.executioner,
        title = Res.string.onBoardingTitle2,
        subTitle = Res.string.onBoardingSubTitle2,
        description = Res.string.onBoardingDescription2
    )

    data object Third: OnBoardingPage(
        image = Res.drawable.executioner,
        title = Res.string.onBoardingTitle3,
        subTitle = Res.string.onBoardingSubTitle3,
        description = Res.string.onBoardingDescription3
    )
}
