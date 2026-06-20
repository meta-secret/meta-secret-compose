package ui.screenContent

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.background_main
import org.jetbrains.compose.resources.painterResource
import core.AppColors
import core.AppString
import core.appString
import ui.theme.AppTextStyles

@Composable
fun CommonBackground(
    text: AppString,
    headerTrailingContent: @Composable (() -> Unit)? = null,
    screenContent: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Image(
            painter = painterResource(Res.drawable.background_main),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 30.dp,
                        bottom = 14.dp,
                        start = 16.dp,
                        end = 16.dp
                    )
            ) {
                Text(
                    modifier = Modifier.align(Alignment.CenterStart),
                    text = appString(text),
                    color = AppColors.White,
                    style = AppTextStyles.MainHeader(),
                )

                headerTrailingContent?.let {
                    Box(
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        it()
                    }
                }
            }
            screenContent()
        }
    }
}
