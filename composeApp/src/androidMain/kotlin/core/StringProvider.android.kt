package core

import android.content.Context
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlinx.coroutines.runBlocking
import kotlinproject.composeapp.generated.resources.Res

class StringProviderAndroid(private val context: Context) : StringProviderInterface {
    @OptIn(ExperimentalResourceApi::class)
    private fun s(id: org.jetbrains.compose.resources.StringResource): String = runBlocking { getString(id) }

    override fun getString(key: AppString): String = s(key.resource())
}
