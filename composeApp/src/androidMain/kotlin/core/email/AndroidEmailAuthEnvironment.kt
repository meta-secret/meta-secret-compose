package core.email

import androidx.fragment.app.FragmentActivity

object AndroidEmailAuthEnvironment {
    @Volatile
    var activity: FragmentActivity? = null
}
