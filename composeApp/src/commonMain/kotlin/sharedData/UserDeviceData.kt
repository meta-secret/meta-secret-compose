package sharedData

import androidx.compose.runtime.Composable

expect fun getAppVersion(): String      //metasecret app version

expect fun getDeviceMake(): String      //device manufacturer

expect fun getDeviceId(): String        //temporary random app ID

@Composable
expect fun actualWidthFactor(): Int        //real display width in Dp

@Composable
expect fun getScreenWidth(): Int

@Composable
expect fun actualHeightFactor(): Int       //real display height in Dp

@Composable
expect fun getScreenHeight(): Int