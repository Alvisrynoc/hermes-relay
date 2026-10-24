package com.hermesandroid.relay.network.upstream

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class GatewayCanonicalMutationsTest {
    private val snapshot = buildJsonObject {
        put("session_id", "s"); put("revision", 4); put("execution_generation", 2)
    }

    @Test fun `model picker uses fenced session mutation`() = runTest {
        val calls = mutableListOf<Pair<String, JsonObject>>()
        val adapter = GatewayCanonicalMutations()
        val result = adapter.execute("config.set", buildJsonObject {
            put("session_id", "s"); put("key", "model"); put("value", "model-b --provider custom --session")
        }, snapshot) { method, params ->
            calls += method to params
            Result.success(buildJsonObject { put("model", "model-b"); put("revision", 5) })
        }!!.getOrThrow()
        assertEquals("session.mutate", calls.single().first)
        val wire = calls.single().second
        assertEquals(4, wire["expected_revision"]!!.jsonPrimitive.int)
        assertEquals(2, wire["expected_generation"]!!.jsonPrimitive.int)
        assertEquals("custom", wire["payload"]!!.jsonObject["provider"]!!.jsonPrimitive.content)
        assertEquals("model-b", result["value"]!!.jsonPrimitive.content)
    }

    @Test fun `ambiguous mutation retry retains exact request and fence`() = runTest {
        val adapter = GatewayCanonicalMutations()
        val calls = mutableListOf<JsonObject>()
        val params = buildJsonObject { put("session_id", "s"); put("key", "reasoning"); put("value", "high") }
        repeat(2) {
            adapter.execute("config.set", params, snapshot) { _, wire ->
                calls += wire; Result.failure(java.io.IOException("lost reply"))
            }
        }
        assertEquals(calls[0], calls[1])
    }

    @Test fun `typed storage error does not prove mutation had no effect`() = runTest {
        val adapter = GatewayCanonicalMutations()
        val calls = mutableListOf<JsonObject>()
        val params = buildJsonObject { put("session_id", "s"); put("key", "reasoning"); put("value", "high") }
        repeat(2) {
            adapter.execute("config.set", params, snapshot) { _, wire ->
                calls += wire; Result.failure(GatewayRpcException("storage_unavailable", 4001))
            }
        }
        assertEquals(calls[0], calls[1])
    }

    @Test fun `rewind succeeds before one stripped submit`() = runTest {
        val calls = mutableListOf<Pair<String, JsonObject>>()
        GatewayCanonicalMutations().execute("prompt.submit", buildJsonObject {
            put("session_id", "s"); put("text", "edited"); put("submission_id", "input-1")
            put("confirm_truncate", true); put("truncate_before_row_id", 21)
        }, snapshot) { method, wire ->
            calls += method to wire; Result.success(buildJsonObject { put("revision", 5) })
        }!!.getOrThrow()
        assertEquals(listOf("session.mutate", "prompt.submit"), calls.map { it.first })
        assertEquals(21, calls.first().second["payload"]!!.jsonObject["target_message_id"]!!.jsonPrimitive.int)
        assertFalse(calls.last().second.containsKey("confirm_truncate"))
        assertFalse(calls.last().second.containsKey("truncate_before_row_id"))
        assertEquals("input-1", calls.last().second["submission_id"]!!.jsonPrimitive.content)
    }

    @Test fun `rewind failure never submits or retries as another target`() = runTest {
        val calls = mutableListOf<String>()
        val result = GatewayCanonicalMutations().execute("prompt.submit", buildJsonObject {
            put("session_id", "s"); put("text", "edited"); put("confirm_truncate", true)
            put("truncate_before_row_id", 21)
        }, snapshot) { method, _ -> calls += method; Result.failure(java.io.IOException("lost reply")) }!!
        assertTrue(result.isFailure)
        assertEquals(listOf("session.mutate"), calls)
    }

    @Test fun `ordinal only cut is refused before any write`() = runTest {
        var writes = 0
        val result = GatewayCanonicalMutations().execute("prompt.submit", buildJsonObject {
            put("session_id", "s"); put("text", "edited"); put("confirm_truncate", true)
            put("truncate_before_user_ordinal", 2)
        }, snapshot) { _, _ -> writes++; Result.success(snapshot) }!!
        assertTrue(result.isFailure)
        assertEquals(0, writes)
    }

    @Test fun `confirmation token is replayed only for exact model and owner`() = runTest {
        val calls = mutableListOf<JsonObject>()
        val adapter = GatewayCanonicalMutations()
        val params = buildJsonObject { put("session_id", "s"); put("key", "model"); put("value", "large --provider custom") }
        adapter.execute("config.set", params, snapshot) { _, wire ->
            calls += wire
            Result.success(buildJsonObject { put("confirm_required", true); put("confirm", "owner-token"); put("confirm_message", "Confirm selection") })
        }!!.getOrThrow()
        adapter.execute("config.set", JsonObject(params + ("confirm_expensive_model" to JsonPrimitive(true))), snapshot) { _, wire ->
            calls += wire; Result.success(buildJsonObject { put("model", "large") })
        }!!.getOrThrow()
        assertEquals("owner-token", calls.last()["payload"]!!.jsonObject["confirm"]!!.jsonPrimitive.content)
        assertNotEquals(calls.first()["request_id"], calls.last()["request_id"])
    }

    @Test fun `unrelated config remains on native config interface`() = runTest {
        val result = GatewayCanonicalMutations().execute("config.set", buildJsonObject {
            put("session_id", "s"); put("key", "fast"); put("value", "1")
        }, snapshot) { _, _ -> fail("Not a mutation"); Result.success(snapshot) }
        assertNull(result)
    }

    @Test fun `named profile is retained on preparatory reads and mutations`() = runTest {
        val calls = mutableListOf<Pair<String, JsonObject>>()
        GatewayCanonicalMutations().execute("config.set", buildJsonObject {
            put("session_id", "s"); put("profile", "engineer"); put("key", "reasoning"); put("value", "high")
        }, null) { method, wire -> calls += method to wire; Result.success(snapshot) }!!.getOrThrow()
        assertEquals(listOf("session.resume", "session.mutate"), calls.map { it.first })
        assertTrue(calls.all { it.second["profile"]!!.jsonPrimitive.content == "engineer" })
    }

    @Test fun `lost submit reply replays original rewind receipt before same admission`() = runTest {
        val calls = mutableListOf<Pair<String, JsonObject>>()
        val adapter = GatewayCanonicalMutations()
        val params = buildJsonObject {
            put("session_id", "s"); put("text", "edited"); put("submission_id", "input-1")
            put("confirm_truncate", true); put("truncate_before_row_id", 21)
        }
        repeat(2) {
            adapter.execute("prompt.submit", params, snapshot) { method, wire ->
                calls += method to wire
                if (method == "session.mutate") Result.success(snapshot)
                else Result.failure(java.io.IOException("lost submit reply"))
            }
        }
        assertEquals(calls[0], calls[2])
        assertEquals(calls[1], calls[3])
    }

    @Test fun `compression preserves focus and returns refreshed display transcript`() = runTest {
        val calls = mutableListOf<Pair<String, JsonObject>>()
        val messages = buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", "summary") }) }
        val result = GatewayCanonicalMutations().execute("session.compress", buildJsonObject {
            put("session_id", "s"); put("focus_topic", "preserve decisions")
        }, snapshot) { method, wire ->
            calls += method to wire
            Result.success(if (method == "session.resume") buildJsonObject {
                put("messages", JsonArray(emptyList())); put("display_messages", messages)
            } else buildJsonObject { put("status", "completed"); put("revision", 5) })
        }!!.getOrThrow()
        assertEquals(listOf("session.mutate", "session.resume"), calls.map { it.first })
        assertEquals("preserve decisions", calls.first().second["payload"]!!.jsonObject["focus"]!!.jsonPrimitive.content)
        assertEquals(messages, result["messages"])
        assertEquals(true, calls.last().second["display_projection"]!!.jsonPrimitive.boolean)
    }
}
