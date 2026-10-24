# Goal command repair test plan

Date: 2026-10-15. Relay baseline: `233d92d04ce909bd01b9b23faca143b55e15e83c`.
The intervening changes since `b99af532` are documentation only; the diagnostic
checkout uses identical production goal code. Tests use synthetic conversations
and local fixtures, with no providers, credentials or personal chat content.

The reported defect is `/goal failed: no active session` in the original app and
HR Candidate while the same conversation works on Desktop. The reproduced
mobile failure occurs when a saved conversation is observed without its live
binding, including after an idle socket loss. A completed phone turn on an
unchanged socket keeps its binding and succeeds; the user's stronger report of
failure after every successful send is not independently reproduced.

The repair explicitly prepares the selected session for `/goal` and consumes
native slash-command directives through the existing result union. Existing
resume failure must not create another conversation. A genuine new draft may
create its normal selected-profile session. Sessionless quick commands remain
available. Navigation during catalog lookup, attachment or reply processing
must cancel the old action before it can submit a foreign prompt.

## Selected lanes

- [Integration plan](it/2026-10-15-goal-command-plan.md): real loopback
  HTTP/WebSocket plus Robolectric view-model ownership and display assertions;
  existing backend goal contracts and one isolated missing-session probe.
- [Android system plan](system/2026-10-15-goal-command-plan.md): one production
  client/view-model/chat-screen scenario on the standard API 36 emulator.
- No separate unit-only, full-app, physical-device, real-provider or live
  deployment lane is selected for this repair.

See the [test report](2026-10-15-goal-command-report.md) for actual outcomes,
unsuccessful development attempts, evidence and limits.