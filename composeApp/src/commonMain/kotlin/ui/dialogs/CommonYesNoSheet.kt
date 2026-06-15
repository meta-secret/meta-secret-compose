package ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import core.AppColors
import core.AppString
import core.appString
import ui.ClassicButton
import ui.theme.AppTextStyles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonYesNoSheet(
    title: String,
    subtitle: String,
    isVisible: Boolean,
    isNoMain: Boolean = false,
    onNo: () -> Unit,
    onYes: () -> Unit,
) {
    if (!isVisible) return

    ModalBottomSheet(
        onDismissRequest = onNo,
        containerColor = AppColors.PopUp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = title,
                style = AppTextStyles.SectionTitle(),
                color = AppColors.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = subtitle,
                style = AppTextStyles.Paragraph(),
                color = AppColors.White75,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val noIsMain = isNoMain
                ClassicButton(
                    action = onNo,
                    text = appString(AppString.no),
                    color = if (noIsMain) AppColors.Warning else Color.Transparent,
                    borderColor = if (noIsMain) AppColors.Warning else AppColors.White10,
                    modifier = Modifier.weight(1f)
                )
                ClassicButton(
                    action = onYes,
                    text = appString(AppString.yes),
                    color = if (noIsMain) Color.Transparent else AppColors.Warning,
                    borderColor = if (noIsMain) AppColors.White10 else AppColors.Warning,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
