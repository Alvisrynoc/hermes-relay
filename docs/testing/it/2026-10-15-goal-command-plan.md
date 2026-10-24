# Goal command integration test plan

See the [plan index](../2026-10-15-goal-command-plan.md) for scope and baseline.

`ChatViewModelGoalCommandTest` exercises the production view model and gateway
client with the existing real-socket `GatewayClientHarness`. Its selected methods
cover cold observed ownership, idle reconnect after a successful send, unchanged
live binding, genuine draft creation, exact resume refusal, literal display vs
transport expansion, delayed replies, away-and-back navigation, delayed public
catalog lookup, and compatibility for sessionless quick commands. These are
integration checks rather than a provider or Android-device certification.

```powershell
.\scripts\android-lane.ps1 gradle :app:testSideloadDebugUnitTest `
  --tests '*ChatViewModelGoalCommandTest*' --no-daemon --console=plain
```

The initial diagnostic class separately verifies the current missing-session
wire shape and swallowed native `type=send` result, including the positive
unchanged-socket countercheck. Backend checks use `scripts/run_tests.sh` with a
disposable interpreter/home and file isolation. The existing selections are:

```bash
scripts/run_tests.sh tests/tui_gateway/test_goal_command.py \
  -k 'slash_exec_routes_goal_to_command_dispatch or goal_resume_after_budget_exhaustion_dispatches_continuation or goal_resume_without_goal_stays_exec' \
  -q --file-retries 0
```

A temporary test copied from that existing fixture adds one provider-free case:
`command.dispatch(goal)` without a session must return code `4001` and
`no active session`; `slash.exec('/goal')` with the valid session succeeds.
The backend is unchanged by this repair.

The [report](../2026-10-15-goal-command-report.md) distinguishes baseline
failures, intermediate fencing checks, final selections and retained evidence.