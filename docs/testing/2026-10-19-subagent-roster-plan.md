# Stale subagent card reconciliation test plan

Baseline: `9e453b43d1c81099134dc8f1c6b0ad0af40e5851`. Date: 2026-10-19.

Reproduce an identified child card retaining Working after the parent completes
and the child's terminal event is missed. Reconcile only against a successful,
complete, exact-session `subagent.list` roster from an already initialized owner.
An absent child stops being live; absence does not establish successful completion.

- [Unit plan](ut/2026-10-19-subagent-roster-plan.md): controller identity/revision
  controls and the selected source-checker boundary.
- [Integration plan](it/2026-10-19-subagent-roster-plan.md): client wire parsing,
  ViewModel polling propagation and one declarative fixture scenario.

Run only focused JVM/Python checks. No APK assembly, emulator, Android
instrumentation, physical phone, full suite or backend change is in scope.
See the [report](2026-10-19-subagent-roster-report.md).
