package com.hermesandroid.relay.viewmodel

import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.hermesandroid.relay.data.AgentDisplay
import com.hermesandroid.relay.data.MessageRole
import com.hermesandroid.relay.network.upstream.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

/** Goal commands must act on the selected conversation and consume native dispatch directives. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatViewModelGoalCommandTest {
    private lateinit var harness: GatewayClientHarness
    private lateinit var scope: CoroutineScope
    private lateinit var client: GatewayChatClient
    private lateinit var handler: ChatHandler
    private lateinit var vm: ChatViewModel
    private val store = ViewModelStore()

    @Before fun setup() {
        harness = GatewayClientHarness(advanceCallbacks = { shadowOf(Looper.getMainLooper()).idle() })
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        client = GatewayChatClient(
            DashboardApiClient(harness.server.url("/").toString().trimEnd('/'), OkHttpClient()),
            okHttpClient = OkHttpClient(), callbackDispatcher = { it() }, scope = scope,
        )
        client.setKeepAliveInBackground(true)
        handler = ChatHandler().also { it.setSessionId(STORED) }
        vm = ChatViewModel().also {
            it.initialize(null, handler)
            it.streamingEndpoint = "gateway"
            it.setProfileMessageLoader { Result.success(emptyList()) }
            it.updateGatewayClient(client)
            it.switchProfileContext(AgentDisplay.profileContextKey("connection-a", null), STORED)
        }
        store.put("goal", vm)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After fun cleanup() { store.clear(); client.shutdown(); scope.cancel(); harness.shutdown() }

    @Test fun publicGoalCatalogReplyCannotMoveTheInvocationToAnotherOwner() = runBlocking {
        harness.suppressAckMethods.add("commands.catalog")
        val before = vm.viewModelScope.coroutineContext[Job]!!.children.toSet()
        vm.sendMessage("/goal")
        val commandJob = vm.viewModelScope.coroutineContext[Job]!!.children.single { it !in before }
        val ack = harness.awaitPendingAck()
        assertEquals("commands.catalog", ack.method)
        vm.switchProfileContext(AgentDisplay.profileContextKey("connection-b", null), "foreign-conversation")
        shadowOf(Looper.getMainLooper()).idle()
        val catalog = buildJsonObject { put("pairs", JsonArray(listOf(JsonArray(listOf(JsonPrimitive("/goal"), JsonPrimitive("Manage goal")))))) }
        harness.releaseAck(ack, catalog)
        await {
            while (true) {
                val extra = harness.pendingAcks.poll() ?: break
                harness.releaseAck(extra, catalog)
            }
            shadowOf(Looper.getMainLooper()).idle()
            !commandJob.isActive
        }
        assertTrue(harness.rpcLog.none { it.first in setOf("session.resume", "session.create", "slash.exec", "command.dispatch", "prompt.submit") })
        assertTrue(handler.messages.value.isEmpty())
    }
    @Test fun coldObservedGoalResumesExactStoredConversation() = runBlocking {
        observe()
        harness.rpcErrors["command.dispatch"] = 4001 to "no active session"
        invokeSlash("/goal")
        assertEquals(STORED, harness.rpcLog.single { it.first == "session.resume" }.second["session_id"]?.jsonPrimitive?.content)
        assertEquals(LIVE, harness.awaitRpc("slash.exec")["session_id"]?.jsonPrimitive?.content)
        assertEquals(0, harness.rpcLog.count { it.first == "session.create" })
        assertTrue(handler.messages.value.none { it.content.contains("no active session") })
    }

    @Test fun idleReconnectAfterSuccessfulSendResumesTheSameGoalConversation() = runBlocking {
        val socket = completeNormalTurn()
        socket.close(1001, "Synthetic idle reconnect")
        await { client.connectionState.value == GatewayConnectionState.Idle }
        observe()
        harness.rpcErrors["command.dispatch"] = 4001 to "no active session"
        invokeSlash("/goal")
        val resumes = harness.rpcLog.filter { it.first == "session.resume" }
        assertEquals(2, resumes.size)
        assertTrue(resumes.all { it.second["session_id"]?.jsonPrimitive?.content == STORED })
        assertEquals(LIVE, harness.awaitRpc("slash.exec")["session_id"]?.jsonPrimitive?.content)
        assertEquals(0, harness.rpcLog.count { it.first == "session.create" })
    }

    @Test fun goalImmediatelyAfterSuccessfulSendKeepsExistingBinding() = runBlocking {
        completeNormalTurn()
        invokeSlash("/goal")
        assertEquals(LIVE, harness.awaitRpc("slash.exec")["session_id"]?.jsonPrimitive?.content)
        assertEquals(1, harness.rpcLog.count { it.first == "session.resume" })
        assertEquals(1, harness.rpcLog.count { it.first == "prompt.submit" })
    }

    @Test fun goalKickoffSubmitsTransportPromptWithLiteralDisplay() = runBlocking {
        assertTrue(client.prewarmAwait(STORED))
        harness.awaitServerSocket()
        harness.suppressAckMethods.add("slash.exec")
        val work = scope.async { invokeSlash("/goal resume") }
        val ack = harness.awaitPendingAck()
        harness.releaseAck(ack, goalDirective())
        work.await()
        val submit = harness.awaitRpc("prompt.submit")
        assertEquals(LIVE, submit["session_id"]?.jsonPrimitive?.content)
        assertEquals("Synthetic expanded goal step", submit["text"]?.jsonPrimitive?.content)
        assertTrue(handler.messages.value.any { it.role == MessageRole.USER && it.content == "/goal resume" })
        assertTrue(handler.messages.value.any { it.content == "Synthetic goal resumes" })
        assertTrue(handler.messages.value.none { it.content.contains("Synthetic expanded goal step") })
    }

    @Test fun goalReplyAfterOwnerSwitchCannotSubmitForeignKickoff() = runBlocking {
        assertTrue(client.prewarmAwait(STORED))
        harness.awaitServerSocket()
        harness.suppressAckMethods.add("slash.exec")
        val work = scope.async { invokeSlash("/goal resume") }
        val ack = harness.awaitPendingAck()
        vm.switchProfileContext(AgentDisplay.profileContextKey("connection-b", null), "foreign-conversation")
        shadowOf(Looper.getMainLooper()).idle()
        harness.releaseAck(ack, goalDirective())
        work.await()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, harness.rpcLog.count { it.first == "prompt.submit" })
        assertTrue(handler.messages.value.isEmpty())
    }

    @Test fun lateNonGoalDirectiveCannotSubmitIntoChangedOwner() = runBlocking {
        assertTrue(client.prewarmAwait(STORED))
        harness.awaitServerSocket()
        harness.suppressAckMethods.add("slash.exec")
        val work = scope.async { invokeSlash("/loop") }
        val ack = harness.awaitPendingAck()
        vm.switchProfileContext(AgentDisplay.profileContextKey("connection-b", null), "foreign-conversation")
        shadowOf(Looper.getMainLooper()).idle()
        harness.releaseAck(ack, goalDirective())
        work.await()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, harness.rpcLog.count { it.first == "prompt.submit" })
        assertTrue(handler.messages.value.isEmpty())
    }

    @Test fun returningToSameOwnerDoesNotReviveAnOldGoalDirective() = runBlocking {
        assertTrue(client.prewarmAwait(STORED))
        harness.awaitServerSocket()
        harness.suppressAckMethods.add("slash.exec")
        val work = scope.async { invokeSlash("/goal resume") }
        val ack = harness.awaitPendingAck()
        vm.switchProfileContext(AgentDisplay.profileContextKey("connection-b", null), "foreign-conversation")
        vm.switchProfileContext(AgentDisplay.profileContextKey("connection-a", null), STORED)
        shadowOf(Looper.getMainLooper()).idle()
        harness.releaseAck(ack, goalDirective())
        work.await()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, harness.rpcLog.count { it.first == "prompt.submit" })
        assertTrue(handler.messages.value.isEmpty())
    }
    @Test fun ownerSwitchDuringGoalAttachmentDoesNotDispatch() = runBlocking {
        observe()
        harness.suppressAckMethods.add("session.resume")
        val work = scope.async { invokeSlash("/goal") }
        val ack = harness.awaitPendingAck()
        vm.switchProfileContext(AgentDisplay.profileContextKey("connection-b", null), "foreign-conversation")
        shadowOf(Looper.getMainLooper()).idle()
        harness.releaseAck(ack, harness.recoveryResult(LIVE))
        work.await()
        assertTrue(harness.rpcLog.none { it.first in setOf("slash.exec", "command.dispatch", "prompt.submit") })
    }

    @Test fun genuineDraftGoalCreatesOnlyItsNormalSelectedSession() = runBlocking {
        vm.switchProfileContext(AgentDisplay.profileContextKey("connection-a", null), null)
        client.clearSession()
        shadowOf(Looper.getMainLooper()).idle()
        invokeSlash("/goal")
        val created = harness.rpcLog.single { it.first == "session.create" }
        assertEquals("webui", created.second["source"]?.jsonPrimitive?.content)
        assertEquals("20260612_120000_abc123", handler.currentSessionId.value)
        assertEquals("live-1", harness.awaitRpc("slash.exec")["session_id"]?.jsonPrimitive?.content)
        assertEquals(0, harness.rpcLog.count { it.first == "session.resume" })
    }

    @Test fun existingGoalResumeFailureDoesNotCreateOrDispatch() = runBlocking {
        observe()
        harness.resumeFails = true
        invokeSlash("/goal")
        assertEquals(1, harness.rpcLog.count { it.first == "session.resume" })
        assertTrue(harness.rpcLog.none { it.first in setOf("session.create", "slash.exec", "command.dispatch", "prompt.submit") })
        assertTrue(handler.messages.value.any { it.content.contains("/goal failed:") })
    }

    @Test fun quickCommandRemainsSessionlessWithoutCreatingConversation() = runBlocking {
        observe()
        invokeSlash("/help")
        assertFalse(harness.awaitRpc("command.dispatch").containsKey("session_id"))
        assertTrue(harness.rpcLog.none { it.first == "session.resume" || it.first == "session.create" })
    }

    private fun goalDirective() = buildJsonObject {
        put("type", "send"); put("notice", "Synthetic goal resumes")
        put("message", "Synthetic expanded goal step"); put("display", "/goal resume")
    }

    private fun observe() {
        client.observe(); harness.awaitServerSocket()
        await { client.connectionState.value == GatewayConnectionState.Ready }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun completeNormalTurn(): okhttp3.WebSocket {
        val completed = CountDownLatch(1)
        val errors = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val callbacks = GatewayTurnCallbacks(
            onSessionId = {}, onStart = {}, onTextDelta = {}, onThinkingDelta = {},
            onToolCallStart = { _, _, _ -> }, onToolCallDone = { _, _ -> }, onToolCallFailed = { _, _ -> },
            onTurnComplete = {}, onReconcileRequired = {}, onComplete = { completed.countDown() }, onUsage = {},
            onError = { errors.add(it); completed.countDown() }, onToolGenerating = {}, onSubagentEvent = {},
            onMoaReference = {}, onInteractionRequest = {}, onInteractionExpired = {},
        )
        client.sendTurn(STORED, "Synthetic normal message", null, callbacks) { errors.add(it) }
        val socket = harness.awaitServerSocket()
        harness.awaitRpc("prompt.submit")
        socket.send(harness.eventFrame("message.start", null, LIVE))
        socket.send(harness.eventFrame("message.complete", buildJsonObject { put("text", "Synthetic answer") }, LIVE))
        assertTrue(completed.await(5, TimeUnit.SECONDS)); assertTrue(errors.isEmpty())
        return socket
    }

    private suspend fun invokeSlash(line: String) = suspendCoroutine<Unit> { continuation ->
        val method = ChatViewModel::class.java.getDeclaredMethod(
            "runServerSlashCommand", GatewayChatClient::class.java, ChatHandler::class.java,
            String::class.java, Integer.TYPE, Continuation::class.java,
        ).apply { isAccessible = true }
        val result = method.invoke(vm, client, handler, line, 0, continuation)
        if (result !== COROUTINE_SUSPENDED) continuation.resume(Unit)
    }

    private fun await(condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < end) { if (condition()) return; Thread.sleep(10) }
        assertTrue("Synthetic goal condition did not settle", condition())
    }

    private companion object {
        const val STORED = "20261015_120000_fixture"
        const val LIVE = "live-resumed"
    }
}
