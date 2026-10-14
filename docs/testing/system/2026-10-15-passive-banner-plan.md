# Passive history banner Android system plan

See the [plan index](../2026-10-15-passive-banner-plan.md).

Select exactly:

```text
com.hermesandroid.relay.viewmodel.ObservedChatStatusInstrumentedTest#passivePollingKeepsTranscriptStableAndExplicitReloadShowsLoading
```

Use the existing synthetic Gateway and production ChatScreen/ViewModel/client
on Pixel 6 AOSP x86_64 API 36. While two synthetic children are active, an
explicit retained-history reload must display Loading messages. Two blocked
passive history reads then must not display that banner or move the retained
message's rendered bounds before/after completion. Active-list refreshes are
explicitly triggered for deterministic reads; this scenario is not a timing
or real-network performance benchmark.

The scenario reuses the status test setup without new fixture fields. It does
not submit/interrupt a turn. It proves the Android subsystem boundary with a
synthetic server, not real phone/Desktop/provider end-to-end acceptance.
