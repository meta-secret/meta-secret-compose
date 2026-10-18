package core.apple

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.FragmentActivity
import java.lang.ref.WeakReference

internal fun interface AppleEmailBrowser {
    fun launch(url: String)
}

internal class AndroidAppleEmailBrowser : AppleEmailBrowser {
    private var host = WeakReference<FragmentActivity>(null)

    fun attach(activity: FragmentActivity) { host = WeakReference(activity) }
    fun owns(activity: FragmentActivity): Boolean = host.get() === activity

    fun detach(activity: FragmentActivity): Boolean {
        if (!owns(activity)) return false
        host.clear()
        return true
    }

    override fun launch(url: String) {
        val activity = host.get() ?: throw AppleBrokerException()
        CustomTabsIntent.Builder().build().launchUrl(activity, Uri.parse(url))
    }
}
