package core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import models.apiModels.AppStateModel
import testutils.FakeDebugLogger
import testutils.FakeKeyValueStorage

class AppStateCacheSynchronizationTest {
    @Test
    fun `startup and concurrent refresh hide stale cache until all complete`() {
        val cache = AppStateCacheProvider(FakeDebugLogger(), FakeKeyValueStorage(), kotlinx.coroutines.Dispatchers.Unconfined)
        assertTrue(cache.synchronization.value.loading)
        cache.beginSynchronization()
        cache.beginSynchronization()
        cache.finishSynchronization(true)
        assertTrue(cache.synchronization.value.loading)
        cache.finishSynchronization(true)
        assertFalse(cache.synchronization.value.loading)
    }

    @Test
    fun `failed refresh keeps stale UI blocked until retry succeeds`() {
        val cache = AppStateCacheProvider(FakeDebugLogger(), FakeKeyValueStorage(), kotlinx.coroutines.Dispatchers.Unconfined)
        cache.beginSynchronization()
        cache.finishSynchronization(false)
        assertTrue(cache.synchronization.value.failed)
        cache.beginSynchronization()
        assertTrue(cache.synchronization.value.loading)
        cache.finishSynchronization(true)
        assertFalse(cache.synchronization.value.failed)
    }

    @Test
    fun `failed state cannot erase cached identities but successful canonical absence does`() {
        val storage = FakeKeyValueStorage()
        storage.syncSecretsFromVault(listOf(models.apiModels.SecretApiModel("old-id", "same label")))
        val cache = AppStateCacheProvider(FakeDebugLogger(), storage, kotlinx.coroutines.Dispatchers.Unconfined)
        cache.updateCache(AppStateModel(success = false))
        assertEquals(listOf("old-id"), storage.secretData.value.map { it.secretId })
        cache.updateCache(AppStateModel(success = true))
        assertEquals(emptyList(), storage.secretData.value)
    }
}
