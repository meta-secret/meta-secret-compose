package sharedData

import androidx.compose.runtime.Composable

@Composable
expect fun getScreenWidth(): Int

@Composable
expect fun getScreenHeight(): Int

@Composable
expect fun getScreenWidthPx(): Int

@Composable
expect fun getScreenHeightPx(): Int