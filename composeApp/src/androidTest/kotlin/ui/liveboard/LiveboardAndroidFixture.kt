package ui.liveboard

import android.content.Context
import core.*
import core.errors.ErrorMapper
import core.metaSecretCore.*
import core.metaSecretCore.liveboard.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import models.apiModels.SecretApiModel
import models.apiModels.UserData
import models.apiModels.AppStateModel
import models.apiModels.JsonConfig
import models.apiModels.Message
import models.apiModels.State
import models.apiModels.VaultFullInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/** Real client composition and physical read proof; Core/OS responses are controlled, not Rust/OS E2E. */
internal class LiveboardAndroidFixture(context: Context, captureCopy: ((String) -> Unit)? = null) {
    val core = AndroidBoardCore()
    val auth = AndroidBoardAuth()
    val platform = LiveboardPlatformAndroid(context)
    private val rootPlatform = captureCopy?.let { capture ->
        object : LiveboardPlatformPort by platform {
            override fun copyText(value: String) { capture(value) }
        }
    } ?: platform
    val privacy = LiveboardPrivacyOwner()
    val notifications = NotificationCoordinator(privacy)
    private val formatter = object : LogFormatterInterface { override fun formatLogMessage(message: String) = message }
    private val logger = AndroidBoardLogger()
    private val storage = AndroidBoardStorage()
    private val strings = StringProviderAndroid(context)
    private val environment = NativeReadEnvironment(Dispatchers.IO, Dispatchers.Main.immediate)
    val cache = AppStateCacheProvider(logger, storage, Dispatchers.Main.immediate)
    val initialization = NativeInitializationOwner(core, environment)
    private val operations = NativeOperationPort(initialization, environment)
    val reads = NativeCanonicalReadOwner(NativeReadDependencies(core, logger, cache, formatter),
        NativeReadRuntime(initialization, environment, privacy))
    private val keys = object : KeyChainInterface {
        private val values = mutableMapOf<String, String>()
        override suspend fun saveString(key: String, value: String): Boolean { values[key] = value; return true }
        override suspend fun getString(key: String) = values[key]
        override suspend fun removeKey(key: String) = values.remove(key) != null
        override suspend fun containsKey(key: String) = key in values
        override suspend fun clearAll(isCleanDB: Boolean): Boolean { values.clear(); return true }
    }
    private val lifecycle = NativeKeyLifecycle(NativeKeyPorts(core, keys), initialization, environment, reads)
    private val reset = NativeLocalReset(initialization, lifecycle, cache)
    val manager = MetaSecretAppManager(NativeManagerPorts(core, keys, storage, logger),
        NativeManagerPresentation(notifications, ErrorMapper(strings), formatter, cache), reads,
        NativeManagerRuntime(lifecycle, operations))
    val socket = MetaSecretSocketHandler(NativeSocketPorts(core, logger, notifications, ErrorMapper(strings)),
        NativeSocketPresentation(cache, strings), NativeSocketRuntime(NoopMetaSecretSocketClient(), operations), reads)
    private val checked = NativeLiveboardOperations(LiveboardCoreAdapter(core), operations, environment)
    private val factory = LiveboardSessionFactory(checked, reads, operations, environment,
        LiveboardClientPorts(privacy, auth, socket, notifications, strings))
    private val info = object : DeviceInfoProviderInterface {
        override fun getAppVersion() = "renderer-test"
        override fun getDeviceMake() = "Android"
        override fun getDeviceId() = "device-a"
    }
    val dependencies = LiveboardRootDependencies(factory, reads, environment,
        LiveboardRootServices(reset, socket, info, notifications), rootPlatform)
    private val sessions = mutableSetOf<LiveboardSession>()

    fun install() {
        startKoin { modules(module {
            single<LiveboardRootDependencies> { dependencies }
            single<OrbitBackdropBlur> { OrbitBackdropBlurAndroid() }
            single<StringProviderInterface> { strings }
            single<DebugLoggerInterface> { this@LiveboardAndroidFixture.logger }
            single<NotificationCoordinatorInterface> { notifications }
        }) }
        runBlocking(Dispatchers.IO) {
            keys.saveString("master_key", "android-renderer-key")
            assertTrue(manager.initWithSavedKey() is InitResult.Success)
            proof()
        }
    }

    suspend fun proof(): NativeReadFrame {
        val expected = requireNotNull(initialization.currentScope())
        val target = requireNotNull(reads.requireFreshRead(expected))
        manager.getStateModel()
        val covered = target.completion.await()
        check(covered.outcome == NativeReadOutcome.SUCCESS && target in covered.coveredRequirements)
        val current = requireNotNull(reads.frames.value)
        check(current.scope == expected && current.outcome == NativeReadOutcome.SUCCESS && current.attempt >= covered.attempt)
        return current
    }

    suspend fun session(): LiveboardSession = withContext(Dispatchers.Main.immediate) {
        requireNotNull(factory.open(requireNotNull(reads.frames.value))).also { sessions.add(it) }
    }

    fun idle(): Boolean = cache.synchronization.value.let { !it.loading && it.activeRefreshes == 0 }

    fun close() = runBlocking(Dispatchers.IO) {
        withTimeout(10_000) {
            auth.complete(false)
            sessions.forEach { it.awaitSettled() }
            withContext(Dispatchers.Main.immediate) { sessions.forEach { it.dispose() }; notifications.dismiss() }
            socket.pauseRefreshesAndAwait(); socket.onAppBackground()
            stopKoin()
        }
    }
}

/** Finite existing biometric port. These callbacks do not prove system authentication. */
internal class AndroidBoardAuth : BiometricAuthenticatorInterface {
    @Volatile var hold = false
    val prompts = AtomicInteger()
    @Volatile private var success: (() -> Unit)? = null
    @Volatile private var failure: ((String) -> Unit)? = null
    override fun isBiometricAvailable() = true
    override fun authenticate(onSuccess: () -> Unit, onError: (String) -> Unit, onFallback: () -> Unit) {
        prompts.incrementAndGet()
        if (hold) { success = onSuccess; failure = onError } else onSuccess()
    }
    fun complete(approved: Boolean) {
        val yes = success; val no = failure
        success = null; failure = null
        if (approved) yes?.invoke() else no?.invoke("Synthetic renderer cancellation")
    }
    override fun openAppSettings() = Unit
}

internal class AndroidBoardCore : MetaSecretCoreInterface {
    val reads = AtomicInteger()
    val calls = CopyOnWriteArrayList<Pair<String, List<String>>>()
    private fun call(name: String, args: List<String>, result: String): String {
        calls.add(name to args); return result
    }
    override fun initAppManager(masterKey: String) = "ok"
    @Volatile private var raw = MEMBER
    @Volatile var recoveryPhase = "queued"
    @Volatile var recoveryClaimId = "renderer-claim"
    @Volatile var recoveredDisclosure: String? = null
    fun publishRecoveredProjection(state: String) { raw = state }
    private val created = AtomicInteger()
    @Volatile var lastCreatedId: String? = null
        private set
    override fun getAppState(): String { reads.incrementAndGet(); return raw }
    fun recoveredPrimary(action: String, ready: Boolean = true) {
        raw = MEMBER.replace("\"primaryAction\":\"showRecovered\"", "\"primaryAction\":\"$action\"")
            .let { if (action == "recover") it.replace(",\"recoveryClaimId\":\"renderer-claim\"", "") else it }
            .let { if (ready) it else it.replace("\"canDelete\":false,\"readiness\":\"ready\"", "\"canDelete\":false,\"readiness\":\"waiting\"") }
    }
    override fun showLocalSecret(secretId: String) = call("local", listOf(secretId), disclosure("local renderer value"))
    override fun showRecovered(claimId: String) = call("recovered", listOf(claimId), recoveredDisclosure
        ?: disclosure("one two three four five six seven eight nine ten eleven twelve"))
    override fun clusterDistributionChecked(name: String, value: String): String {
        val id = "created-${created.incrementAndGet()}"
        lastCreatedId = id
        return call("create", listOf(name, value),
            """{"success":true,"message":{"kind":"create","phase":"queued","secretId":"$id"}}""")
    }
    /** Controlled Core projection only; tests must consume this through an actual Manager physical proof. */
    fun publishCreated(name: String, wordCount: Int? = null) {
        val id = requireNotNull(lastCreatedId)
        val parsed = AppStateModel.fromJson(raw)
        val member = (parsed.getVaultFullInfo() as VaultFullInfo.Member).member
        val metadata = requireNotNull(member.liveboardState).jsonObject.toMutableMap()
        val secrets = metadata.getValue("secrets").jsonArray.filterNot { it.jsonObject["secretId"] == JsonPrimitive(id) }
        metadata["secrets"] = JsonArray(secrets + JsonObject(mapOf("secretId" to JsonPrimitive(id),
            "primaryAction" to JsonPrimitive("showLocal"), "canDelete" to JsonPrimitive(true), "readiness" to JsonPrimitive("ready"))))
        val vault = member.member.vault
        val updated = member.copy(member = member.member.copy(vault = vault.copy(
            secrets = vault.secrets.filterNot { it.id == id } + SecretApiModel(id, name,
                if (wordCount == null) null else "seedPhrase", wordCount))), liveboardState = JsonObject(metadata))
        raw = JsonConfig.json.encodeToString(AppStateModel(Message(State.Vault(VaultFullInfo.Member(updated))), true))
    }
    private fun disclosure(value: String) = """{"success":true,"message":{"secret":"$value"}}"""
    private fun unused(): String = error("Unexpected native route in renderer fixture")
    override fun generateMasterKey() = unused()
    override fun stateEventsAuthToken(vaultName: String) = "renderer-no-network"
    override fun joinResultAuthToken() = unused()
    override fun generateUserCreds(vaultName: String) = unused()
    override fun signUp() = unused()
    override fun updateMembership(candidate: UserData, actionUpdate: String) = unused()
    override fun removeMember(targetDeviceId: String) = unused()
    override fun approveRemoveMember(targetDeviceId: String) = unused()
    override fun declineRemoveMember(targetDeviceId: String) = unused()
    override fun deleteSecret(secretId: String) = unused()
    override fun decideDeleteSecret(requestJson: String) = unused()
    override fun splitSecret(secretName: String, secret: String) = unused()
    override fun findClaim(secretId: String) = unused()
    override fun recover(secretId: String) = unused()
    override fun acceptRecover(claimId: String) = unused()
    override fun declineRecover(claimId: String) = unused()
    override fun sendDeclineCompletion(claimId: String) = unused()
    override fun recoverChecked(secretId: String) = call("recover", listOf(secretId),
        """{"success":true,"message":{"kind":"recover","phase":"$recoveryPhase","secretId":"pass-b","claimId":"$recoveryClaimId"}}""")
    override fun decideJoinRequest(requestJson: String) = unused()
    override fun decideRemoveMember(requestJson: String) = unused()
    override fun deleteSecretChecked(secretId: String) = unused()
    override fun removeMemberChecked(targetDeviceId: String) = call("remove", listOf(targetDeviceId),
        """{"success":true,"message":{"version":1,"kind":"removeDevice","phase":"queued","targetDeviceId":"device-b","requestId":"20bb5e25-598a-4d9b-8cb7-2f942596090a","requestStage":{"source":{"eventId":{"fqdn":{"objType":"DeviceLog","objInstance":"device-a"},"id":{"curr":9007199254740993,"prev":9007199254740992}},"bodyCommitment":"${"ab".repeat(32)}"},"observation":"unconfirmed"},"materialStage":"notStarted","canonicalState":"unknown","readiness":"synchronizing"}}""")
    override fun reconcileProducerReceipt(reference: String) = unused()
    override fun continueLocalRemoveChecked(reference: String) = unused()

    fun declineRemoval() {
        raw = MEMBER.replace("\"operations\":[", """"operations":[{"kind":"removeDevice","requestId":"20bb5e25-598a-4d9b-8cb7-2f942596090a","requesterId":"device-a","targetDeviceId":"device-b","clientStatus":"declined","eligiblePeerIds":[],"canApprove":false,"canDecline":false,"outcome":"declined","resolutionSource":"verified","readiness":"done"},""")
    }
    fun removeOtherDevice() {
        raw = MEMBER.replace(",\"device-b\":{\"member\":{\"userData\":${user("device-b", "Other tablet")}}}", "")
            .replace("\"removableDeviceIds\":[\"device-b\"]", "\"removableDeviceIds\":[]")
    }

    companion object {
        private fun user(id: String, name: String) = """{"device":{"deviceId":"$id","deviceName":"$name","deviceType":"Other","uiCategory":"android","keys":{"dsaPk":"test-dsa","transportPk":"test-transport"}},"vaultName":"Renderer vault"}"""
        private val metadata = """{"schemaVersion":1,"vaultName":"Renderer vault","currentDeviceId":"device-a","storageMode":"copies","canCreateSecret":true,"removableDeviceIds":["device-b"],"secrets":[{"secretId":"pass-a","primaryAction":"showLocal","canDelete":true,"readiness":"ready"},{"secretId":"pass-b","primaryAction":"showRecovered","recoveryClaimId":"renderer-claim","canDelete":false,"readiness":"ready"}],"operations":[{"kind":"join","requestId":"10bb5e25-598a-4d9b-8cb7-2f942596090a","requesterId":"outsider-d","clientStatus":"needApprove","eligiblePeerIds":["device-a"],"canApprove":true,"canDecline":true,"resolutionSource":"unknown","readiness":"waiting"}]}"""
        val MEMBER = """{"success":true,"message":{"state":{"vault":{"member":{"member":{"member":{"userData":${user("device-a", "Current phone")}},"vault":{"vaultName":"Renderer vault","users":{"device-a":{"member":{"userData":${user("device-a", "Current phone")}}},"device-b":{"member":{"userData":${user("device-b", "Other tablet")}}},"outsider-d":{"outsider":{"userData":${user("outsider-d", "New laptop")},"status":"pending"}}},"secrets":[{"id":"pass-a","name":"Local sample"},{"id":"pass-b","name":"Recovered sample","type":"seedPhrase","wordCount":12}]}},"ssClaims":{"claims":{}},"vaultEvents":{"requests":[],"updates":[]},"deletionStates":[],"liveboardState":$metadata}}}}}"""
    }
}

private class AndroidBoardStorage : KeyValueStorageInterface {
    override var isOnboardingCompleted = true
    override var signInInfo: LoginInfo? = null
    override var isWarningVisible = false
    override var secretData: StateFlow<List<Secret>> = MutableStateFlow(emptyList())
    override var deviceData: StateFlow<List<Device>> = MutableStateFlow(emptyList())
    override var isBiometricEnabled = true
    override var cachedDeviceId: String? = null
    override var cachedVaultName: String? = null
    override fun cleanStorage() { secretData = MutableStateFlow(emptyList()); deviceData = MutableStateFlow(emptyList()) }
    override fun resetKeyValueStorage() { cleanStorage(); cachedDeviceId = null; cachedVaultName = null }
    override fun addSecret(secret: Secret) { secretData = MutableStateFlow(secretData.value + secret) }
    override fun removeSecret(secret: Secret) { secretData = MutableStateFlow(secretData.value - secret) }
    override fun syncSecretsFromVault(apiSecrets: List<SecretApiModel>) {
        secretData = MutableStateFlow(apiSecrets.map { Secret(it.id, it.name) })
    }
    override fun addDevice(device: Device) { deviceData = MutableStateFlow(deviceData.value + device) }
    override fun removeDevice(index: Int) { deviceData = MutableStateFlow(deviceData.value.filterIndexed { i, _ -> i != index }) }
}

private class AndroidBoardLogger : DebugLoggerInterface {
    override fun <T : LogTag> log(message: LogTag.Message<T>, extra: String?, success: Boolean?) = Unit
    override fun setLoggerVisibility() = Unit
    override fun testInfo() = Unit
    override fun setAppManagerCreated(created: Boolean) = Unit
    override fun setMasterKeyGenerated(generated: Boolean) = Unit
    override fun setVaultState(state: String?) = Unit
    override fun setDeviceId(deviceId: String?) = Unit
    override fun setClaimsStats(joinRequestsCount: Int, pendingClaimsCount: Int, sentClaimsCount: Int, deliveredClaimsCount: Int) = Unit
    override fun setOuterLoggerVisibility(isVisible: Boolean) = Unit
}
