package metasecret.project.com

import core.VaultSynchronization
import core.metaSecretCore.liveboard.NativeOwnerEpoch
import core.metaSecretCore.liveboard.NativeOwnerToken
import core.metaSecretCore.liveboard.NativeReadFrame
import core.metaSecretCore.liveboard.NativeReadOutcome
import core.metaSecretCore.liveboard.NativeReadScope
import models.apiModels.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/** Controlled typed cache inputs only: not actual native read proof, Core execution or OS/UI E2E. */
class LiveboardCanonicalTestActionsContractTest {
    private val scope = NativeReadScope(NativeOwnerToken(), NativeOwnerEpoch())
    private val pass = PassId("pass-selected", "Selected Secret")
    private val idle = VaultSynchronization()

    @Test fun currentSuccessfulMemberKeepsExactPassAndReceiver() {
        val state = state(claim("current", ClientStatus.NEED_APPROVE))
        val frame = frame(state)
        val view = checkNotNull(lbCanonicalView(frame, state, idle, scope))
        val selected = checkNotNull(lbCanonicalSecret(view, pass.id, pass.name))
        assertEquals("receiver", selected.deviceId)
        assertEquals(pass, selected.pass)
        assertSame(frame, view.frame)
        assertSame(state, view.source)
    }

    @Test fun nullFailedRetiredBusyAndDetachedFramesAreNotProof() {
        val state = state()
        val frame = frame(state)
        assertNull(lbCanonicalView(null, state, idle, scope))
        assertNull(lbCanonicalView(frame.copy(outcome = NativeReadOutcome.FAILED), state, idle, scope))
        assertNull(lbCanonicalView(frame.copy(outcome = NativeReadOutcome.RETIRED), state, idle, scope))
        assertNull(lbCanonicalView(frame.copy(scope = null), state, idle, scope))
        assertNull(lbCanonicalView(frame, state.copy(), idle, scope))
        assertNull(lbCanonicalView(frame, state, VaultSynchronization(loading = true), scope))
        assertNull(lbCanonicalView(frame, state, VaultSynchronization(failed = true), scope))
        assertNull(lbCanonicalView(frame, state, VaultSynchronization(activeRefreshes = 1), scope))
    }

    @Test fun foreignOwnerAndFailedResponseCannotProvideEvidence() {
        val state = state()
        val other = NativeReadScope(NativeOwnerToken(), NativeOwnerEpoch())
        assertNull(lbCanonicalView(frame(state), state, idle, other))
        val failed = state.copy(success = false)
        assertNull(lbCanonicalView(frame(failed), failed, idle, scope))
    }

    @Test fun selectedPassRequiresBothOpaqueIdAndExactName() {
        val view = view(state())
        assertNull(lbCanonicalSecret(view, "foreign-pass", pass.name))
        assertNull(lbCanonicalSecret(view, pass.id, "Different Secret"))
        val original = state()
        val member = (original.getVaultFullInfo() as VaultFullInfo.Member).member
        val duplicate = member.copy(member = member.member.copy(vault = member.member.vault.copy(
            secrets = listOf(SecretApiModel(pass.id, pass.name), SecretApiModel("other-id", pass.name)))))
        assertNull(lbCanonicalSecret(view(replaceMember(original, duplicate)), pass.id, pass.name))
    }

    @Test fun exactPendingClaimMustBecomeDoneInALaterFrameAndRetainCount() {
        val pending = view(state(claim("older", ClientStatus.DONE), claim("current", ClientStatus.NEED_APPROVE)))
        val selected = checkNotNull(lbCanonicalSecret(pending, pass.id, pass.name))
        val observed = checkNotNull(lbCanonicalRecovery(pending, selected, "current", emptySet()))
        val doneState = state(claim("older", ClientStatus.DONE), claim("current", ClientStatus.DONE))
        val done = view(doneState, 2)
        assertEquals(2, lbCompletedReceiverRecoveries(done, observed, 2))
        assertNull(lbCompletedReceiverRecoveries(done, observed, 3))
        assertNull(lbCompletedReceiverRecoveries(view(doneState, 1), observed, 2))
    }

    @Test fun olderDoneClaimsCannotFulfilCurrentUuid() {
        val pending = view(state(claim("older", ClientStatus.DONE), claim("current", ClientStatus.NEED_APPROVE)))
        val selected = checkNotNull(lbCanonicalSecret(pending, pass.id, pass.name))
        val observed = checkNotNull(lbCanonicalRecovery(pending, selected, "current", emptySet()))
        assertNull(lbCompletedReceiverRecoveries(view(pending.source, 2), observed, 1))
        assertNull(lbCompletedReceiverRecoveries(view(state(claim("older", ClientStatus.DONE)), 2), observed, 1))
        assertNull(lbCanonicalRecovery(pending, selected, "older", emptySet()))
        assertNull(lbCanonicalRecovery(pending, selected, "current", setOf("current")))
    }

    @Test fun wrongReceiverPassTypeAndClaimIdentityCannotCompleteCurrentRequest() {
        val pending = view(state(claim("current", ClientStatus.NEED_APPROVE)))
        val selected = checkNotNull(lbCanonicalSecret(pending, pass.id, pass.name))
        val observed = checkNotNull(lbCanonicalRecovery(pending, selected, "current", emptySet()))
        val done = claim("current", ClientStatus.DONE)
        for (invalid in listOf(
            done.copy(receivers = listOf("another-receiver")),
            done.copy(distClaimId = done.distClaimId.copy(passId = PassId("foreign", pass.name))),
            done.copy(distClaimId = done.distClaimId.copy(passId = PassId(pass.id, "Different Secret"))),
            done.copy(distributionType = DistributionType.SPLIT),
            done.copy(distClaimId = done.distClaimId.copy(id = "foreign-claim")),
        )) assertNull(lbCompletedReceiverRecoveries(view(state(invalid), 2), observed, 1))
    }

    @Test fun observationCannotMoveToASuccessorOwner() {
        val pending = view(state(claim("current", ClientStatus.NEED_APPROVE)))
        val selected = checkNotNull(lbCanonicalSecret(pending, pass.id, pass.name))
        val observed = checkNotNull(lbCanonicalRecovery(pending, selected, "current", emptySet()))
        val done = state(claim("current", ClientStatus.DONE))
        val other = NativeReadScope(NativeOwnerToken(), NativeOwnerEpoch())
        val successor = checkNotNull(lbCanonicalView(frame(done, 2).copy(scope = other), done, idle, other))
        assertNull(lbCompletedReceiverRecoveries(successor, observed, 1))
    }

    @Test fun launchBoundaryRejectsSameFrameAndNonAdvancingSameScope() {
        val initial = view(state())
        assertFalse(lbAfterLaunch(initial, initial.frame))
        assertFalse(lbAfterLaunch(view(initial.source, 1), initial.frame))
        assertTrue(lbAfterLaunch(view(initial.source, 2), initial.frame))
        assertTrue(lbAfterLaunch(initial, null))
    }

    @Test fun postOfflineRequiresNoActiveOwnSenderOrCurrentReceiverClaim() {
        val empty = view(state())
        val selected = checkNotNull(lbCanonicalSecret(empty, pass.id, pass.name))
        assertTrue(lbNoActiveRecovery(empty, selected))
        assertFalse(lbNoActiveRecovery(view(withIncomingOperation(state(claim("incoming", ClientStatus.NEED_APPROVE)), true)), selected))
        val own = claim("own", ClientStatus.PENDING).copy(sender = "receiver", receivers = listOf("other"))
        assertFalse(lbNoActiveRecovery(view(state(own)), selected))
        assertTrue(lbNoActiveRecovery(view(state(claim("done", ClientStatus.DONE))), selected))
        assertTrue(lbNoActiveRecovery(view(state(claim("declined", ClientStatus.DECLINED))), selected))
        val foreign = claim("other", ClientStatus.NEED_APPROVE).copy(distClaimId = DistClaimId("other", PassId("another", "Other")))
        assertTrue(lbNoActiveRecovery(view(state(foreign)), selected))
    }

    @Test fun canonicalResolutionOwnsReceiverStateAndMissingMetadataIsNotProof() {
        val initial = state(claim("incoming", ClientStatus.NEED_APPROVE))
        val selected = checkNotNull(lbCanonicalSecret(view(initial), pass.id, pass.name))
        assertTrue(lbNoActiveRecovery(view(withIncomingOperation(initial, false)), selected))
        assertFalse(lbNoActiveRecovery(view(withIncomingOperation(initial, true)), selected))
        val member = (initial.getVaultFullInfo() as VaultFullInfo.Member).member
        assertFalse(lbNoActiveRecovery(view(replaceMember(initial, member.copy(liveboardState = null))), selected))
    }

    @Test fun missingClaimTableIsNotAnAuthoritativeEmptyResult() {
        val original = state()
        val member = (original.getVaultFullInfo() as VaultFullInfo.Member).member
        val missing = replaceMember(original, member.copy(ssClaims = null))
        assertNull(lbCanonicalView(frame(missing), missing, idle, scope))
    }

    private fun claim(id: String, status: ClientStatus) = ClaimObject(
        DistClaimId(id, pass), DistributionType.RECOVER, id, listOf("receiver"), "sender",
        ClaimStatusInfo(), "Fixture Vault", status)

    private fun frame(state: AppStateModel, attempt: Long = 1) =
        NativeReadFrame(attempt, scope, emptySet(), NativeReadOutcome.SUCCESS, state)

    private fun view(state: AppStateModel, attempt: Long = 1) =
        checkNotNull(lbCanonicalView(frame(state, attempt), state, idle, scope))

    private fun state(vararg claims: ClaimObject): AppStateModel {
        val user = UserData(DeviceData("receiver", "Android Fixture", "test", keys = OpenBox("dsa", "transport")), "Fixture Vault")
        val sender = user.copy(device = user.device.copy(deviceId = "sender", deviceName = "Sender Fixture"))
        val other = user.copy(device = user.device.copy(deviceId = "other", deviceName = "Other Fixture"))
        val users = mapOf("receiver" to UserMembership(member = UserDataMember(user)),
            "sender" to UserMembership(member = UserDataMember(sender)), "other" to UserMembership(member = UserDataMember(other)))
        val vault = VaultData("Fixture Vault", users, listOf(SecretApiModel(pass.id, pass.name)))
        val metadata = Json.parseToJsonElement("""{
            "schemaVersion":1,"vaultName":"Fixture Vault","currentDeviceId":"receiver","storageMode":"shares",
            "canCreateSecret":true,"removableDeviceIds":["sender","other"],"secrets":[{"secretId":"pass-selected",
            "primaryAction":"recover","recoveryClaimId":null,"deletionRequestId":null,"canDelete":true,"readiness":"ready"}],"operations":[]
        }""")
        val member = UserMemberFullInfo(VaultMember(UserDataMember(user), vault), SsClaims(claims.associateBy { it.id }), liveboardState = metadata)
        return AppStateModel(Message(State.Vault(VaultFullInfo.Member(member))), true)
    }

    private fun withIncomingOperation(state: AppStateModel, pending: Boolean): AppStateModel {
        val member = (state.getVaultFullInfo() as VaultFullInfo.Member).member
        val status = if (pending) "needApprove" else "done"
        val outcome = if (pending) "null" else "\"accepted\""
        val metadata = member.liveboardState.toString().replace("\"operations\":[]", """"operations":[{
            "kind":"recover","requestId":"incoming","requesterId":"sender","targetDeviceId":null,"secretId":"pass-selected",
            "clientStatus":"$status","eligiblePeerIds":["receiver"],"canApprove":$pending,"canDecline":$pending,
            "outcome":$outcome,"responderId":null,"resolutionSource":"canonical","readiness":"ready"}]""")
        return replaceMember(state, member.copy(liveboardState = Json.parseToJsonElement(metadata)))
    }

    private fun replaceMember(state: AppStateModel, member: UserMemberFullInfo): AppStateModel =
        state.copy(message = Message(State.Vault(VaultFullInfo.Member(member))))
}
