# Post-parent-turn approval integration plan

Use the serialized Windows Android lane with the focused JVM target:

```powershell
scripts/android-lane.ps1 --timeout-seconds 30 gradle :app:testSideloadDebugUnitTest `
  --tests '*GatewayChatClientTest.ownedApprovalAfterParentCompletionIsNotRejectedAsMissingTurn' `
  --tests '*GatewayChatClientTest.postTurnApprovalQueueExpiryAndForeignSocketGuards' `
  --tests '*GatewayChatClientTest.postTurnApprovalCannotReplyAfterProfileOrSessionChange' `
  --tests '*GatewayChatClientTest.postTurnApprovalRemainsOwnedAcrossRealUnsolicitedStart' `
  --tests '*ChatViewModelGatewayInboundTurnTest.ownedApprovalAfterParentTurnRendersAndRepliesWithoutStartingAnotherTurn' `
  --tests '*GatewayChatClientTest.native approval queue keeps each decision and rejects answers after session clear' `
  --no-daemon --console=plain
```

- Complete a parent, await a positive approval callback, answer its original
  typed ID once, and reject invalid choices/duplicates without another prompt.
- Queue numeric and string IDs, cancel the first, answer the next, reject
  foreign-session frames and prevent answers after the original socket closes.
- Reject stale answers after profile/session changes.
- Start a genuine incoming model turn while a prior child approval waits;
  answer/expire through the original request owner without interrupting it.
- Through production ViewModel callbacks, show the real pending card while idle,
  retain it across a real incoming start and answer it without manufacturing a
  turn. This is a component/card-state assertion, not Compose rendering.
- Retain the existing active-turn queue/session-clear control.

The baseline selects only the basic client and ViewModel cases. The final
client selection includes all five client cases. After strengthening the same
ViewModel scenario, rerun only that selector; production stays unchanged.

Extend the existing declarative fixture with `post_turn_approval.json`, and run:

```powershell
$env:PYTHONPATH = 'test-fixtures/vanilla-gateway'
python -m unittest discover -s test-fixtures/vanilla-gateway/tests `
  -p test_fixture.py -k approval_remains_answerable_after_parent_terminal -v
python scripts/check-gateway-scenario-conformance.py <clean-upstream-checkout> `
  --scenario-manifest test-fixtures/vanilla-gateway/vanilla_gateway/scenarios/post_turn_approval.json `
  --evidence-json <evidence-output>
```

The fixture must deliver approval after terminal completion/running=false and
settle the original response. Source conformance checks only the manifest's
`gateway.server_requests` and `gateway.message_complete` requirements against a
clean pinned upstream checkout; record failures, do not relax the checker.
