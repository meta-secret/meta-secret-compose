package ui.screenContent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sharedData.AppColors

@Composable
fun ContentCell (showContent: @Composable () -> Unit) {

    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .background(AppColors.White5, RoundedCornerShape(10.dp)).height(92.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().height(60.dp).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            showContent()
        }
    }
}

@Composable
fun textOnValue(dataSize: Int, base: String, under4: String, from5: String): String {
    var actualString = base
    if (dataSize == 0) actualString = from5
    else if (dataSize in 2..4) actualString = under4
    else if (dataSize > 4) actualString = from5
    return actualString
}