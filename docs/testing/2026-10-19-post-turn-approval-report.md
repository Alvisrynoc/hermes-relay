# Post-parent-turn approval repair test report

Baseline: `b40e3b5fa08391029e31ab2e704b044af0f9dc89`. Date: 2026-10-19.
See the [plan](2026-10-19-post-turn-approval-plan.md).

The baseline reproduced the failure after a parent completed: a legitimate
native approval received `-32000`, `No interactive turn is attached`, and the
ViewModel never received its pending card. The fix retains a request-only
owner after an attached parent completes, using the existing interaction mapper.
It preserves the socket, session, profile and request identity guards, queue,
expiry and response path. A later real incoming model turn does not replace the
older approval's owner. No synthetic prompt or model turn is created.

| Check | Actual outcome |
|---|---|
| Baseline client / ViewModel | Both failed at the expected post-completion approval boundary; 0.692s / 10.167s |
| First fixed compilation | Failed before tests: missing required no-op callbacks and misplaced type reference; corrected |
| Initial focused selection | Client 4 passed (0.788s), ViewModel 1 passed (4.009s); build 4m16s |
| Final five client cases plus original VM case | 6 passed; client 0.869s, VM 4.193s; build 1m08s |
| Strengthened same VM case | 1 passed (4.906s), build 47s; card survives real incoming start and original request is answered |
| Declarative fixture case | 1 passed (0.525s) |
| Current-upstream source conformance | message_complete passed; generic server_requests check failed as described below |

There are six unique final JVM scenarios, not seven: the final ViewModel case
was strengthened and rerun alone without changing production. The final client
counts were read from XML before the narrower rerun replaced the result
directory; retained initial and latest VM XML plus recorded final counts and
source fingerprints are in the [evidence](evidence/2026-10-19-post-turn-approval/summary.json).
The fixture first encountered a missing aiohttp dependency in one isolated
Python environment; the check then passed using an already-installed Python
with aiohttp. No dependencies were installed for that check.

Source conformance used clean vanilla upstream
`5fc490e2b34123212d73ef316086f939567a725a` without starting its Gateway/provider.
The unchanged checker requires equality against twelve older request methods;
upstream adds `setup_beat`, `theme.apply` and `theme.pick` with none missing.
It also requires literal `answers` in `server_requests.send` and
`resolve_response`; those results now delegate to
`tools.clarify_batch.settle_batch`. The approval envelope, result/error
settlement, capabilities, cancellation and replay anchors remain present.
The [failed raw result](evidence/2026-10-19-post-turn-approval/upstream-conformance.json)
is retained. This source check is not full upstream compatibility certification;
no checker or backend change was made to obtain a pass.

Only focused JVM integration/component checks and the Python fixture/source
checks were executed. No APK was assembled, app installed, emulator/device
started, live conversation used, or provider called. No rendered UI, notification
reliability, reconnection or physical-device result is claimed. The request-only
owner is established after an already attached turn completes and is retired
on socket/session/profile change; cold passive browsing and idle reconnect
continue to require the existing explicit review/recovery path. This source
repair has not been packaged or deployed. Raw local build logs remain outside
the public repository.
