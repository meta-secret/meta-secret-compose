package core.metaSecretCore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import models.apiModels.*
import models.appInternalModels.AppErrors
import testutils.FakeDebugLogger
import testutils.FakeMetaSecretCore

class MetaSecretRegistrationResolverTest {
    private val user = UserData(
        device = DeviceData(deviceId = "device-id", deviceName = "Dmitrii phone", deviceType = "Android", keys = OpenBox("dsa", "transport")),
        vaultName = "test@example.org",
    )

    private fun state(value: State): String = JsonConfig.json.encodeToString(
        AppStateModel(message = Message(value), success = true)
    )

    private fun outsider(status: UserDataOutsiderStatus) = State.Vault(
        VaultFullInfo.Outsider(UserDataOutsider(userData = user, status = status))
    )

    private fun member() = State.Vault(VaultFullInfo.Member(UserMemberFullInfo(
        member = VaultMember(UserDataMember(user), VaultData(vaultName = user.vaultName))
    )))

    private inner class CoreStub : MetaSecretCoreInterface by FakeMetaSecretCore() {
        var initial = state(State.Local(user.device))
        var credentials = state(State.Vault(VaultFullInfo.NotExists(user)))
        var registration = state(member())
        val requestedNames = mutableListOf<String>()
        var registrations = 0
        override fun getAppState() = initial
        override fun generateUserCreds(vaultName: String): String {
            requestedNames.add(vaultName)
            return credentials
        }
        override fun signUp(): String {
            registrations++
            return registration
        }
    }

    private fun resolver(core: CoreStub) = MetaSecretStateResolver(core, FakeDebugLogger())

    @Test fun `continue without preparation never calls Core signup`() = runTest {
        val core = CoreStub()
        assertEquals(AppErrors.SignUpError, resolver(core).continueSignUp().error)
        assertEquals(0, core.registrations)
    }

    @Test fun `prepare existing membership does not generate new credentials`() = runTest {
        val core = CoreStub().apply { initial = state(member()) }
        assertEquals(AppErrors.CreateLocalError, resolver(core).prepareSignUp(user.vaultName).error)
        assertEquals(emptyList(), core.requestedNames)
    }

    @Test fun `malformed initial state prevents signup preparation`() = runTest {
        val core = CoreStub().apply { initial = "not-json" }
        val resolver = resolver(core)
        assertEquals(AppErrors.CreateLocalError, resolver.prepareSignUp(user.vaultName).error)
        assertEquals(AppErrors.SignUpError, resolver.continueSignUp().error)
        assertEquals(0, core.registrations)
    }

    @Test fun `credential failure prevents continuation`() = runTest {
        val core = CoreStub().apply { credentials = "{\"success\":false,\"error\":\"offline\"}" }
        val resolver = resolver(core)
        assertEquals(AppErrors.CredsGenerationError, resolver.prepareSignUp(user.vaultName).error)
        assertEquals(AppErrors.SignUpError, resolver.continueSignUp().error)
        assertEquals(0, core.registrations)
    }

    @Test fun `Core not exists permits creation with exact selected name`() = runTest {
        val core = CoreStub()
        val result = resolver(core).prepareSignUp(user.vaultName)
        assertEquals(VaultAvailability.AVAILABLE, result.availability)
        assertNull(result.error)
        assertEquals(listOf(user.vaultName), core.requestedNames)
        assertEquals(0, core.registrations)
    }

    @Test fun `outsider statuses offer explicit join without sending request`() = runTest {
        for (status in UserDataOutsiderStatus.entries) {
            val core = CoreStub().apply { credentials = state(outsider(status)) }
            assertEquals(VaultAvailability.EXISTS, resolver(core).prepareSignUp(user.vaultName).availability)
            assertEquals(0, core.registrations)
        }
    }

    @Test fun `successful membership consumes preparation and prevents duplicate signup`() = runTest {
        val core = CoreStub()
        val resolver = resolver(core)
        resolver.prepareSignUp(user.vaultName)
        assertIs<MemberState>(resolver.continueSignUp().appState)
        assertEquals(AppErrors.SignUpError, resolver.continueSignUp().error)
        assertEquals(1, core.registrations)
    }

    @Test fun `pending result is adapted from Core outsider state`() = runTest {
        val core = CoreStub().apply {
            credentials = state(outsider(UserDataOutsiderStatus.NON_MEMBER))
            registration = state(outsider(UserDataOutsiderStatus.PENDING))
        }
        val resolver = resolver(core)
        resolver.prepareSignUp(user.vaultName)
        assertIs<OutsiderState>(resolver.continueSignUp().appState)
        assertEquals(1, core.registrations)
    }

    @Test fun `signup error preserves Core explanation`() = runTest {
        val core = CoreStub().apply {
            registration = "{\"success\":false,\"error\":\"Vault supports at most 3 devices\"}"
        }
        val resolver = resolver(core)
        resolver.prepareSignUp(user.vaultName)
        val result = resolver.continueSignUp()
        assertEquals(AppErrors.SignUpError, result.error)
        assertEquals("Vault supports at most 3 devices", result.errorMessage)
        assertNull(result.appState)
    }

    @Test fun `clearing preparation prevents old attempt from continuing`() = runTest {
        val core = CoreStub()
        val resolver = resolver(core)
        resolver.prepareSignUp(user.vaultName)
        resolver.clearPreparedSignUp()
        assertEquals(AppErrors.SignUpError, resolver.continueSignUp().error)
        assertEquals(0, core.registrations)
        resolver.prepareSignUp(user.vaultName)
        assertIs<MemberState>(resolver.continueSignUp().appState)
        assertEquals(1, core.registrations)
    }
}
