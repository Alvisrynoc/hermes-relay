# Passive history banner repair test plan

Baseline: `d85972603283884a5c02c0e5cd8244565e29ad04`. Date: 2026-10-15.

Repeated passive history reads must not insert/remove a loading row above the
retained transcript. Explicit history loads retain their visible progress;
passive refresh still tracks in-flight work and publishes retryable errors.
No polling cadence, history ownership, status classification or transport
behavior changes belong to this repair.

- [Integration](it/2026-10-15-passive-banner-plan.md): one existing retained
  history/error boundary check.
- [System](system/2026-10-15-passive-banner-plan.md): one rendered explicit-load
  and two-cycle passive-refresh geometry check on the API 36 emulator.

No full-app suite, provider, physical-device acceptance or deployment is part
of the selected test scope. See the [report](2026-10-15-passive-banner-report.md).
