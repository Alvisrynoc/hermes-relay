# Stale input status repair test report

Baseline: `db467847f01408c45a0c85ad0ef871fa4a194486`. Date: 2026-10-15.
See the [plan](2026-10-15-stale-input-plan.md).

The reducer retained a prior pending overlay after an exact authoritative
Working snapshot, so its presentation remained Needs input. The baseline
unit case reproduced expected Working versus actual NeedsInput. Working now
clears older overlays only for the resolved owner; newer/equal pending evidence
and its timestamp survive delayed snapshots. Genuine Waiting is preserved.
The existing status row also retains owned child work alongside Needs input
using the existing strings and visibility guards.

| Selected check | Actual outcome |
|---|---|
| Baseline pure reducer selection | 3 selected: 1 expected failure, 2 controls passed; 0.062 seconds |
| Final same three unit cases | 3 passed, 0.061 seconds; build 1m13s |
| Single rendered system scenario | 1 passed, 8.043 seconds; APK build 39s |

No live pending-request snapshot was verified. This reproducible reducer gap
must not be interpreted as proof that the reported conversation has no real
unanswered request. No request is answered, no card hydration is introduced,
and no transport, provider, polling cadence or notification behavior changes.
No full app suite, physical phone, real provider or production conversation
was tested. Actual sanitized results and artifact/source hashes are retained
in the linked evidence summary. Both APKs installed successfully only on the
isolated emulator.

[Sanitized evidence and exact source fingerprints](evidence/2026-10-15-stale-input/summary.json)
retain the failing baseline and final unit selection. Raw logs remain outside the
public repository. The exact Android selector passed combined Waiting/child
rendering, then real Working-poll propagation over the synthetic earlier overlay,
without request responses or turn control. The source and APK fingerprints are
retained in the same summary.
