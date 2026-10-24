package com.hermesandroid.relay.viewmodel

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.hermesandroid.relay.data.AgentDisplay
import com.hermesandroid.relay.data.Profile
import com.hermesandroid.relay.network.upstream.ChatHandler
import com.hermesandroid.relay.network.upstream.DashboardApiClient
import com.hermesandroid.relay.network.upstream.GatewayChatClient
import com.hermesandroid.relay.network.upstream.GatewayConnectionState
import com.hermesandroid.relay.network.upstream.models.MessageItem
import com.hermesandroid.relay.ui.LocalSnackbarHost
import com.hermesandroid.relay.ui.components.LocalMessageActionHost
import com.hermesandroid.relay.ui.components.avatar.LocalBackgroundVisualizationEnabled
import com.hermesandroid.relay.ui.screens.ChatScreen
import com.hermesandroid.relay.ui.theme.HermesRelayTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/** One provider-free Android check for selected-session /goal and native send directives. */
class GoalCommandInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var fixture: AndroidGatewayContractFixture
    private lateinit var scope: CoroutineScope
    private lateinit var dashboard: DashboardApiClient
    private lateinit var client: GatewayChatClient
    private lateinit var handler: ChatHandler
    private lateinit var vm: ChatViewModel
    private lateinit var connection: ConnectionViewModel
    private lateinit var voice: VoiceViewModel
    private val store = ViewModelStore()
    private val profile = Profile(name = PROFILE, model = "synthetic")
    @Volatile private var history = listOf(MessageItem(
        id = "initial", role = "assistant", content = JsonPrimitive("Initial synthetic history"),
    ))

    @Before fun setup() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        fixture = AndroidGatewayContractFixture().also {
            it.profileName = PROFILE
            it.slashCommandResult = buildJsonObject {
                put("type", "send"); put("notice", "Synthetic goal resumes")
                put("message", "Synthetic expanded goal step"); put("display", "/goal resume")
            }
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val http = OkHttpClient()
        dashboard = DashboardApiClient(fixture.server.url("/").toString().trimEnd('/'), http)
        client = GatewayChatClient(dashboard, okHttpClient = http,
            callbackDispatcher = { Handler(Looper.getMainLooper()).post(it) }, scope = scope)
        client.setKeepAliveInBackground(true)
        handler = ChatHandler()
        compose.runOnUiThread {
            vm = ChatViewModel().also {
                it.initializeGatewayOnly(app)
                it.initialize(null, handler)
                it.streamingEndpoint = "gateway"
                it.setSelectedProfileProvider { profile }
                it.setSessionProfileNameProvider { PROFILE }
                it.setProfileMessageLoader { Result.success(history) }
                it.updateGatewayClient(client)
            }
            connection = ConnectionViewModel(app).also { it.setStreamingEndpoint("gateway") }
            voice = VoiceViewModel(app)
            store.put("chat", vm); store.put("connection", connection); store.put("voice", voice)
        }
        waitFor { connection.streamingEndpoint.value == "gateway" }
        compose.setContent {
            val snackbar = remember { SnackbarHostState() }
            val actions = remember { SnackbarHostState() }
            CompositionLocalProvider(LocalSnackbarHost provides snackbar,
                LocalMessageActionHost provides actions, LocalBackgroundVisualizationEnabled provides false) {
                HermesRelayTheme(themePreference = "dark") { ChatScreen(vm, connection, voice) }
            }
        }
    }

    @After fun cleanup() {
        compose.runOnUiThread { vm.updateGatewayClient(null); store.clear() }
        client.shutdown(); scope.cancel(); dashboard.shutdown(); fixture.shutdown()
    }

    @Test fun goalResumeAttachesSelectedChatAndRendersItsContinuation() {
        compose.runOnUiThread { assertTrue(vm.openProfileSession(PROFILE, profile,
            AgentDisplay.profileContextKey("fixture-connection", PROFILE), STORED)) }
        waitFor { !vm.isLoadingHistory.value && !vm.isRefreshingHistory.value &&
            handler.messages.value.any { it.content == "Initial synthetic history" } &&
            client.connectionState.value == GatewayConnectionState.Ready }
        val socket = fixture.awaitServerSocket()
        assertEquals(0, fixture.rpcCount("session.resume"))
        compose.runOnUiThread { vm.sendMessage("/goal resume") }
        val submit = fixture.awaitRpc("prompt.submit")
        val resume = fixture.awaitRpc("session.resume")
        assertEquals(STORED, resume["session_id"]?.jsonPrimitive?.content)
        assertEquals(PROFILE, resume["profile"]?.jsonPrimitive?.content)
        assertEquals(LIVE, submit["session_id"]?.jsonPrimitive?.content)
        assertEquals("Synthetic expanded goal step", submit["text"]?.jsonPrimitive?.content)
        waitFor { handler.messages.value.any { it.content == "/goal resume" } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Synthetic goal resumes"))
        compose.onNodeWithText("Synthetic goal resumes").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("/goal resume"))
        compose.onNodeWithText("/goal resume").assertIsDisplayed()
        assertTrue(handler.messages.value.none { it.content.contains("Synthetic expanded goal step") })
        history = history + listOf(
            MessageItem(id = "goal-command", role = "user", content = JsonPrimitive("/goal resume")),
            MessageItem(id = "goal-reply", role = "assistant", content = JsonPrimitive("Synthetic goal continued")),
        )
        socket.send(fixture.event("message.start", null, LIVE))
        socket.send(fixture.event("message.complete", buildJsonObject { put("text", "Synthetic goal continued") }, LIVE))
        waitFor { !handler.isStreaming.value && handler.messages.value.any { it.content == "Synthetic goal continued" } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Synthetic goal continued", substring = true))
        compose.onNodeWithText("Synthetic goal continued", substring = true).assertIsDisplayed()
        assertEquals(1, fixture.rpcCount("session.resume"))
        assertEquals(1, fixture.rpcCount("prompt.submit"))
        assertEquals(0, fixture.rpcCount("session.create"))
        assertEquals(0, fixture.rpcCount("session.interrupt"))
    }

    private fun waitFor(condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < end) { if (condition()) return; Thread.sleep(25) }
        assertTrue("Synthetic goal Android condition did not settle", condition())
    }
    private companion object {
        const val PROFILE = "goal-test-profile"
        const val STORED = "20260821_120000_fixture"
        const val LIVE = "fixture-live-1"
    }
}
