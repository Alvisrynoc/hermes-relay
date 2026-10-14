# Loading and status candidate build report

Date: 2026-10-15. Product source: `934c3fc75713a24a5714c24ff31a9369d6d797e7`
on `hermes_relay_connection_repair`. This includes separate banner and stale-input commits.

The existing ARM64 `sideloadCandidate` variant built successfully in 5m58s
(52 tasks: 13 executed, 39 up-to-date). No packaging source changes were needed.
Candidate metadata records the product source above; DEV_MODE is false.

Delivered file: `hermes-relay-1.18.1-loading-status-repair-uat-arm64.apk`.
Size: 59,610,352 bytes. SHA-256:
`BC32EC98A4DB5E5AFFCF32F3D57654CECB9AA9CD9ED50FB820CF41DD2C824802`.

APK v2 signature verification passed. Certificate SHA-256:
`c7a57d1095330c19a959bc910646a037a3224d5bc68cc8e543353b5c30630675`.
It matches preceding HR Candidate builds. Manifest inspection confirmed package
`com.axiomlabs.hermesrelay.sideload.candidate`, label `HR Candidate`, version
`1.18.1-sideload-candidate` / code 59, ARM64-only native libraries, target SDK 36.

These are build/artifact checks, not runtime certification of the optimized
candidate binary. Focused debug-system results are retained in the
[banner report](../2026-10-15-passive-banner-report.md) and
[stale-input report](../2026-10-15-stale-input-report.md). The isolated emulator
was stopped after both scenarios. No physical phone/provider/live conversation
was tested. [Phone acceptance](../e2e/2026-10-15-loading-status-acceptance-plan.md)
remains pending. Raw logs and APK delivery stay outside the public repository.
