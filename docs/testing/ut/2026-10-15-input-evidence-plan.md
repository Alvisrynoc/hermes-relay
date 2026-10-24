# Pending input evidence unit plan

See the [plan index](../2026-10-15-input-evidence-plan.md).

Select exactly these `SessionActivityRegistryTest` methods through the Windows
Android lane's `:app:testSideloadDebugUnitTest` target:

- `cachedCheckpointInputCannotOverrideConfirmedWorkingOrReturnAfterPolling`
- `processUpdatesDoNotMakeAnOldInputNewerThanWorkingSnapshot`
- `liveWorkingDiscardsUnconfirmedColdCheckpointInput`
- `authoritativeWorkingClearsOlderPendingInputOnlyForItsOwner`
- `authoritativeWorkingPreservesInputNewerThanSnapshot`
- `authoritativeWaitingPreservesOlderPendingInput`

The three new regressions cover repeated cache resurrection, unrelated process
evidence protecting an old input, and cold unconfirmed hints surviving live
confirmation. The existing three controls retain exact owner, genuinely newer
input across delayed snapshots, and real Waiting behavior. These are pure
reducer unit checks, without Android rendering or network assertions.
