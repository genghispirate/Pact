# Pact — instructions for AI sessions

**Before any UI, wording, world-engine, or product work: read `DESIGN.md` in this directory and
follow it exactly.** It is the design bible for v6 ("Haven") — every color token, string, screen
layout, animation duration, world-engine coordinate, psychology mechanic, milestone, and the
release procedure are decided there. Do not re-litigate decisions; implement the next unfinished
milestone from `DESIGN.md` §11 unless the user asks for something else.

**Positioning (locked, July 2026):** Pact is **a habit-building game where reducing screen time is
how your world thrives** — the world is the product, blocking is the mechanism. Build priority:
world → habit loop → social → progression → blocking (blocking last). Two decisions are locked:
**(1b)** an app-run **backend with accounts** (Google/email sign-in, unique usernames, social
discovery) powers ONLY social features — the blocking core stays offline/serverless and the app is
fully usable signed-out; **(2a)** a **discipline economy** — resources earned only from focus/
habits/less screen time, spent only on the world, never purchasable. Build **Phase A (single-player
game, offline, 6.x)** fully before **Phase B (accounts + social, 7.0)**. See DESIGN.md §14–§16.

Hard rules (duplicated from the bible so they're never missed):
- Develop on branch `claude/addiction-blocker-android-app-pl7wop` (Atlas-app repo); also sync
  every release to `genghispirate/pact` main via the worktree procedure in DESIGN.md §12.
- Compile + test + assembleRelease before every commit; swap the single `release/Pact-v*.apk`;
  send the APK to the user.
- Never touch `core/CryptoBox.kt`, `core/Wire.kt`, `core/Transport.kt`, `core/TrustNetwork.kt`,
  or `service/` during visual work.
- All copy in `strings.xml`; no emoji in UI chrome; banned words list in DESIGN.md §5.
- No new dependencies. No PRs unless the user asks. Users must never see crypto/technical terms.
