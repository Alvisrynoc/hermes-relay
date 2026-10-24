package com.hermesandroid.relay.network.upstream

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Native receipt-based policy edits. Never retries an ambiguous write with a new identity. */
internal class GatewayCanonicalMutations {
    private val retained = ConcurrentHashMap<String, JsonObject>()
    private val confirmations = ConcurrentHashMap<String, String>()
    fun reset() { retained.clear(); confirmations.clear() }

    suspend fun execute(
        method: String,
        params: JsonObject,
        snapshot: JsonObject?,
        call: suspend (String, JsonObject) -> Result<JsonObject>,
    ): Result<JsonObject>? {
        val key = params.text("key")
        val truncate = method == "prompt.submit" && params.keys.any { it in truncateKeys }
        if (!truncate && method != "session.compress" &&
            !(method == "config.set" && key in setOf("model", "reasoning"))) return null
        return try {
            val sid = params.text("session_id") ?: error("Open the conversation before changing it")
            val scope = buildJsonObject {
                put("session_id", sid); params["profile"]?.let { put("profile", it) }
            }
            val operation: String
            val payload: JsonObject
            if (truncate) {
                require(params["confirm_truncate"]?.jsonPrimitive?.booleanOrNull == true) { "History changes require confirmation" }
                val row = (params["truncate_before_row_id"] as? JsonPrimitive)?.longOrNull
                require(row != null && row > 0) { "This message needs a saved row identity; reopen history before editing it" }
                operation = "rewind"
                payload = buildJsonObject { put("target_message_id", row) }
            } else if (method == "session.compress") {
                operation = "compress"
                payload = buildJsonObject {
                    params.filterKeys { it in setOf("focus", "preview", "partial", "keep_last") }.forEach { (name, value) -> put(name, value) }
                    if (!params.containsKey("focus")) params["focus_topic"]?.let { put("focus", it) }
                }
            } else if (key == "reasoning") {
                operation = "reasoning"
                val effort = params.text("value") ?: error("Reasoning level is missing")
                payload = buildJsonObject { if (effort == "reset") put("effort", JsonNull) else put("effort", effort) }
            } else {
                operation = "model"
                payload = modelPayload(params.text("value") ?: error("Model is missing"))
            }
            val identity = listOf(scope.toString(), operation, payload.toString(),
                if (truncate) params.text("submission_id") ?: params.text("text") else "").joinToString("\n")
            val confirm = if (params["confirm_expensive_model"]?.jsonPrimitive?.booleanOrNull == true)
                confirmations[identity] ?: error("Request this exact model selection before confirming it") else null
            val requestKey = identity + (confirm ?: "")
            val request = retained[requestKey] ?: run {
                val state = snapshot?.takeIf { it.number("revision") != null && it.number("execution_generation") != null }
                    ?: call("session.resume", JsonObject(scope + ("omit_messages" to JsonPrimitive(true)))).getOrThrow()
                val prepared = buildJsonObject {
                    put("session_id", sid); params["profile"]?.let { put("profile", it) }
                    put("request_id", UUID.randomUUID().toString())
                    put("expected_revision", state.number("revision") ?: error("Session revision is unavailable"))
                    put("expected_generation", state.number("execution_generation") ?: error("Session generation is unavailable"))
                    put("operation", operation)
                    put("payload", if (confirm == null) payload else JsonObject(payload + ("confirm" to JsonPrimitive(confirm))))
                }
                retained.putIfAbsent(requestKey, prepared) ?: prepared
            }
            val changed = call("session.mutate", request).getOrElse { failure ->
                // Storage/publication errors can occur after commit, even with a typed RPC
                // code. Retire only explicit precondition refusals, never an unknown effect.
                if (failure is GatewayRpcException && failure.message in noEffectRefusals)
                    retained.remove(requestKey, request)
                throw failure
            }
            if (changed["confirm_required"]?.jsonPrimitive?.booleanOrNull == true) {
                changed.text("confirm")?.let { confirmations[identity] = it }
                retained.remove(requestKey, request)
                Result.success(changed)
            } else if (truncate) {
                // Keep the rewind receipt until submit is acknowledged. Retrying a lost submit
                // reply must replay the original cut, never cut the newly submitted turn again.
                val submitted = call("prompt.submit", JsonObject(params.filterKeys { it !in truncateKeys }))
                if (submitted.isSuccess) retained.remove(requestKey, request)
                submitted
            } else {
                val projected = if (operation == "compress" &&
                    changed.text("status") !in setOf("preview", "aborted", "noop", "no_op")) {
                    val refreshed = call("session.resume", buildJsonObject {
                        scope.forEach { (name, value) -> put(name, value) }
                        put("display_projection", true)
                    }).getOrThrow()
                    JsonObject(changed + mapOf("messages" to (refreshed["display_messages"] ?: refreshed["messages"]
                        ?: error("Compressed history snapshot is unavailable"))) +
                        (refreshed["info"]?.let { mapOf("info" to it) } ?: emptyMap()))
                } else changed
                retained.remove(requestKey, request)
                confirmations.remove(identity)
                Result.success(if (method == "config.set") JsonObject(projected + ("value" to
                    (projected[if (operation == "model") "model" else "effort"] ?: params.getValue("value")))) else projected)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    private fun modelPayload(value: String): JsonObject {
        val tokens = value.trim().split(Regex("\\s+")).toMutableList()
        if (tokens.lastOrNull() == "--session") tokens.removeAt(tokens.lastIndex)
        require(tokens.firstOrNull()?.let { it.isNotBlank() && !it.startsWith("-") } == true) { "Model is missing" }
        require(tokens.size == 1 || (tokens.size == 3 && tokens[1] == "--provider" && !tokens[2].startsWith("-"))) {
            "This model selection has flags unavailable on the session authority"
        }
        return buildJsonObject { put("model", tokens[0]); if (tokens.size == 3) put("provider", tokens[2]) }
    }

    private fun JsonObject.text(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.number(name: String) = (this[name] as? JsonPrimitive)?.longOrNull
    private val noEffectRefusals = setOf("revision_conflict", "stale_generation", "invalid_params", "permission_denied", "not_found")
    private val truncateKeys = setOf("confirm_truncate", "confirm_empty_truncate", "confirm_deep_truncate",
        "truncate_before_row_id", "truncate_before_message_id", "truncate_before_user_ordinal", "rebind_survivor_row_ids")
}
