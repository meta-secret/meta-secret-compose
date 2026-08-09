package core.metaSecretCore

import core.errors.ErrorMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import models.apiModels.AppStateModel
import models.apiModels.ClaimObject
import models.apiModels.ClaimStatusInfo
import models.apiModels.ClientStatus
import models.apiModels.DeviceData
import models.apiModels.DistClaimId
import models.apiModels.DistributionType
import models.apiModels.JsonConfig
import models.apiModels.Message
import models.apiModels.OpenBox
import models.apiModels.PassId
import models.apiModels.SearchClaimMessage
import models.apiModels.SearchClaimModel
import models.apiModels.SsClaims
import models.apiModels.State
import models.apiModels.UserData
import models.apiModels.UserDataMember
import models.apiModels.UserMemberFullInfo
import models.apiModels.UserMembership
import models.apiModels.VaultData
import models.apiModels.VaultFullInfo
import models.apiModels.VaultMember
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import testutils.FakeAppStateCacheProvider
import testutils.FakeDebugLogger
import testutils.FakeMetaSecretCore
import testutils.FakeNotificationCoordinator
import testutils.FakeStringProvider

class MetaSecretSocketHandlerTest {

    private fun buildAppState(currentDeviceId: String, claims: Map<String, ClaimObject>): AppStateModel {
        fun device(id: String) = DeviceData(
            deviceId = id,
            deviceName = id,
            deviceType = "Android",
            keys = OpenBox(dsaPk = "d", transportPk = "t")
        )

        val currentUserData = UserData(device = device(currentDeviceId), vaultName = "v")
        val vaultMember = VaultMember(
            member = UserDataMember(currentUserData),
            vault = VaultData(
                vaultName = "v",
                users = mapOf(currentDeviceId to UserMembership(member = UserDataMember(currentUserData))),
                secrets = emptyList()
            )
        )
        val memberInfo = UserMemberFullInfo(
            member = vaultMember,
            ssClaims = SsClaims(claims = claims)
        )
        return AppStateModel(
            message = Message(state = State.Vault(vault = VaultFullInfo.Member(memberInfo))),
            success = true
        )
    }

    private fun recoverClaim(
        claimId: String,
        secretName: String,
        sender: String,
        receivers: List<String>,
        clientStatus: ClientStatus
    ) = ClaimObject(
        distClaimId = DistClaimId(id = claimId, passId = PassId(id = "$claimId-pass", name = secretName)),
        distributionType = DistributionType.RECOVER,
        id = claimId,
        receivers = receivers,
        sender = sender,
        status = ClaimStatusInfo(statuses = emptyMap()),
        vaultName = "v",
        clientStatus = clientStatus
    )

    private fun newHandler(core: FakeMetaSecretCore): MetaSecretSocketHandler {
        val stringProvider = FakeStringProvider()
        return MetaSecretSocketHandler(
            metaSecretCore = core,
            logger = FakeDebugLogger(),
            notificationCoordinator = FakeNotificationCoordinator(),
            errorMapper = ErrorMapper(stringProvider),
            appStateCacheProvider = FakeAppStateCacheProvider(),
            stringProvider = stringProvider
        )
    }

    @Test
    fun `receiver sees needApprove and gets READY_TO_RECOVER`() = runBlocking {
        val claim = recoverClaim(
            claimId = "claim1",
            secretName = "secret1",
            sender = "senderDevice",
            receivers = listOf("receiverDevice"),
            clientStatus = ClientStatus.NEED_APPROVE
        )
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState("receiverDevice", mapOf("claim1" to claim))
            )
        }
        val handler = newHandler(core)

        handler.refreshAppState()

        val action = withTimeout(8000) {
            handler.socketActionType.first { it is SocketActionModel.READY_TO_RECOVER }
        } as SocketActionModel.READY_TO_RECOVER
        assertEquals("claim1", action.restoreData.claimId)
        assertEquals("secret1", action.restoreData.secretId)
    }

    @Test
    fun `receiver sees done and gets DISMISS_RECOVERY_REQUEST`() = runBlocking {
        val claim = recoverClaim(
            claimId = "claim1",
            secretName = "secret1",
            sender = "senderDevice",
            receivers = listOf("receiverDevice"),
            clientStatus = ClientStatus.DONE
        )
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState("receiverDevice", mapOf("claim1" to claim))
            )
        }
        val handler = newHandler(core)

        val actionDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(8000) {
                handler.socketActions.first { it is SocketActionModel.DISMISS_RECOVERY_REQUEST }
            } as SocketActionModel.DISMISS_RECOVERY_REQUEST
        }
        handler.refreshAppState()
        val action = actionDeferred.await()
        assertEquals(SocketActionModel.DISMISS_RECOVERY_REQUEST, action)
    }

    @Test
    fun `sender sees accepted and gets RECOVER_ACCEPTED`() = runBlocking {
        val trackedClaim = recoverClaim(
            claimId = "claim1",
            secretName = "secret1",
            sender = "senderDevice",
            receivers = listOf("receiverDevice"),
            clientStatus = ClientStatus.PENDING
        )
        val resolvedClaim = trackedClaim.copy(clientStatus = ClientStatus.ACCEPTED)
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState("senderDevice", mapOf("claim1" to trackedClaim))
            )
            findClaimJson = JsonConfig.json.encodeToString(
                SearchClaimModel.serializer(),
                SearchClaimModel(success = true, message = SearchClaimMessage(claim = resolvedClaim))
            )
        }
        val handler = newHandler(core)
        handler.setProcessingSecretName("secret1")

        handler.actionsToFollow(add = listOf(SocketRequestModel.SHOW_SECRET), exclude = null)

        val action = withTimeout(8000) {
            handler.socketActionType.first { it is SocketActionModel.RECOVER_ACCEPTED }
        } as SocketActionModel.RECOVER_ACCEPTED
        assertEquals("claim1", action.claimId)
        assertEquals("secret1", action.secretId)
    }

    @Test
    fun `sender sees declined and gets RECOVER_DECLINED`() = runBlocking {
        val trackedClaim = recoverClaim(
            claimId = "claim1",
            secretName = "secret1",
            sender = "senderDevice",
            receivers = listOf("receiverDevice"),
            clientStatus = ClientStatus.PENDING
        )
        val resolvedClaim = trackedClaim.copy(clientStatus = ClientStatus.DECLINED)
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState("senderDevice", mapOf("claim1" to trackedClaim))
            )
            findClaimJson = JsonConfig.json.encodeToString(
                SearchClaimModel.serializer(),
                SearchClaimModel(success = true, message = SearchClaimMessage(claim = resolvedClaim))
            )
        }
        val handler = newHandler(core)
        handler.setProcessingSecretName("secret1")

        handler.actionsToFollow(add = listOf(SocketRequestModel.SHOW_SECRET), exclude = null)

        val action = withTimeout(8000) {
            handler.socketActionType.first { it is SocketActionModel.RECOVER_DECLINED }
        } as SocketActionModel.RECOVER_DECLINED
        assertEquals("secret1", action.secretId)
    }

    @Test
    fun `sender sees pending and no action is emitted`() = runBlocking {
        val trackedClaim = recoverClaim(
            claimId = "claim1",
            secretName = "secret1",
            sender = "senderDevice",
            receivers = listOf("receiverDevice"),
            clientStatus = ClientStatus.PENDING
        )
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState("senderDevice", mapOf("claim1" to trackedClaim))
            )
            findClaimJson = JsonConfig.json.encodeToString(
                SearchClaimModel.serializer(),
                SearchClaimModel(success = true, message = SearchClaimMessage(claim = trackedClaim))
            )
        }
        val handler = newHandler(core)
        handler.setProcessingSecretName("secret1")

        handler.actionsToFollow(add = listOf(SocketRequestModel.SHOW_SECRET), exclude = null)
        delay(500)

        assertEquals(SocketActionModel.NONE, handler.socketActionType.value)
    }
}
