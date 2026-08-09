package core.metaSecretCore

import core.errors.ErrorMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import models.apiModels.AppStateModel
import models.apiModels.ClaimObject
import models.apiModels.ClaimStatusInfo
import models.apiModels.ClientStatus
import models.apiModels.DeviceData
import models.apiModels.DistClaimId
import models.apiModels.DistributionType
import models.apiModels.JoinClusterRequest
import models.apiModels.JsonConfig
import models.apiModels.Message
import models.apiModels.OpenBox
import models.apiModels.PassId
import models.apiModels.SsClaims
import models.apiModels.State
import models.apiModels.UserData
import models.apiModels.UserDataMember
import models.apiModels.UserDataOutsider
import models.apiModels.UserDataOutsiderStatus
import models.apiModels.UserMemberFullInfo
import models.apiModels.UserMembership
import models.apiModels.VaultData
import models.apiModels.VaultEvents
import models.apiModels.VaultFullInfo
import models.apiModels.VaultMember
import models.apiModels.VaultRequest
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import testutils.FakeAppStateCacheProvider
import testutils.FakeDebugLogger
import testutils.FakeMetaSecretCore
import testutils.FakeNotificationCoordinator
import testutils.FakeStringProvider

class MetaSecretSocketHandlerInvalidationTest {

    private class FakeSocketClient : MetaSecretSocketClient {
        private val _events = MutableSharedFlow<MetaSecretSocketEvent>(replay = 8, extraBufferCapacity = 8)
        override val events = _events
        var connectCalls = 0
        var disconnectCalls = 0
        var latestSubscription: MetaSecretSocketSubscription? = null
        var failConnect = false

        override fun configure(subscription: MetaSecretSocketSubscription?) {
            latestSubscription = subscription
        }

        override fun connect() {
            connectCalls += 1
            if (failConnect) error("connect failed")
        }

        override fun disconnect() {
            disconnectCalls += 1
        }

        fun emit(event: MetaSecretSocketEvent) {
            _events.tryEmit(event)
        }
    }

    private fun buildAppState(
        currentDeviceId: String = "receiverDevice",
        vaultName: String = "vault1",
        claims: Map<String, ClaimObject> = emptyMap(),
        hasJoinRequest: Boolean = false,
        candidateIsMember: Boolean = false,
    ): AppStateModel {
        fun device(id: String) = DeviceData(
            deviceId = id,
            deviceName = id,
            deviceType = "Android",
            keys = OpenBox(dsaPk = "d", transportPk = "t")
        )

        val currentUserData = UserData(device = device(currentDeviceId), vaultName = vaultName)
        val users = mutableMapOf(currentDeviceId to UserMembership(member = UserDataMember(currentUserData)))
        if (candidateIsMember) {
            val candidateData = UserData(device = device("candidateDevice"), vaultName = vaultName)
            users[candidateData.device.deviceId] = UserMembership(member = UserDataMember(candidateData))
        }
        val vaultMember = VaultMember(
            member = UserDataMember(currentUserData),
            vault = VaultData(
                vaultName = vaultName,
                users = users,
                secrets = emptyList()
            )
        )
        val memberInfo = UserMemberFullInfo(
            member = vaultMember,
            ssClaims = SsClaims(claims = claims),
            vaultEvents = if (hasJoinRequest) {
                VaultEvents(
                    requests = listOf(
                        VaultRequest(
                            joinCluster = JoinClusterRequest(
                                candidate = UserData(
                                    device = device("candidateDevice"),
                                    vaultName = vaultName
                                )
                            )
                        )
                    )
                )
            } else {
                null
            }
        )
        return AppStateModel(
            message = Message(state = State.Vault(vault = VaultFullInfo.Member(memberInfo))),
            success = true
        )
    }

    private fun buildOutsiderAppState(
        currentDeviceId: String = "receiverDevice",
        vaultName: String = "vault1",
        status: UserDataOutsiderStatus = UserDataOutsiderStatus.PENDING
    ): AppStateModel {
        val currentUserData = UserData(
            device = DeviceData(
                deviceId = currentDeviceId,
                deviceName = currentDeviceId,
                deviceType = "Android",
                keys = OpenBox(dsaPk = "d", transportPk = "t")
            ),
            vaultName = vaultName
        )
        return AppStateModel(
            message = Message(
                state = State.Vault(
                    vault = VaultFullInfo.Outsider(
                        UserDataOutsider(userData = currentUserData, status = status)
                    )
                )
            ),
            success = true
        )
    }

    private fun recoverClaim(
        claimId: String,
        secretName: String,
        clientStatus: ClientStatus
    ) = ClaimObject(
        distClaimId = DistClaimId(id = claimId, passId = PassId(id = "$claimId-pass", name = secretName)),
        distributionType = DistributionType.RECOVER,
        id = claimId,
        receivers = listOf("receiverDevice"),
        sender = "senderDevice",
        status = ClaimStatusInfo(statuses = emptyMap()),
        vaultName = "vault1",
        clientStatus = clientStatus
    )

    private fun newHandler(
        core: FakeMetaSecretCore,
        socketClient: FakeSocketClient = FakeSocketClient()
    ): Pair<MetaSecretSocketHandler, FakeSocketClient> {
        val stringProvider = FakeStringProvider()
        val handler = MetaSecretSocketHandler(
            metaSecretCore = core,
            logger = FakeDebugLogger(),
            notificationCoordinator = FakeNotificationCoordinator(),
            errorMapper = ErrorMapper(stringProvider),
            appStateCacheProvider = FakeAppStateCacheProvider(),
            stringProvider = stringProvider,
            socketClient = socketClient
        )
        return handler to socketClient
    }

    private suspend fun waitUntil(timeoutMs: Long = 2000, condition: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!condition()) {
                delay(10)
            }
        }
    }

    @Test
    fun testNoTimerPollingWithoutRefreshTrigger() = runBlocking {
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState()
            )
        }
        val (handler, _) = newHandler(core)

        delay(5500)

        assertEquals(0, core.getAppStateCalls)
    }

    @Test
    fun testStateRefreshProcessesNeedApproveRecoverClaimWithoutRecoverSubscription() = runBlocking {
        val claim = recoverClaim("claim1", "secret1", ClientStatus.NEED_APPROVE)
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState(claims = mapOf("claim1" to claim))
            )
        }
        val (handler, _) = newHandler(core)

        handler.refreshAppState()

        waitUntil {
            handler.socketActionType.value is SocketActionModel.READY_TO_RECOVER
        }
        val action = handler.socketActionType.value as SocketActionModel.READY_TO_RECOVER
        assertEquals("claim1", action.restoreData.claimId)
        assertEquals("secret1", action.restoreData.secretId)
        assertEquals(1, core.getAppStateCalls)
    }

    @Test
    fun testStateRefreshProcessesNeedApproveRecoverClaimWhenSocketConnectFails() = runBlocking {
        val claim = recoverClaim("claim1", "secret1", ClientStatus.NEED_APPROVE)
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState(claims = mapOf("claim1" to claim))
            )
        }
        val (handler, socketClient) = newHandler(core)
        socketClient.failConnect = true

        handler.onAppForeground()

        waitUntil {
            handler.socketActionType.value is SocketActionModel.READY_TO_RECOVER
        }
        val action = handler.socketActionType.value as SocketActionModel.READY_TO_RECOVER
        assertEquals("claim1", action.restoreData.claimId)
        assertEquals("secret1", action.restoreData.secretId)
        assertEquals(1, core.getAppStateCalls)
        assertEquals(1, socketClient.connectCalls)
    }

    @Test
    fun testAppForegroundReconnectsSocketAndRefreshesAppStateOnce() = runBlocking {
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(AppStateModel.serializer(), buildAppState())
        }
        val (handler, socketClient) = newHandler(core)

        handler.onAppForeground()
        waitUntil { core.getAppStateCalls == 1 }
        waitUntil { socketClient.connectCalls == 1 }

        assertEquals(1, socketClient.connectCalls)
        assertEquals(1, core.getAppStateCalls)
        assertEquals("vault1", socketClient.latestSubscription?.vaultName)
        assertEquals("receiverDevice", socketClient.latestSubscription?.deviceId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testWaitingForJoinApproveRefreshesAndSubscribesPendingOutsider() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val core = FakeMetaSecretCore().apply {
                appStateJson = JsonConfig.json.encodeToString(
                    AppStateModel.serializer(),
                    buildOutsiderAppState(currentDeviceId = "androidDevice", vaultName = "vault1")
                )
            }
            val (handler, socketClient) = newHandler(core)

            handler.onAppForeground()
            waitUntil { core.getAppStateCalls == 1 }
            waitUntil { socketClient.latestSubscription?.deviceId == "androidDevice" }

            core.getAppStateCalls = 0

            handler.actionsToFollow(add = listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE), exclude = null)
            waitUntil { core.getAppStateCalls == 1 }
            waitUntil { socketClient.connectCalls >= 1 }
            waitUntil { handler.socketActionType.value == SocketActionModel.JOIN_REQUEST_PENDING }

            assertEquals(1, core.getAppStateCalls)
            assertEquals("vault1", socketClient.latestSubscription?.vaultName)
            assertEquals("androidDevice", socketClient.latestSubscription?.deviceId)
            assertTrue(socketClient.connectCalls >= 1)

            core.appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState(currentDeviceId = "androidDevice", vaultName = "vault1")
            )
            val actionDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(2000) {
                    handler.socketActions.first { it == SocketActionModel.JOIN_REQUEST_ACCEPTED }
                }
            }
            socketClient.emit(MetaSecretSocketEvent.StateInvalidated())

            val action = actionDeferred.await()
            assertEquals(SocketActionModel.JOIN_REQUEST_ACCEPTED, action)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testJoinApproveStopsFollowingAfterAccepted() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val core = FakeMetaSecretCore().apply {
                appStateJson = JsonConfig.json.encodeToString(
                    AppStateModel.serializer(),
                    buildOutsiderAppState(currentDeviceId = "iosDevice", vaultName = "vault1")
                )
            }
            val (handler, socketClient) = newHandler(core)

            handler.actionsToFollow(
                add = listOf(
                    SocketRequestModel.WAIT_FOR_JOIN_APPROVE,
                    SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN
                ),
                exclude = null
            )
            waitUntil { handler.socketActionType.value == SocketActionModel.JOIN_REQUEST_PENDING }

            core.appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState(currentDeviceId = "iosDevice", vaultName = "vault1")
            )
            val acceptedDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(2000) {
                    handler.socketActions.first { it == SocketActionModel.JOIN_REQUEST_ACCEPTED }
                }
            }
            socketClient.emit(MetaSecretSocketEvent.StateInvalidated())
            assertEquals(SocketActionModel.JOIN_REQUEST_ACCEPTED, acceptedDeferred.await())
            assertEquals(SocketActionModel.NONE, handler.socketActionType.value)

            core.appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState(currentDeviceId = "iosDevice", vaultName = "vault1", hasJoinRequest = true)
            )
            socketClient.emit(MetaSecretSocketEvent.StateInvalidated())
            waitUntil { handler.socketActionType.value == SocketActionModel.ASK_TO_JOIN }

            assertEquals(SocketActionModel.ASK_TO_JOIN, handler.socketActionType.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testApprovedJoinRequestClearsAskToJoinAfterInvalidation() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val core = FakeMetaSecretCore().apply {
                appStateJson = JsonConfig.json.encodeToString(
                    AppStateModel.serializer(),
                    buildAppState(hasJoinRequest = true)
                )
            }
            val (handler, socketClient) = newHandler(core)

            handler.actionsToFollow(
                add = listOf(SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN),
                exclude = null
            )
            handler.refreshAppState()
            waitUntil { handler.socketActionType.value == SocketActionModel.ASK_TO_JOIN }

            core.appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState(hasJoinRequest = true, candidateIsMember = true)
            )
            socketClient.emit(MetaSecretSocketEvent.StateInvalidated())

            waitUntil { handler.socketActionType.value == SocketActionModel.NONE }
            assertEquals(SocketActionModel.NONE, handler.socketActionType.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testWaitingForJoinApproveRefreshesAfterSocketErrorFallback() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val core = FakeMetaSecretCore().apply {
                appStateJson = JsonConfig.json.encodeToString(
                    AppStateModel.serializer(),
                    buildOutsiderAppState(currentDeviceId = "iosDevice", vaultName = "vault1")
                )
            }
            val (handler, socketClient) = newHandler(core)

            handler.onAppForeground()
            waitUntil { core.getAppStateCalls == 1 }
            waitUntil { socketClient.latestSubscription?.deviceId == "iosDevice" }

            handler.actionsToFollow(add = listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE), exclude = null)
            waitUntil { handler.socketActionType.value == SocketActionModel.JOIN_REQUEST_PENDING }

            core.appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState(currentDeviceId = "iosDevice", vaultName = "vault1")
            )

            val actionDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(3000) {
                    handler.socketActions.first { it == SocketActionModel.JOIN_REQUEST_ACCEPTED }
                }
            }

            socketClient.emit(MetaSecretSocketEvent.Error("state events failed"))

            val action = actionDeferred.await()
            assertEquals(SocketActionModel.JOIN_REQUEST_ACCEPTED, action)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testAppBackgroundDisconnectsSocketWithoutRefreshingAppState() = runBlocking {
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(AppStateModel.serializer(), buildAppState())
        }
        val (handler, socketClient) = newHandler(core)

        handler.onAppBackground()

        assertEquals(1, socketClient.disconnectCalls)
        assertEquals(0, core.getAppStateCalls)
    }

    @Test
    fun testStateInvalidatedEventsAreCoalescedIntoOneRefresh() = runBlocking {
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(AppStateModel.serializer(), buildAppState())
        }
        val (handler, socketClient) = newHandler(core)

        handler.onAppForeground()
        delay(100)
        core.getAppStateCalls = 0
        socketClient.emit(MetaSecretSocketEvent.StateInvalidated(claimId = "claim1"))
        socketClient.emit(MetaSecretSocketEvent.StateInvalidated(claimId = "claim1"))
        socketClient.emit(MetaSecretSocketEvent.StateInvalidated(claimId = "claim1"))
        delay(400)

        assertEquals(1, core.getAppStateCalls)
    }

    @Test
    fun testSocketInvalidationRefreshesEvenWhenLifecycleFlagIsNotForeground() = runBlocking {
        val claim = recoverClaim("claim1", "secret1", ClientStatus.NEED_APPROVE)
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState(claims = mapOf("claim1" to claim))
            )
        }
        val (handler, socketClient) = newHandler(core)

        socketClient.emit(MetaSecretSocketEvent.StateInvalidated(claimId = "claim1"))

        waitUntil { core.getAppStateCalls == 1 }
        val action = withTimeout(2000) {
            handler.socketActionType.first { it is SocketActionModel.READY_TO_RECOVER }
        } as SocketActionModel.READY_TO_RECOVER
        assertEquals("claim1", action.restoreData.claimId)
        assertEquals("secret1", action.restoreData.secretId)
    }

    @Test
    fun testSocketReconnectRefreshesAppStateOnce() = runBlocking {
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(AppStateModel.serializer(), buildAppState())
        }
        val (handler, socketClient) = newHandler(core)
        handler.onAppForeground()
        waitUntil { core.getAppStateCalls == 1 }
        core.getAppStateCalls = 0
        socketClient.emit(MetaSecretSocketEvent.Connected)
        delay(100)

        assertEquals(1, core.getAppStateCalls)
    }

    @Test
    fun testDoneRecoveryClaimDismissesVisibleClaimAfterInvalidationRefresh() = runBlocking {
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState()
            )
        }
        val (handler, socketClient) = newHandler(core)
        handler.onAppForeground()
        delay(100)

        val claim = recoverClaim("claim1", "secret1", ClientStatus.DONE)
        core.appStateJson = JsonConfig.json.encodeToString(
            AppStateModel.serializer(),
            buildAppState(claims = mapOf("claim1" to claim))
        )
        val dismissAction = async(start = CoroutineStart.UNDISPATCHED) {
            handler.socketActions.first { it is SocketActionModel.DISMISS_RECOVERY_REQUEST }
        }
        socketClient.emit(MetaSecretSocketEvent.StateInvalidated(claimId = "claim1"))

        val action = withTimeout(2000) { dismissAction.await() }
        assertEquals(SocketActionModel.DISMISS_RECOVERY_REQUEST, action)
    }

    @Test
    fun testMultipleDoneRecoveryClaimsEmitSingleDismissAction() = runBlocking {
        val oldClaim = recoverClaim("claim1", "secret1", ClientStatus.DONE)
        val currentClaim = recoverClaim("claim2", "secret1", ClientStatus.DONE)
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState(claims = mapOf("claim1" to oldClaim, "claim2" to currentClaim))
            )
        }
        val (handler, socketClient) = newHandler(core)
        handler.onAppForeground()
        delay(100)

        val dismissAction = async(start = CoroutineStart.UNDISPATCHED) {
            handler.socketActions.first { it is SocketActionModel.DISMISS_RECOVERY_REQUEST }
        }
        socketClient.emit(MetaSecretSocketEvent.StateInvalidated(claimId = null))

        assertEquals(SocketActionModel.DISMISS_RECOVERY_REQUEST, withTimeout(2000) { dismissAction.await() })
    }

    @Test
    fun testNeedApproveClaimEmitsReadyToRecoverOnlyOnceForSameClaimId() = runBlocking {
        val claim = recoverClaim("claim1", "secret1", ClientStatus.NEED_APPROVE)
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState(claims = mapOf("claim1" to claim))
            )
        }
        val (handler, socketClient) = newHandler(core)
        handler.onAppForeground()
        waitUntil { handler.socketActionType.value is SocketActionModel.READY_TO_RECOVER }
        core.getAppStateCalls = 0
        var readyToRecoverEmissions = 0
        val collectionJob = launch {
            handler.socketActionType.collect {
                if (it is SocketActionModel.READY_TO_RECOVER) {
                    readyToRecoverEmissions += 1
                }
            }
        }

        socketClient.emit(MetaSecretSocketEvent.StateInvalidated(claimId = "claim1"))
        val first = withTimeout(2000) {
            handler.socketActionType.first { it is SocketActionModel.READY_TO_RECOVER }
        }
        socketClient.emit(MetaSecretSocketEvent.StateInvalidated(claimId = "claim1"))
        waitUntil { core.getAppStateCalls >= 1 }
        delay(400)
        collectionJob.cancel()

        assertTrue(first is SocketActionModel.READY_TO_RECOVER)
        assertEquals(1, readyToRecoverEmissions)
    }
}
