package core.metaSecretCore.liveboard

import kotlin.test.*
import kotlinx.coroutines.*

class LiveboardRecoverCompletionTest {
    @Test fun testQueuedAndUnknownExactDoneReleaseOnlySelectionKeepTruthAndAllowFreshIntent() = runBlocking {
        withTimeout(60_000) {
            for (phase in listOf("queued", "unknown")) {
                val c = LiveboardRecoverCompletionFixture(phase)
                try {
                    c.start(); val old = c.issue()
                    c.session.sensitive.hide("pass-b") // Terminal proof must survive original privacy-generation change.
                    val calls = c.f.core.calls.value; val auth = c.f.auth.calls
                    c.proof()
                    assertTrue(c.selected().isEmpty(), "R32_EXACT_DONE_WAITING_RELEASES_PRIOR_SELECTION/$phase")
                    assertEquals(calls, c.f.core.calls.value); assertEquals(auth, c.f.auth.calls)
                    val count = c.f.core.reads.value
                    assertSame(old.second, c.session.actions.recover(old.first))
                    assertEquals(calls, c.f.core.calls.value); assertEquals(count, c.f.core.reads.value)
                    c.replyClaim = LiveboardRecoverCompletionFixture.secondClaim
                    val fresh = c.issue()
                    assertNotSame(old.first, fresh.first)
                    assertEquals(calls + NativeCall("recover", listOf("pass-b")), c.f.core.calls.value)
                    c.proof() // A fresh physical observation of old Claim cannot clear the newer issued Claim.
                    c.assertRetained(fresh)
                    val after = c.f.core.calls.value
                    assertSame(old.second, c.session.actions.recover(old.first))
                    assertEquals(after, c.f.core.calls.value)
                } finally { c.close() }
            }
        }
    }

    @Test fun testFalseCanonicalCompletionInputsNeverRetireOrReplacePriorRecover() = runBlocking {
        withTimeout(60_000) {
            val modes = listOf("wrong-kind", "wrong-claim", "wrong-secret", "wrong-requester", "target", "duplicate",
                "pending", "accepted", "unknown", "unverified", "canonical", "contradictory", "permission",
                "decline-permission", "not-waiting", "changed-name", "changed-type", "changed-words", "can-delete", "deleting",
                "active-claim", "not-ready", "foreign-vault", "foreign-device")
            for (mode in modes) {
                val c = LiveboardRecoverCompletionFixture()
                try {
                    c.start(); val old = c.issue()
                    c.proof(mode)
                    c.assertRetained(old)
                    assertEquals(listOf(NativeCall("recover", listOf("pass-b"))), c.f.core.calls.value, mode)
                    assertEquals(0, c.f.auth.calls, mode)
                } finally { c.close() }
            }
        }
    }

    @Test fun testUncoveredStaleFailedAndIncoherentCurrentFramesCannotBorrowCompletion() = runBlocking {
        withTimeout(45_000) {
            for (mode in listOf("uncovered", "stale", "failed", "incoherent")) {
                val c = LiveboardRecoverCompletionFixture()
                try {
                    c.start(); val old = c.issue()
                    val initial = assertNotNull(c.f.reads.frames.value)
                    when (mode) {
                        "uncovered" -> c.session.actions.observe(initial, c.member())
                        "stale" -> {
                            val current = c.proof("pending")
                            c.session.actions.observe(current.copy(), c.member())
                        }
                        "failed" -> {
                            c.f.core.failRead = true
                            val actual = assertIs<NativeReadCall.Failure>(c.f.reads.readAttempt())
                            val failed = actual.frame
                            assertEquals(NativeReadOutcome.FAILED, failed.outcome)
                            assertNull(c.f.reads.frames.value, "Failed physical read clears public current proof")
                            assertTrue(failed.coveredRequirements.isEmpty())
                            c.session.actions.observe(failed, c.member())
                        }
                        else -> {
                            val current = c.proof("pending")
                            c.session.actions.observe(current, c.member()) // Supplied Done is not this physical frame's state.
                        }
                    }
                    c.assertRetained(old)
                } finally { c.close() }
            }
        }
    }

    @Test fun testDisposedOrRetiredNativeOwnerCannotReleaseOldSelectionOrReplayIt() = runBlocking {
        withTimeout(45_000) {
            for (mode in listOf("live", "disposed", "owner")) {
                val c = LiveboardRecoverCompletionFixture()
                try {
                    c.start(); val old = c.issue()
                    val (frame, member) = c.coveredDoneWithoutObservation()
                    if (mode == "disposed") c.session.dispose() else if (mode == "owner") c.f.newOwner()
                    val calls = c.f.core.calls.value; val reads = c.f.core.reads.value; val auth = c.f.auth.calls
                    c.session.actions.observe(frame, member)
                    if (mode == "live") {
                        assertTrue(c.selected().isEmpty(), "Identical covered/coherent Done is usable before retirement")
                        assertSame(old.second, c.session.actions.recover(old.first))
                    } else c.assertRetained(old)
                    assertEquals(calls, c.f.core.calls.value); assertEquals(reads, c.f.core.reads.value)
                    assertEquals(auth, c.f.auth.calls)
                } finally { c.close() }
            }
        }
    }
}
