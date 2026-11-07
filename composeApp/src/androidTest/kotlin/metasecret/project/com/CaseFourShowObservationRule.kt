package metasecret.project.com

import androidx.test.platform.app.InstrumentationRegistry
import core.LogFormatterInterface
import core.MetaSecretCoreServiceAndroid
import core.metaSecretCore.MetaSecretCoreInterface
import core.metaSecretCore.liveboard.LiveboardShowObservationRecord
import core.metaSecretCore.liveboard.encodedShowObservation
import di.androidPlatformModule
import di.appModule
import java.util.concurrent.atomic.AtomicReference
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import ui.liveboard.LiveboardRecoverObservationRecord
import ui.liveboard.LiveboardDecisionObservationRecord
import ui.liveboard.LiveboardPlatformAndroid
import ui.liveboard.LiveboardPlatformPort

/** Observer-only; the shared flag also allocates bounded incoming observation tickets. */
internal class CaseFourShowObservationRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            if (!admitted(description)) {
                base.evaluate()
                return
            }
            check(GlobalContext.getOrNull() == null) { "Show observation requires a cold owned Koin graph" }
            val application = KoinApplication.init()
            var published = false
            var observer: CaseFourObservedPlatform? = null
            val recoverObserver = AtomicReference<CaseFourRecoverPortObserver?>(null)
            var primary: Throwable? = null
            try {
                application.androidContext(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)
                application.allowOverride(true)
                application.modules(appModule, androidPlatformModule, module {
                    single<MetaSecretCoreInterface> {
                        // Preserve the original lazy singleton's first-consumer construction boundary.
                        CaseFourRecoverPortObserver(MetaSecretCoreServiceAndroid(),
                            get<LogFormatterInterface>()::logE2eDiagnostic).also {
                            recoverObserver.set(it)
                            it.ready()
                        }
                    }
                    single<LiveboardPlatformPort> {
                        CaseFourObservedPlatform(get<LiveboardPlatformAndroid>(), get<LogFormatterInterface>())
                    }
                })
                GlobalContext.startKoin(application)
                published = true
                observer = application.koin.get<LiveboardPlatformPort>() as CaseFourObservedPlatform
                base.evaluate()
            } catch (error: Throwable) {
                primary = error
                throw error
            } finally {
                observer?.close()
                recoverObserver.get()?.close()
                try {
                    // Koin 4.1.1 start/stop use this same monitor: identity and stop are one boundary.
                    synchronized(GlobalContext) {
                        val current = GlobalContext.getOrNull()
                        if (current === application.koin) {
                            GlobalContext.stopKoin()
                        } else {
                            check(!published && current == null) { "Show observation Koin ownership changed" }
                            application.close()
                        }
                    }
                } catch (cleanup: Throwable) {
                    if (primary == null) throw cleanup
                    if (cleanup !== primary) primary.addSuppressed(cleanup)
                }
            }
        }
    }

    private fun admitted(description: Description): Boolean {
        if (!BuildConfig.DEBUG || BuildConfig.META_SECRET_ENV != "local" ||
            description.className != CLASS || description.methodName != METHOD) return false
        val arguments = InstrumentationRegistry.getArguments()
        return InstrumentationRegistry.getInstrumentation().targetContext.packageName == "metasecret.project.com" &&
            arguments.getString("class") == "$CLASS#$METHOD" &&
            arguments.getString("setupOnly") == "false" && arguments.getString("recoveryCycles") == "6"
    }

    private companion object {
        const val CLASS = "metasecret.project.com.CaseFourAndroidConcurrentRecoveryTest"
        const val METHOD = "createVaultThenHandleConcurrentRecovery"
    }
}

/** SAME concrete platform; no business, attachment, clipboard or Back implementation is copied. */
internal class CaseFourObservedPlatform(
    original: LiveboardPlatformAndroid,
    private val formatter: LogFormatterInterface,
) : LiveboardPlatformPort by original {
    private val recoverActions = CaseFourRecoverActionObserver(formatter::logE2eDiagnostic)
    @Volatile private var observing = true
    override val recoverObservationEnabled: Boolean get() = observing
    override fun emitRecoverObservation(record: LiveboardRecoverObservationRecord) = recoverActions.emit(record)
    override fun closeRecoverObservation(continuous: Boolean) = recoverActions.ownerClosed(continuous)
    override val decisionObservationEnabled: Boolean get() = observing

    override fun emitDecisionObservation(record: LiveboardDecisionObservationRecord) = Unit

    @Synchronized
    override fun emitShowObservation(record: LiveboardShowObservationRecord) {
        if (!observing) return
        runCatching { record.encodedShowObservation()?.let(formatter::logE2eDiagnostic) }
    }

    @Synchronized
    fun close() { observing = false; recoverActions.close() }
}
