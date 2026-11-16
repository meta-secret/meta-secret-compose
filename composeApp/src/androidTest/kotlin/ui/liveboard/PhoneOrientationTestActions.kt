package ui.liveboard

import android.app.Activity
import android.app.UiAutomation
import android.content.res.Configuration
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import metasecret.project.com.PhoneOrientationPolicy

/** Actual host policy plus test-owned physical rotation; never overrides the Activity request. */
internal class PhoneOrientationTestActions(private val activity: () -> Activity) {
    private data class Baseline(val locked: Boolean, val userRotation: Int)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val policy = PhoneOrientationPolicy()
    private var baseline: Baseline? = null
    private var rotationAttempted = false

    fun applyPolicy() {
        instrumentation.runOnMainSync {
            val host = activity()
            val resolver = host.contentResolver
            val auto = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION)
            val user = Settings.System.getInt(resolver, Settings.System.USER_ROTATION)
            check(auto in 0..1 && user in 0..3) { "Exact rotation baseline unavailable" }
            check(baseline == null) { "Rotation baseline already captured" }
            baseline = Baseline(auto == 0, user)
            policy.apply(host.resources.configuration.smallestScreenWidthDp, host.requestedOrientation) {
                host.requestedOrientation = it
            }
        }
    }

    fun rotate(landscape: Boolean): Int {
        checkNotNull(baseline) { "Rotation requires captured baseline" }
        var expected = Configuration.ORIENTATION_UNDEFINED
        instrumentation.runOnMainSync {
            val width = activity().resources.configuration.smallestScreenWidthDp
            check(width > 0) { "Physical screen class unavailable" }
            expected = if (landscape && width >= 600) Configuration.ORIENTATION_LANDSCAPE
                else Configuration.ORIENTATION_PORTRAIT
        }
        rotationAttempted = true
        check(instrumentation.uiAutomation.setRotation(if (landscape) UiAutomation.ROTATION_FREEZE_90
            else UiAutomation.ROTATION_FREEZE_0)) { "Physical rotation request failed" }
        return expected
    }

    fun restore(priorFailure: Throwable? = null) {
        if (!rotationAttempted) {
            priorFailure?.let { throw it }
            return
        }
        val saved = checkNotNull(baseline)
        val errors = mutableListOf<Throwable>()
        priorFailure?.let(errors::add)
        runCatching {
            check(instrumentation.uiAutomation.setRotation(saved.userRotation)) { "Rotation value restoration failed" }
        }.onFailure(errors::add)
        if (!saved.locked) runCatching {
            check(instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)) {
                "Rotation sensor restoration failed"
            }
        }.onFailure(errors::add)
        if (errors.isNotEmpty()) {
            errors.drop(1).forEach(errors.first()::addSuppressed)
            throw errors.first()
        }
        rotationAttempted = false
    }
}
