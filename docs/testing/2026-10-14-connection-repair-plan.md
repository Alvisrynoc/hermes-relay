# Connection repair test plan

Date: 2026-10-14. This is the executed repair validation plan, including the
later addition of four focused Android runtime checks. Results and unsuccessful
attempts are recorded in the [test report](2026-10-14-connection-repair-report.md).

## Scope and acceptance criteria

| Issue | Required behavior | Smallest relevant validation |
|---|---|---|
| 1: large stored conversation cannot resume/send | Resume and activation avoid the full transcript in WebSocket replies; an old conversation can send and receive without hitting the frame limit | Four Gateway client JVM regressions, existing upstream resume compatibility tests, isolated proxy size reproduction, one Android capped-transport scenario |
| 2: stale/slow history has no loading feedback | Fetch the newest 50 messages first; load older pages only on demand; retain displayed history during refresh; expose loading and recoverable failure; reject stale completions | Twelve REST/pagination/ViewModel JVM tests, upstream REST compatibility checks, one rendered Android history scenario |
| 3: missed pending-input alerts | Background observation can discover sanitized pending input without acquiring the Desktop turn; alerts identify the exact owner; explicit review replays the authenticated card; stale review never answers or interrupts another owner | Backend observer/contract checks, seventeen Android JVM tests, one Android notification-and-card scenario |
| 5: waiting on subagents appears paused | Display only the current conversation's active child count; exclude foreign work and clear stale indicators | Backend roster/ownership checks, two Android JVM tests, one rendered Android status scenario |

Issue 4 was a preceding configuration repair, not part of these source changes.
Its evaluator smoke checks are recorded separately in the report. Credential,
SSH and connectivity diagnostics are also separated from software regression
evidence.

## Tested source

| Surface | Base | Final tested product commit |
|---|---|---|
| Relay | `eeb6e94a` | `b99af532f2db8c39c81bd5665507d9a5c20992c9` |
| Hermes Agent | `66605471e9f` | `36fad15d55a68c427f784ea076304d5dd473379c` |

Focused JVM validation must run on each isolated issue's incremental patch
stack, before its local repair commit. A combined working-tree run is feedback,
not evidence that each issue independently passed. Backend batches can overlap;
their test counts must not be added as though they were distinct tests.

The four runtime tests use the final product commit plus the uncommitted,
test-only [instrumentation patch](evidence/2026-10-14-connection-repair/runtime-tests.patch).
The patch extends the existing synthetic Gateway fixture and adds one four-test
class. It changes no product implementation. It must be applied in a disposable
test checkout, not assumed to be present in the repair commit.

## Environment and isolation

- Host Windows, portable Temurin JDK 17.0.20.1, Gradle 9.8.1, AGP 9.4.1,
  Kotlin 2.4.21, compile SDK 37 and Build Tools 37.0.0.
- Runtime: Pixel 6, Android API 36, AOSP x86_64 image revision 2,
  Emulator 37.2.12, WHPX acceleration, dark theme and portrait orientation.
  Other font/locale/navigation configurations are not certified.
- Process-local SDK, Java preference home, cache and short temporary paths.
  Do not modify the live Hermes Python environment or global Windows settings.
- Serialize Windows builds/device work through `scripts/android-lane.ps1`.
  Select the test emulator explicitly. The executed lane used a separate ADB
  server so existing device/Docker listeners were preserved.
- Synthetic loopback HTTP/WebSocket peers only. No provider calls, credentials
  or personal conversations are needed for source/runtime regressions.
  The preceding approval-evaluator configuration smoke check is an exception:
  it used the already configured provider in an isolated Hermes home and never
  executed its submitted command descriptions.
- Use the Agent canonical `scripts/run_tests.sh` runner with its isolated test
  environment, narrowed test paths/selectors and required blocking checks.
- Use the official native Android resource packager outside the execution
  sandbox when reproducing this host's native packaging restriction. This is
  a build environment condition, not an app-code workaround.

## Focused JVM selection

The exact 35 successful selections are preserved in the sanitized JUnit XML
under [evidence](evidence/2026-10-14-connection-repair/). The issue groups are:

| Issue | Classes | Cases |
|---|---|---:|
| 1 | `GatewayChatClientTest` | 4 |
| 2 | `DashboardApiClientTest`, `SessionMessagePaginationTest`, `ChatViewModelGatewayInboundTurnTest` | 2 + 8 + 2 |
| 3 | `GatewayChatClientTest`, `InteractionRequestNotifierTest`, `ChatViewModelGatewayInboundTurnTest` | 4 + 10 + 3 |
| 5 | `GatewayChatClientTest`, `ChatViewModelGatewayInboundTurnTest` | 1 + 1 |

Use each XML testcase's class and method as a separate `--tests` selection.
Do not substitute an entire large test class for these selections.

```powershell
# Activate an isolated local toolchain first.
$gradleArgs = @(':app:testSideloadDebugUnitTest', '--no-daemon')
foreach ($selector in $selectedClassAndMethodNames) {
    $gradleArgs += @('--tests', $selector)
}
.\scripts\android-lane.ps1 gradle @gradleArgs
```

## Four Android runtime scenarios

All methods belong to
`com.hermesandroid.relay.viewmodel.RelayConnectionRepairInstrumentedTest`.

### 1. `largeHistoryAttachment_survivesTransportCapAndSends`

1. Configure 1,500 synthetic history rows, including reasoning, exceeding
   4 MiB before a fresh client attaches. The fixture closes an oversized
   snapshot with WebSocket code 1009.
2. Open the exact owner and wait for its bounded latest history and Ready
   client state. Submit a synthetic continuation through the production
   ViewModel and receive the synthetic response through the real socket.
3. Persist the completed reply in fixture history before emitting completion.
4. Require zero cap closures, Ready, one submission, zero interrupts and the
   correctly owned/persisted reply. Read it with the existing list scroll
   action and require the actual production chat bubble to render.

This tests the attachment/send boundary. Direct ViewModel submission bypasses
the screen's Send-button follow reducer, so automatic following and Send-button
enablement are not acceptance claims for this scenario.

### 2. `recentHistory_refreshShowsLoadingErrorAndRetainsTranscript`

1. Configure 80 rows before opening; require only the latest 50 and the newest
   visible row. Verify the bounded REST request window.
2. Block a passive refresh. Require a rendered loading indication while the
   existing newest row remains visible.
3. Return HTTP 503. Require a rendered error and the retained transcript.
4. Allow a later healthy passive refresh. Require the error to clear.

Recovery here is a later passive refresh; no explicit Retry button is claimed.
Older-page merge/load behavior is covered by the focused JVM tests.

### 3. `desktopInput_postsOwnedNotificationAndReviewRendersAuthenticatedCard`

1. Open the synthetic conversation, enable persistent background connection
   behavior and move the Activity to the background.
2. Expose a sanitized Desktop-owned pending-input snapshot. Require an actual
   OS notification, a high-importance channel with the new-channel vibration
   preference enabled, and the exact owner's immutable PendingIntent identity.
3. Require no automatic turn acquisition, prompt submission or answer.
4. Return authenticated open-request replay and invoke the production
   exact-owner review endpoint. Require the command approval card to render,
   with no submitted prompt or automatic request response.

This checks notification identity plus the production review/card endpoint.
It does not launch the complete MainActivity notification navigation route.
The JVM lane checks the review-route flag and ordinary passive-route behavior.

### 5. `observedSubagents_renderOnlyCurrentConversationAndClearOnSwitch`

1. Require the rendered current-conversation count of three active children.
2. Supply a foreign durable conversation reporting nine; require the current
   count/label to clear rather than adopt it.
3. Restore the owned count, then switch owner; require immediate clearing.

## Runtime execution recipe

Apply the test-only patch to the final tested product checkout, activate the
isolated SDK/JDK and start only one API 36 test emulator. Build debug artifacts:

```powershell
.\scripts\android-lane.ps1 gradle :app:assembleSideloadDebug --no-daemon
.\scripts\android-lane.ps1 gradle :app:assembleSideloadDebugAndroidTest --no-daemon --max-workers=1
```

The native test-APK packager required outside-sandbox execution on the tested
host. No permanent build property or alternate AAPT2 binary is required for
the successful build.

Install only on the chosen emulator using explicit ADB port and serial. Set
`$appApk` and `$testApk` to their generated files:

```powershell
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
# Set these to the explicitly created isolated test emulator/server.
$adbPort = 5038
$serial = 'emulator-5560'
.\scripts\android-lane.ps1 exec $adb -P $adbPort -s $serial install -r $appApk
.\scripts\android-lane.ps1 exec $adb -P $adbPort -s $serial install -r $testApk

$testClass = 'com.hermesandroid.relay.viewmodel.RelayConnectionRepairInstrumentedTest'
$methods = @(
    'largeHistoryAttachment_survivesTransportCapAndSends',
    'recentHistory_refreshShowsLoadingErrorAndRetainsTranscript',
    'desktopInput_postsOwnedNotificationAndReviewRendersAuthenticatedCard',
    'observedSubagents_renderOnlyCurrentConversationAndClearOnSwitch'
)
$selectors = ($methods | ForEach-Object { "$testClass#$_" }) -join ','
.\scripts\android-lane.ps1 exec $adb -P $adbPort -s $serial shell am instrument -w -r `
    -e class $selectors `
    com.axiomlabs.hermesrelay.sideload.test/androidx.test.runner.AndroidJUnitRunner
```

Require `numtests=4`, four method success statuses and `OK (4 tests)`.
ADB's process exit code alone does not prove success: failed instrumentation
runs also returned exit code zero. Record product SHA, test patch/APK hashes,
selected methods, lane, settings, results and harness limitations.

## Certification limits

Do not run a full app/emulator matrix, live production mutation tests or phone
installation as part of this plan. AOSP results do not certify Samsung sound,
physical vibration, firmware background limits, killed-app/offline delivery,
Meshnet behavior, Internet latency or complete notification-tap navigation.
The synthetic ConnectionViewModel is not bound to the fixture's connection
status header; transport readiness is asserted on GatewayChatClient itself.
These limits are recorded in the report, not treated as passed tests.
