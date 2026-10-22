---
name: session-handoff
description: Write an end-of-session handoff note into the project's handoff/ folder. Use when the user says "session handoff" or asks to hand off / wrap up the session for the next chat.
---

# Session handoff

Write a short note so the next chat can continue without re-reading this conversation.

1. Find the next number: list `handoff/sessions/`, take the highest `NN` and add 1 (start at 01).
2. Write `handoff/sessions/NN-YYYY-MM-DD.md` (today's date). **Hard limit: 20 lines total.** Use exactly these sections:
   - `# Session NN — date`
   - `## Done` — what changed this session, in plain words (3-6 bullets)
   - `## State` — does it build/run, what is installed where, any test data or temp state left behind
   - `## Next` — numbered, in the order the user wants them; say what is blocking each if anything
   - `## Gotchas` — only things that would trip the next session and are NOT already in CLAUDE.md
3. Copy the same text over `handoff/LATEST.md`.
4. If this session taught a lasting project fact (architecture, command, device quirk), put it in `CLAUDE.md`, not in the handoff.
5. Check the file is 20 lines or fewer (`wc -l`) and trim if not. Write for a person to read: no jargon dumps, no code blocks, no secrets.
6. Reply with the file path and a one-line summary of Next.
