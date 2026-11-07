package core.metaSecretCore

import core.LogTag
import kotlinx.coroutines.withContext
import models.apiModels.AppStateModel
import models.apiModels.ClaimObject
import models.apiModels.ClientStatus
import models.apiModels.DistributionType
import models.apiModels.SearchClaimModel
import models.apiModels.VaultFullInfo
import models.appInternalModels.RestoreData
import models.appInternalModels.SocketRequestModel
import models.appInternalModels.SocketActionModel
import core.metaSecretCore.liveboard.NativeInvocation
import core.metaSecretCore.liveboard.NativeReadCall
import core.metaSecretCore.liveboard.NativeRefreshLane
import core.metaSecretCore.liveboard.NativeSocketPorts
import core.metaSecretCore.liveboard.NativeSocketRuntime
import kotlinx.coroutines.CancellationException

/** Original claim observation, on the exact facade state/lane with no new workers. */
internal class NativeSocketClaimObserver(
    ports: NativeSocketPorts,
    private val runtime: NativeSocketRuntime,
    private val state: NativeSocketObservationState,
) {
    private val metaSecretCore = ports.core
    private val logger = ports.logger
    private val uiDispatcher = runtime.uiDispatcher
    private val readOwner = state.readOwner
    private fun mayConsumeActualRead(call: NativeReadCall, lane: NativeRefreshLane): Boolean =
        readOwner.mayConsumeActualRead(call) && lane.mayConsume(call)

    suspend fun handleClaims(currentState: AppStateModel, readCall: NativeReadCall,
        lane: NativeRefreshLane) {
        val currentDeviceId = currentState.getCurrentDeviceId() ?: return
        val vaultFullInfo = currentState.getVaultFullInfo()

        if (vaultFullInfo is VaultFullInfo.Member) {
            val claims = vaultFullInfo.member.ssClaims?.claims
            if (claims.isNullOrEmpty()) {
                logger.e2eDiagnostic(
                    "recovery-state membership=member recoverClaims=0 targeted=0 pendingApproval=0 done=0")
                logger.diagnostic(
                    "claims-refresh-empty device=$currentDeviceId " +
                        "vaultInfo=Member ssClaims=${vaultFullInfo.member.ssClaims != null} " +
                        "claimsCount=${claims?.size ?: 0}"
                )
                withContext(uiDispatcher) {
                    if (!mayConsumeActualRead(readCall, lane)) return@withContext
                    state.pendingRecoveryRequests.value = emptyList()
                }
                logger.log(core.LogTag.SocketHandler.Message.NoClaimsFound, success = true)
                return
            }

            logger.log(core.LogTag.SocketHandler.Message.FoundClaims, "${claims.size}", success = true)
            checkRecoverRequest(claims, currentDeviceId, vaultFullInfo.member.member.vault.users, readCall, lane)
            if (state.actionsToFollow.contains(SocketRequestModel.SHOW_SECRET)) {
                checkRecoverSentStatus(readCall, lane)
            }
        } else {
            logger.e2eDiagnostic(
                "recovery-state membership=non-member recoverClaims=0 targeted=0 pendingApproval=0 done=0")
            logger.diagnostic(
                "claims-refresh-no-member device=$currentDeviceId " +
                    "isOutsider=${
                        vaultFullInfo is VaultFullInfo.Outsider
                    } isNotExists=${vaultFullInfo is VaultFullInfo.NotExists}"
            )
            withContext(uiDispatcher) {
                if (!mayConsumeActualRead(readCall, lane)) return@withContext
                state.pendingRecoveryRequests.value = emptyList()
            }
        }
    }

    private suspend fun checkRecoverRequest(
        claims: Map<String, ClaimObject>,
        currentDeviceId: String,
        users: Map<String, models.apiModels.UserMembership>,
        readCall: NativeReadCall,
        lane: NativeRefreshLane,
    ) {
        val recoverClaimsInVault = claims.values.filter {
            it.distributionType == DistributionType.RECOVER
        }
        val recoverClaimsForDevice = recoverClaimsInVault.filter {
            it.receivers.contains(currentDeviceId)
        }
        val doneClaims = recoverClaimsForDevice.filter { it.clientStatus == ClientStatus.DONE }
        val pendingClaims = recoverClaimsForDevice.filter { it.clientStatus == ClientStatus.NEED_APPROVE }
        logger.e2eDiagnostic(
            "recovery-claim-statuses " + recoverClaimsForDevice.joinToString(",") {
                "${it.distClaimId.id}:${it.clientStatus ?: "unknown"}"
            }.ifEmpty { "none" }
        )
        logger.e2eDiagnostic(
            "recovery-state membership=member recoverClaims=${recoverClaimsInVault.size} " +
                "targeted=${recoverClaimsForDevice.size} pendingApproval=${pendingClaims.size} done=${doneClaims.size}"
        )
        if (recoverClaimsForDevice.isNotEmpty()) {
            logger.e2eDiagnostic(
                "recovery claims for device=$currentDeviceId: " +
                    recoverClaimsForDevice.joinToString { claim ->
                        "claim=${claim.distClaimId.id},sender=${claim.sender},client=${claim.clientStatus}," +
                            "receivers=${claim.receivers.joinToString()}"
                    },
            )
        }
        if (recoverClaimsForDevice.isNotEmpty()) {
            logger.log(
                core.LogTag.SocketHandler.Message.ReceiverClaimStatuses,
                recoverClaimsForDevice.joinToString { "${it.distClaimId.id}=${it.clientStatus}" },
                success = true
            )
        }
        val pendingRecoveryRequests = pendingClaims
            .map {
                val rawSenderType = users[it.sender]?.member?.userData?.device?.deviceType
                    ?: users[it.sender]?.outsider?.userData?.device?.deviceType
                    ?: "Other"
                val senderType = when {
                    rawSenderType.equals("iphone", ignoreCase = true) ||
                        rawSenderType.equals("ios", ignoreCase = true) -> "iOS"
                    rawSenderType.equals("android", ignoreCase = true) -> "Android"
                    rawSenderType.equals("web", ignoreCase = true) -> "Web"
                    else -> rawSenderType
                }
                RestoreData(it.distClaimId.id, it.distClaimId.passId.name, it.sender, senderType)
            }
            .sortedBy { it.claimId }

        if (doneClaims.isNotEmpty()) {
            logger.diagnostic(
                "dismiss-signal-emitted reason=receiver-claim-DONE device=$currentDeviceId " +
                    "claims=" + doneClaims.joinToString { it.distClaimId.id }
            )
            logger.log(core.LogTag.SocketHandler.Message.DismissRecoveryRequest, success = true)
        }
        logger.diagnostic(
            "claims-refresh device=$currentDeviceId " +
                "recoverClaims=" + recoverClaimsForDevice.joinToString {
                    "${it.distClaimId.id}:${it.clientStatus}"
                } +
                " pending=" + pendingClaims.joinToString {
                    "${it.distClaimId.id}:${it.distClaimId.passId.name}"
                }
        )
        withContext(uiDispatcher) {
            if (!mayConsumeActualRead(readCall, lane)) return@withContext
            if (doneClaims.isNotEmpty()) {
                state.lastEmittedReadyToRecoverClaimId = null
                doneClaims.forEach { doneClaim ->
                    state.actionEvents.tryEmit(
                        SocketActionModel.DISMISS_RECOVERY_REQUEST(doneClaim.distClaimId.id)
                    )
                }
            }
            state.pendingRecoveryRequests.value = pendingRecoveryRequests
            logger.e2eDiagnostic(
                "recovery-pending-updated claimIds=" +
                    pendingRecoveryRequests.joinToString(",") { it.claimId }.ifEmpty { "none" }
            )

            val firstNeedApproveClaim = pendingClaims.firstOrNull()
            if (firstNeedApproveClaim != null) {
                val claimId = firstNeedApproveClaim.distClaimId.id
                if (claimId != state.lastEmittedReadyToRecoverClaimId) {
                    state.lastEmittedReadyToRecoverClaimId = claimId
                    val restoreData = RestoreData(
                        claimId,
                        firstNeedApproveClaim.distClaimId.passId.name,
                        firstNeedApproveClaim.sender,
                        pendingRecoveryRequests.firstOrNull { it.claimId == claimId }?.senderType ?: "Other",
                    )
                    logger.log(
                        core.LogTag.SocketHandler.Message.ReadyToRecover,
                        "claimId=$claimId secretId=${restoreData.secretId}",
                        success = true,
                    )
                    state.actionType.value = SocketActionModel.READY_TO_RECOVER(restoreData)
                }
            } else {
                state.lastEmittedReadyToRecoverClaimId = null
                logger.log(core.LogTag.SocketHandler.Message.NothingToRecover, success = true)
            }
        }
        logger.log(
            core.LogTag.SocketHandler.Message.ReceiverClaimStatuses,
            "pending=${pendingRecoveryRequests.joinToString { it.claimId }}",
            success = true,
        )
    }

    private suspend fun checkRecoverSentStatus(readCall: NativeReadCall, lane: NativeRefreshLane) {
        val scope = readCall.frame.scope ?: return
        fun isCurrent() = runtime.operations.isCurrent(scope) && mayConsumeActualRead(readCall, lane)
        try {
            val secretName = state.processingSecretName ?: return
            if (!isCurrent()) return
            val searchResult = when (val entered = runtime.operations.invoke(scope) { metaSecretCore.findClaim(
                secretName) }) {
                is NativeInvocation.Value -> entered.value
                is NativeInvocation.Failure -> if (isCurrent()) throw entered.cause else return
                NativeInvocation.Retired -> return
            }
            if (!isCurrent()) return
            val existingClaim = SearchClaimModel.fromJson(searchResult)
            val claim = existingClaim.claim
            val statusesStr = existingClaim.message?.claim?.status?.statuses?.entries
                ?.joinToString { "${it.key}=${it.value.name}" } ?: "{}"
            logger.log(core.LogTag.SocketHandler.Message.RecoverSentStatusClaimStatus,
                "clientStatus=${claim?.clientStatus?.name ?: "null"} statuses=$statusesStr", success = true)
            withContext(uiDispatcher) {
                if (!isCurrent() || state.processingSecretName != secretName) return@withContext
                when (claim?.clientStatus) {
                    ClientStatus.ACCEPTED -> {
                        val claimId = claim.claimId ?: return@withContext
                        logger.log(core.LogTag.SocketHandler.Message.RecoverSentForSecretId,
                            "secretId=$secretName claimId=$claimId", success = true)
                        publishRecoverAction(readCall, secretName, SocketActionModel.RECOVER_ACCEPTED(claimId,
                            secretName), lane)
                    }
                    ClientStatus.DECLINED -> {
                        logger.log(core.LogTag.SocketHandler.Message.RecoverDeclinedForSecretId,
                            "secretId=$secretName", success = true)
                        publishRecoverAction(readCall, secretName, SocketActionModel.RECOVER_DECLINED(secretName), lane)
                    }
                    else -> { }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (isCurrent()) logger.log(LogTag.ShowSecretVM.Message.PresentingFailed, "${t.message}", success = false)
        }
    }

    private fun publishRecoverAction(readCall: NativeReadCall, secretName: String, action: SocketActionModel,
        lane: NativeRefreshLane) {
        fun currentClaim() = mayConsumeActualRead(readCall, lane) && state.processingSecretName == secretName
        if (!currentClaim()) return
        state.actionType.value = SocketActionModel.NONE
        if (!currentClaim()) return
        state.actionType.value = action
        if (currentClaim()) state.processingSecretName = null
    }

}
