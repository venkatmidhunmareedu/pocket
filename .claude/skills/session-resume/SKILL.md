---
name: session-resume
description: Start-of-session catch-up. Use when the user says "session resume", "pick up where we left off", or starts a new chat on this project.
---

# Session resume

1. Read `handoff/LATEST.md`, then `CLAUDE.md`. If `handoff/` is missing, say so and rely on `CLAUDE.md` alone.
2. Check the handoff against reality before trusting it: does the project build, is the phone reachable (`adb devices`), is the state described still true? Note any difference.
3. Reply in at most 5 lines: what was last done, the current state, and the first item under Next.
4. Do not start work yet. Ask the user to confirm the first Next item or pick another.
