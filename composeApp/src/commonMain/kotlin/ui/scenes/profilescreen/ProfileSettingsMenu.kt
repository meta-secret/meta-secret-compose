package ui.scenes.profilescreen

import core.AppImage
import core.ImageProviderInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import core.AppColors
import core.AppString
import core.appString
import org.koin.compose.koinInject
import ui.theme.AppTextStyles

@Composable
fun ProfileSettingsMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onResetAllDataClick: () -> Unit,
) {
    Box(
        modifier = Modifier,
        contentAlignment = Alignment.TopEnd
    ) {
        val imageProvider: ImageProviderInterface = koinInject()
        IconButton(
            onClick = { onExpandedChange(!expanded) }
        ) {
            Image(
                painter = imageProvider.getPainter(AppImage.SettingsIcon),
                contentDescription = appString(AppString.settings),
                modifier = Modifier.size(24.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            containerColor = AppColors.PopUp
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = appString(AppString.resetAllData),
                        style = AppTextStyles.Paragraph(),
                        color = AppColors.White
                    )
                },
                onClick = {
                    onExpandedChange(false)
                    onResetAllDataClick()
                }
            )
        }
    }
}
