# Goal command Android rendered test plan

See the [plan index](../2026-10-15-goal-command-plan.md).

Select exactly:

```text
com.hermesandroid.relay.viewmodel.GoalCommandInstrumentedTest#goalResumeAttachesSelectedChatAndRendersItsContinuation
```

Use the standard Pixel 6 AOSP x86_64 API 36 emulator and the exact repair source,
through the Windows Android build lane. Reuse the existing embedded Gateway
fixture, with only an optional goal catalog and native slash result extension.

The scenario opens a saved synthetic conversation passively, invokes the public
view-model `/goal resume` path, verifies exact stored/profile resume identity,
and checks one native continuation prompt. The production chat screen renders
the goal notice, literal command and server continuation reply. Expanded
transport text must stay out of local message display. The test uses normal
list scrolling to read rows rather than testing Send-button auto-follow.

This is a subsystem system check against a synthetic server, not a real-phone
end-to-end acceptance check.
It does not exercise a real provider, complete app navigation, phone firmware,
real-network behavior or live deployment. The independently constructed
ConnectionViewModel is not a fixture-backed live header, so actual transport
readiness comes from GatewayChatClient and the authenticated JSON-RPC flow.

The [report](../2026-10-15-goal-command-report.md) records the exact execution and
artifact hashes after the selected scenario runs.