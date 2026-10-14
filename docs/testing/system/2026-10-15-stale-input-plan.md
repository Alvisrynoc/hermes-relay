# Stale input Android system plan

See the [plan index](../2026-10-15-stale-input-plan.md).

Select exactly:

```text
com.hermesandroid.relay.viewmodel.ObservedChatStatusInstrumentedTest#waitingWithChildrenAndStaleInputConvergeToWorking
```

Reuse the synthetic Gateway and production client/ViewModel/ChatScreen on
Pixel 6 AOSP x86_64 API 36. Waiting plus two owned children must render both
facts. A test-only reflection seed reproduces an earlier pending UI overlay;
a real subsequent active-list Working snapshot must clear that overlay and
render the continuing child work. No new production test API or fixture fields
are required, and no request response or turn control is issued.

This scenario proves subsystem state propagation. The overlay is synthetic,
not proof that a live Desktop request was answered or absent. Real request-card
hydration, phone delivery and Desktop/provider end-to-end acceptance are outside
scope. The independent ConnectionViewModel header is not fixture-backed.
