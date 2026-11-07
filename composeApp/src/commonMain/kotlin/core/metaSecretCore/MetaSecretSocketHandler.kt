package core.metaSecretCore

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import models.appInternalModels.RestoreData
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import core.LogTag
import core.metaSecretCore.liveboard.NativeCanonicalReadOwner
import core.metaSecretCore.liveboard.NativeSocketPorts
import core.metaSecretCore.liveboard.NativeSocketPresentation
import core.metaSecretCore.liveboard.NativeSocketRuntime
import core.metaSecretCore.liveboard.NativeReadRequirement
import core.metaSecretCore.liveboard.NativeRefreshLane
import core.metaSecretCore.liveboard.NativeRefreshRequest
import core.metaSecretCore.liveboard.NativeRefreshOrigin

class MetaSecretSocketHandler(
    ports: NativeSocketPorts,
    presentation: NativeSocketPresentation,
    private val runtime: NativeSocketRuntime,
    private val readOwner: NativeCanonicalReadOwner,
) : MetaSecretSocketHandlerInterface {
    private val logger = ports.logger
    private val notificationCoordinator = ports.notification
    private val appStateCacheProvider = presentation.cache
    private val socketClient = runtime.client
    private val uiDispatcher = runtime.uiDispatcher
    private val state = NativeSocketObservationState(readOwner)
    override val socketActionType: StateFlow<SocketActionModel> = state.actionType
    override val socketActions: SharedFlow<SocketActionModel> = state.actionEvents
    override val pendingRecoveryRequests: StateFlow<List<RestoreData>> = state.pendingRecoveryRequests
    private val socketScope = CoroutineScope(runtime.socketDispatcher)
    private val claims = NativeSocketClaimObserver(ports, runtime, state)
    private val consumer = NativeSocketStateConsumer(ports, presentation, runtime, state, claims)
    private val refreshLane = NativeRefreshLane(socketScope, appStateCacheProvider, ::searchRequest, readOwner)
    private val isForeground: Boolean get() = refreshLane.isForeground

    init {
        logger.log(core.LogTag.SocketHandler.Message.Init, success = true)
        socketScope.launch {
            socketClient.events.collect { event ->
                val origin = captureRefreshOrigin()
                when (event) {
                    is MetaSecretSocketEvent.Connected -> {
                        logger.e2eDiagnostic("recovery-socket connected")
                        refreshFromSocketReconnect(origin)
                    }
                    is MetaSecretSocketEvent.StateInvalidated -> refreshFromInvalidation(event, origin)
                    is MetaSecretSocketEvent.Disconnected -> Unit
                    is MetaSecretSocketEvent.Error -> {
                        logger.log(
                            core.LogTag.SocketHandler.Message.SocketError,
                            event.message,
                            success = false
                        )
                        refreshFromSocketError(origin)
                    }
                }
            }
        }
        socketScope.launch {
            socketClient.joinResultEvents.collect { event ->
                val origin = captureRefreshOrigin()
                when (event) {
                    is MetaSecretJoinResultEvent.Resolved -> {
                        if (!state.actionsToFollow.contains(SocketRequestModel.WAIT_FOR_JOIN_APPROVE) ||
                            state.joinResultRefreshInFlight) {
                            return@collect
                        }
                        state.joinResultRefreshInFlight = true
                        logger.log(
                            core.LogTag.SocketHandler.Message.WaitingForJoinResponse,
                            "result=${event.outcome}",
                            success = true,
                        )
                        // SSE carries only the outcome. Fetch one canonical state from Core.
                        refreshLane.request(origin)
                    }
                    is MetaSecretJoinResultEvent.Error -> {
                        logger.log(
                            core.LogTag.SocketHandler.Message.SocketError,
                            event.message,
                            success = false,
                        )
                        if (event.terminal && !state.joinResultAuthorizationErrorShown) {
                            state.joinResultAuthorizationErrorShown = true
                            notificationCoordinator.showError(
                                "This pending join request cannot use live approval updates. " +
                                "Ask a vault member to resolve it, then create a new join request."
                            )
                        }
                    }
                }
            }
        }
    }

    override fun actionsToFollow(
        add: List<SocketRequestModel>?,
        exclude: List<SocketRequestModel>?
    ) {
        val origin = captureRefreshOrigin()
        exclude?.let { toExclude ->
            state.actionsToFollow.removeAll(toExclude.toSet())
            if (SocketRequestModel.WAIT_FOR_JOIN_APPROVE in toExclude) {
                stopJoinResultSubscription()
            }
        }
        
        add?.let { toAdd ->
            if (SocketRequestModel.WAIT_FOR_JOIN_APPROVE in toAdd &&
                SocketRequestModel.WAIT_FOR_JOIN_APPROVE !in state.actionsToFollow
            ) {
                state.joinResultRefreshInFlight = false
                state.joinResultAuthorizationErrorShown = false
                state.configuredJoinResultVault = null
            }
            state.actionsToFollow.addAll(toAdd)
            // A fresh vault is created after the app's first launch refresh. At that
            // time there is no vault name yet, so no SSE subscription can be opened.
            // Starting to follow vault state or join requests must therefore refresh
            // immediately and subscribe to the newly created vault.
            val needsImmediateSync = toAdd.any {
                it == SocketRequestModel.SHOW_SECRET ||
                    it == SocketRequestModel.WAIT_FOR_JOIN_APPROVE ||
                    it == SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN ||
                    it == SocketRequestModel.GET_STATE
            }
            if (needsImmediateSync) {
                refreshLane.request(origin)
            }
        }
        logger.log(core.LogTag.SocketHandler.Message.ActualActionsToFollow, "${state.actionsToFollow}", success = true)
    }

    override fun setProcessingSecretName(secretName: String) {
        state.processingSecretName = secretName
    }

    override fun resetReadyToRecoverDedup(claimId: String?) {
        socketScope.launch(uiDispatcher) {
            if (claimId == null || state.lastEmittedReadyToRecoverClaimId == claimId) {
                state.lastEmittedReadyToRecoverClaimId = null
            }
        }
    }

    override fun resetSocketActionType(expected: SocketActionModel?) {
        socketScope.launch(uiDispatcher) {
            if (expected == null || state.actionType.value == expected) {
                state.actionType.value = SocketActionModel.NONE
            }
        }
    }

    override fun onAppLaunch() {
        val expected = runtime.operations.capture()
        readOwner.beforeForeground(expected)
        if (runtime.operations.capture() != expected) return
        val origin = captureRefreshOrigin()
        logger.log(core.LogTag.SocketHandler.Message.AppLaunchRefresh, success = true)
        refreshLane.foreground(origin)
    }

    override fun onAppForeground() {
        val expected = runtime.operations.capture()
        readOwner.beforeForeground(expected)
        if (runtime.operations.capture() != expected) return
        val origin = captureRefreshOrigin()
        // A terminal authorization failure ends this SSE stream. Reconfigure on
        // foreground so a newly minted short-lived token can resume the request.
        state.configuredJoinResultVault = null
        logger.log(core.LogTag.SocketHandler.Message.ForegroundRefresh, success = true)
        refreshLane.foreground(origin)
    }

    override fun onAppBackground() {
        val expected = runtime.operations.capture()
        readOwner.beforeBackground(expected)
        if (runtime.operations.capture() != expected) return
        val isCurrentBackground = refreshLane.background()
        logger.log(core.LogTag.SocketHandler.Message.BackgroundSocketSuspend, success = true)
        if (!isCurrentBackground()) return
        socketClient.disconnect()
        if (!isCurrentBackground()) return
        socketClient.disconnectJoinResult()
        if (isCurrentBackground()) state.configuredJoinResultVault = null
    }

    override fun refreshAppState() {
        refreshLane.request(captureRefreshOrigin())
    }

    override fun refreshAppState(required: NativeReadRequirement) {
        if (!runtime.operations.isCurrent(required.scope)) return
        refreshLane.request(refreshLane.capture(required.scope))
    }

    private fun refreshFromSocketReconnect(origin: NativeRefreshOrigin) {
        logger.log(core.LogTag.SocketHandler.Message.SocketReconnectRefresh, success = true)
        refreshLane.request(origin)
    }

    private fun refreshFromInvalidation(event: MetaSecretSocketEvent.StateInvalidated, origin: NativeRefreshOrigin) {
        logger.e2eDiagnostic(
            "recovery-invalidation received scope=${event.scope ?: "unknown"} " +
                "claimId=${event.claimId ?: "none"} outcome=${event.decisionOutcome ?: "none"}"
        )
        logger.log(
            core.LogTag.SocketHandler.Message.StateInvalidated,
            "scope=${event.scope} claimId=${event.claimId}",
            success = true,
        )
        logger.e2eDiagnostic(
            "recovery-refresh-start source=invalidation scope=${event.scope ?: "unknown"} " +
                "claimId=${event.claimId ?: "none"}"
        )
        // Do not cancel an in-flight sync: cancellation can drop the refresh entirely.
        // The same lane coalesces a follow-up without replacing the active flight.
        refreshLane.request(origin)
    }

    private fun refreshFromSocketError(origin: NativeRefreshOrigin) {
        if (!isForeground) return
        refreshLane.request(origin)
    }

    private fun captureRefreshOrigin() = refreshLane.capture(runtime.operations.capture())

    private suspend fun searchRequest(request: NativeRefreshRequest): Boolean =
        consumer.searchRequest(request, refreshLane)

    private fun stopJoinResultSubscription() = consumer.stopJoinResultSubscription()

    override suspend fun pauseRefreshesAndAwait() {
        refreshLane.pauseAndAwait()
    }

    override fun pauseRefreshes() {
        logger.log(LogTag.SocketHandler.Message.RefreshesPaused, success = true)
        refreshLane.pause()
    }

    override fun resumeRefreshes() {
        val origin = captureRefreshOrigin()
        logger.log(LogTag.SocketHandler.Message.RefreshesResumed, success = true)
        // An invalidation may arrive while a secret action pauses refreshes. The
        // paused request is intentionally skipped, so retry once on resume while
        // the app is visible to avoid leaving pending requests stale.
        refreshLane.resume(origin)
    }
}
