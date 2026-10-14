# Observed status integration plan

See the [plan index](../2026-10-15-observed-status-plan.md).

Select only the idle-parent child-completion regression and the existing
exact-owner/unavailable optional-count control in
`ChatViewModelGatewayInboundTurnTest`. The child-only fixture disables its
unrelated default running process; otherwise that independent busy overlay
would mask the idle polling defect.

```powershell
.\scripts\android-lane.ps1 gradle :app:testSideloadDebugUnitTest `
  --tests '*ChatViewModelGatewayInboundTurnTest.idleParentWithObservedChildrenPollsCompletionAtBusyCadence' `
  --tests '*ChatViewModelGatewayInboundTurnTest.observedSubagentCountStaysWithExactOwnerAndClearsWhenUnavailable' `
  --no-daemon --console=plain
```

These are real-loopback-socket/Robolectric integration checks, not a provider or
physical device certification. Actual results belong in the report.
