# Connection repair test report

Date: 2026-10-14. See the [executed test plan](2026-10-14-connection-repair-plan.md)
for acceptance criteria, isolation and reproduction commands.

## Result and tested changes

The final isolated per-issue Android JVM selections passed **35 cases**.
The final Android runtime build passed **four selected scenarios in 30.103
seconds** on Pixel 6 / API 36 / AOSP x86_64. Backend observer, contract, roster
and resume compatibility batches passed, together with the **11 required
candidate checks**. The optional REST and source-conformance diagnostics have
baseline failures described below; this is not an all-suite or all-platform pass.

| Issue | Relay commit | Hermes Agent commit | Independent Android JVM result |
|---|---|---|---:|
| 1: lightweight attachment | `04a33c6509818c76c19c29b5b07bd11d5c0cbec5` | Existing support; no Agent change | 4 passed |
| 2: recent history/loading/errors | `67871dcc668b5f1ffd4c33c94f26e478544edcd5` | Existing support; no Agent change | 12 passed |
| 3: pending-input observation/review | `cc2c9b2336db4cada4d099f2fde3784b791ad81f` | `b14240c3b392c85b4d42aabf4606e1e00566628f` | 17 passed |
| 5: observed subagent count | `b99af532f2db8c39c81bd5665507d9a5c20992c9` | `36fad15d55a68c427f784ea076304d5dd473379c` | 2 passed |

Relay base: `eeb6e94a`. Agent base: `66605471e9f`. JVM batches used independent
incremental issue stacks. Runtime validation used the final Relay product
commit plus an isolated test-only instrumentation patch. No additional product
change, release, push, live restart or phone installation resulted from runtime
testing. Repair commits retained their configured local bot identity.

## Test-level mapping

Plans are separated in the [index](2026-10-14-connection-repair-plan.md).
The final 35 JVM cases comprise 18 unit/component cases (pagination 8,
Robolectric notifier 10) and 17 client/handler/ViewModel integration cases
(Gateway 9, REST client 2, ViewModel 6). Agent observer/roster/session batches
are in-process integration coverage; generated-contract and required source
checks are supporting checks. The four Android runtime cases are chat-subsystem
system tests with synthetic external peers. Real phone/Desktop end-to-end
acceptance is pending. Reclassification does not add executions or erase the
unsuccessful attempts below.

## Evidence and provenance

| Evidence | Retention and meaning |
|---|---|
| [JVM selections](evidence/2026-10-14-connection-repair/jvm-selections.json) and `issue-*` JUnit XML | Exact final 35 testcase identities and results. XML hostname, properties and console streams removed; result identities/counts retained. |
| [Final runtime output](evidence/2026-10-14-connection-repair/runtime-final.txt) | Four explicit selectors, four success statuses, `OK (4 tests)`, 30.103 seconds. |
| [Runtime summary](evidence/2026-10-14-connection-repair/runtime-summary.json) | Product SHA, source/APK hashes, emulator lane and limitations. |
| [Runtime test patch](evidence/2026-10-14-connection-repair/runtime-tests.patch) | Exact isolated instrumentation source and fixture extensions. Not part of the product repair commit. |
| Backend and initial diagnostic runs | Outcomes were recorded during execution; standalone raw console logs were not retained. Commands below are canonical equivalents, not verbatim saved shell transcripts. |
| Intermediate Android build/runtime attempts | Attempt inventory below; original local logs stayed outside the repository where they contain private workspace paths. Final public evidence is sanitized. |

Do not add backend batch counts together: observer/contract cases were reused
between stages. Do not count intermediate runtime failures, interrupted starts
or feedback runs as additional successful unique tests.

The documentation bundle was checked for local link validity, JSON/XML result
counts, patch hash consistency and private metadata. `git apply --check` passed
for the saved test-only patch against the product source. Evidence attributes
preserve that patch's exact bytes and required unified-diff blank context markers.

## Focused Android JVM tests

All cases below passed in the corresponding final isolated issue tree. Class
names are under `com.hermesandroid.relay`.

### Issue 1 — four cases

`network.upstream.GatewayChatClientTest`:

- `existing session id is resumed not recreated`
- `recoverTurn activates exact live session and continues deltas and tool events`
- `recoverTurn falls back to durable resume when activate is unsupported`
- `socket loss mid-turn activates original session without resume and completes`

These cover bounded attachment parameters on the relevant resume/activate
paths, preservation of session identity, and continuation after socket loss.

### Issue 2 — twelve cases

`network.upstream.DashboardApiClientTest`:

- `getSessionMessages_latestUsesOneBoundedPage`
- `getSessionMessages_scopesToProfileAndParsesUpstreamEnvelope`

`network.upstream.SessionMessagePaginationTest`:

- `cancellation is never converted to an empty transcript`
- `complete history pages oldest first without gaps`
- `latest mode makes exactly one bounded latest request`
- `legacy envelope is accepted as one complete response`
- `exact 500 requests one terminal page and returns no duplicates`
- `payload safety bound fails clearly before another page`
- `recent refresh keeps older rows only through exact overlap`
- `older history requests one earlier latest window`

`viewmodel.ChatViewModelGatewayInboundTurnTest`:

- `passiveHistoryRefreshShowsLoadingAndKeepsTranscriptOnFailure`
- `staleHistoryReadCannotEraseATurnCompletedDuringTheFetch`

### Issue 3 — seventeen cases

`network.upstream.GatewayChatClientTest`:

- `active snapshot accepts optional sanitized pending identities`
- `input review admits replay before callbacks and stays desktop owned`
- `input review rejects stale admission without delivering or interrupting`
- `rejected input review drops buffered asks without replying in the next owner`

The last regression includes late stale frames and later legitimate review of
the same unanswered request; answered/cancelled retirement stays separate.

`notifications.InteractionRequestNotifierTest`:

- `copyAndPublicVersionNeverExposePromptCommandOrSecretMetadata`
- `resolvedOrExpiredRequestCancelsOnlyItsStableSlot`
- `missingAndroidPermissionSkipsPosting`
- `backgroundAndSettingAndPermissionGatesAreExplicit`
- `appRelaunchCleanupRemovesOnlyInteractionChannelNotifications`
- `newInputChannelEnablesVibration`
- `ownedInputTapRequestsReviewWhileOrdinaryConversationTapStaysPassive`
- `existingInputChannelKeepsUserVibrationPreference`
- `requestReplayReplacesOneNotificationAndDeepLinksToExactSession`
- `sameStoredSessionInTwoProfilesKeepsIndependentNotificationSlots`

`viewmodel.ChatViewModelGatewayInboundTurnTest`:

- `inputNotificationReviewRejectsWrongOwnerAndSupervision`
- `desktopPendingSnapshotAlertsWithoutClaimingOrAnsweringRequest`
- `inputNotificationReviewReplaysCardWithoutInventingPhoneTurn`

### Issue 5 — two cases

- `network.upstream.GatewayChatClientTest.active snapshot child count is optional and nonnegative`
- `viewmodel.ChatViewModelGatewayInboundTurnTest.observedSubagentCountStaysWithExactOwnerAndClearsWhenUnavailable`

The ViewModel regression covers foreign ownership, immediate owner-switch
clearing, repopulation and clearing after unsupported/unavailable activity.

### Intermediate JVM feedback

An earlier combined working-tree selection passed **11 cases** after cold
toolchain setup (9m46s elapsed): Gateway client 2, pagination 8, ViewModel 1.
It contained all repair changes and is not used as independent per-issue
evidence. Its sanitized XML is retained under `mixed-feedback/` in the evidence
directory. Exact recovered selection:

```powershell
.\scripts\android-lane.ps1 --timeout-seconds 30 gradle :app:testSideloadDebugUnitTest `
    --tests '*GatewayChatClientTest.existing session id is resumed not recreated' `
    --tests '*GatewayChatClientTest.recoverTurn falls back to durable resume when activate is unsupported' `
    --tests '*SessionMessagePaginationTest*' `
    --tests '*ChatViewModelGatewayInboundTurnTest.passiveHistoryRefreshShowsLoadingAndKeepsTranscriptOnFailure' `
    --no-daemon --console=plain
```

The first final issue-3 selection passed 16 of 17 cases. The remaining request
race test failed with `server socket never opened` because the fixture's socket
queue was consumed twice for the same connection. Reusing that socket corrected
the test; the same 17-case selection passed. Issue 5 first passed two cases,
then passed the same two after its ownership-switch assertion was strengthened.
Other development feedback without retained counts is not represented as a
separate fully certified test run.

## Hermes Agent behavioral and contract checks

All commands in this section are canonical equivalents from an isolated
candidate checkout/home. Use `scripts/run_tests.sh`, not bare pytest.

| Batch | Selection | Recorded result |
|---|---|---|
| Existing active-list regression | `test_session_active_list_reports_live_sessions`, `test_session_active_list_excludes_finalized_sessions` | 2 passed |
| Resume/activate compatibility | Entire `tests/tui_gateway/test_resume_live_lazy_session.py` plus six selected resume/activate cases in `test_tui_gateway_server.py` | 13 passed |
| Isolated issue 3 | Four parameterized observer cases: approval/clarify × explicit profile home/named launch-home fallback; generated TypeScript/OpenRPC contract tests | 6 passed |
| Issue 3 + 5 candidate | Observer 5 + existing roster/ownership/lineage 8 + generated contracts 2 | 15 passed |

The observer checks require explicit profile attribution, only `{id, method}`
for pending requests, no command/prompt exposure, cleared settled requests,
conversation-only child counts and unchanged control ownership.

The eight existing roster cases in `tests/tui_gateway/test_subagent_snapshot.py`
were:

- `test_snapshot_projects_only_this_sessions_runtime_records`
- `test_live_tail_and_steer_share_exact_owner_and_end_with_child`
- `test_interrupt_requires_exact_live_owner_but_direct_helper_stays_legacy`
- `test_reattach_preserves_child_controls_including_late_registration`
- `test_reattach_does_not_adopt_foreign_or_retired_generations`
- `test_any_attach_path_carries_subagent_authority_without_registry_sync`
- `test_list_follows_the_conversation_across_ui_sid_and_compression_rotation`
- `test_list_surfaces_failed_delegations_that_outlived_the_live_roster`

Generated contract tests in `tests/tui_gateway/contracts/test_generated.py`
were `test_generated_files_are_current` and `test_catalog_covers_the_whole_wire`.
Canonical equivalent final candidate selection:

```bash
bash scripts/run_tests.sh tests/tui_gateway/test_session_observer_snapshot.py \
  tests/tui_gateway/test_subagent_snapshot.py \
  tests/tui_gateway/contracts/test_generated.py -q
```

The resume compatibility selection includes seven parameterized live/lazy
resume cases and these six server cases:

- `test_session_resume_uses_parent_lineage_for_display` (two parameterized cases)
- `test_session_resume_deferred_history_acknowledges_and_reuses`
- `test_session_resume_deferred_history_failure_can_retry`
- `test_session_resume_deferred_history_close_cancels_build`
- `test_session_activate_can_omit_duplicate_desktop_transcript`

Canonical equivalent selection:

```bash
bash scripts/run_tests.sh tests/tui_gateway/test_resume_live_lazy_session.py -q
bash scripts/run_tests.sh tests/tui_gateway/test_tui_gateway_server.py \
  -k 'session_resume_uses_parent_lineage_for_display or session_resume_deferred_history or session_activate_can_omit_duplicate_desktop_transcript' -q
```

### Eleven required candidate checks

`python scripts/check --staged --base 66605471e9f` was run with a disposable
candidate `GIT_INDEX_FILE`. All eleven checks passed on that candidate. This
does not claim that unrelated ignored desktop cache/archive files passed a
whole-host working-tree scan. Pre-commit Ruff also passed using the isolated
test environment; hooks were not bypassed.

| Check | Implementation |
|---|---|
| `footguns` | `scripts/check-windows-footguns.py --all` |
| `shebangs` | `scripts/check_bash_shebangs.py` |
| `no-tmp` | `scripts/check_no_tmp_literals.py` |
| `config-writers` | `scripts/check_config_yaml_writers.py` |
| `os-marker-fakes` | `scripts/ci/check_os_marker_fakes.py` |
| `agents-md` | `scripts/ci/check_agents_md_size.py` |
| `case-collisions` | `scripts/check-case-collisions.py` |
| `lazy-deps` | `scripts/ci/check_lazy_deps_imports.py` |
| `profile-archives` | `scripts/ci/check_profile_archive_boundary.py` |
| `ruff` | Required Ruff lane |
| `health` | Required health lane |

## Optional REST and upstream-conformance diagnostics

These were compatibility diagnostics, not changes to the REST API.

```bash
bash scripts/run_tests.sh tests/hermes_cli/test_session_message_page_owner.py \
  tests/hermes_cli/test_session_messages_inline_images.py -q
bash scripts/run_tests.sh tests/hermes_cli/test_web_server.py \
  -k 'get_session_messages_latest_page_with_compacted_rows or get_session_messages_omitted_limit_defaults_to_500' -q
```

The optional seven-case selection recorded **six passes and one baseline
failure** on clean upstream source/home:

- Serving-profile page ownership: named profile passed; custom-launch/default
  profile case failed; untyped failed-turn rows passed.
- Image expansion default and `inline_images=false` placeholder both passed.
- Latest compacted chronological page and omitted-limit default of 500 both
  passed.

The failed default-profile case expected one default-home row, while the
unmodified baseline resolved the custom launch home. It was preserved, not
silently fixed as part of these repairs. Earlier cache entries also show failures
for the two web selectors; their original failure cause was not retained.
Their isolated clean-source rerun passed. Counts overlap the four passing
latest-window/offset/image compatibility checks and must not be added twice.

The Relay source-conformance checker also failed against the **unmodified Agent
base** because its source-pattern checks did not recognize existing helper
delegation for durable session seams. The relevant source inspection is not
reported as passing upstream conformance. Behavioral compatibility tests passed;
that does not turn a failed source checker into a pass. See the existing
[Gateway contract testing guide](../gateway-contract-testing.md) for the runner.
The recovered command is only a canonical equivalent:
`python scripts/check-gateway-scenario-conformance.py <clean-vanilla-upstream-checkout>`.
The exact original manifest argument/failure list was not retained and is not
invented here.

## Android runtime results

All four methods in the final same-build run passed:

| Method | Proven behavior | Result |
|---|---|---|
| `largeHistoryAttachment_survivesTransportCapAndSends` | >4 MiB stored history; capped socket survives attachment/send; correctly owned persisted reply can be read; Ready; no interrupt | Pass |
| `recentHistory_refreshShowsLoadingErrorAndRetainsTranscript` | Latest 50 of 80; real loading/error UI; retained newest row; healthy passive recovery clears error | Pass |
| `desktopInput_postsOwnedNotificationAndReviewRendersAuthenticatedCard` | OS notification/channel/exact-owner identity; passive observation; authenticated review renders approval card without automatic answer | Pass |
| `observedSubagents_renderOnlyCurrentConversationAndClearOnSwitch` | Rendered owned count; foreign work excluded; owner switch clears label | Pass |

The actual app was `sideloadDebug`, compiled against SDK 37, running on API 36.
The fixture and production chat/client/ViewModel/OS notification components
were exercised; provider responses and historical content were synthetic.

### Runtime artifact hashes (SHA-256)

- Product app APK: `5023BA5488E508D816C81E693D2EFB988EF3ECC4D06B45CB450DC09E2ABE7C70`
- Final test APK: `D290F6A135B558D7C55E91CD124B7C6825E49D16A86298F45344F92C991571AE`
- Final test-only patch: `DA768806BE3FBD9A2D64BD9E7D7E4E9097710A267B16DE27E030C56BD87530F6`

### Runtime attempt history

| Attempt | Selection/outcome | Correction or interpretation |
|---|---|---|
| Initial four-method run | First two methods failed shared setup with `ComposeNotIdleException`; third started before cancellation; fourth not executed | Stopped/force-stopped only the test target. Disable unrelated procedural sphere animation through an existing test CompositionLocal; remember snackbar hosts. No product change. |
| Issue 1 single-method run, 9.118s | Reply reached handler; immediate displayed-text assertion failed | Synthetic completed reply was not yet persisted in authoritative fixture history; persisted before terminal event for subsequent runs. |
| Issue 1 retry, 9.525s | Same display assertion failed | The persistence correction was valid but did not resolve the separate viewport/harness problem. No pass claimed. |
| Issue 2 single-method run, 7.288s | Expected 50 rows but read setup's one cached row | Start setup without a saved session/history; configure each scenario before opening; wait exact owner, requested page/tail, both history loading flags and Ready. |
| Issues 3 + 5, 13.222s | Both passed on the earlier helper | Useful evidence; later reconfirmed on the final common build. |
| Corrected issues 1 + 2, 19.503s | Issue 2 passed; issue 1 reply existed in exact-owner VM and handler but was below viewport | Saved synthetic screenshot/semantics. Connection send was successful; automatic viewport assertion remained a different UI action boundary. |
| Bounded-settling test build | Test APK built; this intermediate version was not executed | No invented pass/fail for an unexecuted version. |
| Final scope correction | Retained cap/Ready/submit/no-interrupt/owned-reply checks and real rendered reply assertion; used normal list scroll action to read the reply | Direct ViewModel send does not invoke ChatScreen's Send-button follow reducer. Automatic following is outside this attachment-boundary scenario; no scrolling product code changed. |
| Final four-method run, 30.103s | Four successes; `OK (4 tests)` | Final common source/test build; authoritative runtime result. |

## Build and environment attempt history

These are prerequisites/diagnostics, not additional passing app tests.

| Activity | Outcome |
|---|---|
| Portable JDK/SDK archive verification | Official download checksums verified; isolated tools provisioned. Gradle configuration `help --no-daemon --stacktrace` passed. |
| Initial focused-test toolchain startup | Long temporary path exceeded Java local socket constraints; process-local short TEMP/TMP resolved it. |
| Initial Android test attempt before toolchain setup | Java was unavailable; zero tests executed. |
| Initial backend environment | Installed Python 3.11 did not meet the source's Python 3.14 requirement. A disposable PM/test-home environment resolved setup without modifying the live environment; exact initial error transcript not retained. |
| Initial broader instrumentation task request | Two existing lifecycle/service classes were selected, then the task was cancelled before runtime execution when scope was corrected to four repair-specific checks. It provides no app-runtime pass evidence. |
| SDK preference-home setup | Analytics initially attempted the real user preference directory. Isolated Java/Android homes were added. Conflicting legacy Android preference variables caused an app-build configuration failure; removed process-local aliases. |
| App APK assembly | `:app:assembleSideloadDebug` passed in 2m27s; APK installed only on the selected emulator. Native symbol-stripping warning did not prevent packaging. |
| First test APK assembly | Unsupported assertion import and unavailable permission-rule dependency failed Kotlin compilation; also a native AAPT2 link crash. Removed the import and granted the emulator test permission through UiAutomation; no dependency/product change. |
| Same test APK, one worker | Kotlin compiled; Maven AAPT2 still crashed with empty error output. |
| SDK Build Tools AAPT2 override | Native crash also occurred while relinking app resources. Temporary diagnostic property only; not adopted as a repair. |
| Shorter isolated checkout | Source hashes verified; same Maven AAPT2 link crash persisted. Does not establish a path-length cause. |
| Same build outside execution sandbox | Original Maven AAPT2 linked successfully; test APK assembly passed in 32s. Evidence points to the execution sandbox restriction, not a required source/tool-version fix. |
| Subsequent test-only APK rebuilds | Visualization, persistence, fresh-history, bounded-settling and final scoped versions each built successfully, approximately 19–20s per rebuild. Only the executed selections above carry runtime results. |
| Emulator readiness | WHPX usable, API 36/x86_64, boot completed, exact isolated serial/ADB server. Existing Docker/device listeners preserved. |
| Cleanup and repository checks | Test emulator shut down after final success. Both repair product repositories were clean before these requested documentation additions. No global Windows feature/PATH changes. |

## Pre-repair diagnostic and configuration checks

These explain the original investigation and are not counted as the 35 JVM or
four runtime regressions. Original private logs/configuration are not published.

| Check | Recorded observation and limit |
|---|---|
| Historical payload inspection | Existing conversation metadata exceeded the 4 MiB frame ceiling (roughly 4.45 MiB of opaque reasoning/details). Only aggregate size is recorded; no personal transcript is included. |
| Isolated installed Secure Link proxy reproduction | 1 KiB at 4 MiB cap delivered; 5 MiB at 4 MiB cap failed with size-limit closure; 5 MiB at an in-memory diagnostic 16 MiB cap delivered. No live cap increase was deployed. |
| Auto-approval evaluator configuration smoke | Isolated Hermes home, configured OAuth provider, retained model/low effort: benign Python-print command description approved; destructive root-delete description denied. Neither command was executed. Candidate/saved-configuration probes and warning/auth-error inspection were performed; no full live-agent approval-history coverage is claimed. |
| OAuth configuration inspection/validation | Stored login entries and direct token-validation results were inspected before requested cleanup. No credentials, token values or account identifiers are retained in this report. This was configuration diagnosis, not Relay regression coverage. |
| SSH/network operational diagnostics | Service/listener/firewall/key-file/ACL checks and reachability diagnostics were performed during earlier setup. User-reported network recovery followed a VPN toggle. These are not automated proof of mobile Meshnet reachability or successful post-key-install phone authentication. |
| Git checks | Repair branch, author/committer configuration, issue-commit sequence and tracked-clean state checked. Local commits only; no push or deployment. |

## Phone acceptance candidate artifact verification

The arm64 `sideloadCandidate` APK was assembled from product commit
`b99af532f2db8c39c81bd5665507d9a5c20992c9`. Assembly, including R8 and the
variant's lintVital tasks, passed in 7m46s. Signature verification and static
manifest inspection passed. This is build/artifact evidence, not a phone
acceptance result or a runtime pass for this optimized APK. The four automated
runtime tests above used the separate `sideloadDebug` variant.

| Property | Verified value |
|---|---|
| File | `hermes-relay-1.18.1-connection-repair-uat-arm64.apk` |
| Package / label | `com.axiomlabs.hermesrelay.sideload.candidate` / `HR Candidate` |
| Version | `1.18.1-sideload-candidate`, code 59 |
| ABI / SDK | `arm64-v8a`; min 26, target 36, compile 37 |
| Size | 59,608,664 bytes |
| SHA-256 | `889EC7776B8B07ABE225A3DF968C1E17275E2427B6834262D3E2B7B55BDF0AA1` |
| MD5 | `683D80369B6702FF2B85FBCDB18BDC7B` |
| Signing | Isolated debug certificate; v2 verified; one signer |
| Certificate SHA-256 | `c7a57d1095330c19a959bc910646a037a3224d5bc68cc8e543353b5c30630675` |

The separate package installs alongside existing Relay and requires pairing
again. PC backend activation, phone installation, real notification sound/
vibration and full notification-tap navigation remain unexecuted prerequisites
or checks in the [E2E plan](e2e/2026-10-14-connection-repair-plan.md).

## Remaining validation limits

- Samsung physical sound/vibration, One UI background limits, locked-device
  delivery, killed-app/offline behavior and actual Internet/Meshnet conditions
  were not tested on the repaired phone build.
- Complete OS-notification → MainActivity → chat navigation was not executed.
  The JVM route flag, OS notification identity and production authenticated
  review/card endpoint were tested separately.
- Synthetic ConnectionViewModel header readiness is unbound; actual Ready
  assertions use GatewayChatClient. Real account sign-in and full app wiring
  are not certified by this harness.
- Automatic Send-button following is not tested by direct ViewModel submission;
  reply rendering was read through a normal list scroll action.
- No full Android suite, full emulator/form-factor matrix, live production
  session mutations or full backend suite was run. No performance conclusion
  about slow Internet or a real large session database follows from loopback
  fixtures.
- The optional REST baseline failure and source-conformance checker failure
  remain explicit gaps; they are not silently represented as passed checks.
