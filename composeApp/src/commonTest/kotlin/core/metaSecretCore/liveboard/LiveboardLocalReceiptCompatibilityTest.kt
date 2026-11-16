package core.metaSecretCore.liveboard

import core.metaSecretCore.MetaSecretCoreInterface
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import testutils.FakeStringProvider
import ui.liveboard.LiveboardRootTestFixture
import ui.liveboard.rootCheck

class LiveboardLocalReceiptCompatibilityTest {
    @Test
    fun testLocalLegacyIgnoresRecoveredReceiptMetadataAtPublicShowBoundary() = runBlocking {
        withTimeout(45_000) {
            val failures = mutableListOf<String>()
            val plaintext = "synthetic local receipt compatibility value"
            val cases = listOf(null, "null", "\"unknown recovered proof\"", "[]", "{\"version\":99}")
            for ((index, receipt) in cases.withIndex()) {
                val f = LiveboardRootTestFixture()
                var session: LiveboardSession? = null
                try {
                    val localCalls = mutableListOf<String>()
                    val native = object : MetaSecretCoreInterface by f.core {
                        override fun showLocalSecret(secretId: String): String {
                            localCalls.add(secretId)
                            return buildJsonObject {
                                put("success", true)
                                put("message", buildJsonObject {
                                    put("secret", plaintext)
                                    if (receipt != null) put("revealReceipt", Json.parseToJsonElement(receipt))
                                })
                            }.toString()
                        }
                    }
                    f.start()
                    val environment = NativeReadEnvironment(Dispatchers.IO, f.main)
                    val checked = NativeLiveboardOperations(LiveboardCoreAdapter(native), f.operations, environment)
                    val factory = LiveboardSessionFactory(checked, f.reads, f.operations, environment,
                        LiveboardClientPorts(f.privacy, f.auth, f.socket, f.notifications, FakeStringProvider()))
                    val frame = assertNotNull(f.reads.frames.value)
                    val current = assertNotNull(factory.open(frame)).also { session = it }
                    assertTrue(current.inbox.state.value.ready)
                    val reads = f.core.reads.value
                    val auth = f.auth.calls
                    val shown = current.sensitive.show("pass-a")
                    val value = current.sensitive.state.value.revealed["pass-a"]?.value
                    val copied = current.sensitive.copyValue("pass-a")
                    rootCheck(failures, "Local legacy receipt case/$index") {
                        assertTrue(shown)
                        assertEquals(plaintext, value)
                        assertEquals(plaintext, copied)
                    }
                    assertEquals(listOf("pass-a"), localCalls, "Exactly the selected Local native route")
                    assertTrue(f.core.calls.value.isEmpty(), "No recovered or additional native operation")
                    assertEquals(auth + 1, f.auth.calls)
                    assertEquals(reads, f.core.reads.value, "Local Show cannot manufacture another read")
                    assertSame(frame, f.reads.frames.value)
                    assertEquals(frame.scope, f.initialization.currentScope())
                } finally { session?.dispose(); f.close() }
            }
            assertEquals(emptyList(), failures, "R32_LOCAL_LEGACY_IGNORES_RECOVERED_RECEIPT")
        }
    }
}
