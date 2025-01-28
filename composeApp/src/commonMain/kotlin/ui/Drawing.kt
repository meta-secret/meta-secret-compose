package ui

import AppColors
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import sharedData.getScreenHeightPx
import sharedData.getScreenWidthPx

@Composable
fun DrawLine(xStart: Float, xEnd: Float, yStart: Float, yEnd: Float, isVerticalLine: Boolean) {
    val actualPxHeight = getScreenHeightPx() / 812f
    val actualPxWidth = getScreenWidthPx() / 375f

    Canvas(modifier = Modifier.fillMaxHeight()) {

        if (isVerticalLine) {
            drawLine(
                color = AppColors.White10,
                start = Offset(xStart, yStart * actualPxHeight),
                end = Offset(xEnd, yEnd * actualPxHeight),
                strokeWidth = actualPxHeight
            )
        } else {
            drawLine(
                color = AppColors.White10,
                start = Offset(xStart * actualPxWidth, yStart * actualPxHeight),
                end = Offset(xEnd * actualPxWidth, yEnd * actualPxHeight),
                strokeWidth = actualPxHeight
            )
        }
    }
}