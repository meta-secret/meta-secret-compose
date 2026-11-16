package core.metaSecretCore.liveboard

import core.metaSecretCore.MetaSecretCoreInterface
import models.apiModels.JsonConfig
import models.apiModels.UserData
import testutils.FakeMetaSecretCore

/** Parser fixtures only. These values do not grant native admission or permissions. */
internal object LiveboardNativeFixtures {
    const val vaultName = "parser-vault"
    const val currentDeviceId = "device-a"
    const val otherDeviceId = "device-b"
    const val claimId = "opaque-claim::same-pass::chosen"
    const val requestId = "10bb5e25-598a-4d9b-8cb7-2f942596090a"

    val metadata = """
        {
          "schemaVersion":1,
          "vaultName":"parser-vault",
          "currentDeviceId":"device-a",
          "storageMode":"copies",
          "canCreateSecret":true,
          "removableDeviceIds":["device-b"],
          "secrets":[
            {"secretId":"pass-a","primaryAction":"showLocal","canDelete":true,"readiness":"ready"},
            {"secretId":"pass-b","primaryAction":"showRecovered","recoveryClaimId":"opaque-claim::same-pass::chosen","canDelete":false,"readiness":"ready"}
          ],
          "operations":[
            {
              "kind":"recover","requestId":"opaque-claim::same-pass::chosen","requesterId":"device-a",
              "secretId":"pass-b","clientStatus":"accepted","eligiblePeerIds":[],
              "canApprove":false,"canDecline":false,"outcome":"accepted",
              "responderId":"device-b","resolutionSource":"verified","readiness":"ready"
            }
          ]
        }
    """.trimIndent()

    fun member(liveboardState: String? = metadata): String {
        val optionalMetadata = liveboardState?.let { ",\"liveboardState\":$it" }.orEmpty()
        return """
            {"success":true,"message":{"state":{"vault":{"member":{
              "member":{
                "member":{"userData":${user(currentDeviceId, "Current phone", "iphone")}},
                "vault":{
                  "vaultName":"parser-vault",
                  "users":{
                    "device-a":{"member":{"userData":${user(currentDeviceId, "Current phone", "iphone")}}},
                    "device-b":{"member":{"userData":${user(otherDeviceId, "Other tablet", "tablet")}}}
                  },
                  "secrets":[
                    {"id":"pass-a","name":"Same name"},
                    {"id":"pass-b","name":"Same name","type":"seedPhrase","wordCount":12}
                  ]
                }
              },
              "ssClaims":{"claims":{}},"vaultEvents":{"requests":[],"updates":[]},"deletionStates":[]
              $optionalMetadata
            }}}}}
        """.trimIndent()
    }

    fun envelope(message: String) = """{"success":true,"message":$message}"""

    fun checked(
        kind: String = "recover",
        phase: String = "queued",
        secretId: String = "pass-b",
        claim: String = claimId,
        request: String = requestId,
    ): String {
        val identity = when (kind) {
            "create" -> """"secretId":"$secretId""""
            "recover" -> """"secretId":"$secretId","claimId":"$claim""""
            else -> """"requestId":"$request""""
        }
        return """{"kind":"$kind","phase":"$phase",$identity}"""
    }

    fun source(curr: String = "9007199254740993", commitment: String = "ab".repeat(32)): String {
        val prev = (curr.toULong() - 1u).toString()
        return """{"eventId":{"fqdn":{"objType":"DeviceLog","objInstance":"device-a"},"id":{"curr":$curr,"prev":$prev}},"bodyCommitment":"$commitment"}"""
    }

    fun attemptedDecision(curr: String = "9007199254740994") =
        """{"state":"attempted","source":${source(curr)},"observation":"unconfirmed"}"""

    fun producer(
        kind: String = "removeDevice",
        curr: String = "9007199254740993",
        decision: String? = null,
        request: String? = requestId,
        phase: String = "unknown",
    ): String {
        val subject = if (kind == "deleteSecret") """"secretId":"pass-b"""" else """"targetDeviceId":"device-b""""
        val requestField = request?.let { """, "requestId":"$it"""" }.orEmpty()
        val decisionField = decision?.let { """, "decisionStage":$it""" }.orEmpty()
        return """{"version":1,"kind":"$kind","phase":"$phase",$subject$requestField,
            "requestStage":{"source":${source(curr)},"observation":"unconfirmed"}$decisionField,
            "materialStage":"notStarted","canonicalState":"unknown","readiness":"synchronizing"}""".trimIndent()
    }

    fun producerValue(
        kind: LiveboardKind = LiveboardKind.RemoveDevice,
        curr: String = "9007199254740993",
        decision: LiveboardProducerDecision? = null,
        request: String? = requestId,
    ) = LiveboardProducerReceipt(
        version = 1,
        identity = LiveboardProducerIdentity(
            kind = kind,
            secretId = if (kind == LiveboardKind.DeleteSecret) "pass-b" else null,
            targetDeviceId = if (kind == LiveboardKind.RemoveDevice) otherDeviceId else null,
            requestId = request,
        ),
        state = LiveboardProducerState(LiveboardPhase.Unknown, "notStarted", "unknown", "synchronizing", null),
        requestStage = LiveboardProducerAttempt(sourceValue(curr), "unconfirmed"),
        decisionStage = decision,
    )

    fun sourceValue(curr: String = "9007199254740993") = LiveboardProducerSource(
        eventId = LiveboardArtifactId(
            LiveboardFqdn("DeviceLog", currentDeviceId),
            LiveboardSequence(curr, (curr.toULong() - 1u).toString()),
        ),
        bodyCommitment = "ab".repeat(32),
    )

    private fun user(id: String, name: String, category: String) = """
        {"device":{"deviceId":"$id","deviceName":"$name","deviceType":"Other",
          "uiCategory":"$category","keys":{"dsaPk":"parser-dsa","transportPk":"parser-transport"}},
          "vaultName":"parser-vault"}
    """.trimIndent()
}

internal data class NativeCall(val name: String, val arguments: List<String>)

internal class RecordingNativeCore : MetaSecretCoreInterface by FakeMetaSecretCore() {
    val calls = mutableListOf<NativeCall>()
    var result = """{"success":false,"errorCode":"OperationUnavailable"}"""
    var failure: Throwable? = null

    private fun record(name: String, vararg arguments: String): String {
        calls += NativeCall(name, arguments.toList())
        failure?.let { throw it }
        return result
    }

    override fun clusterDistributionChecked(name: String, value: String) = record("create", name, value)
    override fun recoverChecked(secretId: String) = record("recover", secretId)
    override fun decideJoinRequest(requestJson: String) = record("join", requestJson)
    override fun decideRemoveMember(requestJson: String) = record("decideRemove", requestJson)
    override fun deleteSecretChecked(secretId: String) = record("delete", secretId)
    override fun removeMemberChecked(targetDeviceId: String) = record("remove", targetDeviceId)
    override fun reconcileProducerReceipt(reference: String) = record("reconcile", reference)
    override fun continueLocalRemoveChecked(reference: String) = record("continue", reference)
    override fun showRecovered(claimId: String) = record("showRecovered", claimId)
    override fun acceptRecover(claimId: String) = record("acceptRecover", claimId)
    override fun declineRecover(claimId: String) = record("declineRecover", claimId)
    override fun splitSecret(secretName: String, secret: String) = record("LEGACYsplit", secretName, secret)
    override fun recover(secretId: String) = record("LEGACYrecover", secretId)
    override fun showLocalSecret(secretId: String) = record("LEGACYshowLocal", secretId)
    override fun findClaim(secretId: String) = record("LEGACYfindClaim", secretId)
    override fun updateMembership(candidate: UserData, actionUpdate: String) = record(
        "LEGACYupdateMembership", JsonConfig.json.encodeToString(UserData.serializer(), candidate), actionUpdate,
    )
    override fun removeMember(targetDeviceId: String) = record("LEGACYremoveMember", targetDeviceId)
    override fun approveRemoveMember(targetDeviceId: String) = record("LEGACYapproveRemoveMember", targetDeviceId)
    override fun declineRemoveMember(targetDeviceId: String) = record("LEGACYdeclineRemoveMember", targetDeviceId)
    override fun deleteSecret(secretId: String) = record("LEGACYdeleteSecret", secretId)
    override fun decideDeleteSecret(requestJson: String) = record("LEGACYdecideDeleteSecret", requestJson)
    override fun sendDeclineCompletion(claimId: String) = record("LEGACYsendDeclineCompletion", claimId)
    override fun generateMasterKey() = record("LEGACYgenerateMasterKey")
    override fun initAppManager(masterKey: String) = record("LEGACYinitAppManager", masterKey)
    override fun getAppState() = record("LEGACYgetAppState")
    override fun stateEventsAuthToken(vaultName: String) = record("LEGACYstateEventsAuthToken", vaultName)
    override fun joinResultAuthToken() = record("LEGACYjoinResultAuthToken")
    override fun generateUserCreds(vaultName: String) = record("LEGACYgenerateUserCreds", vaultName)
    override fun signUp() = record("LEGACYsignUp")
}
