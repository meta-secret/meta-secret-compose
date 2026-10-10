package core.metaSecretCore

import core.errors.ErrorMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
        var latestJoinResultSubscription: MetaSecretJoinResultSubscription? = null
        var joinResultConnectCalls = 0
        var joinResultDisconnectCalls = 0
        private val _joinResultEvents = MutableSharedFlow<MetaSecretJoinResultEvent>(replay = 8, extraBufferCapacity = 8)
        override val joinResultEvents = _joinResultEvents

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

        override fun configureJoinResult(subscription: MetaSecretJoinResultSubscription?) {
            latestJoinResultSubscription = subscription
        }

        override fun connectJoinResult() {
            joinResultConnectCalls += 1
        }

        override fun disconnectJoinResult() {
            joinResultDisconnectCalls += 1
        }

        fun emit(event: MetaSecretSocketEvent) {
            _events.tryEmit(event)
        }

        fun emitJoinResult(event: MetaSecretJoinResultEvent) {
            _joinResultEvents.tryEmit(event)
        }
    }

    private fun buildAppState(
        currentDeviceId: String = "receiverDevice",
        vaultName: String = "vault1",
        claims: Map<String, ClaimObject> = emptyMap(),
        hasJoinRequest: Boolean = false,
        candidateIsMember: Boolean = false,
        currentMembershipStatus: UserDataOutsiderStatus? = null,
    ): AppStateModel {
        fun device(id: String) = DeviceData(
            deviceId = id,
            deviceName = id,
            deviceType = "Android",
            keys = OpenBox(dsaPk = "d", transportPk = "t")
        )

        val currentUserData = UserData(device = device(currentDeviceId), vaultName = vaultName)
        val currentMembership = currentMembershipStatus?.let { status ->
            UserMembership(outsider = UserDataOutsider(currentUserData, status))
        } ?: UserMembership(member = UserDataMember(currentUserData))
        val users = mutableMapOf(currentDeviceId to currentMembership)
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

    private fun buildLocalAppState(
        currentDeviceId: String = "receiverDevice",
    ): AppStateModel = AppStateModel(
        message = Message(
            state = State.Local(
                local = DeviceData(
                    deviceId = currentDeviceId,
                    deviceName = currentDeviceId,
                    deviceType = "Android",
                    keys = OpenBox(dsaPk = "d", transportPk = "t"),
                )
            )
        ),
        success = true,
    )

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
        socketClient: FakeSocketClient = FakeSocketClient(),
        uiDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ): Pair<MetaSecretSocketHandler, FakeSocketClient> {
        val stringProvider = FakeStringProvider()
        val handler = MetaSecretSocketHandler(
            metaSecretCore = core,
            logger = FakeDebugLogger(),
            notificationCoordinator = FakeNotificationCoordinator(),
            errorMapper = ErrorMapper(stringProvider),
            appStateCacheProvider = FakeAppStateCacheProvider(),
            stringProvider = stringProvider,
            socketClient = socketClient,
            uiDispatcher = uiDispatcher,
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
    fun testPendingJoinUsesDedicatedSseWithoutPollingAndRefreshesOnceOnAccepted() = runBlocking {
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
            assertNull(socketClient.latestSubscription)
            assertEquals(0, socketClient.connectCalls)

            core.getAppStateCalls = 0

            handler.actionsToFollow(add = listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE), exclude = null)
            waitUntil { core.getAppStateCalls == 1 }
            waitUntil { handler.socketActionType.value == SocketActionModel.JOIN_REQUEST_PENDING }

            assertEquals("vault1", socketClient.latestJoinResultSubscription?.vaultName)
            assertEquals(1, socketClient.joinResultConnectCalls)
            assertEquals(0, socketClient.connectCalls)

            // The former implementation performed getAppState() every 2 seconds.
            delay(2_200)
            assertEquals(1, core.getAppStateCalls, "pending join must not poll Core state")

            core.appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState(currentDeviceId = "androidDevice", vaultName = "vault1")
            )
            val actionDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(2000) {
                    handler.socketActions.first { it == SocketActionModel.JOIN_REQUEST_ACCEPTED }
                }
            }
            socketClient.emitJoinResult(MetaSecretJoinResultEvent.Resolved(MetaSecretJoinResultOutcome.ACCEPTED))

            val action = actionDeferred.await()
            assertEquals(SocketActionModel.JOIN_REQUEST_ACCEPTED, action)
            assertEquals(2, core.getAppStateCalls, "one terminal SSE result causes one canonical refresh")
            handler.onAppBackground()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testPendingJoinRefreshesCanonicalStateOnceAfterDeclinedEvent() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val core = FakeMetaSecretCore().apply {
                appStateJson = JsonConfig.json.encodeToString(
                    AppStateModel.serializer(),
                    buildOutsiderAppState(currentDeviceId = "webDevice", status = UserDataOutsiderStatus.PENDING),
                )
            }
            val (handler, socketClient) = newHandler(core)
            handler.onAppForeground()
            waitUntil { core.getAppStateCalls == 1 }
            core.getAppStateCalls = 0
            handler.actionsToFollow(add = listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE), exclude = null)
            waitUntil { handler.socketActionType.value == SocketActionModel.JOIN_REQUEST_PENDING }
            // Allow the initial foreground refresh job to finish before swapping
            // the fake Core response used by the terminal-result refresh.
            delay(50)

            core.appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildOutsiderAppState(currentDeviceId = "webDevice", status = UserDataOutsiderStatus.DECLINED),
            )
            val declined = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(2_000) {
                    handler.socketActions.first { it == SocketActionModel.JOIN_REQUEST_DECLINED }
                }
            }
            socketClient.emitJoinResult(MetaSecretJoinResultEvent.Resolved(MetaSecretJoinResultOutcome.DECLINED))

            assertEquals(SocketActionModel.JOIN_REQUEST_DECLINED, declined.await())
            assertEquals(2, core.getAppStateCalls, "the terminal SSE event should trigger one canonical refresh")
            assertNull(socketClient.latestJoinResultSubscription)
            handler.onAppBackground()
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
            socketClient.emitJoinResult(MetaSecretJoinResultEvent.Resolved(MetaSecretJoinResultOutcome.ACCEPTED))
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
    fun testWaitingForJoinApproveRefreshesAfterSseAcceptedWithoutMemberSocket() = runBlocking {
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
            assertNull(socketClient.latestSubscription)
            assertEquals(0, socketClient.connectCalls)

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

            socketClient.emitJoinResult(MetaSecretJoinResultEvent.Resolved(MetaSecretJoinResultOutcome.ACCEPTED))

            val action = actionDeferred.await()
            assertEquals(SocketActionModel.JOIN_REQUEST_ACCEPTED, action)
            handler.onAppBackground()
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
    fun testResumeRefreshesRetriesInvalidationSkippedWhilePaused() = runBlocking {
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState()
            )
        }
        val (handler, socketClient) = newHandler(core)

        handler.onAppForeground()
        waitUntil { core.getAppStateCalls == 1 }
        core.getAppStateCalls = 0
        handler.pauseRefreshes()

        val claim = recoverClaim("claim-after-resume", "secret1", ClientStatus.NEED_APPROVE)
        core.appStateJson = JsonConfig.json.encodeToString(
            AppStateModel.serializer(),
            buildAppState(claims = mapOf(claim.distClaimId.id to claim))
        )
        socketClient.emit(MetaSecretSocketEvent.StateInvalidated(claimId = claim.distClaimId.id))
        delay(100)
        assertEquals(0, core.getAppStateCalls)

        handler.resumeRefreshes()

        waitUntil {
            handler.pendingRecoveryRequests.value.any { it.claimId == claim.distClaimId.id }
        }
        assertTrue(core.getAppStateCalls >= 1)
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testRecoveryUiStateIsPublishedOnMainDispatcher() = runBlocking {
        val mainDispatcher = StandardTestDispatcher()
        val claim = recoverClaim("claim-main", "secret1", ClientStatus.NEED_APPROVE)
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState(claims = mapOf(claim.distClaimId.id to claim)),
            )
        }
        val (handler) = newHandler(core, uiDispatcher = mainDispatcher)

        handler.onAppForeground()
        waitUntil { core.getAppStateCalls == 1 }
        delay(100)

        // The socket worker has received and parsed the claim, but Compose
        // state remains untouched until the Main dispatcher processes it.
        assertTrue(handler.pendingRecoveryRequests.value.isEmpty())
        assertEquals(SocketActionModel.NONE, handler.socketActionType.value)

        mainDispatcher.scheduler.advanceUntilIdle()
        waitUntil {
            handler.pendingRecoveryRequests.value.any { it.claimId == claim.distClaimId.id }
        }
        assertTrue(handler.socketActionType.value is SocketActionModel.READY_TO_RECOVER)
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
    fun testMemberToDeclinedOutsiderEmitsCurrentDeviceRemovedExactlyOnce() = runBlocking {
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState(currentDeviceId = "removedDevice", vaultName = "vault1")
            )
        }
        val (handler, socketClient) = newHandler(core)
        handler.onAppForeground()
        waitUntil { core.getAppStateCalls == 1 }

        var removalEvents = 0
        val collector = launch {
            handler.socketActions
                .filter { it == SocketActionModel.CURRENT_DEVICE_REMOVED }
                .collect { removalEvents += 1 }
        }
        core.appStateJson = JsonConfig.json.encodeToString(
            AppStateModel.serializer(),
            buildOutsiderAppState(
                currentDeviceId = "removedDevice",
                vaultName = "vault1",
                status = UserDataOutsiderStatus.DECLINED,
            )
        )

        socketClient.emit(MetaSecretSocketEvent.StateInvalidated())
        waitUntil { removalEvents == 1 }
        socketClient.emit(MetaSecretSocketEvent.StateInvalidated())
        waitUntil { core.getAppStateCalls >= 3 }
        delay(100)

        assertEquals(1, removalEvents)
        collector.cancel()
    }

    @Test
    fun testMemberToLocalEmitsCurrentDeviceRemoved() = runBlocking {
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState(currentDeviceId = "removedDevice", vaultName = "vault1")
            )
        }
        val (handler, socketClient) = newHandler(core)
        handler.onAppForeground()
        waitUntil { core.getAppStateCalls == 1 }
        val removalEvent = async(start = CoroutineStart.UNDISPATCHED) {
            handler.socketActions.first { it == SocketActionModel.CURRENT_DEVICE_REMOVED }
        }

        core.appStateJson = JsonConfig.json.encodeToString(
            AppStateModel.serializer(),
            buildLocalAppState(currentDeviceId = "removedDevice")
        )
        socketClient.emit(MetaSecretSocketEvent.StateInvalidated())

        assertEquals(
            SocketActionModel.CURRENT_DEVICE_REMOVED,
            withTimeout(2_000) { removalEvent.await() },
        )
        handler.onAppBackground()
    }

    @Test
    fun testMemberToDifferentLocalDeviceDoesNotEmitCurrentDeviceRemoved() = runBlocking {
        val previousState = buildAppState(currentDeviceId = "memberDevice", vaultName = "vault1")
        val currentState = buildLocalAppState(currentDeviceId = "differentDevice")

        assertEquals(false, isCurrentDeviceRemovalTransition(previousState, currentState))
    }

    @Test
    fun testMemberToDeclinedOwnVaultEntryEmitsCurrentDeviceRemoved() = runBlocking {
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState(currentDeviceId = "removedDevice", vaultName = "vault1")
            )
        }
        val (handler, socketClient) = newHandler(core)
        handler.onAppForeground()
        waitUntil { core.getAppStateCalls == 1 }
        val removalEvent = async(start = CoroutineStart.UNDISPATCHED) {
            handler.socketActions.first { it == SocketActionModel.CURRENT_DEVICE_REMOVED }
        }

        core.appStateJson = JsonConfig.json.encodeToString(
            AppStateModel.serializer(),
            buildAppState(
                currentDeviceId = "removedDevice",
                vaultName = "vault1",
                currentMembershipStatus = UserDataOutsiderStatus.DECLINED,
            )
        )
        socketClient.emit(MetaSecretSocketEvent.StateInvalidated())

        assertEquals(
            SocketActionModel.CURRENT_DEVICE_REMOVED,
            withTimeout(2_000) { removalEvent.await() },
        )
        handler.onAppBackground()
    }

    @Test
    fun testMemberToPendingOwnVaultEntryDoesNotEmitCurrentDeviceRemoved() = runBlocking {
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState(currentDeviceId = "candidateDevice", vaultName = "vault1")
            )
        }
        val (handler, socketClient) = newHandler(core)
        handler.onAppForeground()
        waitUntil { core.getAppStateCalls == 1 }
        var removalEvents = 0
        val collector = launch {
            handler.socketActions
                .filter { it == SocketActionModel.CURRENT_DEVICE_REMOVED }
                .collect { removalEvents += 1 }
        }

        core.appStateJson = JsonConfig.json.encodeToString(
            AppStateModel.serializer(),
            buildAppState(
                currentDeviceId = "candidateDevice",
                vaultName = "vault1",
                currentMembershipStatus = UserDataOutsiderStatus.PENDING,
            )
        )
        socketClient.emit(MetaSecretSocketEvent.StateInvalidated())
        waitUntil { core.getAppStateCalls == 2 }
        delay(100)

        assertEquals(0, removalEvents)
        collector.cancel()
        handler.onAppBackground()
    }

    @Test
    fun testInitialOrPendingOutsiderDoesNotEmitCurrentDeviceRemoved() = runBlocking {
        for (status in listOf(UserDataOutsiderStatus.PENDING, UserDataOutsiderStatus.DECLINED)) {
            val core = FakeMetaSecretCore().apply {
                appStateJson = JsonConfig.json.encodeToString(
                    AppStateModel.serializer(),
                    buildOutsiderAppState(status = status)
                )
            }
            val (handler, _) = newHandler(core)
            var removalEvents = 0
            val collector = launch {
                handler.socketActions
                    .filter { it == SocketActionModel.CURRENT_DEVICE_REMOVED }
                    .collect { removalEvents += 1 }
            }

            handler.onAppForeground()
            waitUntil { core.getAppStateCalls == 1 }
            delay(100)

            assertEquals(0, removalEvents, "initial outsider status=$status must not reset a join flow")
            collector.cancel()
            handler.onAppBackground()
        }
    }

    @Test
    fun testMemberToPendingOutsiderDoesNotEmitCurrentDeviceRemoved() = runBlocking {
        val core = FakeMetaSecretCore().apply {
            appStateJson = JsonConfig.json.encodeToString(
                AppStateModel.serializer(),
                buildAppState(currentDeviceId = "candidateDevice", vaultName = "vault1")
            )
        }
        val (handler, socketClient) = newHandler(core)
        handler.onAppForeground()
        waitUntil { core.getAppStateCalls == 1 }
        var removalEvents = 0
        val collector = launch {
            handler.socketActions
                .filter { it == SocketActionModel.CURRENT_DEVICE_REMOVED }
                .collect { removalEvents += 1 }
        }

        core.appStateJson = JsonConfig.json.encodeToString(
            AppStateModel.serializer(),
            buildOutsiderAppState(
                currentDeviceId = "candidateDevice",
                vaultName = "vault1",
                status = UserDataOutsiderStatus.PENDING,
            )
        )
        socketClient.emit(MetaSecretSocketEvent.StateInvalidated())
        waitUntil { core.getAppStateCalls == 2 }
        delay(100)

        assertEquals(0, removalEvents)
        collector.cancel()
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

        val action = withTimeout(2000) { dismissAction.await() } as SocketActionModel.DISMISS_RECOVERY_REQUEST
        assertEquals("claim1", action.claimId)
    }

    @Test
    fun testMultipleDoneRecoveryClaimsEmitClaimSpecificDismissActions() = runBlocking {
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

        val dismissActions = async(start = CoroutineStart.UNDISPATCHED) {
            handler.socketActions
                .filterIsInstance<SocketActionModel.DISMISS_RECOVERY_REQUEST>()
                .take(2)
                .toList()
        }
        socketClient.emit(MetaSecretSocketEvent.StateInvalidated(claimId = null))

        val claimIds = withTimeout(2000) { dismissActions.await() }.map { it.claimId }.toSet()
        assertEquals(setOf("claim1", "claim2"), claimIds)
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
