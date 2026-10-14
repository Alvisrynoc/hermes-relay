# Loading and status phone acceptance plan

Level: end-to-end acceptance. Status: pending; no execution claimed.
Use the existing HR Candidate installation updated with the artifact in the
[build report](../checks/2026-10-15-loading-status-candidate-report.md).

1. Open the affected real conversation while Desktop subagents work. Read the
   retained transcript across several passive refreshes. The loading banner
   must not repeatedly appear/disappear or shift the text. An explicit history
   load must still indicate progress; history-read failures remain visible.
2. While the main agent genuinely awaits input and children work, verify both
   Needs input and the owned child count remain visible. When the server reports
   working after that request closes, Needs input must clear. Do not treat child
   activity as proof that no real unanswered request exists.

Record actual phone results against the candidate APK hash. Automated reducer
and synthetic rendered checks do not verify this live conversation's request
state, phone firmware or notification delivery. No full-app matrix is selected.
