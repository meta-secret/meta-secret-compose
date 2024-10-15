package scenes.onboarding

import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.empty
import kotlinproject.composeapp.generated.resources.executioner
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
    data object First: OnBoardingPage(
        image = Res.drawable.executioner,
        title = Res.string.gotSecrets,
        subTitle = Res.string.splitIt,
        description = Res.string.secureSafe
    )

    data object Second: OnBoardingPage(
        image = Res.drawable.executioner,
        title = Res.string.empty,
        subTitle = Res.string.splitIt,
        description = Res.string.secureSafe
    )

    data object Third: OnBoardingPage(
        image = Res.drawable.executioner,
        title = Res.string.empty,
        subTitle = Res.string.splitIt,
        description = Res.string.secureSafe
    )
}
