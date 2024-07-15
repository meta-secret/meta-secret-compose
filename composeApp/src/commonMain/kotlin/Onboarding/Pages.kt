package Onboarding


import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.background_main
import kotlinproject.composeapp.generated.resources.empty
import kotlinproject.composeapp.generated.resources.gotSecrets
import kotlinproject.composeapp.generated.resources.secureSafe
import kotlinproject.composeapp.generated.resources.splitIt
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

sealed class OnBoardingPage(
    val image: DrawableResource,
    val title: StringResource,
    val subTitle: StringResource,
    val description: StringResource
) {
    object First: OnBoardingPage(
        image = Res.drawable.background_main,
        title = Res.string.gotSecrets,
        subTitle = Res.string.splitIt,
        description = Res.string.secureSafe
    )

    object Second: OnBoardingPage(
        image = Res.drawable.background_main,
        title = Res.string.empty,
        subTitle = Res.string.splitIt,
        description = Res.string.secureSafe
    )

    object Third: OnBoardingPage(
        image = Res.drawable.background_main,
        title = Res.string.empty,
        subTitle = Res.string.splitIt,
        description = Res.string.secureSafe
    )
}
