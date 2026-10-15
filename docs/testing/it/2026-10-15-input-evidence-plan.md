# Pending input cache component integration plan

See the [plan index](../2026-10-15-input-evidence-plan.md).

Select exactly:

```text
com.hermesandroid.relay.viewmodel.ChatViewModelGatewayInboundTurnTest.cachedBackgroundInputCannotReconfirmAfterWorkingButFreshEventsRemainTrusted
```

This in-process component integration test uses the production ViewModel's
registered background-input callback and cached checkpoint publication. It
confirms a live Requested event, applies authoritative Working, republishes
cached input twice, and requires Working to remain. A fresh request must still
be trusted; expiration of the old request must preserve the fresh pending map,
and expiration of the fresh request must settle it.

The existing shared setup uses a local Gateway harness, but this new boundary
is callback-driven. It does not prove wire delivery, card rendering, phone
notifications or a real server's pending state. No new production test API,
APK assembly, emulator or full class selection is used.
