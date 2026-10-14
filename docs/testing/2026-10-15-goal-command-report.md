# Goal command repair test report

Date: 2026-10-15. Baseline: `233d92d04ce909bd01b9b23faca143b55e15e83c`.
See the [plan](2026-10-15-goal-command-plan.md).

## Diagnosis and limits

The native backend requires a runtime session for goal commands. An observed
saved chat does not itself attach that session. Relay previously fell through
from its local `no live session` error to `command.dispatch` with no session ID,
which reproduces the exact `4001 / no active session` response. An idle socket
reconnect exposes the same path after a successful phone turn. A successful
phone turn followed immediately by `/goal` on an unchanged socket kept the
binding and succeeded. The user's always-after-send report is therefore broader
than the independently reproduced lifecycle condition.

A second failure in the same goal path is confirmed: native `slash.exec` can
return a dispatch `type=send` directive, but Relay previously treated every
success as output text. The diagnostic observed `(no output)` and no submitted
goal continuation. The repair reuses the existing directive handler, keeping
literal display separate from expanded transport text.

## Executed checks

| Check | Actual result | Evidence level |
|---|---|---|
| Existing backend goal selections | 3 passed | Canonical runner console result; synthetic isolated home |
| Backend missing-session counterprobe | 1 passed | Isolated probe log; current methods_tools.py matches tested copy |
| Android diagnostic wire/state cases | 3 passed | Real loopback socket + Robolectric; unchanged live binding control passed |
| Initial test-first goal regressions | 9 selected: 6 failed, 3 passed | Expected failures before production edit |
| Initial repair feedback | 9 passed | Preliminary fixed source |
| Navigation-only fence feedback | 10 selected: 1 failed, 9 passed | Away-and-back same-owner test exposed a remaining fence gap |
| Binding-revision and typed-result fence | 11 passed | Final result before delayed public-catalog review finding |
| Delayed public-catalog regression | 1 selected: failed before its fence | Public-send ownership race reproduced |
| Final goal regression selection | 12 passed, 5.327 seconds | Exact final source, including public catalog fence |
| Single Android rendered goal scenario | 1 passed, 10.249 seconds; APK build passed in 2m23s | Subsystem system check on API 36; no device/provider claim |

The first test-first draft had two fixture expectations corrected before green
validation (`webui` source and the fixture's created stored ID). Its late-directive
owner assertion was strengthened because the old implementation swallowed send
directives entirely. The away-and-back regression then proved that navigation
generation alone does not cover every direct profile-context switch, requiring
the existing ConversationBinding revision as well.

No full app suite, physical phone, real provider, production conversation,
service restart, push or production APK deployment is part of this test report. Emulator
artifacts serve only the selected synthetic runtime check.

## Retained evidence

Sanitized JVM evidence and selection metadata are retained under
`evidence/2026-10-15-goal-command/`. Raw logs stay in the private task workspace;
the public evidence excludes machine hostnames, user paths and runtime output.
The exact runtime selector passed on the isolated API 36 emulator. Both app and
test APKs built successfully (71 tasks: 14 executed, 5 from cache, 52 up-to-date).
This synthetic server/VM/client/ChatScreen/OS check is classified as system testing;
real-phone Desktop/provider acceptance remains unexecuted.
The public-catalog review regression also reproduced a distinct ownership gap:
when the command catalog reply arrived after navigation, the old public goal
invocation could adopt the newly selected conversation. The invocation owner is
now captured before catalog lookup; both stale execution and stale catalog
notices/publication are fenced. Final detailed selectors and source hashes are
in [the evidence summary](evidence/2026-10-15-goal-command/summary.json).
