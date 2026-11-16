package core.registration

import core.KeyChainInterface
import core.errors.ErrorMapper
import core.metaSecretCore.*
import core.metaSecretCore.liveboard.LegacyNativeReadComposition
import core.metaSecretCore.liveboard.NativeManagerPorts
import core.metaSecretCore.liveboard.NativeManagerPresentation
import core.metaSecretCore.liveboard.NativeRegistrationLifecycle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.encodeToString
import models.apiModels.*
import models.appInternalModels.SocketActionModel
import testutils.*

internal class RegistrationSessionFixture {
    val core = RegistrationCoreStub()
    val keys = RegistrationKeysStub()
    val storage = FakeKeyValueStorage()
    val cache = FakeAppStateCacheProvider()
    val socket = RegistrationSocketStub()
    private val logger = FakeDebugLogger()
    private val formatter = FakeLogFormatter()
    private val composition = LegacyNativeReadComposition(core, cache, logger, formatter)
    private val manager = composition.manager(
        NativeManagerPorts(core, keys, storage, logger),
        NativeManagerPresentation(FakeNotificationCoordinator(), ErrorMapper(FakeStringProvider()), formatter, cache),
    )
    val session = CoreRegistrationSession(
        RegistrationCorePorts(core, manager, MetaSecretStateResolver(core, logger, composition.reads, composition.operations), socket),
        RegistrationStoragePorts(keys, storage, cache), NativeRegistrationLifecycle(composition.keyLifecycle(keys)),
    )
    suspend fun restorePending() = restoreIncomplete(UserDataOutsiderStatus.PENDING)
    suspend fun restoreDeclined() = restoreIncomplete(UserDataOutsiderStatus.DECLINED)
    suspend fun restoreIncomplete(status: UserDataOutsiderStatus) {
        check(status in listOf(UserDataOutsiderStatus.PENDING, UserDataOutsiderStatus.DECLINED))
        keys.saveString("master_key", "stored-device-key")
        core.value = core.outsider(status)
        val expected = if (status == UserDataOutsiderStatus.PENDING) RegistrationStatus.Pending else RegistrationStatus.Declined
        check(session.resume().status == expected)
    }
}

internal class RegistrationKeysStub(private val stored: FakeKeyChain = FakeKeyChain()) : KeyChainInterface by stored {
    var clearAllCalls = 0
    var clearAllIsCleanDb: Boolean? = null
    var clearReturns = true
    var retainOnClear = false
    var unreadable = false
    override suspend fun getString(key: String): String? = if (unreadable) null else stored.getString(key)
    override suspend fun clearAll(isCleanDB: Boolean): Boolean {
        clearAllCalls++
        clearAllIsCleanDb = isCleanDB
        if (!clearReturns) return false
        if (retainOnClear) return true
        return stored.clearAll(isCleanDB)
    }
}

internal class RegistrationSocketStub : MetaSecretSocketHandlerInterface by FakeSecretSocketHandler() {
    override val socketActions = MutableSharedFlow<SocketActionModel>()
    var onPause: () -> Unit = {}
    var resumes = 0
    override suspend fun pauseRefreshesAndAwait() { onPause() }
    override fun resumeRefreshes() { resumes++ }
}

internal class RegistrationCoreStub : MetaSecretCoreInterface by FakeMetaSecretCore() {
    var user = UserData(DeviceData("device-id", "Dmitrii phone", "Android", keys = OpenBox("dsa", "transport")), "Family Vault")
    var value: State = State.Local(user.device)
    var exists = true
    var submissions = 0
    var credentialGenerations = 0
    var initializations = 0
    var failInitialization = false
    var failCredentials = false
    var unreadableState = false
    override fun generateMasterKey() = "{\"success\":true,\"message\":\"test-device-key\"}"
    override fun initAppManager(masterKey: String): String {
        initializations++
        if (failInitialization) error("Key unavailable")
        if (masterKey == "test-device-key") value = State.Local(user.device)
        return "ok"
    }
    override fun getAppState() = if (unreadableState) "{\"success\":false}" else json(value)
    override fun generateUserCreds(vaultName: String): String {
        credentialGenerations++
        if (failCredentials) return "{\"success\":false,\"error\":\"offline\"}"
        user = user.copy(vaultName = vaultName)
        value = if (exists) outsider(UserDataOutsiderStatus.NON_MEMBER) else State.Vault(VaultFullInfo.NotExists(user))
        return json(value)
    }
    override fun signUp(): String {
        submissions++
        value = if (exists) outsider(UserDataOutsiderStatus.PENDING) else member()
        return json(value)
    }
    fun outsider(status: UserDataOutsiderStatus) = State.Vault(VaultFullInfo.Outsider(UserDataOutsider(user, status)))
    fun member() = State.Vault(VaultFullInfo.Member(UserMemberFullInfo(
        member = VaultMember(UserDataMember(user), VaultData(user.vaultName)),
    )))
    private fun json(state: State) = JsonConfig.json.encodeToString(AppStateModel(Message(state), true))
}
