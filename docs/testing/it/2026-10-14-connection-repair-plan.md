# Connection repair integration test plan

Date: 2026-10-14. See the [plan index](../2026-10-14-connection-repair-plan.md)
for source commits, acceptance criteria and environment isolation.

## Android client integration

These JVM tests integrate real clients, handlers and ViewModels with synthetic
HTTP/WebSocket peers. They do not use a real Desktop or provider.

| Issue | Classes under `com.hermesandroid.relay` | Cases | Required behavior |
|---|---|---:|---|
| 1 | `network.upstream.GatewayChatClientTest` | 4 | Bounded resume/activate; exact session identity; fallback compatibility; recovery after socket loss |
| 2 | `network.upstream.DashboardApiClientTest`, `viewmodel.ChatViewModelGatewayInboundTurnTest` | 2 + 2 | Bounded/profile-scoped REST history; loading/error with retained transcript; reject stale history completion |
| 3 | `network.upstream.GatewayChatClientTest`, `viewmodel.ChatViewModelGatewayInboundTurnTest` | 4 + 3 | Sanitized observation; passive Desktop ownership; authenticated review/card replay; reject foreign/stale requests without answering or interrupting |
| 5 | `network.upstream.GatewayChatClientTest`, `viewmodel.ChatViewModelGatewayInboundTurnTest` | 1 + 1 | Optional nonnegative child count; exact-owner display state; clear on switch/unavailable activity |

Final result: **17 passed**. Use the exact methods for these classes in the
[recorded selectors](../evidence/2026-10-14-connection-repair/jvm-selections.json).
Run through the same narrow JVM recipe in the
[unit plan](../ut/2026-10-14-connection-repair-plan.md#execution).

## Hermes Agent integration

Exercise observer/session handlers with the runtime registry, session storage
and roster ownership in an isolated test environment. Provider/transport
substitutes keep these in-process integration tests, not end-to-end tests.

| Batch | Selected coverage | Recorded result |
|---|---|---|
| Existing active-list regression | Live session inclusion and finalized session exclusion | 2 passed |
| Resume/activate compatibility | Seven live/lazy resume cases plus six selected server resume/activate cases | 13 passed |
| Issue 3 observer behavior | Approval/clarify across explicit profile and named launch-home fallback | 4 passed; original batch also included 2 contract checks |
| Final observer/roster behavior | Sanitized pending requests, settled-request clearing, conversation-only child count, unchanged control ownership, exact-owner lineage/reattachment | 5 observer + 8 roster passed; original batch also included 2 contract checks |

Do not add these overlapping batch counts into a unique total. Exact methods,
canonical commands and evidence retention limits are in the
[report](../2026-10-14-connection-repair-report.md#hermes-agent-behavioral-and-contract-checks).
Use the Agent canonical runner, not bare pytest:

```bash
bash scripts/run_tests.sh tests/tui_gateway/test_session_observer_snapshot.py \
  tests/tui_gateway/test_subagent_snapshot.py -q
bash scripts/run_tests.sh tests/tui_gateway/test_resume_live_lazy_session.py -q
bash scripts/run_tests.sh tests/tui_gateway/test_tui_gateway_server.py \
  -k 'session_resume_uses_parent_lineage_for_display or session_resume_deferred_history or session_activate_can_omit_duplicate_desktop_transcript' -q
```

Generated contract/static checks are in the
[checks plan](../checks/2026-10-14-connection-repair-plan.md). The optional REST
compatibility diagnostic recorded six passes and one unmodified-baseline
profile expectation failure; it is not a complete pass. Its selection and
interpretation remain in the
[report](../2026-10-14-connection-repair-report.md#optional-rest-and-upstream-conformance-diagnostics).
