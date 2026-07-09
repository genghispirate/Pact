# Pact — instructions for AI sessions

**Before any UI, wording, world-engine, or product work: read `DESIGN.md` in this directory and
follow it exactly.** It is the design bible for v6 ("Haven") — every color token, string, screen
layout, animation duration, world-engine coordinate, psychology mechanic, milestone, and the
release procedure are decided there. Do not re-litigate decisions; implement the next unfinished
milestone from `DESIGN.md` §11 unless the user asks for something else.

Hard rules (duplicated from the bible so they're never missed):
- Develop on branch `claude/addiction-blocker-android-app-pl7wop` (Atlas-app repo); also sync
  every release to `genghispirate/pact` main via the worktree procedure in DESIGN.md §12.
- Compile + test + assembleRelease before every commit; swap the single `release/Pact-v*.apk`;
  send the APK to the user.
- Never touch `core/CryptoBox.kt`, `core/Wire.kt`, `core/Transport.kt`, `core/TrustNetwork.kt`,
  or `service/` during visual work.
- All copy in `strings.xml`; no emoji in UI chrome; banned words list in DESIGN.md §5.
- No new dependencies. No PRs unless the user asks. Users must never see crypto/technical terms.
