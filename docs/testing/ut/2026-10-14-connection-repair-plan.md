# Connection repair unit test plan

Date: 2026-10-14. See the [plan index](../2026-10-14-connection-repair-plan.md)
for source commits, acceptance criteria and isolated toolchain setup.

## Boundary and selection

These tests exercise pagination logic and the notification component in a JVM
environment. Robolectric Android shadows are component substitutes, not a
physical device or proof of audible notification delivery.

| Issue | Class under `com.hermesandroid.relay` | Selected cases | Required behavior |
|---|---|---:|---|
| 2 | `network.upstream.SessionMessagePaginationTest` | 8 | One bounded latest request; ordered complete/older pages; legacy envelope; safety bound; cancellation propagation; exact-overlap refresh |
| 3 | `notifications.InteractionRequestNotifierTest` | 10 | Privacy-safe copy; permission/background/settings gates; exact-owner stable slots; cancellation/cleanup; review route; new-channel vibration; preserve existing user preferences |

Final result: **18 passed**. Select only the methods for these two classes in
the [recorded selectors](../evidence/2026-10-14-connection-repair/jvm-selections.json).
The [report](../2026-10-14-connection-repair-report.md) lists every method and
retains unsuccessful development attempts separately.

## Execution

Run the corresponding isolated issue stack through the Windows lane. Each
selector is the full class plus recorded method name; do not select a broader
class or suite.

```powershell
$gradleArgs = @(':app:testSideloadDebugUnitTest', '--no-daemon')
foreach ($selector in $selectedClassAndMethodNames) {
    $gradleArgs += @('--tests', $selector)
}
.\scripts\android-lane.ps1 gradle @gradleArgs
```

Retain JUnit identities/counts and source provenance. No emulator, account
credential or live provider is needed. The Gradle task's `UnitTest` name does
not determine the level of other tests: real client/socket tests are in the
[integration plan](../it/2026-10-14-connection-repair-plan.md).
