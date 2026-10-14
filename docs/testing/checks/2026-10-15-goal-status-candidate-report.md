# Goal and observed-status candidate build report

Date: 2026-10-15. Product source: `709c8b72531953ca095438878bfbb7002fc81dbb`
on `hermes_relay_connection_repair`.

The existing ARM64 `sideloadCandidate` variant built successfully in 5m51s
(52 tasks: 24 executed, 28 up-to-date). No packaging source change was needed.
The candidate metadata records the product source above; DEV_MODE is false.

The delivered file is `hermes-relay-1.18.1-goal-status-repair-uat-arm64.apk`.
Size: 59,610,352 bytes. SHA-256:
`5183DAE033360F9416D997F82D00CD5BEE9C49088D1876E9287712BDE15025C0`.

APK signature verification passed using v2. The signer certificate SHA-256 is
`c7a57d1095330c19a959bc910646a037a3224d5bc68cc8e543353b5c30630675`,
matching the preceding HR Candidate. Manifest inspection confirmed:

- Package: `com.axiomlabs.hermesrelay.sideload.candidate`.
- Label: `HR Candidate`.
- Version: `1.18.1-sideload-candidate`, code 59.
- Native architecture: `arm64-v8a`; target SDK 36.

These are build and artifact checks, not runtime acceptance of the optimized
candidate binary. The reviewed production sources passed the focused debug
Android system scenarios described in the [goal report](../2026-10-15-goal-command-report.md)
and [status report](../2026-10-15-observed-status-report.md). The isolated emulator
was stopped after those scenarios. This APK has not been installed on a physical
phone; [phone/Desktop acceptance](../e2e/2026-10-15-goal-status-acceptance-plan.md)
remains pending. Raw build logs and APK delivery stay outside the public repository.
