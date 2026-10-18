package ui

import core.AppImage
import core.ImageProviderInterface
import core.AppString
import core.appString
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject

@Composable
fun AddButton(
    label: AppString = AppString.addSecretAction,
    testTag: String = "add-secret-fab",
    enabled: Boolean = true,
    action: (Boolean) -> Unit,
) {
    val imageProvider: ImageProviderInterface = koinInject()
    val imgSize = 80
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 60.dp, end = 24.dp),
        Alignment.BottomEnd
    ) {
        Image(
            painter = imageProvider.getPainter(AppImage.AddButton),
            contentDescription = appString(label),
            modifier = Modifier
                .size(imgSize.dp)
                .testTag(testTag)
                .alpha(if (enabled) 1f else .35f)
                .clickable(enabled = enabled, role = Role.Button) { action(true) }
        )
    }
}


