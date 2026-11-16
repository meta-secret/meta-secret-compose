package core.metaSecretCore.liveboard

import core.metaSecretCore.InitResult
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import models.apiModels.AppStateModel
import models.apiModels.MasterKeyModel

data class NativeKeyResult<T>(
    val owner: NativeOwnerToken,
    val value: T,
    val retiredScope: NativeReadScope?,
    val failure: Exception? = null,
)

/** Exclusive local key/native transitions; comparison replaces the Manager's sole holder. */
class NativeKeyLifecycle(
    private val ports: NativeKeyPorts,
    private val initializationOwner: NativeInitializationOwner,
    private val environment: NativeReadEnvironment,
    private val readOwner: NativeCanonicalReadOwner,
) {
    private class StartupReceipt(val origin: NativeOwnerToken, val initialized: NativeReadScope)
    private val startupReceipt = MutableStateFlow<StartupReceipt?>(null)
    private var initializedMasterKey: String? = null
    val isInitialized: Boolean get() = initializationOwner.currentScope() != null
    fun capture(): NativeOwnerToken = initializationOwner.capture()
    fun isCurrent(owner: NativeOwnerToken): Boolean = initializationOwner.isCurrent(owner)

    suspend fun initializeSavedKey(expectedOwner: NativeOwnerToken): NativeKeyResult<InitResult>? {
        withContext(environment.ui) {
            if (initializationOwner.isCurrent(expectedOwner)) readOwner.beforeKey(expectedOwner)
        }
        val transition = initializationOwner.beginTransition(expectedOwner, retire = false) ?: return null
        return withContext(NonCancellable) {
            var epoch: NativeOwnerEpoch? = null
            var stamp: NativeOwnerToken? = null
            var failure: Exception? = null
            var result: InitResult = InitResult.Error("Native owner retired")
            try {
                transition.drained.await()
                if (initializationOwner.owns(transition)) {
                    val masterKey = withContext(environment.io) { readSavedKey(transition) }
                    if (initializationOwner.owns(transition)) {
                        val prior = transition.priorScope
                        if (!masterKey.isNullOrEmpty() && prior != null && initializedMasterKey == masterKey) {
                            epoch = prior.epoch
                            result = InitResult.Success("Already initialized")
                        } else if (initializationOwner.retire(transition)) {
                            clearStartup(transition)
                            initializedMasterKey = null
                            if (masterKey.isNullOrEmpty()) {
                                withContext(environment.io) {
                                    check(initializationOwner.owns(transition))
                                    ports.keys.clearAll(isCleanDB = true)
                                }
                                result = InitResult.Error("No master key found")
                            } else {
                                val raw = initializationOwner.initialize(transition, masterKey)
                                if (initializationOwner.owns(transition)) {
                                    initializedMasterKey = masterKey
                                    val initializedEpoch = NativeOwnerEpoch()
                                    epoch = initializedEpoch
                                    if (prior == null) startupReceipt.value = StartupReceipt(
                                        expectedOwner, NativeReadScope(transition.owner, initializedEpoch),
                                    )
                                    result = InitResult.Success(raw)
                                }
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                clearStartup(transition)
                failure = error
                initializedMasterKey = null
                initializationOwner.retire(transition)
                result = InitResult.Error(error.message ?: "Unknown error")
            } finally {
                stamp = finishTransition(transition, epoch)
            }
            stamp?.let { NativeKeyResult(it, result, transition.priorScope.takeIf { prior -> prior?.owner !== it },
                failure) }
        }
    }

    suspend fun clearProfile(expectedOwner: NativeOwnerToken): NativeKeyResult<Boolean>? {
        withContext(environment.ui) {
            if (initializationOwner.isCurrent(expectedOwner)) readOwner.beforeKey(expectedOwner)
        }
        val transition = initializationOwner.beginTransition(expectedOwner, retire = true) ?: return null
        return withContext(NonCancellable) {
            var stamp: NativeOwnerToken? = null
            var failure: Exception? = null
            var cleared = false
            clearStartup(transition)
            initializedMasterKey = null
            try {
                transition.drained.await()
                if (initializationOwner.owns(transition)) {
                    cleared = withContext(environment.io) {
                        check(initializationOwner.owns(transition))
                        ports.keys.clearAll(isCleanDB = true)
                    }
                }
            } catch (error: Exception) {
                failure = error
            } finally {
                stamp = finishTransition(transition, null)
            }
            stamp?.let { NativeKeyResult(it, cleared, transition.priorScope, failure) }
        }
    }

    /** Only this exact successful startup lineage can cross an original uninitialized token. */
    internal suspend fun <T> registrationResume(
        expectedOrigin: NativeOwnerToken,
        allowStartupHandoff: Boolean,
        action: suspend RegistrationPermit.() -> T,
    ): NativeKeyResult<T?>? {
        val scope = initializationOwner.currentScope()
        val handoff = startupReceipt.value?.takeIf {
            allowStartupHandoff && it.origin === expectedOrigin && it.initialized == scope
        }
        val owner = if (capture() === expectedOrigin) expectedOrigin else handoff?.initialized?.owner ?: return null
        var claimed = handoff == null
        val result = registration(owner) {
            if (handoff != null) {
                if (!matches(handoff.initialized) || !startupReceipt.compareAndSet(handoff, null)
                    ) return@registration null
                claimed = true
            }
            action()
        }
        // A losing concurrent claimant receives no successor receipt to adopt.
        return result.takeIf { claimed }
    }

    private fun clearStartup(transition: NativeAdmissionTracker.Transition) {
        if (initializationOwner.owns(transition)) startupReceipt.value = null
    }

    internal suspend fun <T> registration(
        expectedOwner: NativeOwnerToken,
        action: suspend RegistrationPermit.() -> T,
    ): NativeKeyResult<T?>? {
        withContext(environment.ui) {
            if (initializationOwner.isCurrent(expectedOwner)) readOwner.beforeKey(expectedOwner)
        }
        val transition = initializationOwner.beginTransition(expectedOwner, retire = false) ?: return null
        return withContext(NonCancellable) {
            val permit = RegistrationPermit(transition)
            var value: T? = null
            var failure: Exception? = null
            var stamp: NativeOwnerToken? = null
            try {
                transition.drained.await()
                if (initializationOwner.owns(transition)) value = permit.action()
            } catch (error: Exception) {
                failure = error
                permit.failClosed()
            } finally {
                stamp = finishTransition(transition, permit.epoch)
            }
            stamp?.let { NativeKeyResult(it, value, transition.priorScope.takeIf { prior -> prior?.owner !== it },
                failure) }
        }
    }

    /** No observer or shared-cache publication is allowed while this permit exists. */
    internal inner class RegistrationPermit(private val transition: NativeAdmissionTracker.Transition) {
        var epoch: NativeOwnerEpoch? = transition.priorScope?.epoch
            private set
        private var retired = false

        fun matches(scope: NativeReadScope): Boolean =
            initializationOwner.owns(transition) && transition.priorScope == scope

        private fun checkOwned() = check(initializationOwner.owns(transition)) { "Native owner retired" }
        private suspend fun raw(native: () -> String): String =
            initializationOwner.invokeExclusive(transition, native) ?: error("Native owner retired")

        suspend fun savedKey(): String? = withContext(environment.io) { readSavedKey(transition).also { checkOwned() } }
        suspend fun contains(key: String): Boolean = withContext(environment.io) {
            checkOwned()
            ports.keys.containsKey(key).also { checkOwned() }
        }
        suspend fun save(key: String, value: String): Boolean = withContext(environment.io) {
            checkOwned()
            ports.keys.saveString(key, value).also { checkOwned() }
        }
        fun failClosed() {
            clearStartup(transition)
            initializedMasterKey = null
            epoch = null
            if (!retired && initializationOwner.retire(transition)) retired = true
        }
        suspend fun clear(): Boolean {
            checkOwned()
            failClosed()
            return withContext(environment.io) {
                checkOwned()
                ports.keys.clearAll(isCleanDB = true).also { checkOwned() }
            }
        }
        suspend fun initializeSaved() {
            val key = savedKey()
            if (key.isNullOrEmpty()) {
                clear()
                error("No master key found")
            }
            if (epoch != null && initializedMasterKey == key) return
            initializeKey(key)
        }
        suspend fun initializeKey(key: String) {
            checkOwned()
            failClosed()
            initializationOwner.initialize(transition, key)
            checkOwned()
            initializedMasterKey = key
            epoch = NativeOwnerEpoch()
        }
        suspend fun generateMasterKey(): MasterKeyModel = MasterKeyModel.fromJson(raw { ports.core
            .generateMasterKey() })
        suspend fun snapshot(): AppStateModel = AppStateModel.fromJson(raw { ports.core.getAppState() })
        suspend fun generateCredentials(name: String): AppStateModel = AppStateModel.fromJson(raw { ports.core
            .generateUserCreds(name) })
        suspend fun signUp(): AppStateModel = AppStateModel.fromJson(raw { ports.core.signUp() })
    }

    private suspend fun finishTransition(
        transition: NativeAdmissionTracker.Transition,
        epoch: NativeOwnerEpoch?,
    ): NativeOwnerToken? {
        val stamp = initializationOwner.finish(transition, epoch)
        val prior = transition.priorScope
        // Finish releases admission before retirement may resume observers inline.
        if (prior != null && (stamp == null || prior.owner !== stamp)) readOwner.retireScope(prior)
        return stamp
    }

    private suspend fun readSavedKey(transition: NativeAdmissionTracker.Transition): String? {
        check(initializationOwner.owns(transition))
        val primary = ports.keys.getString("master_key")
        if (!primary.isNullOrEmpty()) return primary
        check(initializationOwner.owns(transition))
        if (!ports.keys.containsKey("master_key")) return null
        check(initializationOwner.owns(transition))
        return ports.keys.getString("master_key")
    }
}
