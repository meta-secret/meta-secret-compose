package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import models.apiModels.liveboard.LiveboardStateDecoder

class LiveboardRevealAuthorizationTest {
    private data class DecisionCase(val kind: LiveboardKind, val accepted: Boolean, val mode: String)
    @Test
    fun testIndependentCardsAndBothNativeDisclosureRoutesStayTransientAcrossRealInvalidationAndAuth() = runBlocking {
        withTimeout(45_000) {
            val failures = mutableListOf<String>()
            val reached = mutableListOf<String>()
            cards(failures)
            for (id in listOf("pass-a", "pass-b")) {
                for (effect in listOf("hide", "filter", "route", "error", "background", "delete")) {
                    lateReveal(this, id, effect, failures, reached)
                }
                revealAuth(this, id, false, failures)
                revealAuth(this, id, true, failures)
            }
            assertEquals(12, reached.size)
            assertEquals(emptyList(), failures, "Both real reveal routes and all invalidation/auth/fresh controls reached: $reached")
        }
    }

    private suspend fun cards(failures: MutableList<String>) {
        val f = LiveboardFeatureFixture()
        try {
            val session = f.start()
            f.disclose(session, "pass-a"); f.disclose(session, "pass-b")
            if (session.sensitive.state.value.revealed.keys != setOf("pass-a", "pass-b")) failures.add("Show B hid the independently disclosed A card")
            val b = session.sensitive.copyValue("pass-b")
            assertNotNull(b)
            session.sensitive.hide("pass-a")
            assertNull(session.sensitive.copyValue("pass-a"))
            if (session.sensitive.copyValue("pass-b") != b) failures.add("Hide A cleared B card or B Copy eligibility")
            f.disclose(session, "pass-a")
            if (session.sensitive.state.value.revealed.size != 2) failures.add("Second deliberate A disclosure displaced B")
            f.notifications.showError("Two-card fixture error")
            if (!f.disclosuresEmpty(session)) failures.add("Global error did not conceal both held cards and both Copy values")
            f.seenError(session, "Two-card fixture error")
            f.proof(); session.inbox.dismissError(); f.proof()
            f.disclose(session, "pass-b")
            assertEquals(b, session.sensitive.copyValue("pass-b"))
        } finally { f.close() }
    }

    private suspend fun lateReveal(scope: CoroutineScope, id: String, effect: String, failures: MutableList<String>, reached: MutableList<String>) {
        val f = LiveboardFeatureFixture()
        try {
            val session = f.start()
            val route = if (id == "pass-a") "local" else "recovered"
            val native = f.hold(route)
            f.io.queue()
            val show = f.track(scope.async(f.main, CoroutineStart.UNDISPATCHED) { session.sensitive.show(id) })
            val worker = f.run(f.io.take(), scope)
            native.entered.await(); f.io.resumeInline()
            when (effect) {
                "hide" -> session.sensitive.hide(id)
                "filter" -> session.sensitive.filterChanged("Nothing matches this fixture")
                "route" -> session.sensitive.routeChanged()
                "error" -> f.notifications.showError("Late reveal fixture error")
                "background" -> f.socket.onAppBackground()
                else -> {
                    val deleting = assertNotNull(session.actions.deleteIntent(id))
                    assertIs<NativeLiveboardOutcome.Entered<*>>(session.actions.delete(deleting))
                    f.core.primary("unavailable", id = id); f.proof()
                }
            }
            native.release.complete(Unit); native.exited.await(); worker.join(); show.await()
            if (id in session.sensitive.state.value.revealed || session.sensitive.copyValue(id) != null) failures.add("$id/$effect: late native response disclosed after invalidation")
            val actual = f.core.calls.value.first { it.name == route }
            assertEquals(listOf(if (route == "local") id else FeatureIds.claim), actual.arguments)
            if (effect == "background") f.socket.onAppForeground()
            f.core.fresh(); f.proof()
            session.inbox.dismissError(); f.proof(); f.settleSocket()
            session.sensitive.filterChanged("")
            f.disclose(session, id)
            assertNotNull(session.sensitive.copyValue(id))
            reached.add("$id/$effect/actual-entry/late-return/fresh-deliberate")
        } finally { f.close() }
    }

    private suspend fun revealAuth(scope: CoroutineScope, id: String, stale: Boolean, failures: MutableList<String>) {
        val f = LiveboardFeatureFixture()
        try {
            val session = f.start()
            val profile = f.profile()
            f.auth.held = true
            val show = f.track(scope.async(f.main, CoroutineStart.UNDISPATCHED) { session.sensitive.show(id) })
            val callback = f.auth.take()
            if (stale) {
                if (id == "pass-b") f.core.primary("showRecovered", "a-different-opaque-claim")
                else f.core.primary("unavailable", id = id)
                f.proof(); callback.success()
            } else callback.error("Fixture OS cancellation")
            if (show.isCancelled) show.join() else show.await()
            if (f.core.calls.value.any { it.name in listOf("local", "recovered") }) failures.add("$id/${if (stale) "stale" else "cancel"}: invalid OS consent entered Core Show")
            if (!f.disclosuresEmpty(session)) failures.add("$id: invalid OS consent left a disclosed value")
            f.notifications.dismiss()
            val live = f.successor(profile)
            f.disclose(live, id)
            val expectedRoute = if (id == "pass-a") "local" else "recovered"
            assertEquals(expectedRoute, f.core.calls.value.last().name)
            assertEquals(listOf(if (id == "pass-a") id else FeatureIds.claim), f.core.calls.value.last().arguments)
        } finally { f.close() }
    }

    @Test
    fun testBothDecisionsForEveryKindRevalidateFrozenConsentAndCancelOrStaleSendsNoNativeCall() = runBlocking {
        withTimeout(45_000) {
            val failures = mutableListOf<String>()
            val reached = mutableListOf<String>()
            for (kind in FeatureIds.kinds) for (accepted in listOf(true, false)) {
                for (mode in listOf("cancel", "error", "stale", "fresh")) {
                    decision(this, DecisionCase(kind, accepted, mode), failures, reached)
                }
            }
            decision(this, DecisionCase(LiveboardKind.Join, true, "retire"), failures, reached)
            decision(this, DecisionCase(LiveboardKind.DeleteSecret, true, "responseFalse"), failures, reached)
            decision(this, DecisionCase(LiveboardKind.Recover, false, "responseMalformed"), failures, reached)
            assertEquals(35, reached.size)
            assertEquals(emptyList(), failures, "All four kinds/both decisions/callback and fresh exact-native controls reached: $reached")
        }
    }

    private suspend fun decision(scope: CoroutineScope, case: DecisionCase, failures: MutableList<String>, reached: MutableList<String>) {
        val (kind, accepted, mode) = case
        val f = LiveboardFeatureFixture()
        try {
            val session = f.start()
            val profile = f.profile()
            val identity = f.identity(kind)
            val selected = assertNotNull(session.actions.decisionIntent(identity, accepted))
            f.auth.held = true
            val action = f.track(scope.async(f.main, CoroutineStart.UNDISPATCHED) { session.actions.decide(selected) })
            val callback = f.auth.take()
            var successor: LiveboardSession? = null
            when (mode) {
                "cancel" -> callback.error("Fixture OS cancellation")
                "error" -> callback.error("Fixture OS unavailable")
                "stale" -> {
                    val field = when (kind) {
                        LiveboardKind.Join -> "actor"
                        LiveboardKind.RemoveDevice -> "permission"
                        LiveboardKind.DeleteSecret -> "kind"
                        else -> "claim"
                    }
                    f.core.stale(identity, field); f.proof()
                    val current = assertNotNull(LiveboardStateDecoder.decode(assertNotNull(f.reads.frames.value?.state)))
                        .operations.firstOrNull { it.identity == identity }
                    if (field == "permission") {
                        val changed = assertNotNull(current)
                        assertTrue(!changed.permissions.canApprove && !changed.permissions.canDecline)
                    } else assertNull(current, "The actual canonical frozen identity must have changed before releasing consent")
                    callback.success()
                }
                "retire" -> { successor = f.successor(profile); callback.success() }
                "responseFalse" -> { f.core.decisionRaw = "{\"success\":false,\"error\":\"fixture private response\"}"; callback.success() }
                "responseMalformed" -> { f.core.decisionRaw = "{\"success\":\"true\",\"message\":\"fixture private response\"}"; callback.success() }
                else -> callback.success()
            }
            val result = if (action.isCancelled) { action.join(); null } else action.await()
            session.awaitSettled()
            val calls = f.core.calls.value.filter { it.name in listOf("join", "decideRemove", "decideDelete", "accept", "decline") }
            val responseControl = mode in listOf("responseFalse", "responseMalformed")
            if (mode != "fresh" && !responseControl && calls.isNotEmpty()) failures.add("$kind/$accepted/$mode: cancelled or stale frozen consent entered native")
            if (mode == "fresh" || responseControl) {
                val entered = assertIs<NativeLiveboardOutcome.Entered<*>>(result)
                if (responseControl && entered.result is NativeResult.Success) failures.add("$kind/$mode: false or malformed native result became a successful returned decision")
                assertTrue(!entered.result.toString().contains("fixture private response"))
                assertEquals(1, calls.size)
                decisionArguments(calls.single(), kind, accepted)
            }
            f.notifications.dismiss()
            val live = successor ?: f.successor(profile)
            f.auth.held = false
            val fresh = assertNotNull(live.actions.decisionIntent(f.identity(kind), accepted))
            val count = f.core.calls.value.size
            val positive = assertIs<NativeLiveboardOutcome.Entered<*>>(live.actions.decide(fresh))
            assertIs<NativeResult.Success<*>>(positive.result)
            assertEquals(count + 1, f.core.calls.value.size)
            decisionArguments(f.core.calls.value.last(), kind, accepted)
            assertTrue(!positive.toString().contains("Fixture OS"))
            reached.add("$kind/$accepted/$mode/fresh-native")
        } finally { f.close() }
    }

    private fun decisionArguments(call: NativeCall, kind: LiveboardKind, accepted: Boolean) {
        val route = when (kind) {
            LiveboardKind.Join -> "join"
            LiveboardKind.RemoveDevice -> "decideRemove"
            LiveboardKind.DeleteSecret -> "decideDelete"
            else -> if (accepted) "accept" else "decline"
        }
        assertEquals(route, call.name)
        if (kind == LiveboardKind.Recover) assertEquals(listOf(FeatureIds.claim), call.arguments)
        else {
            val raw = Json.parseToJsonElement(call.arguments.single()).jsonObject
            assertEquals(setOf("requestId", "accepted"), raw.keys)
            assertEquals(FeatureIds.request(kind), raw.getValue("requestId").jsonPrimitive.content)
            assertEquals(accepted.toString(), raw.getValue("accepted").jsonPrimitive.content)
        }
    }
}
