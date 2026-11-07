package ui.liveboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.Settings

/** Activity Back and clipboard share one exact replaceable attachment, without native authority. */
class LiveboardPlatformAndroid(private val context: Context) : LiveboardPlatformPort {
    private inner class Attachment(val owner: Any, val back: () -> Boolean) : LiveboardRootAttachment {
        override fun isCurrent(): Boolean = current === this
        override fun close() { if (current === this) current = null }
    }
    private var current: Attachment? = null
    override val reducedMotion: Boolean get() =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    override fun attach(owner: Any, onBack: () -> Boolean): LiveboardRootAttachment = Attachment(owner, onBack)
        .also { current = it }
    fun dispatchBack(): Boolean = current?.takeIf { it.isCurrent() }?.back?.invoke() == true
    override suspend fun readClipboard(): String? = context.getSystemService(ClipboardManager::class.java)
        ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    override fun copyText(value: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", value))
    }
}
