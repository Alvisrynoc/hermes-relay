package com.hermesandroid.relay.network.upstream

import kotlinx.serialization.json.*
import java.util.UUID

internal data class GatewayCanonicalRequest(val method: String, val params: JsonObject)
data class GatewayAdmissionIdentity(val submissionId: String?, val admissionId: String?, val cancelRequested: Boolean = false,
    val editUnconfirmed: Boolean = false)

/** Maps Android's presentation model onto the advertised session-authority wire.
 * Fences and prompt identities come only from owner snapshots/events, never guessed defaults.
 */
internal class GatewayCanonicalSessions {
    @Volatile var enabled = false
    private data class Owner(val profile: String, val session: String)
    private val snapshots = mutableMapOf<Owner, JsonObject>()
    private val prompts = mutableMapOf<Owner, MutableMap<String, JsonObject>>()
    private fun owner(sid: String, profile: String?) = Owner(profile.orEmpty().ifBlank { "default" }, sid)

    @Synchronized fun reset() { enabled = false; snapshots.clear(); prompts.clear() }
    @Synchronized fun snapshot(sessionId: String, profile: String?): JsonObject? = snapshots[owner(sessionId, profile)]
    @Synchronized fun profileForSession(sessionId: String): String? = snapshots.keys.filter { it.session == sessionId }.singleOrNull()?.profile

    @Synchronized fun prepare(method: String, params: JsonObject, profile: String?): GatewayCanonicalRequest {
        if (!enabled) return GatewayCanonicalRequest(method, params)
        val sid = params.text("session_id").orEmpty()
        val key = owner(sid, params.text("profile") ?: profile)
        val values = params.toMutableMap()
        if (sid.isNotEmpty() || method == "session.create") {
            profile?.let { values.putIfAbsent("profile", JsonPrimitive(it)) }
        }
        var native = method
        when (method) {
            "session.create" -> {
                values.remove("cols")
                values["source"] = JsonPrimitive("gui")
                values.putIfAbsent("request_id", JsonPrimitive(UUID.randomUUID().toString()))
                values.remove("reasoning_effort")?.let { values["reasoning"] = it }
                values.remove("fast")?.let { values["service_tier"] = JsonPrimitive(if (it.jsonPrimitive.booleanOrNull == true) "fast" else "normal") }
            }
            "session.activate", "session.resume" -> {
                native = "session.resume"
                values.keys.retainAll(setOf("session_id", "profile", "defer_history", "omit_messages", "cols", "display_projection"))
                // Authority snapshots are already bounded by the requested history policy.
                // Android needs the display projection when recovering its transcript.
                if (params["omit_messages"]?.jsonPrimitive?.booleanOrNull != true)
                    values["display_projection"] = JsonPrimitive(true)
            }
            "prompt.submit" -> {
                require(params["confirm_truncate"] == null) { "Canonical rewind must commit before admission" }
                values.putIfAbsent("submission_id", JsonPrimitive(UUID.randomUUID().toString()))
                values["clarify_batch"] = JsonPrimitive(true)
            }
            "session.interrupt", "session.steer", "session.redirect" -> {
                val generation = params["execution_generation"] ?: snapshots[key]?.get("execution_generation")
                require(generation?.jsonPrimitive?.longOrNull != null) { "Session control needs a current owner snapshot" }
                values["execution_generation"] = generation
            }
            "approval.respond", "clarify.respond" -> {
                val kind = if (method == "approval.respond") "approval" else "clarify"
                val candidates = prompts[key].orEmpty().values.filter { it.text("kind") == kind }
                val requested = params.text("request_id") ?: params.text("prompt_id")
                val prompt = if (requested != null) candidates.singleOrNull { it.text("prompt_id") == requested }
                    else candidates.singleOrNull()
                require(prompt != null) { "Prompt is no longer active or its identity is ambiguous" }
                require(params["all"]?.jsonPrimitive?.booleanOrNull != true) { "Canonical approvals require one exact prompt" }
                values.keys.retainAll(setOf("session_id", "profile", "choice", "answer"))
                values["prompt_id"] = prompt.getValue("prompt_id")
                values["execution_generation"] = prompt.getValue("execution_generation")
                if (kind == "approval") require(params.text("choice") in prompt.array("choices").map { it.jsonPrimitive.content }) { "Approval choice is not offered by the owner" }
            }
            "session.events.since" -> {
                val snapshot = snapshots[key]
                val requested = params["last_sequence"] ?: params["last_seen"]
                values["last_sequence"] = requested?.takeUnless { it.jsonPrimitive.longOrNull == Long.MAX_VALUE }
                    ?: snapshot?.get("last_sequence") ?: JsonPrimitive(0)
                snapshot?.get("replay_epoch")?.let { values.putIfAbsent("replay_epoch", it) }
            }
        }
        return GatewayCanonicalRequest(native, JsonObject(values))
    }

    @Synchronized fun observeResult(method: String, params: JsonObject, result: JsonObject, profile: String?): JsonObject {
        if (!enabled) return result
        val sid = result.text("session_id") ?: (result["ref"] as? JsonObject)?.text("session_id") ?: params.text("session_id")
        if (sid != null && method in setOf("approval.respond", "clarify.respond") && result.text("status") in setOf("resolved", "already_resolved")) {
            params.text("prompt_id")?.let { prompts[owner(sid, params.text("profile") ?: profile)]?.remove(it) }
        }
        if (sid != null && (result.number("execution_generation") != null || result.number("revision") != null)) {
            val key = owner(sid, params.text("profile") ?: profile)
            val previous = snapshots[key]
            val epoch = result.number("authority_epoch") ?: previous?.number("authority_epoch")
            val generation = result.number("execution_generation") ?: previous?.number("execution_generation") ?: 0
            val oldEpoch = previous?.number("authority_epoch")
            val sameEpoch = epoch == oldEpoch
            val stale = previous != null && ((epoch != null && oldEpoch != null && epoch < oldEpoch) ||
                (sameEpoch && generation < (previous.number("execution_generation") ?: 0)) ||
                (sameEpoch && result.number("revision") != null && result.number("revision")!! < (previous.number("revision") ?: 0)) ||
                (sameEpoch && result.text("replay_epoch") == previous.text("replay_epoch") && result.number("last_sequence") != null &&
                    result.number("last_sequence")!! < (previous.number("last_sequence") ?: 0)))
            if (!stale) {
                snapshots[key] = JsonObject(previous.orEmpty() + result)
                if (result.containsKey("prompts")) prompts[key] = result.array("prompts").mapNotNull { it as? JsonObject }
                    .associateBy { it.text("prompt_id").orEmpty() }.toMutableMap()
            }
        }
        if (method in setOf("approval.respond", "clarify.respond") && result.text("status") == "already_resolved")
            return JsonObject(result + ("status" to JsonPrimitive("expired")))
        if (method !in setOf("session.create", "session.resume", "session.activate")) return result
        val info = result["info"] as? JsonObject ?: JsonObject(emptyMap())
        val name = info.text("profile_name") ?: profile ?: "default"
        return JsonObject(result + mapOf("info" to JsonObject(info + ("profile" to JsonPrimitive(name))),
            "profile" to JsonPrimitive(name), "status" to JsonPrimitive(if (result["running"]?.jsonPrimitive?.booleanOrNull == true) "running" else "idle")))
    }

    /** False means a delayed frame belongs to a retired generation and cannot update UI/control state. */
    @Synchronized fun observeEvent(envelope: JsonObject, profile: String?): Boolean {
        if (!enabled) return true
        val sid = envelope.text("session_id") ?: return true
        // The transport has no profile stamp on events. Reuse a unique attached owner only.
        val owners = snapshots.keys.filter { it.session == sid }
        val key = owners.singleOrNull() ?: if (owners.isEmpty()) owner(sid, profile) else return false
        val previous = snapshots[key]
        val payload = envelope["payload"] as? JsonObject
        val type = envelope.text("type").orEmpty()
        // session.info projects the current handle even if the stream is still stamped
        // with the admission whose settlement caused that projection.
        val generation = if (type == "session.info") payload?.number("execution_generation") ?: envelope.number("execution_generation")
            else envelope.number("execution_generation") ?: payload?.number("execution_generation")
        val epoch = if (type == "session.info") payload?.number("authority_epoch") ?: envelope.number("authority_epoch")
            else envelope.number("authority_epoch") ?: payload?.number("authority_epoch")
        if (previous != null && epoch != null && previous.number("authority_epoch") != null) {
            if (epoch < previous.number("authority_epoch")!!) return false
            if (epoch == previous.number("authority_epoch") && generation != null && generation < (previous.number("execution_generation") ?: 0)) return false
        }
        if (previous != null && envelope.text("replay_epoch") == previous.text("replay_epoch") && envelope.number("seq") != null &&
            envelope.number("seq")!! < (previous.number("last_sequence") ?: 0)) return false
        val updates = mutableMapOf<String, JsonElement>()
        for (field in listOf("authority_epoch", "execution_generation", "replay_epoch")) envelope[field]?.let { updates[field] = it }
        envelope["seq"]?.let { updates["last_sequence"] = it }
        snapshots[key] = JsonObject(previous.orEmpty() + updates)
        if (type == "session.info" && payload != null) {
            val fields = payload.filterKeys { it in setOf("authority_epoch", "execution_generation", "revision", "pending", "running") }
            snapshots[key] = JsonObject(snapshots[key].orEmpty() + fields)
        }
        if (type in setOf("approval.request", "clarify.request") && payload?.text("prompt_id") != null) {
            prompts.getOrPut(key) { mutableMapOf() }[payload.text("prompt_id")!!] = payload
        }
        if (type in setOf("approval.settled", "clarify.settled")) payload?.text("prompt_id")?.let { prompts[key]?.remove(it) }
        return true
    }

    fun promptPayload(payload: JsonObject?): JsonObject? = payload?.let {
        if (it.text("prompt_id") == null) it else JsonObject(it + mapOf("request_id" to it.getValue("prompt_id"),
            "_canonical_prompt" to JsonPrimitive(true)) + (it["timeout_s"]?.let { timeout -> mapOf("timeout_seconds" to timeout) } ?: emptyMap()))
    }

    @Synchronized fun promptEvents(sessionId: String, profile: String?): List<Pair<String, JsonObject>> =
        prompts[owner(sessionId, profile)].orEmpty().values.map { "${it.text("kind")}.request" to promptPayload(it)!! }
}

private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive)?.longOrNull
private fun JsonObject.array(key: String) = get(key) as? JsonArray ?: JsonArray(emptyList())
