# Session 04 — 2026-10-22
## Done
- Benchmarked category guessing on 50 made-up Indian payees: e5-small 60%, MiniLM 54%, FunctionGemma 42%; nothing shipped, brands defeat small models.
- Added AI chat ("Ask Pocket (AI)" in the side menu): OpenAI-compatible base URL / key / model, streaming replies, saved chat history drawer, rename/delete, stop, copy, local spending-trend chart.
- Payment watcher: Amazon Pay fixed, old records trigger like any other, stacking notifications, "Stop scanning" button.
- Listed big disk hogs for the user (docker images, npm/pnpm caches, Cursor, huggingface).
- Git repo (main, no remote yet), README, release build with R8 and a signing key (not committed); R8 build tested on the phone.
## State
- Builds and is installed on the phone (USB 167dfaca). Chat tested only against a mock server; the mock config was removed from the phone.
- Phone still has SAMPLE data, 2 test files in Documents/PocketBackup, and one test chat.
## Next
1. Try the chat with a real provider (user's key); check long replies, errors, rotation.
2. Category guessing is done (learned payee, spelling match, word list); check it on real payees. Provider-based fallback is a noted opt-in idea only (privacy).
3. Clear sample data; landscape pass; 3 unseen themes; signed release build; CSV import.
## Gotchas
- Chat sends a summary of the user's records to the chosen provider; say so if the feature is described to others.
