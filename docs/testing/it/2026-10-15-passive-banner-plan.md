# Passive history banner integration plan

See the [plan index](../2026-10-15-passive-banner-plan.md).

```powershell
.\scripts\android-lane.ps1 gradle :app:testSideloadDebugUnitTest `
  --tests '*ChatViewModelGatewayInboundTurnTest.passiveHistoryRefreshShowsLoadingAndKeepsTranscriptOnFailure' `
  --no-daemon --console=plain
```

This existing real-socket/Robolectric check blocks a passive history read,
verifies the internal `isRefreshingHistory` guard and retained messages, then
fails the read and verifies a visible retryable failure with the transcript
preserved. Its historical method name refers to internal refresh state; it
does not require the passive loading banner to be rendered.
