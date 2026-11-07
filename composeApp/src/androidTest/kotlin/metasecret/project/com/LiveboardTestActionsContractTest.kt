package metasecret.project.com

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeTimeoutException
import models.apiModels.*
import kotlinx.serialization.json.Json
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.rules.RuleChain
import org.junit.Rule
import org.junit.Test

/** Test-adapter component contracts only; no Core, membership or OS-auth substitute. */
class LiveboardTestActionsContractTest {
    val compose = createComposeRule()
    @get:Rule val componentResources = RuleChain.outerRule(LiveboardComponentStringsRule()).around(compose)

    @Test fun exactNamePreservesOpaquePassIdentity() {
        compose.setContent {
            Column {
                Text("Alpha, β", Modifier.testTag("lb-secret-name-opaque-A"))
                Text("Alpha", Modifier.testTag("lb-secret-name-opaque-B"))
            }
        }
        assertEquals("opaque-A", compose.lbFindSecret("Alpha, β"))
        assertEquals("opaque-B", compose.lbFindSecret("Alpha"))
    }

    @Test fun duplicateNamesCannotChooseTheFirstCard() {
        compose.setContent {
            Column {
                Text("Duplicate", Modifier.testTag("lb-secret-name-pass-A"))
                Text("Duplicate", Modifier.testTag("lb-secret-name-pass-B"))
            }
        }
        assertThrows(IllegalStateException::class.java) { compose.lbFindSecret("Duplicate") }
    }

    @Test fun absentNamesCannotReuseAnotherSecret() {
        compose.setContent { Text("Existing", Modifier.testTag("lb-secret-name-pass-A")) }
        assertThrows(IllegalStateException::class.java) { compose.lbFindSecret("Missing") }
    }

    @Test fun incomingIdentityComesFromTheRenderedHeadAndTrueCount() {
        compose.setContent {
            Column(Modifier.testTag("lb-inbox-head-Recover-claim-B")) {
                Text("2", Modifier.testTag("lb-inbox-count"))
                Text("iPhone18,3", Modifier.testTag("lb-request-sender"))
                Text("Secret B", Modifier.testTag("lb-request-subject"))
            }
        }
        val head = checkNotNull(compose.lbVisibleIncoming())
        assertEquals("Recover", head.kind)
        assertEquals("claim-B", head.requestId)
        assertEquals("iPhone18,3", head.requesterName)
        assertEquals("Secret B", head.subject)
        assertEquals(2, head.count)
    }

    @Test fun duplicateValuesCannotSatisfyAbsence() {
        compose.setContent { Column { repeat(2) { Text("private", Modifier.testTag("lb-value-pass")) } } }
        assertThrows(IllegalStateException::class.java) { compose.lbValueAbsent("pass") }
    }

    @Test fun duplicateCopiesCannotSatisfyAbsence() {
        compose.setContent { Column { repeat(2) { Text("Copy", Modifier.testTag("lb-copy-pass")) } } }
        assertThrows(IllegalStateException::class.java) { compose.lbValueAbsent("pass") }
    }

    @Test fun duplicatedCopiesAfterHideCannotCompleteHide() {
        val hidden = mutableStateOf(false)
        compose.setContent {
            Column {
                if (!hidden.value) Text("private", Modifier.testTag("lb-value-pass"))
                else repeat(2) { Text("Copy", Modifier.testTag("lb-copy-pass")) }
                Button(onClick = { hidden.value = true }, modifier = Modifier.testTag("lb-primary-pass")) { Text("Hide") }
            }
        }
        assertThrows(ComposeTimeoutException::class.java) { compose.lbHide("pass", timeoutMillis = 100) }
    }

    @Test fun canonicalJoinOutsiderMatchesExactRenderedRequester() {
        renderIncoming("Join", "Alex’s iPad, 2", null)
        val operation = lbBindIncoming(checkNotNull(compose.lbVisibleIncoming()), canonical("Join"))
        assertEquals("peer", operation.identity.requesterId)
        assertEquals("request-1", operation.identity.requestId)
    }

    @Test fun canonicalMemberMatchesExactRenderedRequester() {
        renderIncoming("Recover", "Alex’s iPad, 2", "Secret B")
        val operation = lbBindIncoming(checkNotNull(compose.lbVisibleIncoming()), canonical("Recover"))
        assertEquals("peer", operation.identity.requesterId)
        assertEquals("pass-b", operation.identity.secretId)
    }

    @Test fun wrongRenderedRequesterIsRejectedForJoinAndMember() {
        for (kind in listOf("Join", "Recover")) {
            val head = incoming(kind).copy(requesterName = "Different Device")
            assertThrows(IllegalStateException::class.java) { lbBindIncoming(head, canonical(kind)) }
        }
    }

    @Test fun missingCanonicalRequesterCannotBorrowAnotherLabel() {
        val state = canonical("Join")
        val member = (state.getVaultFullInfo() as VaultFullInfo.Member).member
        val users = member.member.vault.users - "peer"
        assertThrows(IllegalStateException::class.java) { lbBindIncoming(incoming("Join"), replaceUsers(state, users)) }
    }

    @Test fun duplicateCanonicalRequesterCannotChooseTheFirstMapping() {
        val state = canonical("Join")
        val users = (state.getVaultFullInfo() as VaultFullInfo.Member).member.member.vault.users
        val duplicate = replaceUsers(state, users + ("another-key" to users.getValue("peer")))
        assertThrows(IllegalStateException::class.java) { lbBindIncoming(incoming("Join"), duplicate) }
    }

    @Test fun staleRequestIdentityCannotMatchAnOldRenderedHead() {
        val state = canonical("Recover")
        val member = (state.getVaultFullInfo() as VaultFullInfo.Member).member
        val next = member.copy(liveboardState = Json.parseToJsonElement(member.liveboardState.toString().replace("request-1", "request-2")))
        assertThrows(IllegalStateException::class.java) { lbBindIncoming(incoming("Recover"), replaceMember(state, next)) }
    }

    @Test fun inconsistentJoinVaultAndDeclinedOutsiderAreRejected() {
        val state = canonical("Join")
        val users = (state.getVaultFullInfo() as VaultFullInfo.Member).member.member.vault.users
        val outsider = checkNotNull(users.getValue("peer").outsider)
        val wrongVault = users + ("peer" to UserMembership(outsider = outsider.copy(userData = outsider.userData.copy(vaultName = "Other Vault"))))
        val declined = users + ("peer" to UserMembership(outsider = outsider.copy(status = UserDataOutsiderStatus.DECLINED)))
        for (invalid in listOf(wrongVault, declined)) {
            assertThrows(IllegalStateException::class.java) { lbBindIncoming(incoming("Join"), replaceUsers(state, invalid)) }
        }
    }

    @Test fun requesterAndEligiblePeerGuardsRemainRequired() {
        val state = canonical("Recover")
        val member = (state.getVaultFullInfo() as VaultFullInfo.Member).member
        for ((old, replacement) in listOf(
            "\"requesterId\":\"peer\"" to "\"requesterId\":\"self\"",
            "\"eligiblePeerIds\":[\"self\"]" to "\"eligiblePeerIds\":[\"other\"]",
        )) {
            val changed = member.copy(liveboardState = Json.parseToJsonElement(member.liveboardState.toString().replace(old, replacement)))
            assertThrows(IllegalStateException::class.java) { lbBindIncoming(incoming("Recover"), replaceMember(state, changed)) }
        }
    }

    private fun renderIncoming(kind: String, label: String, subject: String?) {
        compose.setContent {
            Column(Modifier.testTag("lb-inbox-head-$kind-request-1")) {
                Text("1", Modifier.testTag("lb-inbox-count"))
                Text(label, Modifier.testTag("lb-request-sender"))
                if (subject != null) Text(subject, Modifier.testTag("lb-request-subject"))
            }
        }
    }

    private fun incoming(kind: String) = LiveboardTestIncoming(kind, "request-1", "Alex’s iPad, 2", if (kind == "Join") null else "Secret B", 1)

    /** Controlled payloads test adapter correlation only; they are not native read proof or Core E2E. */
    private fun canonical(kind: String): AppStateModel {
        val vault = "Fixture Vault"
        fun user(id: String, name: String) = UserData(DeviceData(id, name, "test", keys = OpenBox("dsa", "transport")), vault)
        val self = user("self", "This Android")
        val peer = user("peer", "Alex’s iPad, 2")
        val users = mapOf("self" to UserMembership(member = UserDataMember(self)), "peer" to
            if (kind == "Join") UserMembership(outsider = UserDataOutsider(peer, UserDataOutsiderStatus.PENDING))
            else UserMembership(member = UserDataMember(peer)))
        val secretId = if (kind == "Join") "null" else "\"pass-b\""
        val metadata = Json.parseToJsonElement("""{
          "schemaVersion":1,"vaultName":"Fixture Vault","currentDeviceId":"self","storageMode":"${if (kind == "Join") "whole" else "copies"}",
          "canCreateSecret":true,"removableDeviceIds":[],"secrets":[
            {"secretId":"pass-b","primaryAction":"showLocal","recoveryClaimId":null,"deletionRequestId":null,"canDelete":true,"readiness":"ready"}
          ],"operations":[{"kind":"${kind.replaceFirstChar { it.lowercase() }}","requestId":"request-1","requesterId":"peer",
            "targetDeviceId":null,"secretId":$secretId,"clientStatus":"needApprove","eligiblePeerIds":["self"],
            "canApprove":true,"canDecline":true,"outcome":null,"responderId":null,"resolutionSource":"canonical","readiness":"ready"}]
        }""")
        val member = UserMemberFullInfo(VaultMember(UserDataMember(self), VaultData(vault, users, listOf(SecretApiModel("pass-b", "Secret B")))), liveboardState = metadata)
        return AppStateModel(Message(State.Vault(VaultFullInfo.Member(member))), true)
    }

    private fun replaceUsers(state: AppStateModel, users: Map<String, UserMembership>): AppStateModel {
        val member = (state.getVaultFullInfo() as VaultFullInfo.Member).member
        return replaceMember(state, member.copy(member = member.member.copy(vault = member.member.vault.copy(users = users))))
    }

    private fun replaceMember(state: AppStateModel, member: UserMemberFullInfo): AppStateModel =
        state.copy(message = Message(State.Vault(VaultFullInfo.Member(member))))

}
