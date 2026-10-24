# Stale subagent card reconciliation test report

Baseline: `9e453b43d1c81099134dc8f1c6b0ad0af40e5851`. Date: 2026-10-19.
See the [plan](2026-10-19-subagent-roster-plan.md).

The baseline reproduced an identified child remaining live after parent
completion when its own completion event was missed. Parent completion and
reconnect intentionally preserve detached children, but there was no roster
reconciliation. Existing activity polling now reads the standard read-only
`subagent.list` only when identified live cards exist and the current attached
runtime has confirmed initialization. No session is resumed or created for this
read. An exact successful roster removes only unchanged comparable IDs absent
from that roster, without inventing a Completed result.

| Selection | Actual outcome |
|---|---|
| Unchanged-production VM regression | Failed at expected roster/card assertion; 1 failure, 9.346s; build 2m09s |
| First four-case repair run | VM 1 passed (4.443s), controller 2 passed (0.006s); client 1 failed (0.407s); build 2m22s |
| Client completion barrier correction | 1 passed (0.560s), build 46s |
| Final strengthened same client case | 1 passed (0.516s), build 38s |
| Declarative fixture scenario | 1 passed (0.524s) |
| Source-checker unit regression | Initially 1 passed (0.077s); strengthened missing-boundary controls also passed (0.078s) |
| Existing complete-contract fixture control | 1 passed (0.074s) |
| Selected current-upstream source checks | message_complete and subagent_roster both passed |

There are four unique final JVM cases: controller two, VM one and the latest
client one. The initial client failure was a test ordering race: the completion
callback fires before the client retains its request/session owner. An existing
same-socket RPC barrier fixes the test boundary. The final version positively
reads an authorized roster before malformed/unsupported checks and asserts all
four roster RPCs occurred, so early initialization rejection cannot falsely pass
those controls. Production did not change during these narrower client reruns.
[Sanitized XML, selectors and exact source fingerprints](evidence/2026-10-19-subagent-roster/summary.json)
retain baseline, intermediate and final results. Raw build logs remain outside
the public repository.

Snapshot application preserves current children, newer child progress, foreign
scope, anonymous or child-session-only identities, missing/malformed arrays,
request failure and owner changes. The upstream roster does not expose a child
session ID, so that separate namespace is never compared against subagent IDs.
Removed IDs are retired against late event resurrection. The existing poll
interval, UI, provider/Agent source and historical activity receipts are unchanged.

Source conformance used clean vanilla upstream
`5fc490e2b34123212d73ef316086f939567a725a` without starting its Gateway/provider.
The [selected result](evidence/2026-10-19-subagent-roster/upstream-conformance.json)
checks the existing session-authorized roster and terminal contract. Only the
new roster checker is added; previous generic server-request checks are unchanged.
This is source/fixture evidence, not live upstream runtime certification.

No APK was assembled or installed, emulator/device started, live conversation
used, provider called or full suite run. These results establish JVM state/wire
behavior, not rendered Android or physical-device behavior. A passive lazy
attachment or idle reconnect without confirmed initialized runtime preserves its
partial cards until existing attachment/readiness evidence permits a roster read.
This source repair has not been packaged or deployed.
