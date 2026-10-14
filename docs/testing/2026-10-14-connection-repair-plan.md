# Connection repair test plan

Date: 2026-10-14. This index links plans by test level, including the
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

## Plans by test level

Classify by the exercised boundary, not by the Gradle task name or whether a
JVM/emulator is used. These files reorganize the existing validation; they do
not add product scope or claim new test executions.

| Folder | Plan | Boundary and status |
|---|---|---|
| `ut/` | [Unit tests](ut/2026-10-14-connection-repair-plan.md) | Pagination logic and Robolectric notification component: 18 final JVM cases passed |
| `it/` | [Integration tests](it/2026-10-14-connection-repair-plan.md) | Real Android client/handler/ViewModel with synthetic peers: 17 final JVM cases passed; isolated Agent registry/session integration batches recorded separately |
| `system/` | [Android system tests](system/2026-10-14-connection-repair-plan.md) | Android chat subsystem and OS notification behavior: four focused emulator scenarios passed; external Desktop/provider mocked |
| `e2e/` | [End-to-end phone acceptance](e2e/2026-10-14-connection-repair-plan.md) | Real phone, Desktop and remote route: pending, not executed |
| `checks/` | [Build, contract and diagnostic checks](checks/2026-10-14-connection-repair-plan.md) | Source/contract/artifact verification and earlier operational diagnostics; baseline failures remain explicit |

The UT and Android IT selections partition the original 35 final JVM cases;
18 + 17 = 35, not additional runs. Backend batches overlap and must not be
summed into a distinct total. The existing report and evidence paths remain
stable. Phone acceptance status must be recorded independently of automated
system test results.

## Certification limits

Do not run a full app/emulator matrix, live production mutation tests or phone
installation as part of this plan. AOSP results do not certify Samsung sound,
physical vibration, firmware background limits, killed-app/offline delivery,
Meshnet behavior, Internet latency or complete notification-tap navigation.
The synthetic ConnectionViewModel is not bound to the fixture's connection
status header; transport readiness is asserted on GatewayChatClient itself.
These limits are recorded in the report, not treated as passed tests.
