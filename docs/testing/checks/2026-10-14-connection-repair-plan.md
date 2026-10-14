# Connection repair build, contract and diagnostic checks

Date: 2026-10-14. These checks support the behavioral test levels; build success
and static verification are not end-to-end passes. Shared setup and source
provenance are in the [plan index](../2026-10-14-connection-repair-plan.md).

## Required source and contract checks

- Run generated contract checks through the canonical Agent runner:
  `bash scripts/run_tests.sh tests/tui_gateway/contracts/test_generated.py -q`.
  Require `test_generated_files_are_current` and
  `test_catalog_covers_the_whole_wire` to pass. Both passed in the recorded
  observer batches; do not double count them as additional behavioral tests.
- Run `python scripts/check --staged --base 66605471e9f` against a disposable
  candidate index. All 11 selected checks passed: footguns, shebangs, no-tmp,
  config-writers, os-marker-fakes, agents-md, case-collisions, lazy-deps,
  profile-archives, Ruff and health. Hooks remain enabled.
- Record the upstream source-conformance diagnostic independently. It failed
  on the unmodified Agent base because source-pattern checks did not recognize
  helper delegation. Behavioral compatibility passes do not erase that gap.

Exact selections, check implementations and baseline failures remain in the
[report](../2026-10-14-connection-repair-report.md).

## Android build and artifact checks

- Verify official toolchain archives and isolated Java/SDK/cache paths.
- Build only the selected debug app/test APKs needed by the
  [system plan](../system/2026-10-14-connection-repair-plan.md). Record failed
  packaging/setup attempts separately from executed app tests.
- Build the arm64 `sideloadCandidate` for phone acceptance from the final
  product SHA. Verify signature, package, app label, ABI and SDK manifest
  values; record byte count and SHA-256. Candidate build/signature verification
  passed. That optimized candidate APK has not been runtime-tested; the four
  automated system tests used `sideloadDebug` of the same product source.
- Check documentation links, selector/XML counts, evidence hashes, whitespace
  and public-repository privacy. Preserve the exact instrumentation patch.

## Earlier operational diagnostics

Historical payload size, isolated proxy frame-limit reproduction,
auto-approval evaluator configuration smoke, OAuth validation and SSH/network
checks are recorded in the report as diagnostic/configuration evidence. They
are not additional passing source regression tests. Preserve aggregate results
and limitations without publishing transcripts, tokens or private deployment
details. No live provider action, PC restart or deployment is authorized by
this documentation plan.
