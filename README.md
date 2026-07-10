# Pact 🛡

**A private, encrypted trust network for beating phone addiction — together.**

Pact puts your distracting apps on a **daily budget**. You don't quit them forever; you decide up
front how many minutes a day each one gets. When the time runs out, the app locks — and the only
way to more is your **circle**, the people you trust. Then the part that makes it stick: you and
your friends **share your screen time** and **race to keep the longest streak** within your
limits. No passwords, no accounts, no servers, no codes to type.

<p align="center"><em>Ready-to-install APK: <a href="release/Pact-v5.20.apk"><code>release/Pact-v5.20.apk</code></a></em></p>

---

## How it works

Pact has two sides, chosen on first launch:

**If you're budgeting your apps:**

1. **Choose your name and build your circle.** Add trusted people — a partner, a parent, a
   friend, a therapist. In person, they scan your QR. Apart, you generate a **short invite code**
   and send it over any messenger; they type it in. Either way, one step pairs you, and you can
   add as many people as you like.
2. **Pick your apps and set a daily limit for each.** Slide Instagram to 20 minutes, TikTok to
   10, or all the way to a hard lock. A guided, one-permission setup raises the shield; the
   accessibility service then measures your time in each app and steps in when the budget's gone.
3. **Ran out of time today?** The wall goes up. What happens next is your choice, per app:
   - **Ask my circle** (default): the wall sends a request to your trusted people. They see the
     app, how long you asked for, and why. If they approve — per your rule — the time starts
     automatically, even if you walked away.
   - **Mindful pause**: a 30-second breath and a quick "what's pulling you?", then a short
     stretch of extra time — no one to ask, but a cooldown stops back-to-back top-ups.
   - Staying inside your budget never involves the wall at all — the app just opens.
4. **The circle decides the hard stuff too.** Raising a daily limit, removing an app, softening a
   lock, turning off strict mode, or ending your Pact are all requests your circle approves.
   Tightening a limit is always free and instant.

**If you're a trusted person:**

Install the same app, pick "I'm a trusted person", then scan their QR or enter the invite code
they sent you. Their requests land on your home screen — Approve, Not now, or grant a custom
amount with a note. You can also just message them. One device can hold the trust of several
people.

## Streaks, screen time & challenges — the social layer

This is what turns a willpower app into something you *want* to open:

- **Your streak** counts every day you stayed inside all your limits. Blow a budget and it resets
  — so the number is honestly earned.
- **Share your screen time.** Give any friend "can see my stats" and today's minutes and your
  streak flow to them, end-to-end encrypted. No feed, no servers — just the people you chose.
- **Challenges.** Start a *No-Scroll Week* or a *7-Day Streak*, invite friends, and everyone
  races to keep the longest streak within their limits. A live leaderboard ranks the group by
  streak, with a 🔥 for each person and a flag when someone breaks.
- **A shareable streak card.** One tap renders a clean, private card — your streak, your screen
  time, your challenge rank — straight to the Android share sheet for a story or a group chat.
  Nothing personal leaves unless you send it.

## The world — a tiny handcrafted village that grows as you do

Blocking is the strict edge; the **world** is the heart. It's a **low-poly vector diorama** — a
small, dense, handcrafted-looking village drawn entirely in Compose as clean flat-shaded geometry
(no assets, no 3D engine, no pixels) — living **right inside the card** on the World tab, like a
terrarium on your desk. There's **no separate screen and nothing to navigate**: one fixed
isometric camera holds the whole island in frame and **pulls back on its own as you level up**
(a tiny clearing at Level 1 → a village → a castle on the hill), so the footprint stays compact
while the *density* grows. **Tap anything** and a small glass card tells its story — a building's
stage, a tree's age, or a villager's name, what they're doing, their mood, and what they did for
the world today. Tap the pond and fish jump; tap the sky and the birds scatter.

It's alive and lit by your **real time of day**: warm dawn, bright noon, a gold dusk, then a blue
night with twinkling stars, glowing windows, flickering lanterns and **fireflies**. The land is
organic, not a grid — soft grass with darker patches, flower clusters, a curved stone-and-dirt path
network radiating from a central fountain plaza, a river, a duck pond with ripple rings, rocks and
logs, and a **campfire** flickering by the plaza. Everything breathes: trees sway, crops wave, water
ripples, clouds drift, chimneys smoke, a windmill turns, birds cross the sky, butterflies wander the
flowers, villagers stroll the plaza (a couple, a darting child, an elder, a fisher at the pond, a
builder at work, a pet), and **weather** rolls through (clear, cloudy, rain, snow) with **seasons**
tinting the foliage and dropping autumn leaves or winter snow. It thrives when you look after
yourself and gently fades when you don't. **No guilt, no blocking** — just care:

- **Richness, not size.** As your overall level climbs, the world grows *denser* rather than larger:
  a cottage, then a turning **windmill**, then a **castle** on the back hill, **boats** on the pond,
  and eventually **festival bunting** strung between the rooftops — all inside the same compact
  footprint, with the camera easing back to keep it framed.

- **Every habit builds a specific building with its own personality.** Habits are tagged by
  category, and each grows its own building in the village — reading raises an ivy-clad **Library**
  with a lantern, cooking a **Bakery** with a smoking chimney and a bread sign, exercise a **Gym**
  with a banner and weights, coding a **Workshop** with a log pile, meditation a **Temple** with a
  hedge garden, sleep a **Moon** shrine that glows at night, and gardening a fenced **Farm** with
  crop rows and a scarecrow; walking grows the surrounding **Forest**. Buildings never pop into
  existence: they progress through **foundation → framing → walls → finished** (a builder villager
  hammers away until then). "Your World" also lists each structure and its level — progress is
  always visible, no rewards menu.
- **Send villagers on expeditions.** Once you've checked off a habit, dispatch a villager on a
  Meadow walk, Forest trek, or Mountain climb. They come back — after real time — with a **one-off
  cosmetic** for your world: a topiary, a lantern, a wishing well, a stone statue, a fountain, a
  banner. Purely decorative, so no two worlds look alike; the world *is* the reward.
- **The world advances through eras** purely by building, never by spending: Clearing → Garden →
  Farm → Village → Town → City → Nature Reserve → Floating Isles → Fantasy Kingdom.
- **Neglect wilts it.** Skip your days and health drops, colours fade toward a wan brown, and a
  plot occasionally withers — recoverable the moment you come back.
- It's a small, deterministic idle sim advanced one day at a time, entirely local. The circle is
  now an **optional** accountability mode layered on top, not the core. Fuller villager schedules,
  gradual villager-built construction, ambient audio, and a shared world are the roadmap.

## The trust model — public-key cryptography, zero setup

There is **no shared secret and no TOTP**. Instead, every device generates an **Ed25519**
signing key and an **X25519** encryption key on first launch, wrapped by the hardware-backed
Android Keystore. Public keys are exchanged once — by QR in person, or by a short code sealed with
itself and passed through the relay when apart. From then on:

- Every request, approval, and message is **signed** by its author and **sealed** (encrypted)
  to the recipient's public key. The transport only ever carries opaque ciphertext.
- Incoming messages are rejected unless they decrypt, carry a valid signature from a **pinned**
  key you actually trust, haven't expired, and use a **nonce** never seen before.

You never see a key, a code, or a protocol name. You see people, messages, and requests.

### Approval rules

Configurable per circle: **any one** person can approve, a **majority** must, or **everyone**
must. The rule engine is a pure, unit-tested function — approvals are deterministic.

### Permissions

Each trusted person is scoped: approve requests, view your stats, or **chat only**. You can
change this, or remove someone, any time.

## Security properties

| Threat | Defense |
|---|---|
| Forged approval | Every payload is Ed25519-signed; a signature from anyone but a pinned key is rejected |
| Replay / duplicate approval | Per-sender nonce ledger + one-decision-per-approver + short request expiry |
| Tampering | AES-256-GCM authenticated encryption; any bit-flip fails to open |
| Eavesdropping | Sealed to the recipient's X25519 key; the relay sees only ciphertext on a random inbox |
| Key substitution | Keys are pinned at pairing; only the initial pair-accept may come from an unpinned key |
| Reading keys at rest | Private keys wrapped by the Android Keystore; chat history encrypted at rest |
| Chaining self-unlocks | Yellow tier has a 30-minute cooldown |
| Quietly disabling the shield | Strict mode also locks system Settings and the package installer |
| Losing a trusted person's device | Revoke them — their key is unpinned and everything from it is rejected |

## Offline & delivery

Messages queue locally and drain with exponential backoff, so nothing is lost when you're
offline. Delivery is **instant while either app is open** (a fast in-app sync loop) and syncs
on a ~15-minute schedule in the background via WorkManager. Honest limitation: with no
app-owned servers there is no push, so background delivery isn't instantaneous.

## Transport is pluggable

Business logic talks only to a `Transport` interface, so the carrier can be swapped —
public relay → Nostr → custom — **without touching the crypto, the approval engine, or the UI**.
The default is an open public relay (no accounts) that carries only end-to-end-encrypted
payloads on unguessable inbox topics.

## The shield — on-device enforcement

Blocking runs entirely on the device, offline. An `AccessibilityService` watches the foreground
app and, when a locked app is over its daily budget, covers it with a full-screen
`TYPE_ACCESSIBILITY_OVERLAY` window drawn by the service itself — not a launched activity, which
modern Android silently blocks as a "background activity start."

Detection is deliberately **self-healing**, and split from metering:

- **Detection** — every `WINDOW_STATE_CHANGED` **and** `WINDOWS_CHANGED` event funnels through one
  idempotent `onForeground()` that derives the true foreground app (from `getRootInActiveWindow()`
  when an event's package is stale) and makes the wall's state match it. This fixes the classic
  failure where leaving a blocked app and returning left it open — some launchers never re-fire
  `WINDOW_STATE_CHANGED` on resume, but the window-layer change still does, and the reconcile
  re-asserts the wall.
- **Metering** — a self-rescheduling tick books each app's foreground time and raises the wall the
  instant the daily budget is spent, independent of whether any event fired.

**Staying alive** is the other half of reliability. A quiet foreground service (`ShieldService`,
`START_STICKY`) keeps Pact's process resident so aggressive OEM battery managers can't kill the
watcher in the background — the most common reason blockers "just stop working." When the user
grants **Usage Access**, that service also polls `UsageStatsManager` once a second as a *second*
foreground-app detector, catching the rare missed event. Both are optional; the app degrades
gracefully without them.

> **Reliability ceiling.** On stock Android no tap-to-install app can be 100 % — the user can
> disable the accessibility service, and the OS can still reclaim the process. The only
> framework-enforced, unbypassable path is `DevicePolicyManager.setPackagesSuspended()`, which
> requires Pact to be a **Device Owner** (provisioned via `adb` on a device with no accounts) or a
> work-profile owner. That's a planned opt-in "maximum strength" mode for power users; the default
> build above targets ~99 % without any special setup.

## Also included

A **deep-black interface with a violet hero accent** (a premium OLED palette — black + purple,
green for good/online, coral for stop) and a **bottom nav with a raised Farm button** · a first-launch **language drop-in** (10 languages, applied per-app, no system
trip) · **emoji squad avatars** exchanged at pairing · a home **co-op tower** that grows with the
squad's combined streak · per-app daily limits · **challenges & a live streak leaderboard** ·
end-to-end-encrypted screen-time sharing · **accurate device-wide screen time** (via
`UsageStatsManager` when Usage Access is granted, falling back to an on-device estimate) ·
**villager expeditions** that return cosmetic decorations · **live squad presence** (a coarse, short-lived
in-the-zone / off-track / idle status, no app names) · **unlock requests as interactive
"Lockbox" widgets inside the chat** (grant/deny after the roast, flip-to-green) · confetti,
haptics, and a pulsing co-op tower · a one-tap shareable streak card · a **weekly Receipt** (a
Sunday "Wrapped" of your week, rendered as a shareable receipt, with a Sunday nudge) · **remote
pairing by short code** (or QR in person) · a keep-alive foreground service + optional Usage-Access
redundancy · **focus sessions** (lock everything for
a set stretch, no top-ups) · urge tracking · post-break reflection · an Insights screen (streak,
walls per day, most tempting apps, craving hours, triggers) · a home-screen widget · quiet,
actionable notifications (requests, approvals, messages, challenge invites) · encrypted
passphrase backup + stats CSV · animated, Compose-drawn illustrations · 10 languages.

## Install

Copy `release/Pact-v5.20.apk` to **both** phones — yours and each trusted person's — allow
"install from unknown sources", and follow the in-app setup. Requires Android 8.0+ (API 26).
No Google services needed.

## Build from source

```bash
cd pact
# Point local.properties at your Android SDK, then:
./gradlew assembleRelease   # → app/build/outputs/apk/release/app-release.apk
./gradlew test              # crypto + approval-policy unit tests
```

The release build is signed with the checked-in keystore (`keystore/pact-release.keystore`,
passwords in `app/build.gradle.kts`) so anyone can produce an installable build. **This keystore
is intentionally public — generate your own to distribute.**

## Architecture

```
app/src/main/java/com/pact/app/
├── MainActivity.kt / PactApp.kt   # navigation, live-sync lifecycle
├── core/
│   ├── CryptoBox.kt      # Ed25519 sign/verify + X25519 sealed boxes (pure JVM, tested)
│   ├── Wire.kt           # signed+sealed payload protocol, inbound verification
│   ├── Transport.kt      # swappable transport interface + relay impl + offline outbox
│   ├── TrustNetwork.kt   # contacts, chat, request/approval engine, policy (tested)
│   ├── SyncWorker.kt     # WorkManager background sync
│   ├── PactState.kt      # blocking state, tiers, stats
│   ├── Notifications.kt / Backup.kt / Apps.kt / Qr.kt / Vault.kt
├── service/              # accessibility shield + service-drawn lock overlay
├── PactWidget.kt         # home-screen widget
└── ui/                   # Compose: onboarding, home, circle, chat, requests,
                          #   lock wall, insights, settings, scanner
```

Kotlin · Jetpack Compose · Material 3 · BouncyCastle · OkHttp · WorkManager · StateFlow ·
10 languages · min SDK 26, target SDK 35.
