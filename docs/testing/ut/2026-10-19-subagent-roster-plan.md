# Subagent roster unit test plan

Select only these controller methods through the serialized Android JVM lane:

- `SubagentActivityControllerTest.rosterAbsenceRetiresOnlyComparableUnchangedChildren`
- `SubagentActivityControllerTest.detached child survives parent completion and a new turn with stable preview identity`

An exact successful roster removes an unchanged absent subagent ID, preserves
present children and newer progress, and cannot apply to a foreign scope.
A child-session-only card has no comparable roster namespace and must survive.
Late events cannot resurrect a retired child. Parent completion alone must keep
an identified live child; retain that existing control.

For the new selected source-conformance requirement, run the existing fixture
control and the focused authority/identity check:

```powershell
python -m unittest discover -s scripts/tests -p check_gateway_scenario_conformance_test.py `
  -k subagent_roster -v
python -m unittest discover -s scripts/tests -p check_gateway_scenario_conformance_test.py `
  -k test_complete_contract_fixture_passes -v
```

The new check must fail cleanly if authority, method or source file disappears.
Do not change the previous generic server-request checker.
