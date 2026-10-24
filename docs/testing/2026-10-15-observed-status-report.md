# Observed chat status repair test report

Date: 2026-10-15. Baseline: `819f58df0db7bb3df91b115e4ad52aa19a93b256`.
See the [plan](2026-10-15-observed-status-plan.md).

## Repair boundary

The main chat header previously showed work only for a phone-owned streaming
turn, while existing passive live activity appeared in drawer rows. The open
Gateway chat now reads that existing exact-owner record directly. Confirmed
idle plus an actually Ready Gateway is required for the ready label; missing,
revalidating, unavailable or disconnected state must not infer readiness.
Owned active children override an idle parent and retain the existing busy poll
condition. The scalar optional count is fenced by its captured owner before
it can appear in another selected chat. No runtime session is attached or
controlled just to display this status.

## Executed checks

| Selection | Actual outcome | Interpretation |
|---|---|---|
| Initial child cadence draft | Compilation failed: fixture runtime constant corrected | No product behavior result |
| Baseline cadence control with default process | 1 passed | The fixture's running-process busy overlay can mask child-only timing |
| Baseline cadence control without process.list | 1 passed | Startup callbacks remain a timing limitation; not red proof of the defect |
| First two-check status feedback | 1 passed, 1 failed | Test-only SSE switch expectation contradicted existing Gateway transport affinity; removed |
| Final two-check integration selection | 2 passed, 6.041 seconds; build 1m24s | Exact selected-owner, unavailable and completion boundaries |
| Single rendered Android scenario | 1 passed, 11.338 seconds; APK build 44s | Synthetic API 36 subsystem system test |

A Windows text-decoding error in the edit helper was corrected using explicit
UTF-8. An intervening pre-edit control build passed; it is not fixed-source
validation. No extra timing framework or additional test matrix was introduced.
The cadence control is not described as a failing-then-passing regression.

## Evidence and limits

[Sanitized evidence](evidence/2026-10-15-observed-status/) retains baseline and
first-feedback XML. [Selection metadata and source fingerprints](evidence/2026-10-15-observed-status/summary.json)
identify the exact final source. Android runtime results, APK hashes and test-only patch fingerprint are retained
in the same summary. Both APKs installed successfully on the isolated emulator.
The rendered check passed main working, idle-parent children, automatic child
completion/ready and unavailable states without resume/activate/create/prompt/interrupt
RPCs or a phone-owned streaming turn. Raw build logs remain outside the public repository.

No full app suite, physical phone, provider, live conversation, service restart,
push or production installation was tested. The standalone ConnectionViewModel
header is not fixture-backed; actual Gateway client state governs the tested
status row. Real Desktop/provider/phone end-to-end acceptance remains pending.
