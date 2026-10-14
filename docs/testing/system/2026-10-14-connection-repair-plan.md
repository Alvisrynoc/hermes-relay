# Connection repair Android system test plan

Date: 2026-10-14. These four focused runtime tests exercise the Android chat
subsystem: production ChatScreen, ViewModel, client and OS notifications, with
synthetic external HTTP/WebSocket peers. They run on an emulator but do not
exercise the full app, real Desktop or provider end to end.

Final result: **four passed** in 30.103 seconds on Pixel 6 / API 36 / AOSP
x86_64. See the [plan index](../2026-10-14-connection-repair-plan.md) for shared
source/environment setup and the [report](../2026-10-14-connection-repair-report.md)
for every attempt and limitation. Apply the isolated
[test-only patch](../evidence/2026-10-14-connection-repair/runtime-tests.patch)
to the final tested product in a disposable checkout. No product change is
introduced by this plan.

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
