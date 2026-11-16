package core.metaSecretCore.liveboard

import core.KeyChainInterface
import core.metaSecretCore.MetaSecretCoreInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import models.apiModels.AppStateModel
import models.apiModels.DeviceData
import models.apiModels.JsonConfig
import models.apiModels.Message
import models.apiModels.OpenBox
import models.apiModels.State
import models.apiModels.UserData
import models.apiModels.UserDataMember
import models.apiModels.UserDataOutsider
import models.apiModels.UserDataOutsiderStatus
import models.apiModels.UserMemberFullInfo
import models.apiModels.VaultData
import models.apiModels.VaultFullInfo
import models.apiModels.VaultMember
import testutils.FakeMetaSecretCore

/** Records real port entries/effects; never marks an owner initialized or fabricates proof. */
internal class NativeRegistrationTrace {
    val calls = MutableStateFlow<List<NativeCall>>(emptyList())
    fun add(name: String, vararg arguments: String) { calls.update { it + NativeCall(name, arguments.toList()) } }
    fun count(name: String) = calls.value.count { it.name == name }
}

internal class NativeRegistrationCore(private val trace: NativeRegistrationTrace) :
    MetaSecretCoreInterface by FakeMetaSecretCore() {
    private val device = DeviceData("registration-device", "Fixture phone", "Android", keys = OpenBox("d", "t"))
    var user = UserData(device, "Fixture Vault")
    val state = MutableStateFlow<State>(State.Local(device))
    var exists = false
    var ambiguousSubmit = false
    var unreadable = false
    private val gates = MutableStateFlow<Map<String, NativeTransitionGate>>(emptyMap())

    fun hold(name: String, gate: NativeTransitionGate) { gates.update { it + (name to gate) } }
    private fun takeGate(name: String): NativeTransitionGate? {
        while (true) {
            val old = gates.value
            val gate = old[name] ?: return null
            if (gates.compareAndSet(old, old - name)) return gate
        }
    }
    fun local() { state.value = State.Local(device) }
    fun outsider(status: UserDataOutsiderStatus): State = State.Vault(
        VaultFullInfo.Outsider(UserDataOutsider(user, status)),
    )
    fun member(): State = State.Vault(VaultFullInfo.Member(UserMemberFullInfo(
        member = VaultMember(UserDataMember(user), VaultData(user.vaultName)),
    )))
    fun json() = if (unreadable) "{\"success\":false}" else JsonConfig.json.encodeToString(
        AppStateModel(Message(state.value), true),
    )
    private fun <T> entered(name: String, arguments: List<String> = emptyList(), body: () -> T): T = runBlocking {
        trace.add("core.$name", *arguments.toTypedArray())
        takeGate(name)?.hold()
        body()
    }
    override fun generateMasterKey() = entered("generate") { "{\"success\":true,\"message\":\"generated-device-key\"}" }
    override fun initAppManager(masterKey: String) = entered("init", listOf(masterKey)) { "ok" }
    override fun getAppState() = entered("read") { json() }
    override fun generateUserCreds(vaultName: String) = entered("creds", listOf(vaultName)) {
        user = user.copy(vaultName = vaultName)
        state.value = if (exists) outsider(UserDataOutsiderStatus.NON_MEMBER)
            else State.Vault(VaultFullInfo.NotExists(user))
        json()
    }
    override fun signUp() = entered("signUp") {
        state.value = if (exists) outsider(UserDataOutsiderStatus.PENDING) else member()
        if (ambiguousSubmit) error("Synthetic response lost after native submission")
        json()
    }
    override fun splitSecret(secretName: String, secret: String) = entered("split", listOf(secretName, secret)) {
        "{\"success\":true}"
    }
}

internal class NativeRegistrationKeys(private val trace: NativeRegistrationTrace) : KeyChainInterface {
    val values = MutableStateFlow<Map<String, String>>(emptyMap())
    var clearReturns = true
    var retainOnClear = false
    var unreadable = false
    var pendingSaveGate: NativeTransitionGate? = null
    override suspend fun saveString(key: String, value: String): Boolean {
        trace.add("key.save.enter", key, value)
        if (key == "pending_vault_email") pendingSaveGate?.also { pendingSaveGate = null }?.hold()
        values.update { it + (key to value) }
        trace.add("key.save", key, value)
        return true
    }
    override suspend fun getString(key: String): String? = if (unreadable) null else values.value[key]
    override suspend fun containsKey(key: String) = values.value.containsKey(key)
    override suspend fun removeKey(key: String): Boolean { values.update { it - key }; return true }
    override suspend fun clearAll(isCleanDB: Boolean): Boolean {
        trace.add("key.clear", isCleanDB.toString())
        if (clearReturns && !retainOnClear) values.value = emptyMap()
        return clearReturns
    }
}
