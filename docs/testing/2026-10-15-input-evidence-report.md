# Pending input evidence repair test report

Baseline: `2945649d79e5855f349a662f0e9398461da920ff`. Date: 2026-10-15.
See the [plan](2026-10-15-input-evidence-plan.md).

The remaining false Needs input state had two independent sources: cached
checkpoint input inherited confirmed live freshness, and unrelated process
updates advanced the shared timestamp used to protect pending input. Input now
has one independent confirmed observation timestamp. Cache publication stays
unconfirmed; direct live Requested/Expired events remain confirmed and exact
by request identity. Cold unconfirmed hints do not survive live confirmation.
No UI, polling, backend, protocol, consent tool or notification behavior is
added by this repair.

| Selection | Actual outcome |
|---|---|
| Baseline six reducer checks | 3 failures reproducing Working versus NeedsInput, 3 controls passed; 0.060 seconds |
| Initial VM draft | Test-only null key seed failed before the behavior assertion; corrected |
| Corrected VM baseline | Expected Working versus NeedsInput failure; 4.017 seconds |
| Final six reducer checks | 6 passed, 0.010 seconds |
| Final callback-driven component check | 1 passed, 3.939 seconds |

The original VM draft tried to find a background key before adding its
checkpoint. Correcting the private test seed exposed the intended cached-input
failure. The old-expiry control was strengthened within the same scenario to
check that the fresh pending identities survive unchanged.

Only focused JVM tests were executed. No APK was assembled, no emulator or
phone was used, and no app was installed. These checks do not establish rendered
behavior, live request absence, wire delivery or notification reliability. The
callback check is in-process component integration; the reducers are unit tests.
The final exact seven-selector JVM build passed in 2m26s (31 tasks: 5 executed,
26 up-to-date). [Sanitized XML, selectors and source fingerprints](evidence/2026-10-15-input-evidence/summary.json)
retain each recorded outcome. The previously delivered APK is unchanged; this
source repair has not been packaged. Raw build logs remain outside the public
repository.
