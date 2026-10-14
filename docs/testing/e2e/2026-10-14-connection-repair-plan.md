# Connection repair end-to-end acceptance test plan

Date: 2026-10-14. **Status: not executed.** These are minimal phone acceptance
checks, not results from the synthetic emulator harness.

## Prerequisites

- Install the arm64 `sideloadCandidate` APK, labelled **HR Candidate**. Its
  separate package preserves the existing Relay installation; pair it again.
- Use the real phone, real Desktop and the actual remote connection route.
- For issues 3 and 5, the Desktop must be running the repaired Agent source
  listed in the [plan index](../2026-10-14-connection-repair-plan.md). Backend
  deployment/restart has not been performed by these tests.
- Enable persistent connection/background chat alerts and the desired Android
  notification permission/channel sound and vibration preferences.
- Use a disposable conversation and harmless input request. No credential
  sharing or destructive command is required.

## Focused checks

| Issue | Procedure | Acceptance |
|---|---|---|
| 1 | Open an affected large Desktop conversation on the phone; continue using the actual Send button | Same conversation accepts the message and displays its response without gateway disconnection |
| 2 | Reopen the app after backgrounding; open that conversation; briefly disconnect/reconnect networking during history refresh | Newest history arrives first; loading is visible; failed refresh retains history and shows a recoverable error; recovery refreshes correctly |
| 3 | While the paired phone is backgrounded/locked, trigger one harmless Desktop approval or clarification; tap its notification | Notification reaches the phone with configured sound/vibration; tap opens the exact conversation and authenticated card; explicitly responding resolves that request |
| 5 | Start a harmless Desktop delegation in the paired conversation; observe it from the phone, then switch conversations and return | Only that conversation's active child status appears; foreign status does not leak; status clears when children finish |

Record app/Agent source versions, device/One UI version, connection route,
notification settings, background/locked state, observed result and timestamps
without private transcript or credentials. Distinguish delivery from audible
sound/physical vibration and from successful notification-tap navigation.

Do not mark these passed based on the four synthetic Android
[system tests](../system/2026-10-14-connection-repair-plan.md). Killed-app/offline
delivery, a full device matrix and broad performance benchmarking are outside
this focused acceptance plan.
