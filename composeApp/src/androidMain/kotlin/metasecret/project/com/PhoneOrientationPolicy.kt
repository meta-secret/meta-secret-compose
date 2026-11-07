package metasecret.project.com

import android.content.pm.ActivityInfo

/** Host-only portrait request; large screens retain their existing orientation policy. */
internal class PhoneOrientationPolicy {
    private var ownsPortrait = false

    fun apply(smallestWidthDp: Int, currentRequest: Int, request: (Int) -> Unit) {
        if (smallestWidthDp <= 0) return
        if (smallestWidthDp < PHONE_WIDTH_LIMIT_DP) {
            if (currentRequest != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                request(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                ownsPortrait = true
            }
        } else if (ownsPortrait) {
            if (currentRequest == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                request(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
            }
            ownsPortrait = false
        }
    }
}

private const val PHONE_WIDTH_LIMIT_DP = 600
