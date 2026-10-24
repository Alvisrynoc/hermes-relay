# Observed status Android system plan

See the [plan index](../2026-10-15-observed-status-plan.md).

Select exactly:

```text
com.hermesandroid.relay.viewmodel.ObservedChatStatusInstrumentedTest#observedWorkingChildrenAndIdleStayVisibleWithoutOwningTheTurn
```

The production chat screen opens a synthetic saved conversation passively and
renders main-agent working, idle-parent child work, child completion/confirmed
ready, then unavailable active-list evidence. The phone must remain outside a
streaming turn and issue no resume, activate, create, prompt or interrupt RPCs.
Child completion uses automatic busy polling rather than explicit refresh.

Use the standard Pixel 6 AOSP x86_64 API 36 emulator through the Windows Android
lane. The actual Gateway client readiness is authoritative; the independent
ConnectionViewModel header is not backed by the fixture. The scenario tests a
subsystem with a synthetic server, not real Desktop/provider/phone end-to-end
acceptance or firmware delivery.
