package metasecret.project.com

import androidx.test.platform.app.InstrumentationRegistry
import core.AppString
import core.StringProviderAndroid
import core.StringProviderInterface
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.dsl.module

/** Outermost test-only resource; never adopts or stops an existing global graph. */
internal class LiveboardComponentStringsRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            synchronized(GlobalContext) {
                check(GlobalContext.getOrNull() == null) { "Component strings require a cold Koin context" }
            }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val strings = StringProviderAndroid(context)
            val application = KoinApplication.init()
            val owned = application.koin
            var registered = false
            var primary: Throwable? = null
            try {
                application.modules(module { single<StringProviderInterface> { strings } })
                synchronized(GlobalContext) {
                    check(GlobalContext.getOrNull() == null) { "Component Koin changed before registration" }
                    GlobalContext.startKoin(application)
                    registered = true
                }
                check(GlobalContext.getOrNull() === owned)
                check(owned.get<StringProviderInterface>() === strings)
                // Positive setup proof precedes every broad malformed-data assertThrows.
                check(lbLabel(AppString.lbError).isNotBlank()) { "Real Android strings are unavailable" }
                base.evaluate()
            } catch (failure: Throwable) {
                primary = failure
                throw failure
            } finally {
                try {
                    synchronized(GlobalContext) {
                        if (GlobalContext.getOrNull() === owned) {
                            GlobalContext.stopKoin()
                            check(GlobalContext.getOrNull() == null)
                        } else {
                            application.close()
                            check(!registered) { "Owned component Koin identity changed" }
                        }
                    }
                } catch (cleanup: Throwable) {
                    val failure = primary
                    if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
                }
            }
        }
    }
}
