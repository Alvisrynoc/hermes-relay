# Pending input evidence repair test plan

Baseline: `2945649d79e5855f349a662f0e9398461da920ff`. Date: 2026-10-15.

Only the remaining Needs input classification is in scope. Cached checkpoint
hints cannot reconfirm input after authoritative Working evidence. Input age
must use its own confirmed observation timestamp, not unrelated process/turn
updates. Direct live Requested and Expired events retain exact request identity.

- [Unit plan](ut/2026-10-15-input-evidence-plan.md): six selected reducer checks.
- [Component integration plan](it/2026-10-15-input-evidence-plan.md): one
  callback-driven ViewModel/cache publication check.

Only the JVM test target is selected. Do not assemble APKs, start an emulator,
install an app, run whole suites or change backend, polling, UI or consent tools.
No rendered/system or end-to-end result is claimed. See the
[report](2026-10-15-input-evidence-report.md).
