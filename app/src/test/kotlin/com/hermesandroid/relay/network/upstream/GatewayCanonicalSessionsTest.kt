package com.hermesandroid.relay.network.upstream

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class GatewayCanonicalSessionsTest {
    private val json = Json
    private fun obj(value: String) = json.parseToJsonElement(value).jsonObject

    @Test fun legacyRequestsRemainUnchangedUntilAuthorityAdvertised() {
        val adapter = GatewayCanonicalSessions()
        val params = obj("""{"source":"webui","cols":80}""")
        assertEquals(GatewayCanonicalRequest("session.create", params), adapter.prepare("session.create", params, null))
    }

    @Test fun canonicalCreateUsesGuiAndNativeSettings() {
        val adapter = GatewayCanonicalSessions().apply { enabled = true }
        val request = adapter.prepare("session.create", obj("""{"source":"webui","cols":80,"reasoning_effort":"high","fast":true,"profile":"work"}"""), "work")
        assertEquals(obj("""{"source":"gui","reasoning":"high","service_tier":"fast","profile":"work"}"""), JsonObject(request.params.filterKeys { it != "request_id" }))
        assertTrue(request.params["request_id"]!!.jsonPrimitive.content.isNotBlank())
    }

    @Test fun controlUsesExactSnapshotGenerationAndPrompt() {
        val adapter = GatewayCanonicalSessions().apply { enabled = true }
        adapter.observeResult("session.resume", obj("""{"session_id":"s","profile":"work"}"""), obj("""{"session_id":"s","authority_epoch":4,"execution_generation":7,"revision":3,"prompts":[{"kind":"approval","prompt_id":"p","execution_generation":7,"choices":["once","deny"]}],"info":{"profile_name":"work"}}"""), "work")
        val request = adapter.prepare("approval.respond", obj("""{"session_id":"s","request_id":"p","choice":"once"}"""), "work")
        assertEquals(obj("""{"session_id":"s","profile":"work","execution_generation":7,"prompt_id":"p","choice":"once"}"""), request.params)
        assertEquals(7, adapter.prepare("session.interrupt", obj("""{"session_id":"s"}"""), "work").params["execution_generation"]!!.jsonPrimitive.int)
        assertThrows(IllegalArgumentException::class.java) {
            adapter.prepare("approval.respond", obj("""{"session_id":"s","request_id":"other","choice":"once"}"""), "work")
        }
    }

    @Test fun snapshotFencesDoNotCrossProfilesAndResetWithSocket() {
        val adapter = GatewayCanonicalSessions().apply { enabled = true }
        adapter.observeResult("session.resume", obj("""{"session_id":"s"}"""), obj("""{"session_id":"s","execution_generation":9,"info":{"profile_name":"one"}}"""), "one")
        assertNull(adapter.snapshot("s", "two"))
        adapter.reset()
        assertNull(adapter.snapshot("s", "one"))
        assertFalse(adapter.enabled)
    }

    @Test fun submitRequiresStableAdmissionIdentityAndPreservesExplicitQueue() {
        val adapter = GatewayCanonicalSessions().apply { enabled = true }
        val params = obj("""{"session_id":"s","text":"hello","submission_id":"stable","queued":true}""")
        assertEquals("stable", adapter.prepare("prompt.submit", params, null).params["submission_id"]!!.jsonPrimitive.content)
        assertEquals(true, adapter.prepare("prompt.submit", params, null).params["clarify_batch"]!!.jsonPrimitive.boolean)
    }

    @Test fun lateGenerationCannotReplaceNewerControlFence() {
        val adapter = GatewayCanonicalSessions().apply { enabled = true }
        adapter.observeResult("session.resume", obj("""{"session_id":"s"}"""), obj("""{"session_id":"s","authority_epoch":2,"execution_generation":9,"info":{}}"""), null)
        assertFalse(adapter.observeEvent(obj("""{"session_id":"s","authority_epoch":2,"execution_generation":8,"seq":10}"""), null))
        assertEquals(9, adapter.snapshot("s", null)!!["execution_generation"]!!.jsonPrimitive.int)
    }

    @Test fun oldEpochRevisionAndReplaySnapshotCannotResurrectRetiredPrompt() {
        val adapter = GatewayCanonicalSessions().apply { enabled = true }
        val params = obj("""{"session_id":"s"}""")
        adapter.observeResult("session.resume", params, obj("""{"session_id":"s","authority_epoch":3,"execution_generation":9,"revision":8,"replay_epoch":"e","last_sequence":20,"prompts":[],"info":{}}"""), null)
        for (stale in listOf(
            """{"session_id":"s","authority_epoch":2,"execution_generation":99,"prompts":[{"kind":"approval","prompt_id":"p"}]}""",
            """{"session_id":"s","authority_epoch":3,"execution_generation":9,"revision":7,"prompts":[{"kind":"approval","prompt_id":"p"}]}""",
            """{"session_id":"s","authority_epoch":3,"execution_generation":9,"revision":8,"replay_epoch":"e","last_sequence":19,"prompts":[{"kind":"approval","prompt_id":"p"}]}""",
        )) adapter.observeResult("session.resume", params, obj(stale), null)
        assertEquals(3, adapter.snapshot("s", null)!!["authority_epoch"]!!.jsonPrimitive.int)
        assertTrue(adapter.promptEvents("s", null).isEmpty())
        assertTrue(adapter.observeEvent(obj("""{"session_id":"s","type":"session.info","payload":{"authority_epoch":3,"execution_generation":10,"revision":9}}"""), null))
        assertEquals(10, adapter.snapshot("s", null)!!["execution_generation"]!!.jsonPrimitive.int)
    }
}
