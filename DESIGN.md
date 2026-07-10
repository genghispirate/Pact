# PACT — DESIGN BIBLE v6 ("Haven")

**This document is law.** It exists so that any model or developer can continue building Pact
without asking questions. Every decision is already made: colors, wording, layout, animation
timings, the world engine's geometry, the psychology, the milestones and their order. If this
document and the current code disagree, **this document wins**. If something is genuinely not
covered, choose the option that is *calmer, quieter, and more like checking on a small living
place* — never the option that adds pressure, guilt, or visual noise.

---

## 0. Rules for whoever implements this

1. Work milestone by milestone (§11), in order. One milestone = one commit = one version bump.
2. Every commit must compile (`/opt/gradle/bin/gradle --no-daemon :app:compileReleaseKotlin`),
   pass tests (`:app:testReleaseUnitTest`), and build (`:app:assembleRelease`) before committing.
3. Never invent a color, string, spacing, or duration. Use only the tokens in §3 and copy in §5–§6.
4. All user-facing text lives in `strings.xml`. Never hardcode UI strings in Kotlin.
5. Never touch `core/CryptoBox.kt`, `core/Wire.kt`, `core/Transport.kt`, `core/TrustNetwork.kt`,
   or anything in `service/` during visual work. They are finished and load-bearing.
6. No new library dependencies. Everything here is achievable with Compose + what's already in
   `build.gradle.kts`.
7. Follow the release procedure in §12 exactly (APK naming, README, syncing the two repos).
8. Commit messages end with the `Co-Authored-By: Claude <noreply@anthropic.com>` and
   `Claude-Session:` trailers, and never mention model names inside code or commits.

---

## 1. Diagnosis — what is wrong today (audit, July 2026, v5.17)

**Wording is three products at once.** Onboarding says "sponsor", "unlock codes", "hand over the
key" (v1: strict approval app). Home says "System armed", "squad", "doomscroll", "Danger zone"
(v3: aggressive social challenge). The World tab says "no guilt, just care" (v5: living world).
A user meets three personalities in two minutes. This is the single biggest reason it feels
disorganized.

**IA is overgrown.** Five tabs (Home, Stats, Farm-FAB, Circle, Settings) plus separate screens for
Chat, Challenges, Receipts, Focus, AddApps. Stats vs Home overlap; Settings contains identity
("Your sponsor") that isn't settings; the world — supposedly the heart — is one tab among five and
Home barely references it.

**Visual chrome is heavy and inconsistent.** 1.5dp borders on everything, four accent colors used
interchangeably (Violet/Periwinkle/Mint/Amber on the same screen), emoji inside product copy
("📱➡️🗑️", "System armed 🛡"), ALL-CAPS headers in receipts, font weights up to Black 38sp.
Premium apps whisper; this shouts.

**The world renderer has structural flaws.** The river is drawn as a stroke that runs off the
island silhouette into the void. Layout is polar-random, so composition is left-heavy with an
empty right half (visible in the user's screenshot). Night grading crushes everything into
grey-brown murk. Buildings carry floating emoji signs (cheap). Villagers are ~0.3 tile tall —
invisible. Clouds spawn over the card corner. Grass patches are 26 random blotches instead of a
handful of soft areas.

**Psychology is accidental.** Streaks reset hard (rage-quit risk). Nothing tells you what changed
since your last visit (the core "living world" hook is unexploited). Habit check-ins live one tab
away from where you land. Screen time is displayed but not connected to the world you're growing.

Everything below fixes these.

---

## 2. Product thesis & psychology

> **REPOSITIONING (product-lead review, July 2026 — supersedes any "app blocker" framing).**
> Pact is **a habit-building game where reducing screen time is how your world thrives.** The
> world is the product and the reason people open the app; blocking is the *mechanism*, not the
> identity. Build in this priority order, top to bottom: **(1) the world → (2) the habit loop →
> (3) the social layer → (4) the progression system → (5) the blocking tools.** If a decision
> serves a lower item at the expense of a higher one, the higher one wins.

**One sentence (store tagline):** *Grow a living world by spending less time on the apps that
drain you — and build it alongside your friends.*

**Two locked decisions from that review:**
- **Backend + accounts (Decision 1b).** Pact now has an app-run backend. Users sign in with
  **Google or email**, claim a **unique username**, and find friends by username. The server
  powers only what genuinely needs it — accounts, the social graph, island visits, gifts,
  neighborhood rankings, trading, co-op events. Everything else stays offline-first (§15).
- **Discipline economy (Decision 2a).** Resources exist and give depth, but are **earned only
  from focus / habits / reduced screen time and spent only on your world. Never purchasable, no
  loot boxes, no premium currency.** "Earned by living well, not by paying" is a core promise
  and a competitive moat (§14).

This supersedes the founding "no servers, no accounts, no Google services" constraint **for the
social/game layer only**. The **blocking core stays fully offline and serverless** — the shield,
per-app budgets, the wall, and the trusted-person unlock (Ed25519/relay) never require the server
or an account, so the app is completely usable signed-out. Sign-in is required only to use social
features. Update all "no accounts ever" copy accordingly (§15.7).

The user opens the app for 20–30 seconds. In that window they must: (1) feel "I wonder what
changed" satisfied, (2) do at most one meaningful action, (3) leave feeling slightly better.
Every design choice serves that loop.

### The mechanics (all mandatory)

| # | Mechanic | Implementation |
|---|---|---|
| P1 | **News on open** | Every time Today appears, compute a diff since last open (points gained, structure stage-ups, expedition returns, health delta, new decor, weather). Show ONE line under the greeting, e.g. "While you were away: the library got its roof." Priority order: expedition returned > structure stage-up > rare animal > health recovered > weather line. Store `last_seen_*` values in prefs. One line, never a list. |
| P2 | **Endowed progress** | New installs seed the world at 40 points (Garden Lv1 in progress), 2 villagers, 3 starter habits pre-added. A 0% start kills retention; a 15% start doubles it. |
| P3 | **Streak insurance** | One "quiet day" per calendar week: the first missed day does NOT reset `goodStreak`; it freezes it. Copy: "A quiet day. Your streak is safe." Second miss in a week resets, copy: "Fresh start — your world is still here." Never the words "lost", "failed", "broke", "died". |
| P4 | **Loss = rest, never death** | Health decay desaturates colors and slows villagers (§8). Nothing is ever destroyed, no withered corpses, no graves, no dead trees. Minimum health floor 25%. First habit check after a 2+ day gap awards double points with copy "Welcome back. Your world missed you." |
| P5 | **Milestone animals** | Streak 7 → a fox appears at dawn/dusk. Streak 30 → a deer at the forest edge at night. Streak 100 → an owl on the tallest tree. These are announced via P1 news and permanent once earned (do not remove on streak reset). |
| P6 | **Two-tap rule** | Any daily action (check habit, start focus, start expedition) is reachable within 2 taps of app open. Habit checklist lives ON Today. |
| P7 | **No downward comparison** | Today never shows red, down-arrows, or "over budget" framing. Comparisons (vs last week) exist only on Progress, phrased neutrally: "2h 10m less than last week" in Mint or "1h 20m more than last week" in TextMid (never Coral). |
| P8 | **One notification max/day** | 20:30 local, only if ≥1 habit unchecked AND notifications granted. Copy: title "Your world", body "The lanterns are on. One small habit before bed?" Soft-ask permission after the user's first habit check (never in onboarding). |
| P9 | **Celebration budget** | Confetti/haptic celebration only on: first habit ever, streak 7/30/100, structure finished, expedition collected. Everything else gets a quiet 250ms ease. Scarcity keeps it premium. |

---

## 3. Design tokens (replace Theme.kt with exactly this)

### 3.1 Color

```kotlin
// backgrounds
val Ink          = Color(0xFF0A0A10)   // app background (lifted from pure black)
val Surface1     = Color(0xFF15151D)   // cards
val Surface2     = Color(0xFF1D1D27)   // raised elements, chips, input fields
val Surface3     = Color(0xFF262633)   // pressed states
val Hairline     = Color(0x0FFFFFFF)   // 6% white — ALL borders, 1dp always (never 1.5dp)

// brand & semantic accents
val Violet       = Color(0xFF7C5CFF)   // interactive, brand, focus
val VioletDeep   = Color(0xFF5B3FE0)   // gradient end only
val VioletGhost  = Color(0x247C5CFF)   // 14% violet — selected-state tints
val Mint         = Color(0xFF4ADE80)   // success, positive delta, streaks
val Amber        = Color(0xFFFFC24B)   // caution (70–105% of budget)
val Coral        = Color(0xFFFF6B85)   // destructive buttons ONLY, never data
val Sky          = Color(0xFF6FC3FF)   // screen-time data accent

// text
val TextHi       = Color(0xFFF5F5FA)
val TextMid      = Color(0xFFA7A7BC)
val TextLow      = Color(0xFF62627A)

val PactGradient = Brush.linearGradient(listOf(Color(0xFF8B5CFF), Color(0xFF6D4AFF)))
val OnAccent     = Color(0xFFFFFFFF)
```

Keep old names compiling by aliasing (`val CardBorder = Hairline`, `val Rose = Coral`,
`val Periwinkle = Violet`, `val TextPrimary = TextHi`, `val TextSecondary = TextMid`,
`val TextTertiary = TextLow`) so M0 doesn't require touching every file; then milestones migrate
screens off the aliases.

**Usage law:**
- One accent color per card. A card about screen time uses Sky; about streaks, Mint; interactive
  cards, Violet. Never two accents in one card except number+icon pairs.
- Mint = good things only. Amber = caution only. Coral appears nowhere except "Stop"/"Remove"
  buttons and the shield-off warning.
- Emoji never appear in labels, buttons, or titles. They are allowed only as *content*: habit
  icons, avatar faces, mood in the villager sheet.
- Gradients: `PactGradient` on primary CTA buttons and the world FAB only. Nowhere else.

### 3.2 Typography (system font; replace Typography block)

```kotlin
displaySmall  = 34.sp / 40.sp, Bold, -0.8.sp     // big numbers (Progress)
headlineSmall = 24.sp / 30.sp, Bold, -0.4.sp     // screen titles
titleMedium   = 18.sp / 24.sp, SemiBold, -0.2.sp // card titles
titleSmall    = 15.sp / 20.sp, SemiBold          // row titles, buttons
bodyLarge     = 16.sp / 24.sp, Normal            // reading text
bodyMedium    = 14.sp / 21.sp, Normal            // secondary text
labelLarge    = 15.sp / 20.sp, SemiBold, 0.1.sp  // button labels
labelMedium   = 11.sp / 14.sp, Medium, 0.8.sp    // ALL-CAPS section labels (uppercase in code)
```

Rules: sentence case everywhere (titles, buttons, labels — "Add a habit", never "Add A Habit").
ALL-CAPS only via `labelMedium` section headers. No font weight above Bold. Numbers use tabular
feel by keeping them in `displaySmall`/`titleMedium` alone.

### 3.3 Shape, spacing, elevation

- Radii: chips/pills = full; buttons 16dp; cards 20dp; the world card 24dp; bottom sheets 28dp top.
- Spacing grid of 4: screen horizontal padding **20dp**; card inner padding **16dp**; card-to-card
  gap **12dp**; section gap **24dp** (label sits 8dp above content); list row min-height **56dp**;
  touch targets ≥ **48dp**.
- No shadows anywhere (OLED black kills them). Depth = Surface1→2→3 steps + Hairline borders.
  Glass (white 12% fill, white 18% border, blur-free) is allowed only floating over the world.

### 3.4 Motion

| Thing | Spec |
|---|---|
| Screen/tab content enter | fade + 8dp rise, 250ms, easing `cubic-bezier(0.2, 0, 0, 1)` |
| Bottom sheet | 350ms same easing |
| Habit check | checkbox fills with spring(dampingRatio 0.55, stiffness Medium); row tint 250ms |
| Number changes | animate value over 400ms (screen time, points) |
| World camera pull-back | 1600ms, easing above |
| Progress bars | animate to value 700ms on first composition |
| Ban | infinite pulsing/breathing on UI chrome. Motion lives in the world, not the chrome. |

### 3.5 Haptics

`HapticFeedbackType`: habit check = `Confirm`; streak milestone & expedition collect =
double `Confirm` 120ms apart; destructive confirm = `LongPress`. Nothing else vibrates. The
block wall NEVER vibrates (no punishment feedback).

---

## 4. Information architecture (final)

### 4.1 Bottom bar — FOUR slots, the World IS home (product-lead §7)

The World is not a tab you visit; it is the home screen you land on. Fewer pages, less
navigation.

```
[ World ]   [ Friends ]   [ Stats ]   [ Profile ]
  home        Group        Insights     Person
```

- **World (home, default landing)** — the terrarium full-bleed, edge-to-edge. Floating glass
  overlays on top of it (never separate pages): the **news line** (P1), a compact **today strip**
  (habit checklist + focus + screen-time ring in a bottom sheet pulled up by a small handle), the
  stage/level/resource chips, and the tap-to-inspect sheets. Opening the app = watching your
  world, with one pull-up for the day's actions. This satisfies the 20–30s loop with zero tab
  hops.
- **Friends** — your friends by username, their island snapshots (visit), gifts, challenges,
  neighborhood ranking, co-op events, chat. (Merges the old Circle + Chat + Challenges and adds
  the §15 social features.)
- **Stats** — streak calendar, 14-day screen-time chart, postcards, insights, and **personal
  history** ("hours saved → tangible things", §14.6). (Merges Stats + Receipts + Insights.)
- **Profile** — avatar + **username** + earned **titles** (§14.5), account/sign-in (§15),
  app-budget management, shield & permission status, screen-time goal, privacy, language, about.

`Screen` enum final: `World, Friends, Stats, Profile, Focus, AddApps, Chat, SignIn` — Focus,
AddApps, Chat, SignIn are pushed screens (not tabs). Back from a pushed screen returns to its
origin; system back on a tab returns to World; back on World exits. The old center Grass-FAB is
gone (the whole home is the world). The habit checklist that used to be a "Today" tab is the
pull-up sheet on World.

### 4.2 What dies
- The separate Stats, Receipts, Challenges, Circle, Settings top-level screens (content moves).
- The co-op "tower" as a concept: `SquadTowerCard` is replaced by `FriendsStrip` (avatars +
  presence dots + streaks, one row, taps into Friends).
- The "sponsor / guardian / unlock codes" onboarding path as the default (§6.1).
- Floating emoji signs in the world renderer.
- "Enter world" (already removed) and every string listed as banned in §5.

---

## 5. Voice & wording system

### 5.1 Voice
Calm, warm, second person, present tense. Short sentences. No exclamation marks except the five
P9 celebration moments (max one). No emoji in chrome. No jargon: the words *sync, relay,
encrypted, service, accessibility, API, permission* never appear on a primary screen (they may
appear in You → Privacy in plain phrasing). Sentence case everywhere. Numerals ("3 habits", not
"three").

**One metaphor everywhere:** your **world** and the **friends** who visit it. The words *squad*,
*sponsor*, *guardian*, *circle*, *tower*, *armed*, *doomscroll*, *danger zone*, *lockdown* are
**banned**. "Trusted person" → "friend". The strict feature-set ("hold the key") still exists but
is *opt-in* inside Friends, phrased as "Ask a friend before unlocking".

### 5.2 String replacement table (apply verbatim in strings.xml)

| key | new value |
|---|---|
| intro_page1_title | Meet your world |
| intro_page1_body | A tiny place that grows every time you look after yourself. Watch it fill with gardens, villagers, and light. |
| intro_page2_title | Habits build it |
| intro_page2_body | Reading raises a library. Sleep grows a moon garden. Less scrolling, more flowers. Small days, visible change. |
| intro_page3_title | Friends can visit |
| intro_page3_body | Share your world, keep streaks together, and cheer each other on. Private by design — no accounts, nothing leaves your phones unencrypted. |
| intro_welcome_title | Pact |
| intro_welcome_body | A tiny world that grows when you do. |
| intro_welcome_cta | Get started |
| role_user_title | Start my world |
| role_user_body | Grow a world from my habits, and put my most distracting apps on a budget. |
| role_sponsor_title | I'm here for a friend |
| role_sponsor_body | Someone asked me to help them stay accountable. |
| hero_active_title | Shield on |
| hero_active_sub | Your app budgets are being looked after |
| hero_down_title | Shield off |
| hero_down_sub | App budgets are paused. Turn the shield on to bring them back. |
| hero_enable | Turn the shield on |
| greet_sub | *(delete — replaced by the news line, §2 P1)* |
| farm_title | Your world |
| farm_habits_label | Today's habits |
| farm_habits_hint | Small check-ins, visible change. Skip a day and your world just rests. |
| stat_screen_time_label | screen time |
| stat_limited_label | apps on budget |
| stat_streak_label | day streak |
| receipt_title | A postcard from your world |
| receipt_share_button | Share this week |
| receipt_share_hint | A little proof you showed up. |
| receipt_caption | My week in Pact — growing a world by living better. |
| receipt_denied | *(remove from postcard)* |
| receipt_tower | *(remove from postcard)* |
| settings_sponsor_section | Your friends |
| settings_paired | Connected — they can cheer you on |
| settings_danger | Careful |
| challenge_start_body | Invite friends to keep their streaks side by side. Longest streak leads. |
| challenge_need_friends | Add a friend first. |
| pair_share_message | Join my world on Pact 🌱 Install it, choose "I'm here for a friend", and enter this code: %1$s |
| seal_title | Your world is ready |
| seal_body | It grows while you live well. Come back tomorrow and see what changed. |
| seal_begin | See my world |
| exp_title | Send a villager exploring |
| exp_need_habit | Check one habit today and a villager will be ready to explore. |
| focus_deep_work | Deep focus |
| block_wall (all variants) | Keep tone: "That's your %1$s for today." / "It'll be back tomorrow." / options phrased "Ask %1$s" and "Take a breath (30s)". No shame lines. |

New strings to add: `news_expedition` ("While you were away: %1$s came home with something."),
`news_stage` ("While you were away: the %1$s grew."), `news_animal_fox` ("A fox has started
visiting at dusk."), `news_recovered` ("Your world perked up again."), `news_quiet` ("Your world
rested while you were gone."), `streak_safe` ("A quiet day. Your streak is safe."),
`welcome_back` ("Welcome back. Your world missed you."), `goal_label` ("Screen time goal"),
`tab_today` Today, `tab_friends` Friends, `tab_world` World, `tab_progress` Progress, `tab_you` You.

Locale folders (`values-de` etc.) are stale — **delete all 9 locale folders** in M1 (they fall
back anyway and currently show the old product's voice). Re-translate after v6 stabilizes, never
before.

---

## 6. Screen-by-screen specification

### 6.1 Onboarding (rebuild in M5)
Four pages, horizontal pager, skip always top-right (`TextMid`), page dots Violet.
1. **Meet your world** — the actual `WorldCanvas` in PEEK quality as hero (level-3 demo snapshot,
   fixed seed), title + body from §5.
2. **Habits build it** — three example habit rows animating a check every 1.8s, tiny structure
   chips filling.
3. **Friends can visit** — two avatar bubbles + presence dots + a challenge chip mock.
4. **Name** — "What should the villagers call you?" single field, continue.
Then straight to Today. No permissions, no app picking, no role selection here. A single quiet
link under page 3: "I'm here for a friend" → the old sponsor pairing flow (unchanged internals).
App budgets and the shield are offered contextually: first time the user opens You → App budgets,
or taps the screen-time card's "Set budgets" action.

### 6.2 Today (rebuild of HomeScreen in M3)
Top to bottom:
1. **Header** row: `headlineSmall` greeting ("Good morning, Sam" — time-based, existing logic),
   avatar chip right (28dp, opens You). Below, the **news line** (`bodyMedium`, TextMid): §2 P1.
2. **World peek** — 112dp card, radius 20, `WorldCanvas(quality = PEEK)` full-bleed, right-aligned
   glass chip "Lv 4 · Garden". Tap → World tab. No border on the image itself (Hairline only).
3. **Today's habits** — section label TODAY'S HABITS. Up to 5 rows: emoji, name, check circle.
   Checking animates per §3.4 + fills row `VioletGhost`→ fades to `Mint` 12% tint. Under the last:
   "+ Add" text button (Violet). Checking a habit shows nothing else — the reward is the world.
4. **Focus card** — one row: "Deep focus" `titleSmall`, sub "25 min · phone quiet" `bodyMedium`
   TextMid, right chevron. Tap → Focus screen. When a session is live this card becomes a live
   countdown with a thin Violet progress line.
5. **Screen time** — card, Sky accent: left = ring (56dp, stroke 6dp, Sky→Amber per §9 thresholds)
   with hours centered `titleMedium`; right = "2h 10m of your 3h goal" + first-run states:
   no Usage Access → "Turn on accurate tracking" ghost button (opens the §9 sheet);
   no goal → "Set a goal" ghost button.
6. **App budgets** — collapsed summary card: "3 apps on budget · Instagram 12m left". Tap → You →
   App budgets. (Full list no longer lives on Today.)
7. Bottom padding 96dp for the bar.

`FriendsStrip` appears between 4 and 5 ONLY if ≥1 friend exists: one row of avatar bubbles with
presence dots + "🔥 n" streaks, tap → Friends.

### 6.3 World tab (rework of FarmScreen in M4)
1. World card full-bleed within 12dp side padding, **420dp** tall, radius 24 — the terrarium
   (§7). Floating top-left glass chip: stage + level ("Garden · Lv 4"). Floating top-right glass
   chip: 🌱 points count. Both `labelMedium` on white.
2. News line (same P1 component, world-flavored).
3. **Your world** — structure chips grid (existing `StructureChip`, restyled: Surface1, Hairline,
   selected accent Violet, progress bar 4dp).
4. **Expeditions** — existing card, restyled to §3; states per current logic.
5. **Habits** — NOT here anymore (moved to Today). Instead a single ghost row: "Habits live on
   Today →" for the first week (remove after v6.2).
Stats trio (LV/streak/health) dies — level is in the chip, streak lives on Progress, health is
implicit in the world's look.

### 6.4 Friends (merge in M6)
Sections: **Friends** (rows: avatar, name, presence dot + word — "in the zone"/"around"/"resting",
streak 🔥n, chevron → chat), **Challenges** (existing cards restyled), **Add a friend** (QR +
invite code, existing flows, new §5 copy). The strict "ask before unlocking" toggle lives in a
friend's detail sheet: "Holds your key — you'll ask them for extra time." (existing TrustNetwork
permission, relabeled).

### 6.5 Progress (merge in M6)
1. **This week** — 7 dots (habit-complete days Mint, quiet days Surface3, today outlined Violet).
2. **Streak** — `displaySmall` number + "day streak", best streak `bodyMedium` TextMid below.
3. **Screen time** — 14-day bar chart from `UsageHistory` (§9), bars Sky 60%, today Violet;
   goal line 1dp Hairline; delta line phrased per P7.
4. **Postcards** — horizontal cards of past weekly postcards (existing receipt render, new copy).
5. **Insights** — existing craving-hours / most-tempting rows, restyled, only if shield data exists.

### 6.6 You (merge in M6)
Rows grouped by section labels: PROFILE (name, avatar emoji picker) · WORLD (screen-time goal
chips 2/3/4/5h; notification toggle) · APP BUDGETS (list + add, the old AddApps/limits UI) ·
SHIELD (status pill + fix action; strict mode toggle with friend-approval note) · PRIVACY ("Your
data never leaves your phone unencrypted. No accounts exist." + backup/export rows) · LANGUAGE ·
ABOUT (version, licenses).

### 6.7 Focus screen
Keep current design; restyle ring to Violet on Surface1 (remove any second accent), title
"Deep focus", chips 15/25/45/60, sub-line "Locked apps stay quiet". On finish (≥25m): return to
Today with news line "That focus built the workshop a little." and workshop +5 (§9).

### 6.8 Block wall (overlay)
Background Ink 96%, app icon, `headlineSmall` "That's your Instagram for today", `bodyMedium`
TextMid "It'll be back tomorrow." Buttons: primary "Ask Maya" (only if friend holds key),
secondary ghost "Take a breath — 30s", tertiary text "Close". No coral, no shake, no vibration.

---

## 7. World engine v3 — the terrarium

### 7.1 Files (refactor, M2)
```
ui/world/WorldModel.kt    // Scene: authored anchors, level gating, state→SceneSpec (pure)
ui/world/WorldPalette.kt  // time-of-day buckets, weather, season colors, grade()
ui/world/WorldRenderer.kt // all DrawScope.draw* functions (stateless)
ui/world/WorldLife.kt     // villager/animal schedules & routes (pure functions of hour + seed)
ui/world/WorldCanvas.kt   // @Composable: frame clock, static-layer cache, taps, WorldTap sheet
```
`WorldCanvas(snap, modifier, quality: Quality, onTap: (WorldTap)->Unit)` where
`enum Quality { PEEK, FULL }` — PEEK disables particles and life beyond villagers, halves star
count, and is used on Today and onboarding.

### 7.2 Camera (fixed, never user-controlled)
Isometric 2:1 (`nx=(x−y)/2`, `ny=(x+y)/4 − z·0.42`). View radius by level tier:

| level | R |
|---|---|
| 1–2 | 5.2 |
| 3–5 | 5.7 |
| 6–9 | 6.2 |
| 10–14 | 6.8 |
| 15–24 | 7.4 |
| 25–39 | 8.0 |
| 40+ | 8.6 |

`u = min(w·0.96/(2R), h·0.94/(R·0.5+1.9))`, world origin at `(w/2, h·0.53)`. Camera change
animates 1600ms. No pan, no pinch, no double-tap zoom (taps only select).

### 7.3 The island (fixes the silhouette + river)
- **Coast**: a FIXED 14-point blob. Radii multipliers (clockwise from east):
  `1.00, 0.96, 1.04, 0.98, 0.92, 1.02, 1.06, 0.97, 0.94, 1.03, 0.99, 0.95, 1.05, 1.01` — gentle
  (±8%), evaluated at `R_island = R − 0.4`. Catmull-Rom closed. Never regenerate per frame.
- **Skirt**: extrude down 0.34u. Two bands: upper `#6B4B2F`, lower `#54381F`, plus 6 authored rock
  clusters at fixed angles (15°, 70°, 140°, 200°, 265°, 320°).
- Contact shadow ellipse 20% black, then a **water ring**: a second blob +0.5u radius drawn UNDER
  the island in `#2C6FB8→#1E4E8C` vertical gradient = the island floats in a small sea. This
  frames the diorama and kills the "floating in void" look.
- **River**: clip to the island path (`clipPath`). Authored course: `(−2.2,−4.0) → (−1.6,−2.2) →
  (−0.6,−0.2) → (0.8,1.8) → (2.2,3.2) → (3.6,4.4)` ending exactly at the pond center. Width tapers
  0.5u→0.3u. Two stepping stones (0.1u circles, `#B9C0C8`) where it crosses the plaza path.
- **Pond**: authored at `(3.6, 4.4)`, rx 1.3u, ry 0.65u; shore ring sand `#D8C79A` 0.14u; two-tone
  water; 1 lily pad (0.12u, `#5BAE58`) at (+0.4,−0.1); 2 ducks; ripple ring every 2.6s.
- **Ground**: radial 3-stop noon grass `#7DCB58 → #58A844 → #47953A`. Exactly **9** darker patches
  (blobs, 6% black alpha), authored positions (see anchor table). A "mown" lighter ellipse under
  the plaza. Grass tufts: 40, seeded positions, 3-blade, sway ±0.05u @ 2.4s.

### 7.4 Authored anchor map (world units; NEVER randomize these)

| thing | pos | notes |
|---|---|---|
| plaza + fountain | (0, 0) | stone circle r 1.1u, mown ring r 1.5u |
| library | (−2.6, −1.4) | NW of plaza |
| gym | (2.4, −1.8) | NE |
| temple | (−3.0, 1.6) | W, on 0.2 rise |
| workshop | (2.8, 1.4) | E |
| bakery | (−1.2, 2.6) | S |
| school | (1.4, 2.9) | SE |
| farm plot | (−2.2, −3.2) | fenced field, NW |
| moon shrine | (0.6, −3.4) | N |
| cottage (L3) | (2.6, −3.0) | |
| windmill (L6) | (−4.4, 0.4) | on 0.3 rise, W |
| campfire | (1.2, 1.6) | SE of plaza |
| pond | (3.6, 4.4) | SE |
| castle (L14) | (−1.6, −5.2) | on the north hill, drawn behind |
| hills ×3 | (−4.0,−4.6) (−1.6,−5.6) (1.2,−5.2) | ridge across the north |
| forest arcs | 8 clumps NW/N/E at r 4.6–5.4, angles 100–170° & 330–30° | density = level-gated |
| boats (L10/22) | pond ±0.4 | |
| flower beds | (−1.8, 0.8), (1.8, −0.6), (0.4, 3.6), (−3.6, 3.0) | grow with garden level |

Paths: smooth curves plaza→each *built* structure, plaza→pond, plaza→campfire. Tan `#CBAE86`
0.26u over `#B79468` 0.18u. Paths appear WITH the building's foundation stage.

### 7.5 Scale law (in u — enforce everywhere)
villager 0.42 tall (head 0.16 — chibi 40%) · door 0.48 · house body 1.1w × 0.6h + roof 0.35 ·
tree 0.9–1.3 · windmill 1.7 · castle 2.3 · fountain 0.5. If it looks wrong, the door law decides:
**a villager must fit through every door**.

### 7.6 Time-of-day palette (6 buckets, lerp between bucket centers ±40min)

| bucket | sky top→bottom | sun/moon | grass mul | ambient | extras |
|---|---|---|---|---|---|
| night 22–5 | #101A3E→#26355F | moon #E8EDFC | ×0.80, hue→#3E6B4F | #33447E @ 34% | stars 46, fireflies, windows warm #FFD98A, lantern pools |
| dawn 5–8 | #F3A96B→#FFE0B0 | low sun #FFD9A0 | ×0.95 | #FFB877 @ 22% | long shadows (shadow ellipse ×1.6 east), mist band 8% white |
| morning 8–11 | #7FC6EA→#CFEFF5 | — | ×1.02 | 8% | |
| noon 11–16 | #7FCBEA→#D6F0F4 | high | ×1.06 | 6% | shortest shadows |
| golden 16–19 | #FF9E5E→#FFD79A | low | ×1.00 warm +10% | #FF9860 @ 26% | rim-light west sides +8% white |
| dusk 19–22 | #6A4A8C→#2E3A66 | — | ×0.88 | 30% | lanterns ignite one by one (stagger 400ms), windows on |

**Night is never murky**: brightness floor 0.78, and warm light sources (windows #FFD98A,
lanterns, campfire) get 30% larger glow at night so the scene reads as cozy, not dead.
`grade(light)`: end-of-frame overlay — night navy multiply 22%, golden warm overlay 10%,
vignette 16% (radial). Nothing else grades.

### 7.7 Buildings — silhouette identity, no emoji
Remove nativeCanvas emoji signs entirely. Identity = shape + roof + one prop:
library: tall, roof `#5C79C0`, ivy dots + door lantern · bakery: roof `#C97C4A`, chimney smoke
always, round bread sign disc `#E8C989` · gym: wide low, roof `#D95E54`, banner · workshop: roof
`#3E9B8E`, log pile + saw-horse · temple: roof `#8A6BEE`, hedge + incense wisp · school: roof
`#5BA24C`, small bell gable · moon shrine: dome `#6A6BD0`, glows at night · cottage: thatch
`#B98A4E` · construction stages stay (foundation→frame→walls→done) with the builder villager
hammering (arm swing 0.5s) and sawdust puffs.
Windows: `#9AD0EE` day → `#FFD98A` night (lerp by light.night), glow halo at night.

### 7.8 Life (WorldLife.kt) — schedule by real hour

| hours | villagers do |
|---|---|
| 6–9 | one waters the farm plot (watering-can arc + droplets), others walk plaza→their building |
| 9–12, 13–17 | each villager assigned to a built structure: stands at door, tiny work loop (bob/hammer/read) |
| 12–13 | pairs on plaza benches (2 sit dots), pet circles fountain |
| 15–18 | children (if level ≥4) chase loop on the mown ring |
| 17–20 | gather at campfire (up to 4 sit around it), stroll couples |
| 20–22 | lantern lighting walk (one villager visits each lantern, it ignites as they pass) |
| 22–6 | asleep; 1 night-owl walks slow loop; cat on bakery roof |

Animals: ducks 2 (pond, always) · butterflies 6 day (over flower beds) · fireflies 18 night ·
a bird lands on a random roof every ~40s for 6s then flies off · cat naps 13–16 by bakery ·
fox (P5) dawn+dusk forest edge · deer (P5) night forest edge · owl (P5) night on tallest tree.
Movement: waypoint routes, smoothstep, speed 0.05–0.12 u/s. Villagers get 2-frame leg shuffle
(alternate 0.04u leg offset @ 4Hz when moving) — not just bobbing.

### 7.9 Weather & seasons
Daily seed = dayKey. Schedule: choose per 70-min block from CLEAR .55 / CLOUDY .2 / RAIN .15 /
SNOW (winter, replaces rain) with 4s crossfade (alpha-lerp both particle systems). Rain: 110
streaks + pond ripple rate ×3 + villagers stand under rooflines. Season foliage (existing) +
spring blossom dots, autumn leaf fall, winter: snow caps roofs (white rect 0.06u on ridges) +
pond ice sheen (white 20% overlay) + no butterflies.

### 7.10 Events (news + visual, 12; one per open max, 6h cooldown, seed = dayKey)
rainbow after rain (arc 3 bands 20% alpha) · hot-air balloon drifts L20+ (2.5-min crossing) ·
market stall Saturday (striped awning by plaza) · nesting birds spring (twigs on library roof) ·
snowman winter (kids build it 15–18) · shooting star night (1.2s streak) · fishing catch (fisher
pulls, splash + news) · double butterflies after 3-habit day · kite child windy days · lantern
festival on stage-up night (extra strings of lights) · fox/deer/owl arrivals (P5) · postcard day
(Sunday morning: pigeon lands on fountain).

### 7.11 Performance (mandatory)
- **Static layer cache**: render sky-less ground (island, sea ring, skirt, patches, paths, river,
  pond base, plaza, mown ring) into an `ImageBitmap` once per `(layoutHash, sizeBucket,
  paletteBucket)` where paletteBucket = 20-min slot. Use `Canvas(ImageBitmap(w,h))` +
  `CanvasDrawScope`. Redraw budget after cache: sky gradient, cached bitmap blit, animated objects,
  particles ≤ 40, grade. Target: full scene < 2ms/frame mid-range.
- Zero allocations in the frame path: preallocate `Path`s and reuse; no `List.map` inside draw.
- Clock pauses off-screen: gate `withFrameNanos` loop on `LocalLifecycleOwner` RESUMED and on
  the canvas being attached. PEEK quality also drops the clock to 30fps (frame-skip every other).
- Cap: trees ≤ 26, villagers ≤ 8, stars 46 (23 PEEK).

---

## 7-B. ART DIRECTION OVERRIDE (user decision, July 2026) — dense rectangular tile world

The world is a **dense, top-down, RECTANGULAR pixel-tile village** (Stardew-like reference the
user supplied), NOT an isometric island. This overrides §7.2–§7.5 geometry. §7.6 lighting,
weather, seasons, §7.11 caching, tap rules, villagers and all P-mechanics still apply.

- **Map**: 18 cols × 20 rows, tile = cardWidth/18, vertically centered; overflow rows crop
  (top-down worlds read as crops of a bigger place). PEEK shows the middle band. No sky, no sea:
  ground fills the card edge-to-edge. Projection `P(x,y) = origin + (x·tile, y·tile)`; height =
  drawing upward; y-sort by row. Chunky flat-color details at quarter-tile resolution, no outlines.
- **Zone map** (authored): rows 0–4 building band (habit structures side by side with yards) ·
  cols 12–17 rows 0–5 stone plaza (light stone, 2×2 fountain, hedges, 2 lamps) · rows 5–6 dotted
  stone path across + spurs to doors · rows 7–12 cols 1–6 tilled field A (dark soil `#5C4328`,
  furrows, crop rows) with a 2×3 rounded pond · cols 8–12 fenced crop field B · cols 13–16
  rows 7–10 golden wheat patch · rows 13–15 flower meadow + campfire at (10,14) + second pond ·
  rows 16–17 horizontal dirt road with fences · rows 18–19 orchard fringe. Trees (green/autumn/
  pine mix) cluster cols 0–2 and along the road; density grows with level.
- **Grass**: per-tile 4-tone variation (seeded), 1-in-6 tiles get a detail (tuft/tiny flower/
  pebble). Never uniform.
- **Sky replaced by ground-level atmosphere**: drifting cloud *shadows* (soft dark ellipses,
  6% alpha), fireflies + warm windows at night, rain/snow overlays as before; grade() unchanged.
- Landmarks keep their level gates: cottage L3 (4,2 area), windmill L6 (15,8), castle L14 → a
  stone **keep** top-center at (8,0), boats on the meadow pond L10/22, festival bunting L30.

## 8. World ↔ wellbeing coupling (health & look)

Health (0–100, floor 25). Daily tick (existing `tickIfNeeded`) becomes:
`dayQuality = 0.6·habitScore + 0.4·screenScore` where `habitScore = min(1, done/3)` and
`screenScore = clamp(2 − minutes/goal, 0, 1)` (missing usage data → habitScore only).
Good day = quality ≥ 0.55 → health +12, points += 6 + 2·done. Quiet day → health −10 (streak
insurance P3 applies), villager speed ×0.7 next day, butterflies −50% next day.
Visual mapping of health: 100→saturation 1.0, 25→0.72 + grass hue drifts 8° toward yellow —
subtle, recoverable, never brown-dead (delete old wilt-to-brown lerp).

---

## 9. Screen time integration (final architecture)

- **Source**: existing `UsageTracker.screenTimeTodayMinutes()` (UsageStatsManager event walk).
  Poll every 20s while app foreground (existing `produceState`), also on each Today composition.
- **History**: new `core/UsageHistory.kt` — prefs `"usage_history"` JSON `{dayKey: minutes}`,
  ring-capped to 30 entries. Written on every poll (idempotent upsert of today) and read by
  Progress chart + daily tick. ShieldService (if running) upserts hourly.
- **Goal**: prefs `screen_goal_min` default 180. Set in You via chips 2h/3h/4h/5h.
- **Ring thresholds**: < 70% of goal Mint · 70–105% Amber · >105% Violet (data stays honest but
  never red; the label under it: "past your goal — tomorrow's a fresh start").
- **Permission flow (Usage Access)**: bottom sheet, title "Accurate screen time", body "Android
  can tell Pact exactly how long the screen was used today. It stays on this phone." Button
  "Turn on" → `Settings.ACTION_USAGE_ACCESS_SETTINGS`; ghost "Not now". Trigger: first tap on the
  screen-time card, never during onboarding.
- **World coupling**: §8 formula + per-app garden bonus: every budgeted app that ends the day
  under budget → +1 garden point (cap 5/day) at tick. Focus ≥ 25min → +5 workshop points + P1 line.

---

## 10. Habit → world mapping (unchanged categories, locked effects)

| habit category | structure | extra visible effect while leveling |
|---|---|---|
| garden 🌱 | fenced farm plot | +1 flower bed content per level |
| gym 🏋️ | gym | villager jog loop appears L2 |
| library 📖 | library | ivy density; reading villager on bench |
| temple 🧘 | temple | hedge garden ring; incense |
| forest 🚶 | forest arcs | +2 trees per level (cap table §7.4) |
| workshop 💻 | workshop | log pile grows; saw-horse |
| bakery 🍳 | bakery | smoke always; bread sign; cat naps nearby |
| school 🗣️ | school | bell gable; children L4 |
| moon 😴 | moon shrine | night glow; fireflies +4 |

Expeditions, decor placement, and STAGES thresholds stay exactly as implemented (FarmState).

---

## 11. Milestones (execute in order; one commit each)

**Done (v5.18–v5.21):** M0 tokens · M1 voice purge · M2 world-engine refactor · world v4 dense
rectangular tile village (§7-B). These stand.

**Phase A — the single-player game (all offline, no account needed). Ship this first; it is the
app's new identity.**

| M | scope | key files | definition of done |
|---|---|---|---|
| M3 | **World is home** (§4.1): land on the tile world edge-to-edge; floating news line (P1) + pull-up "today" sheet (habit checklist + focus + screen-time ring). Delete the Today tab & center FAB. | MainActivity, BottomBar, new `WorldHome.kt`, FarmState last_seen | app opens to the world; one pull-up reaches every daily action; 2-tap rule holds |
| M4 | **Discipline economy** (§14.1): `Economy.kt` — resource types, earn rules, sinks; wire earning into habit/focus/screen-tick; a build/shop sheet that spends resources on decor only. | core/Economy.kt, FarmState, world sheet | resources only ever increase from real actions (unit test); nothing purchasable exists in code |
| M5 | **Progression unlocks gameplay** (§14.2): region/system unlock table; each milestone opens a real mechanic (chickens→eggs, boats→fishing, mine→stone, etc.). Infinite tail via procedural regions + seasons (§14.4). | core/Progression.kt, world/* | reaching a milestone visibly adds a mechanic, not just a prettier building |
| M6 | **Identity & history** (§14.5–14.6): earned **titles**; **personal history** ("hours saved → tangible things"); Profile screen. | core/Titles.kt, ui/ProfileScreen.kt, UsageHistory.kt | a title unlocks and shows on Profile; history sentence renders from real usage totals |
| M7 | **Retention calendar** (§14.3): daily merchant, daily visitor, weekend challenge, seasonal festival, rare events; P5 animals; streak insurance P3; §8/§9 coupling. | core/LiveOps.kt, world/WorldLife.kt, FarmState | opening on two different days shows different visitors/events; quiet-day freeze unit-tested |
| M8 | **Feel pass**: motion & haptics §3.4–3.5, celebration budget P9, notification P8, delight moments §7 (bell at noon, shooting star, etc.). | all ui | no infinite chrome animation; delights fire but never block |

**Phase B — accounts + social (needs the backend, §15). Start only after Phase A ships.**

| M | scope | key files | definition of done |
|---|---|---|---|
| M9 | **Backend skeleton** (§15): `SocialBackend` interface + Firebase impl behind it; Auth (Google + email); username claim (unique, §15.3); Sign-in screen; signed-out app fully works. | core/social/*, ui/SignInScreen.kt, Profile | can sign in, claim a username, sign out; blocking core untouched and works signed-out |
| M10 | **Friends & async social** (§15.4): find by username, friend requests, publish your world snapshot, **visit a friend's island** (read-only render), leave **gifts** (async), encouragement. | core/social/*, ui/FriendsScreen.kt, world render-from-snapshot | two accounts can befriend, visit, and gift; visit renders the same tile engine from a snapshot |
| M11 | **Live social** (§15.5): neighborhood **rankings** (server-computed, anti-cheat §15.6), **co-op events / festivals**, optional **trading** of earned decor. | Cloud Functions, core/social/* | ranking updates from server; a trade moves a decor item between two accounts atomically |
| M12 | **Store & v7.0**: Play Store listing per §11-B screenshots, privacy policy for the account data (§15.7), README rewrite, versionName 7.0. | README, store assets | listing tells the world→friends→streak story; policy covers stored fields |

Version numbers: Phase A = 6.x (`6.0`…`6.5`, codes 31+); Phase B ships as 7.0. One APK in repo at
a time, name `release/Pact-v{X}.apk`.

### 11-B. Play Store screenshots (design them now, §11 of the review)
Decide in 3 seconds; show the *world*, never encryption/permissions. Order:
1. A lush level-30 world at golden hour, glass caption "Your focus built this."
2. The pull-up habit sheet mid-check, world behind it, "Small days. Visible change."
3. Two friends' islands side by side + a gift, "Grow it with friends."
4. The streak calendar + a big number, "%d-day streak."
5. Personal history card, "You saved 412 hours. That's 31 books."
Screens 1–2 are Phase-A-shippable; 3 needs Phase B.

---

## 12. Engineering & release procedure (memorize)

1. Work on branch `claude/addiction-blocker-android-app-pl7wop` in `genghispirate/Atlas-app`
   (subdir `pact/`). Never push elsewhere without permission.
2. Build/test: `/opt/gradle/bin/gradle --no-daemon :app:testReleaseUnitTest :app:assembleRelease`
   from `pact/`.
3. Swap APK: `git rm` old `release/Pact-v*.apk`, copy
   `app/build/outputs/apk/release/app-release.apk` → `release/Pact-v{new}.apk`, update README refs.
4. Commit to Atlas-app branch, push with retries (`git push -u origin <branch>`).
5. **Sync the standalone repo** `genghispirate/pact` (root = `pact/`):
   `git fetch pactrepo main` → `git worktree add -B pact-publish <scratch>/pactwt pactrepo/main`
   → copy the changed files from `pact/` into the worktree (delete removed APK) → commit there
   with a matching message → `git push pactrepo pact-publish:main` → remove worktree + branch.
   (Remote `pactrepo` = the proxy URL for `genghispirate/pact`.)
6. Send the APK to the user with `SendUserFile` and a 1-line "what changed".

---

## 13. QA checklist → see §16

The release gate now lives in **§16** (Phase A + Phase B), updated for the world-is-home IA and
the game/backend direction. The earlier "Today tab / river ends in pond / island framed by sea"
checks are obsolete (superseded by the §7-B tile world and §4.1 four-tab IA).

---

## 14. Game design — the reason to come back (product-lead review §2–§14)

The world must be a *game*, not a progress bar. Five systems make it one. All are offline; none
require the backend.

### 14.1 The discipline economy (Decision 2a — earned only, spent only on the world)

Six resources. Every source is a real, healthy action; there is **no purchase path anywhere**.

| resource | icon | earned by | spent on |
|---|---|---|---|
| **Seeds** | 🌱 | each habit checked (+2) | planting flowers/crops/trees |
| **Wood** | 🪵 | a completed Focus session (+3 / 25min) | building & repairing structures |
| **Stone** | 🪨 | every budgeted app kept under budget that day (+1, cap 5) | paths, walls, the mine, the castle |
| **Flowers** | 🌸 | a full-habit day (all checked) (+1) | decorations, gifts to friends |
| **Crystals** | 💎 | streak milestones & rare events only (scarce) | rare decorations, region unlocks |
| **Season tickets** | 🎟️ | one per week you finish ≥5 good days | the seasonal festival track (§14.3) |

Rules: resources are stored in `Economy` (prefs JSON, same pattern as `categoryPoints`). They can
**only increase from the actions above** (enforce in one `award()` funnel; a unit test asserts no
other writer). Sinks are always **cosmetic or gameplay unlocks**, never "pay to skip." Show a slim
resource bar as a glass chip row on World home. No timers-to-buy, no ads, no IAP. If we ever
monetize, it is a one-time "supporter" cosmetic pack — decided later, not now.

### 14.2 Progression that unlocks *gameplay* (not just prettier tiles)

Overall level (`FarmState.level`) gates a **small number of deep systems** (avoid a mechanic every
level — §3 of the review's warning about bloat). Between milestones, everything *deepens* (more
crop types, more decor, denser regions), it doesn't add new tutorials.

| level | region/system unlocked | new *gameplay* (not just visuals) |
|---|---|---|
| 1 | **Clearing** | plant seeds, check habits |
| 5 | **Garden & coop** | chickens lay **eggs** (a daily collectible → seeds) |
| 10 | **Pond & docks** | **fishing** mini-moment (tap when the float dips → flowers/crystals) |
| 15 | **Windmill & fields** | crops mature over days → **harvest** for wood/seeds |
| 22 | **Harbor** | boats run **errands**: send one out, it returns in real hours with resources (the expedition system, reframed) |
| 30 | **Mountain & mine** | spend focus to **mine** stone/crystals; deepest sink |
| 40 | **Castle & festivals** | host a **festival** (spends season tickets → world-wide decoration event + rare decor) |
| 50+ | **New regions, procedurally themed by season** | infinite tail (§14.4) |

Each unlock is announced via P1 news ("The harbor is open — send a boat exploring") and by the
thing actually appearing in the world. Reaching a level with no habit progress is impossible, so
gameplay unlocks *are* discipline rewards.

### 14.3 Retention calendar — "I need to come back tomorrow" (`LiveOps.kt`, seed = dayKey)

All deterministic from the date + a local seed (no server needed for the single-player calendar):
- **Daily merchant** (a caravan tile appears 1 day in ~2): offers to swap surplus resources
  (e.g. 10 seeds → 1 crystal). One trade/day.
- **Daily visitor**: a themed villager wanders in (a bard, a painter, a traveler) with a one-line
  greeting and a tiny gift (a few flowers). Different each day.
- **Weekend challenge** (Sat–Sun): a small goal ("check 3 habits both days") → season ticket.
- **Seasonal festival** (first weekend of each real season): the world dresses up; a festival
  track spends season tickets for exclusive seasonal decor. Rotates 4×/year → always something new.
- **Rare events** (low daily probability, 6h cooldown, one at a time): meteor shower (tap falling
  stars → crystals), rainbow after rain, mystery seed (plant it, it grows into a random rare
  plant over 3 days), treasure chest (washes up on the shore → resources), world anniversary
  (install date → a commemorative decoration).
- **Limited decorations**: each festival's decor is only obtainable that season → collection
  pressure without FOMO-for-money.

### 14.4 Infinite progression (never "you win")

Past level 50, the world grows **outward into new regions** whose theme is chosen by the current
real season (spring meadow, summer coast, autumn woods, winter tundra) and a rotating biome list,
so a year-2 player still sees new ground. Decoration and crop catalogs are open-ended lists we
append to across versions. There is **no level cap and no "complete" state**; the number just
keeps climbing and the map keeps unfurling.

### 14.5 Identity — titles (`Titles.kt`)

Players earn **titles** for patterns of discipline; the active title shows under their username on
Profile and to friends (Phase B). Titles are permanent once earned. Examples & unlock rules:
- **Forest Guardian** — Forest structure ≥ Lv5 · **Master Builder** — 10 structures finished ·
  **Iron Discipline** — 30-day streak · **Deep Thinker** — 50 focus sessions · **Bookworm** —
  Library ≥ Lv5 · **Focus Champion** — 100 focus hours · **Early Bird** — 20 habits checked
  before 8am · **Night Owl** — 20 after 10pm · **Explorer** — all regions reached · **Minimalist**
  — a week entirely under every budget.
Titles create recognition ("that's the person with the legendary winter village") and attachment.

### 14.6 Personal history — make progress tangible (`UsageHistory.kt` + `Milestones`)

The app periodically translates saved time into something a human feels. "Saved time" =
Σ over days of `max(0, goal − screenTimeMinutes)` since install (only counts days with usage
data). Rendered on Stats and surfaced via P1 news at thresholds:
- "You've saved **412 hours** from distracting apps."
- "That's enough time to **read ~31 books**." (1 book ≈ 13h)
- "Or **walk from Berlin to Hamburg**." (~289 km ≈ 60h)
- "Or watch the Lord of the Rings trilogy **~34 times**." (~11.4h each)
Pick the comparison that matches the current total (a small table of {hours → phrase}). This is the
"this world represents my discipline" payoff (review §6, §14). Shareable as a postcard.

---

## 15. Backend & accounts (Decision 1b) — the seamless social server

The blocking core stays **offline and serverless** (shield, budgets, wall, trusted-person unlock
via the existing Ed25519/relay). The backend exists **only** for accounts and social. The app is
100% usable **signed-out**; sign-in gates only Friends/social.

### 15.1 Choice: Firebase, behind an interface
Use **Firebase** — Auth (Google + email/password), **Firestore** (world snapshots + social graph),
**Cloud Functions** (server-authoritative rankings, trades, anti-cheat), **App Check** (abuse). It
is the fastest path to "sign in with Google, find friends by username" with near-zero ops and a
generous free tier. Wrap ALL of it behind a `core/social/SocialBackend` **interface** (mirroring
the existing `Transport` abstraction) with a `FirebaseSocial` implementation, so the provider can
later be swapped (Supabase/custom) **without touching UI or game code**. No Firebase type may leak
above the interface.

New deps (M9 only): `com.google.firebase:firebase-auth-ktx`, `firebase-firestore-ktx`,
`firebase-functions-ktx`, `com.google.android.gms:play-services-auth`, and the
`com.google.gms.google-services` Gradle plugin. This intentionally supersedes the old "no Google
services" line — note it in README.

### 15.2 Setup the human must do (write this in README; code can't do it here)
1. Create a Firebase project; add an Android app with applicationId `com.pact.app`.
2. Enable Auth providers: Google, Email/Password.
3. Add the SHA-1/SHA-256 of the release keystore (`keystore/pact-release.keystore`) for Google
   Sign-In.
4. Download `google-services.json` → `app/`. **Git-ignore it** (do not commit; it's not a secret
   but keeps forks clean — provide `google-services.json.example`).
5. Deploy `functions/` and `firestore.rules` (both live in the repo).
The app must **degrade gracefully with no `google-services.json`** (social simply shows "sign-in
unavailable"; single-player is unaffected) so the open-source build still compiles and runs.

### 15.3 Identity & usernames
- Account = Firebase UID (from Google or email). On first sign-in, force a **username claim**:
  3–15 chars `[a-z0-9_]`, unique. Uniqueness via a `usernames/{lowercased}` doc created in a
  transaction (fails if exists). Display name + avatar emoji stored on the profile.
- `users/{uid}`: `{ username, displayName, avatar, title, level, worldStage, createdAt,
  lastActive }`. Public-readable minimal card; private fields (email) never stored in Firestore
  (Auth holds them).

### 15.4 Async social (M10) — works even though friends are rarely online at the same time
- **World snapshot**: on meaningful change (≤ 1/hour), publish `worlds/{uid}` = a compact JSON of
  the layout inputs (level, categoryPoints, decor, titles, resources-for-display) — NOT a bitmap.
  Visiting renders the snapshot through the **same tile engine** (`WorldCanvas` gains a
  `fromSnapshot` path), so a visit looks identical to the owner's world, read-only.
- **Friends**: request/accept by username; `friends/{uid}/{friendUid}` docs both sides.
- **Gifts**: `gifts/{toUid}/{id}` = `{from, type, ts}`; delivered next time the recipient opens
  (P1 news: "Maya sent you sunflowers"). Costs the sender Flowers (§14.1). Rate-limited.
- **Encouragement**: lightweight reactions to a friend's streak, same delivery.

### 15.5 Live social (M11)
- **Neighborhood ranking**: a Cloud Function recomputes friend-group leaderboards (by streak, by
  world level, by "most improved") on a schedule; client reads `rankings/{groupId}`. Multi-axis
  per review §5 — never a single "least screen time" board.
- **Co-op festivals**: a shared goal doc friends contribute to (`coop/{eventId}`), Function
  validates contributions and unlocks a shared decoration for all participants.
- **Trading**: earned decor only; a Function performs an atomic two-sided swap (never resources →
  no economy inflation; anti-cheat §15.6).

### 15.6 Anti-cheat & authority
Anything competitive (rankings, trades, co-op) is **server-authoritative**: the client sends
*actions/claims*, Functions validate against server-side records (e.g., streak derived from
day-stamped check-ins the server also stores, not a client number). Rate-limit writes; App Check
on all callable Functions; Firestore Rules deny client writes to `rankings`, `usernames`
uniqueness, and any resource balance. Single-player resources stay client-side (no incentive to
cheat a solo cosmetic economy), but the *displayed* competitive stats are recomputed server-side.

### 15.7 Privacy posture & copy (must update — supersedes "no accounts ever")
The old promise was "no accounts, no servers." The new, honest promise:
> **Your world and habits live on your phone. Signing in (optional) syncs only what friends need
> to see — your username, world snapshot, streak, and gifts — so you can play together. Your app
> list, your screen-time details, and the blocking keys never leave your device.**
- Blocking/trusted-person data: **never** uploaded (stays E2E/relay).
- Uploaded: username, avatar, level/stage, streak, world *snapshot inputs*, gifts, friend edges.
- **Not** uploaded: which apps you block, per-app usage, focus contents, private keys.
- Provide account deletion (Function wipes `users`, `worlds`, `usernames`, `friends`, `gifts`).
- A real privacy policy ships with M12 (Play requires it once accounts exist).
- Sign-in copy: "Sign in to grow your world with friends. Skip to keep everything on this phone."

### 15.8 Files (Phase B)
```
core/social/SocialBackend.kt   // interface: auth, profile, friends, snapshot, gifts, ranking, trade
core/social/FirebaseSocial.kt  // the only file that imports Firebase
core/social/SocialModels.kt    // Profile, FriendEdge, WorldSnapshot, Gift, RankRow, Trade
ui/SignInScreen.kt · ui/FriendsScreen.kt · ui/VisitWorld.kt
functions/ (TypeScript)        // rankings, trades, coop, deleteAccount, claimUsername
firestore.rules · firestore.indexes.json
```

---

## 16. QA checklist (release gate)

**Phase A (6.x):** world is the home screen (no Today tab) · resources only rise from real actions
(unit test) · nothing purchasable in code · a level milestone adds a *mechanic*, not just a sprite ·
a title unlocks and shows on Profile · personal-history sentence renders from real totals · two
different days show different merchant/visitor/event · quiet-day streak freeze unit-tested · single
accent per card · 1dp hairlines · no emoji in chrome · sentence case · no pure-red · cozy night
screenshot · villager fits door · 60fps tile world on mid-range · signed-out app fully usable.

**Phase B (7.0):** signed-out app 100% functional · sign in with Google AND email · username
uniqueness enforced server-side · visiting a friend renders their snapshot in the tile engine ·
gift delivers on next open · ranking is server-computed and can't be client-forged · trade is
atomic · account deletion wipes all server docs · privacy copy matches what's actually stored ·
app builds & runs with NO `google-services.json` (social shows "unavailable").

---

*End of bible. Build calmly. The world is the product; blocking is how it grows.*
