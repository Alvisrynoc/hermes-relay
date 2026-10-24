# Passive history banner repair test report

Baseline: `d85972603283884a5c02c0e5cd8244565e29ad04`. Date: 2026-10-15.
See the [plan](2026-10-15-passive-banner-plan.md).

The passive refresh flag was included in a conditional loading row above the
transcript. Every polling read inserted and removed that row, changing the
available transcript height. The repair changes only the rendered condition:
explicit `isLoadingHistory` still shows progress; passive `isRefreshingHistory`
remains the internal concurrency guard without a loading row. Error, retained
history and older-history behavior are unchanged. Needs-input classification
is outside this repair.

| Selected check | Actual outcome |
|---|---|
| Existing retained-history/error integration check | 1 passed, 4.150 seconds; build 1m03s |
| One explicit/passive rendered system scenario | 1 passed, 12.681 seconds; APK build 1m18s |

No failing baseline runtime run was executed for the directly inspected UI
condition. The exact rendered selector passed on API 36: explicit loading remained visible,
while two blocked passive reads kept the banner absent and transcript bounds
unchanged. Actual results and source/artifact hashes are retained in the evidence
summary. Both APKs installed successfully only on the isolated emulator.
No full app suite, physical phone, provider, live conversation or production
installation was exercised. The independent ConnectionViewModel header is not
fixture-backed; the test uses the real synthetic Gateway client.

[Sanitized evidence and source fingerprints](evidence/2026-10-15-passive-banner/summary.json)
identify the exact selected check. Raw build logs remain outside the public repository.
