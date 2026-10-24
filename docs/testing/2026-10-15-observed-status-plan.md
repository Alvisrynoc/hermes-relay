# Observed chat status repair test plan

Date: 2026-10-15. Baseline: `819f58df0db7bb3df91b115e4ad52aa19a93b256`.

The open Gateway chat must expose its existing exact-owner activity evidence,
including Desktop work that is not a phone-owned streaming turn. Confirmed idle
requires an actually ready Gateway socket; unavailable or revalidating evidence
must not imply readiness. An idle parent with active owned children retains the
existing busy polling cadence so completion clears promptly.

- [Integration](it/2026-10-15-observed-status-plan.md): focused real-socket
  view-model ownership, unavailable-state and child-completion checks.
- [System](system/2026-10-15-observed-status-plan.md): one synthetic Android
  client/view-model/ChatScreen/OS scenario on API 36.

No real-provider/physical-phone end-to-end acceptance, full app suite, new
activity protocol, live service restart or deployment is selected.
