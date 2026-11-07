package metasecret.project.com

import androidx.compose.ui.test.junit4.ComposeTestRule
import core.AppStateCacheProviderInterface
import core.VaultSynchronization
import core.metaSecretCore.liveboard.NativeReadFrame
import core.metaSecretCore.liveboard.NativeReadOutcome
import core.metaSecretCore.liveboard.NativeReadScope
import core.metaSecretCore.liveboard.LiveboardKind
import models.apiModels.liveboard.LiveboardStateDecoder
import models.apiModels.*
import org.junit.rules.ExternalResource
import org.koin.core.context.GlobalContext

internal data class LiveboardCanonicalView(val frame: NativeReadFrame, val source: AppStateModel, val member: UserMemberFullInfo)
internal data class LiveboardCanonicalSecret(val scope: NativeReadScope, val deviceId: String, val vaultName: String, val pass: PassId)
internal data class LiveboardCanonicalRecovery(val secret: LiveboardCanonicalSecret, val claimId: String, val afterAttempt: Long)

/** This rule observes the pre-Activity cache only; it never creates or publishes a read frame. */
internal class LiveboardCanonicalLaunchBoundary : ExternalResource() {
    var beforeFrame: NativeReadFrame? = null
        private set
    override fun before() {
        beforeFrame = GlobalContext.getOrNull()?.getOrNull<AppStateCacheProviderInterface>()?.canonicalFrames?.value
    }
}

/** Only an idle successful scoped frame and its identical current cached Member can be evidence. */
internal fun lbCanonicalView(frame: NativeReadFrame?, source: AppStateModel?, sync: VaultSynchronization,
    expectedScope: NativeReadScope? = null): LiveboardCanonicalView? {
    if (frame == null || frame.scope == null || frame.outcome != NativeReadOutcome.SUCCESS || frame.attempt <= 0 ||
        source == null || !source.success || frame.state !== source ||
        expectedScope != null && frame.scope != expectedScope || sync.loading || sync.failed || sync.activeRefreshes != 0) return null
    val member = (source.getVaultFullInfo() as? VaultFullInfo.Member)?.member ?: return null
    val own = member.member.member.userData
    val vault = member.member.vault
    if (own.vaultName != vault.vaultName || own.device.deviceId.isBlank() || member.ssClaims == null ||
        vault.users[own.device.deviceId]?.member?.userData != own) return null
    val claims = member.ssClaims.claims
    if (claims.any { (key, claim) -> key != claim.id } || claims.values.map { it.id }.distinct().size != claims.size) return null
    return LiveboardCanonicalView(frame, source, member)
}

/** Existing cache reads only. Concurrent publication/revocation invalidates the whole observation. */
internal fun lbReadCanonical(expectedScope: NativeReadScope? = null): LiveboardCanonicalView? {
    val cache = GlobalContext.get().get<AppStateCacheProviderInterface>()
    val frame = cache.canonicalFrames.value
    val state = cache.appState.value
    val sync = cache.synchronization.value
    val view = lbCanonicalView(frame, state, sync, expectedScope) ?: return null
    return view.takeIf { cache.canonicalFrames.value === frame && cache.appState.value === state && cache.synchronization.value === sync }
}

internal fun lbCanonicalSecret(view: LiveboardCanonicalView, passId: String, name: String): LiveboardCanonicalSecret? {
    val secrets = view.member.member.vault.secrets
    if (passId.isBlank() || name.isBlank() || secrets.count { it.id == passId } != 1 || secrets.count { it.name == name } != 1 ||
        secrets.none { it.id == passId && it.name == name }) return null
    val own = view.member.member.member.userData
    return LiveboardCanonicalSecret(checkNotNull(view.frame.scope), own.device.deviceId, own.vaultName, PassId(passId, name))
}

private fun selectedView(view: LiveboardCanonicalView, secret: LiveboardCanonicalSecret): Boolean =
    lbCanonicalSecret(view, secret.pass.id, secret.pass.name) == secret

private fun receiverClaim(claim: ClaimObject, secret: LiveboardCanonicalSecret): Boolean =
    claim.distributionType == DistributionType.RECOVER && claim.distClaimId.id == claim.id &&
        claim.distClaimId.passId == secret.pass && claim.vaultName == secret.vaultName &&
        secret.deviceId in claim.receivers && claim.sender != secret.deviceId

internal fun lbCanonicalRecovery(view: LiveboardCanonicalView, secret: LiveboardCanonicalSecret,
    claimId: String, alreadySeen: Set<String>): LiveboardCanonicalRecovery? {
    if (!selectedView(view, secret) || claimId in alreadySeen) return null
    val claim = view.member.ssClaims?.claims?.get(claimId) ?: return null
    if (!receiverClaim(claim, secret) || claim.clientStatus != ClientStatus.NEED_APPROVE) return null
    return LiveboardCanonicalRecovery(secret, claimId, view.frame.attempt)
}

internal fun lbCompletedReceiverRecoveries(view: LiveboardCanonicalView, observed: LiveboardCanonicalRecovery, cycle: Int): Int? {
    if (cycle <= 0 || !selectedView(view, observed.secret) || view.frame.attempt <= observed.afterAttempt) return null
    val claims = view.member.ssClaims?.claims ?: return null
    val exact = claims[observed.claimId] ?: return null
    if (!receiverClaim(exact, observed.secret) || exact.clientStatus != ClientStatus.DONE) return null
    val completed = claims.values.count { receiverClaim(it, observed.secret) && it.clientStatus == ClientStatus.DONE }
    return completed.takeIf { it >= cycle }
}

/** Sender history preserves the old Core lookup; receiver pending comes only from Core's operation permissions. */
internal fun lbNoActiveRecovery(view: LiveboardCanonicalView, secret: LiveboardCanonicalSecret): Boolean {
    if (!selectedView(view, secret)) return false
    val claims = view.member.ssClaims?.claims ?: return false
    val senderActive = claims.values.any { claim ->
        claim.distributionType == DistributionType.RECOVER && claim.distClaimId.passId == secret.pass &&
            claim.sender == secret.deviceId && claim.clientStatus !in setOf(ClientStatus.DONE, ClientStatus.DECLINED)
    }
    val canonical = LiveboardStateDecoder.decode(view.source) ?: return false
    if (canonical.identity.currentDeviceId != secret.deviceId || canonical.identity.vaultName != secret.vaultName) return false
    val incomingPending = canonical.operations.any { operation ->
        operation.identity.kind == LiveboardKind.Recover && operation.identity.secretId == secret.pass.id &&
            operation.identity.requesterId != secret.deviceId && secret.deviceId in operation.permissions.eligiblePeerIds &&
            operation.status.outcome == null && (operation.permissions.canApprove || operation.permissions.canDecline)
    }
    return !senderActive && !incomingPending
}

internal fun lbAfterLaunch(view: LiveboardCanonicalView, before: NativeReadFrame?): Boolean =
    view.frame !== before && (before == null || view.frame.scope != before.scope || view.frame.attempt > before.attempt)

internal fun ComposeTestRule.lbWaitCanonicalSecret(passId: String, name: String): LiveboardCanonicalSecret {
    var selected: LiveboardCanonicalSecret? = null
    waitUntil(180_000) { selected = lbReadCanonical()?.let { lbCanonicalSecret(it, passId, name) }; selected != null }
    return checkNotNull(selected)
}

internal fun ComposeTestRule.lbObserveCanonicalRecovery(secret: LiveboardCanonicalSecret,
    claimId: String, alreadySeen: Set<String>): LiveboardCanonicalRecovery {
    check(claimId !in alreadySeen) { "A previous recovery UUID cannot satisfy another cycle" }
    var observed: LiveboardCanonicalRecovery? = null
    waitUntil(180_000) { observed = lbReadCanonical(secret.scope)?.let { lbCanonicalRecovery(it, secret, claimId, alreadySeen) }; observed != null }
    return checkNotNull(observed)
}
