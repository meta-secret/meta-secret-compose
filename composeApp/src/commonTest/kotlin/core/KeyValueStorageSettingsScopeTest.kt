package core

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.Settings
import com.russhwolf.settings.serialization.decodeValue
import com.russhwolf.settings.serialization.encodeValue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalSettingsApi::class, ExperimentalSerializationApi::class)
class KeyValueStorageSettingsScopeTest {
    @Test
    fun testEagerConstructionAndOperationsUseOneInjectedSettingsInstance() {
        val supplied = MemorySettings()
        val initial = listOf(Secret("opaque-a", "fixture-a"))
        supplied.encodeValue(ListSerializer(Secret.serializer()), StorageKeys.SECRET_DATA.key, initial)
        var calls = 0
        val storage = KeyValueStorageImpl(TestDeviceInfo()) { calls += 1; supplied }

        assertEquals(1, calls, "Eager construction must resolve the supplied factory once")
        assertTrue(storage.secretData.value == initial, "Eager state must come from supplied Settings")
        storage.isOnboardingCompleted = true
        storage.cachedVaultName = "fixture"
        storage.cachedDeviceId = "fixture-device"
        storage.addSecret(Secret("opaque-b", "fixture-b"))

        assertTrue(supplied.getBoolean(StorageKeys.ONBOARDING_INFO.key, false))
        assertTrue(supplied.getStringOrNull(StorageKeys.CACHED_VAULT_NAME.key) == "fixture")
        assertTrue(supplied.getStringOrNull(StorageKeys.CACHED_DEVICE_ID.key) == "fixture-device")
        val persisted = supplied.decodeValue(
            ListSerializer(Secret.serializer()), StorageKeys.SECRET_DATA.key, defaultValue = emptyList(),
        )
        assertTrue(persisted == storage.secretData.value && persisted.size == 2,
            "Public operations must persist into the same supplied instance")
        storage.cachedDeviceId = null
        assertTrue(!supplied.hasKey(StorageKeys.CACHED_DEVICE_ID.key))
        assertEquals(1, calls, "Operations must not resolve a second/default Settings instance")
    }

    @Test
    fun testCleanStorageClearsOnlySuppliedScopeAndPreservesIndependentSentinel() {
        val supplied = MemorySettings()
        val legacy = MemorySettings()
        supplied.putString("sentinel", "owned-fixture")
        legacy.putString("sentinel", "legacy-fixture")
        val storage = KeyValueStorageImpl(TestDeviceInfo()) { supplied }
        storage.isBiometricEnabled = true

        storage.cleanStorage()

        assertTrue(supplied.keys.isEmpty(), "Only supplied scope must be cleared")
        assertTrue(legacy.size == 1 && legacy.getStringOrNull("sentinel") == "legacy-fixture",
            "An independent legacy Settings sentinel must survive")
    }

    @Test
    fun testFactoryFailurePropagatesDuringEagerConstructionWithoutFallback() {
        val expected = IllegalStateException("controlled factory failure")
        val device = TestDeviceInfo()
        var calls = 0
        val actual = assertFailsWith<IllegalStateException> {
            KeyValueStorageImpl(device) { calls += 1; throw expected }
        }
        assertSame(expected, actual)
        assertEquals(1, calls)
        assertEquals(0, device.makeCalls, "Factory failure must precede later initialization")
    }

    // Compile-only compatibility: never invoke platform default Settings() without its context.
    fun legacyDefaultConstructorCompiles(device: DeviceInfoProviderInterface): KeyValueStorageImpl =
        KeyValueStorageImpl(device)
}

private class TestDeviceInfo : DeviceInfoProviderInterface {
    var makeCalls = 0
    override fun getAppVersion() = "fixture"
    override fun getDeviceMake(): String { makeCalls += 1; return "fixture" }
    override fun getDeviceId() = "fixture-device"
}

private class MemorySettings : Settings {
    private val values = mutableMapOf<String, Any>()
    override val keys: Set<String> get() = values.keys.toSet()
    override val size: Int get() = values.size
    override fun clear() = values.clear()
    override fun remove(key: String) { values.remove(key) }
    override fun hasKey(key: String) = values.containsKey(key)
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun getInt(key: String, defaultValue: Int) = getIntOrNull(key) ?: defaultValue
    override fun getIntOrNull(key: String) = values[key] as? Int
    override fun putLong(key: String, value: Long) { values[key] = value }
    override fun getLong(key: String, defaultValue: Long) = getLongOrNull(key) ?: defaultValue
    override fun getLongOrNull(key: String) = values[key] as? Long
    override fun putString(key: String, value: String) { values[key] = value }
    override fun getString(key: String, defaultValue: String) = getStringOrNull(key) ?: defaultValue
    override fun getStringOrNull(key: String) = values[key] as? String
    override fun putFloat(key: String, value: Float) { values[key] = value }
    override fun getFloat(key: String, defaultValue: Float) = getFloatOrNull(key) ?: defaultValue
    override fun getFloatOrNull(key: String) = values[key] as? Float
    override fun putDouble(key: String, value: Double) { values[key] = value }
    override fun getDouble(key: String, defaultValue: Double) = getDoubleOrNull(key) ?: defaultValue
    override fun getDoubleOrNull(key: String) = values[key] as? Double
    override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    override fun getBoolean(key: String, defaultValue: Boolean) = getBooleanOrNull(key) ?: defaultValue
    override fun getBooleanOrNull(key: String) = values[key] as? Boolean
}
