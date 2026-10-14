# Stale input status repair test plan

Baseline: `db467847f01408c45a0c85ad0ef871fa4a194486`. Date: 2026-10-15.

An exact authoritative Working row must retire an older pending UI overlay.
Pending evidence at least as new as the snapshot start must survive repeated
delayed snapshots. Genuine Waiting retains Needs input; owned child work remains
visible alongside it. This repair does not answer requests, hydrate cards or
change the Gateway protocol. A synthetic failure does not establish the live
server's current pending-request state.

- [Unit plan](ut/2026-10-15-stale-input-plan.md): three selected pure reducer cases.
- [System plan](system/2026-10-15-stale-input-plan.md): one existing-fixture
  Android registry/ViewModel/ChatScreen propagation scenario.

No separate integration/full-app suite, provider or real-phone acceptance is
selected. See the [report](2026-10-15-stale-input-report.md).
