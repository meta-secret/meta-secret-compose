package ui.scenes.secretsscreen

import androidx.lifecycle.viewModelScope
import core.AppStateCacheProviderInterface
import core.VaultSynchronization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import models.apiModels.*
import models.appInternalModels.SocketActionModel
import testutils.FakeKeyValueStorage
import testutils.FakeScreenMetricsProvider
import testutils.FakeSecretSocketHandler
import testutils.FakeVaultStatsProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SecretPrimaryActionSnapshotTest {
    private val secret = SecretApiModel("opaque-current", "same label")

    private fun claim(
        id: String = "exact-claim",
        pass: SecretApiModel = secret,
        sender: String = "current-device",
        vault: String = "vault",
        type: DistributionType = DistributionType.RECOVER,
        status: ClientStatus? = ClientStatus.ACCEPTED,
    ) = ClaimObject(DistClaimId("distribution", PassId(pass.id, pass.name)), type, id,
        sender = sender, status = ClaimStatusInfo(), vaultName = vault, clientStatus = status)

    private fun state(
        secrets: List<SecretApiModel> = listOf(secret),
        claims: List<ClaimObject> = listOf(claim()),
    ): AppStateModel {
        val user = UserData(DeviceData("current-device", "device", "Android", keys = OpenBox("dsa", "transport")), "vault")
        val member = UserMemberFullInfo(VaultMember(UserDataMember(user), VaultData("vault", secrets = secrets)),
            SsClaims(claims.associateBy { it.id }))
        return AppStateModel(Message(State.Vault(VaultFullInfo.Member(member))), success = true)
    }

    @Test
    fun `accepted Core status exposes exact claim using opaque Secret ID`() {
        assertEquals(mapOf(secret.id to SecretPrimaryActionState(SecretPrimaryAction.Show, "exact-claim")),
            secretPrimaryActionsFromState(state()))
    }

    @Test
    fun `same label recreated with a new ID cannot inherit an accepted claim`() {
        val recreated = secret.copy(id = "opaque-recreated")
        val actions = secretPrimaryActionsFromState(state(secrets = listOf(recreated)))
        assertEquals(SecretPrimaryAction.Recover, actions.getValue(recreated.id).action)
        assertFalse(actions.containsKey(secret.id))
        assertFalse(actions.containsKey(secret.name))
    }

    @Test
    fun `other sender Vault distribution and exact name mismatch cannot expose Show`() {
        val unrelated = listOf(claim(sender = "other-device"), claim(vault = "other-vault"),
            claim(type = DistributionType.SPLIT), claim(pass = secret.copy(name = "other label")))
        unrelated.forEach {
            assertEquals(SecretPrimaryAction.Recover, secretPrimaryActionsFromState(state(claims = listOf(it))).getValue(secret.id).action)
        }
    }

    @Test
    fun `accepted plus pending active claims are ambiguous and fail closed`() {
        assertEquals(SecretPrimaryAction.Recover, secretPrimaryActionsFromState(state(claims = listOf(
            claim(), claim(id = "pending-claim", status = ClientStatus.PENDING)))).getValue(secret.id).action)
    }

    @Test
    fun `completed and declined history do not override one accepted active claim`() {
        val actions = secretPrimaryActionsFromState(state(claims = listOf(claim(),
            claim(id = "completed", status = ClientStatus.DONE), claim(id = "declined", status = ClientStatus.DECLINED))))
        assertEquals("exact-claim", actions.getValue(secret.id).acceptedClaimId)
    }

    @Test
    fun `missing Core status or exact claim ID cannot expose Show`() {
        listOf(claim(status = null), claim(id = ""), claim(status = ClientStatus.NEED_APPROVE)).forEach {
            assertEquals(SecretPrimaryAction.Recover, secretPrimaryActionsFromState(state(claims = listOf(it))).getValue(secret.id).action)
        }
    }

    @Test
    fun `null failed and nonMember snapshots cannot retain accepted actions`() {
        listOf(null, state().copy(success = false), AppStateModel(success = true)).forEach {
            assertEquals(emptyMap(), secretPrimaryActionsFromState(it))
        }
    }

    @Test
    fun `snapshot replacement clears stale Show without extending canonical synchronization`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cache = CountingCache()
        val socket = FakeSecretSocketHandler()
        val stats = FakeVaultStatsProvider()
        val vm = SecretsScreenViewModel(FakeKeyValueStorage(), FakeScreenMetricsProvider(), socket, stats, cache)
        try {
            cache.beginSynchronization()
            cache.updateCache(state())
            advanceUntilIdle()
            assertEquals("exact-claim", vm.primaryActions.value.getValue(secret.id).acceptedClaimId)
            repeat(5) { socket.socketActions.emit(SocketActionModel.UPDATE_STATE) }
            advanceUntilIdle()
            assertEquals(1, cache.begins)
            assertEquals(0, cache.finishes)
            assertEquals(0, stats.refreshCalls)
            cache.finishSynchronization(true)
            assertFalse(vm.synchronization.value.loading)
            val recreated = secret.copy(id = "new-id")
            cache.updateCache(state(secrets = listOf(recreated)))
            advanceUntilIdle()
            assertEquals(mapOf(recreated.id to SecretPrimaryActionState(SecretPrimaryAction.Recover)), vm.primaryActions.value)
            cache.updateCache(state().copy(success = false))
            advanceUntilIdle()
            assertEquals(emptyMap(), vm.primaryActions.value)
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private class CountingCache : AppStateCacheProviderInterface {
        override val appState = MutableStateFlow<AppStateModel?>(null)
        override val synchronization = MutableStateFlow(VaultSynchronization())
        var begins = 0
        var finishes = 0
        override fun beginSynchronization() {
            begins++
            synchronization.value = VaultSynchronization(loading = true, activeRefreshes = 1)
        }
        override fun finishSynchronization(success: Boolean) {
            finishes++
            synchronization.value = VaultSynchronization(failed = !success)
        }
        override fun updateCache(state: AppStateModel) { appState.value = state }
        override fun clearCache() { appState.value = null }
    }
}
