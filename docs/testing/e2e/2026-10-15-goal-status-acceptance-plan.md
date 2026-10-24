# Goal and observed-status phone acceptance plan

Level: end-to-end acceptance. Status: pending; no execution claimed.
Use the repaired HR Candidate with the real Desktop and phone connection.
The candidate artifact checks are in the [build report](../checks/2026-10-15-goal-status-candidate-report.md).

1. Open an existing Desktop conversation in Relay and run the same `/goal`
   command that succeeds on Desktop. Verify no `no active session` failure.
   After a normal phone message completes, repeat the command, checking the
   reported always-after-send condition that automated diagnosis did not reproduce.
   If that goal command starts/resumes work, verify its continuation appears.
2. Observe a Desktop-started turn in that chat. Verify main-agent working,
   child work/count when the parent is idle, and ready when work completes.
   If the gateway becomes unavailable, the row must not claim ready.

Record actual results and failures against the candidate APK hash. No full-app
matrix, notification delivery or firmware certification is implied by these two checks.
