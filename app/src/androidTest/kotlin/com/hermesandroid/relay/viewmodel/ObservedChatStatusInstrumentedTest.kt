package com.hermesandroid.relay.viewmodel

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.hermesandroid.relay.data.AgentDisplay
import com.hermesandroid.relay.data.SessionActivityRegistry
import com.hermesandroid.relay.data.SessionActivityUpdate
import com.hermesandroid.relay.data.SessionActivityPhase
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/** One synthetic Android check of the selected conversation status and child completion. */
class ObservedChatStatusInstrumentedTest {
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
            it.activeSessionStatus = "working"
            it.activeSubagentCount = 0
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

    @Test fun observedWorkingChildrenAndIdleStayVisibleWithoutOwningTheTurn() {
        compose.runOnUiThread { assertTrue(vm.openProfileSession(PROFILE, profile,
            AgentDisplay.profileContextKey("fixture-connection", PROFILE), STORED)) }
        waitFor { !vm.isLoadingHistory.value && !vm.isRefreshingHistory.value &&
            client.connectionState.value == GatewayConnectionState.Ready }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Agent working").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Agent working").assertIsDisplayed()
        assertTrue(!handler.isStreaming.value)
        fixture.activeSessionStatus = "idle"
        fixture.activeSubagentCount = 3
        compose.runOnUiThread { vm.requestSessionActivityRefresh() }
        waitFor { vm.observedSubagentCount.value == 3 }
        compose.onNodeWithText("Working with 3 subagents").assertIsDisplayed()
        fixture.activeSubagentCount = 0
        // Completion must arrive on the busy poll, not an explicit refresh or idle 30-second timer.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Agent ready").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Agent ready").assertIsDisplayed()
        assertEquals(0, vm.observedSubagentCount.value)
        fixture.activeListUnavailable = true
        compose.runOnUiThread { vm.requestSessionActivityRefresh() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Agent status unavailable").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Agent status unavailable").assertIsDisplayed()
        compose.onNodeWithText("Agent ready").assertDoesNotExist()
        assertTrue(!handler.isStreaming.value)
        for (method in listOf("session.resume", "session.activate", "session.create", "prompt.submit", "session.interrupt")) {
            assertEquals("passive status must not control the Desktop turn: $method", 0, fixture.rpcCount(method))
        }
    }

    @Test fun passivePollingKeepsTranscriptStableAndExplicitReloadShowsLoading() {
        fixture.activeSubagentCount = 2
        compose.runOnUiThread { assertTrue(vm.openProfileSession(PROFILE, profile,
            AgentDisplay.profileContextKey("fixture-connection", PROFILE), STORED)) }
        waitFor { !vm.isLoadingHistory.value && !vm.isRefreshingHistory.value &&
            handler.messages.value.any { it.content == "Initial synthetic history" } &&
            vm.observedSubagentCount.value == 2 }
        compose.onNodeWithText("Initial synthetic history").assertIsDisplayed()

        val explicitRead = CompletableDeferred<Result<List<MessageItem>>>()
        compose.runOnUiThread {
            vm.setProfileMessageLoader { explicitRead.await() }
            vm.switchSession(STORED)
        }
        waitFor { vm.isLoadingHistory.value }
        compose.onNodeWithText("Loading messages…").assertIsDisplayed()
        explicitRead.complete(Result.success(history))
        waitFor { !vm.isLoadingHistory.value && !vm.isRefreshingHistory.value }
        compose.onNodeWithText("Loading messages…").assertDoesNotExist()

        repeat(2) {
            val before = compose.onNodeWithText("Initial synthetic history").fetchSemanticsNode().boundsInRoot
            val passiveRead = CompletableDeferred<Result<List<MessageItem>>>()
            compose.runOnUiThread {
                vm.setProfileMessageLoader { passiveRead.await() }
                vm.requestSessionActivityRefresh()
            }
            waitFor { vm.isRefreshingHistory.value }
            assertTrue(!vm.isLoadingHistory.value)
            compose.onNodeWithText("Loading messages…").assertDoesNotExist()
            assertEquals("passive refresh moved retained transcript", before,
                compose.onNodeWithText("Initial synthetic history").fetchSemanticsNode().boundsInRoot)
            passiveRead.complete(Result.success(history))
            waitFor { !vm.isRefreshingHistory.value }
            compose.onNodeWithText("Loading messages…").assertDoesNotExist()
            assertEquals(before, compose.onNodeWithText("Initial synthetic history").fetchSemanticsNode().boundsInRoot)
        }
        assertTrue(!handler.isStreaming.value)
        assertEquals(0, fixture.rpcCount("prompt.submit"))
        assertEquals(0, fixture.rpcCount("session.interrupt"))
    }

    @Test fun waitingWithChildrenAndStaleInputConvergeToWorking() {
        fixture.activeSessionStatus = "waiting"
        fixture.activeSubagentCount = 2
        compose.runOnUiThread { assertTrue(vm.openProfileSession(PROFILE, profile,
            AgentDisplay.profileContextKey("fixture-connection", PROFILE), STORED)) }
        waitFor { vm.observedSubagentCount.value == 2 &&
            vm.currentSessionActivity(vm.sessionActivities.value)?.phase() == SessionActivityPhase.NeedsInput }
        compose.onNodeWithText("Needs input · Working with 2 subagents").assertIsDisplayed()
        // Reproduce a retained earlier UI overlay, not a real unanswered server request.
        compose.runOnUiThread {
            @Suppress("UNCHECKED_CAST")
            val records = ChatViewModel::class.java.getDeclaredField("sessionActivityRegistry")
                .apply { isAccessible = true }.get(vm) as MutableStateFlow<SessionActivityRegistry>
            val observed = requireNotNull(vm.currentSessionActivity(records.value))
            records.value = records.value.reduce(SessionActivityUpdate.PendingInputOpened(
                owner = observed.owner, requestId = "synthetic-stale-input",
                generation = observed.evidence.generation,
                observedAtMillis = observed.evidence.observedAtMillis,
            ))
        }
        fixture.activeSessionStatus = "working"
        compose.runOnUiThread { vm.requestSessionActivityRefresh() }
        waitFor { vm.currentSessionActivity(vm.sessionActivities.value)?.let {
            it.phase() == SessionActivityPhase.Working && it.pendingInputs.isEmpty()
        } == true }
        compose.onNodeWithText("Working with 2 subagents").assertIsDisplayed()
        compose.onNodeWithText("Needs input", substring = true).assertDoesNotExist()
        assertTrue(!handler.isStreaming.value)
        for (method in listOf("session.resume", "session.activate", "prompt.submit", "session.interrupt")) {
            assertEquals("readonly status must not answer or control a turn: $method", 0, fixture.rpcCount(method))
        }
    }

    private fun waitFor(condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < end) { if (condition()) return; Thread.sleep(25) }
        assertTrue("Synthetic status Android condition did not settle", condition())
    }
    private companion object {
        const val PROFILE = "status-test-profile"
        const val STORED = "20260821_120000_fixture"
        const val LIVE = "fixture-live-1"
    }
}
