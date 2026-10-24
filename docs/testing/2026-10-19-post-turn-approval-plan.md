# Post-parent-turn approval repair test plan

Baseline: `b40e3b5fa08391029e31ab2e704b044af0f9dc89`. Date: 2026-10-19.

Scope: an attached conversation must still present and answer a native approval
when its parent model turn has completed. Request ownership must retain the
original session, profile, socket generation and request identity; it must not
create a prompt or model turn. Existing active-turn behavior remains covered.

The [integration plan](it/2026-10-19-post-turn-approval-plan.md) selects five
client wire cases, one ViewModel/card-state case, one declarative fixture check
and the fixture's current-upstream source-conformance requirements. Establish
baseline failures before the repair, then execute only these focused selectors.

No APK assembly, instrumentation, emulator, device, provider or whole-suite
execution is authorized for this repair. The ViewModel case is in-process
component integration, not a rendered Android result. See the
[report](2026-10-19-post-turn-approval-report.md).
