# Stale input unit test plan

See the [plan index](../2026-10-15-stale-input-plan.md).

Select exactly three methods in `SessionActivityRegistryTest`:

- `authoritativeWorkingClearsOlderPendingInputOnlyForItsOwner`
- `authoritativeWorkingPreservesInputNewerThanSnapshot`
- `authoritativeWaitingPreservesOlderPendingInput`

```powershell
.\scripts\android-lane.ps1 gradle :app:testSideloadDebugUnitTest `
  --tests '*SessionActivityRegistryTest.authoritativeWorkingClearsOlderPendingInputOnlyForItsOwner' `
  --tests '*SessionActivityRegistryTest.authoritativeWorkingPreservesInputNewerThanSnapshot' `
  --tests '*SessionActivityRegistryTest.authoritativeWaitingPreservesOlderPendingInput' `
  --no-daemon --console=plain
```

The first case includes a different owner whose pending state must survive.
The newer-input case applies two older snapshots to catch loss of the retained
pending evidence timestamp. The Waiting control preserves the genuine input
phase and request identity. These are pure reducer unit checks, not RPC tests.
