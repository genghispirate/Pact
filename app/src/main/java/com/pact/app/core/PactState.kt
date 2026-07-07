package com.pact.app.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Single source of truth for the app's persisted state. Backed by
 * SharedPreferences (offline, instant), exposed to Compose as a StateFlow
 * snapshot, and safe to read from the AccessibilityService on every event
 * because reads hit the in-memory snapshot.
 */
class PactState private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("pact_state", Context.MODE_PRIVATE)

    /** How hard an app is to open. RED = sponsor code; YELLOW = mindful delay. */
    enum class Tier { YELLOW, RED }

    enum class Role { UNSET, USER, SPONSOR }

    /** Why the user reached for the app — logged during yellow unlocks. */
    enum class Trigger { BORED, STRESS, HABIT, ANXIETY, LONELY, PROCRASTINATION, NEEDED }

    /** One day of aggregate stats. [day] is yyyymmdd. */
    data class DayStats(
        val day: Int,
        val blocksPerApp: Map<String, Int> = emptyMap(),
        val unlocks: Int = 0,
        val walkaways: Int = 0,
        val minutesUnlocked: Int = 0,
    ) {
        val blocks: Int get() = blocksPerApp.values.sum()
    }

    /** One unlock, for history and reflection. [worthIt] null until reflected. */
    data class UnlockEvent(
        val at: Long,
        val pkg: String,
        val tier: Tier,
        val trigger: Trigger?,
        val durationMinutes: Int,
        val worthIt: Boolean?,
        val reflectionDismissed: Boolean = false,
    ) {
        val endsAt: Long get() = at + durationMinutes * 60_000L
    }

    data class Snapshot(
        val role: Role = Role.UNSET,
        val setupComplete: Boolean = false,
        val guardianName: String = "",
        val blocked: Set<String> = emptySet(),
        val tiers: Map<String, Tier> = emptyMap(),
        /** Minutes of daily allowance per app. 0 (or absent) = no time, a hard lock. */
        val dailyLimits: Map<String, Int> = emptyMap(),
        /** Milliseconds already spent in each app today (resets at midnight). */
        val usedTodayMillis: Map<String, Long> = emptyMap(),
        val unlockUntil: Map<String, Long> = emptyMap(),
        /** Yellow self-unlock rests until this time, per app. */
        val yellowCooldownUntil: Map<String, Long> = emptyMap(),
        val strictMode: Boolean = false,
        /** A focus session locks every blocked app, no unlocks, until this time. */
        val focusUntil: Long = 0L,
        /** Rolling daily stats, most recent last. Bounded to [DAYS_KEPT]. */
        val days: List<DayStats> = emptyList(),
        /** Cumulative count of block events per hour of day (24 buckets). */
        val hourHistogram: List<Int> = List(24) { 0 },
        /** Recent unlocks, most recent last. Bounded to [EVENTS_KEPT]. */
        val events: List<UnlockEvent> = emptyList(),
        val lastUnlockAt: Long = 0L,
        val setupAt: Long = 0L,
        val longestStreakDays: Int = 0,
    ) {
        fun tierOf(pkg: String): Tier = tiers[pkg] ?: Tier.RED

        fun focusActive(nowMillis: Long = System.currentTimeMillis()): Boolean = focusUntil > nowMillis

        /** Does this app have a daily time allowance (vs. being a hard lock)? */
        fun hasLimit(pkg: String): Boolean = (dailyLimits[pkg] ?: 0) > 0

        fun limitMinutes(pkg: String): Int = dailyLimits[pkg] ?: 0

        fun usedMillis(pkg: String): Long = usedTodayMillis[pkg] ?: 0L

        /** Milliseconds of allowance still left on this app today. */
        fun remainingMillis(pkg: String): Long {
            val limit = dailyLimits[pkg] ?: 0
            if (limit <= 0) return 0L
            return (limit * 60_000L - usedMillis(pkg)).coerceAtLeast(0L)
        }

        /** Whole minutes of allowance left today (rounded up while any time remains). */
        fun remainingMinutes(pkg: String): Int =
            ((remainingMillis(pkg) + 59_999L) / 60_000L).toInt()

        /** Total minutes spent across all limited apps today — the shareable number. */
        fun screenTimeTodayMinutes(): Int =
            (usedTodayMillis.filterKeys { it in blocked }.values.sum() / 60_000L).toInt()

        val today: DayStats get() = days.lastOrNull()?.takeIf { it.day == dayKey(System.currentTimeMillis()) }
            ?: DayStats(dayKey(System.currentTimeMillis()))

        /** Whole days since the last unlock (or since setup if never unlocked). */
        fun streakDays(nowMillis: Long = System.currentTimeMillis()): Int {
            val anchor = maxOf(lastUnlockAt, setupAt)
            if (anchor == 0L) return 0
            return ((nowMillis - anchor) / 86_400_000L).toInt()
        }

        /** The unlock whose break just ended and hasn't been reflected on yet. */
        fun pendingReflection(nowMillis: Long = System.currentTimeMillis()): UnlockEvent? =
            events.lastOrNull { it.worthIt == null && !it.reflectionDismissed && it.endsAt < nowMillis }
    }

    private val _snapshot = MutableStateFlow(read())
    val snapshot: StateFlow<Snapshot> = _snapshot

    /** Called after every state change; hosts (widget, etc.) can observe. */
    var onChanged: ((Snapshot) -> Unit)? = null

    // ---------------------------------------------------------------- setup

    fun completeSetup(myName: String, blocked: Set<String>, limits: Map<String, Int> = emptyMap()) {
        prefs.edit()
            .putString(KEY_ROLE, Role.USER.name)
            .putBoolean(KEY_SETUP, true)
            .putString(KEY_GUARDIAN, myName.trim())
            .putStringSet(KEY_BLOCKED, blocked)
            .putString(KEY_LIMITS, encodeIntMap(limits))
            .putLong(KEY_SETUP_AT, System.currentTimeMillis())
            .apply()
        refresh()
    }

    fun reset() {
        prefs.edit().clear().apply()
        refresh()
    }

    // ----------------------------------------------------- trusted-person role

    /** Mark this install as a trusted person's device (holds others' locks). */
    fun becomeSponsor() {
        prefs.edit().putString(KEY_ROLE, Role.SPONSOR.name).apply()
        refresh()
    }

    // ------------------------------------------------------------- blocking

    fun addBlocked(packages: Collection<String>, limitMinutes: Int = DEFAULT_LIMIT_MINUTES) {
        val s = _snapshot.value
        val next = s.blocked + packages
        // Only set a limit for apps that don't already have one — never clobber.
        val newLimits = packages.filter { it !in s.dailyLimits }.associateWith { limitMinutes }
        prefs.edit()
            .putStringSet(KEY_BLOCKED, next)
            .putString(KEY_LIMITS, encodeIntMap(s.dailyLimits + newLimits))
            .apply()
        refresh()
    }

    /** Set an app's daily allowance in minutes. 0 turns it into a hard lock. */
    fun setDailyLimit(pkg: String, minutes: Int) {
        val next = _snapshot.value.dailyLimits + (pkg to minutes.coerceIn(0, 24 * 60))
        prefs.edit().putString(KEY_LIMITS, encodeIntMap(next)).apply()
        refresh()
    }

    /** Accrue foreground time the shield measured for a limited app today. */
    fun recordUsage(pkg: String, addMillis: Long) {
        if (addMillis <= 0L) return
        val today = dayKey(System.currentTimeMillis())
        val storedDay = prefs.getInt(KEY_USAGE_DAY, 0)
        val current = if (storedDay == today) decodeLongMap(prefs.getString(KEY_USAGE, "") ?: "") else emptyMap()
        val next = current + (pkg to (current[pkg] ?: 0L) + addMillis)
        prefs.edit()
            .putInt(KEY_USAGE_DAY, today)
            .putString(KEY_USAGE, encodeUsage(next))
            .apply()
        refresh()
    }

    /** Is this app under Pact's management (blocked, or system-guarded in strict mode)? */
    fun isManaged(pkg: String): Boolean {
        val s = _snapshot.value
        return s.setupComplete && (s.blocked.contains(pkg) ||
            (s.strictMode && pkg in PROTECTED_WHEN_STRICT))
    }

    fun removeBlocked(pkg: String) {
        val s = _snapshot.value
        prefs.edit()
            .putStringSet(KEY_BLOCKED, s.blocked - pkg)
            .putString(KEY_UNLOCKS, encodeLongMap(s.unlockUntil - pkg))
            .putString(KEY_LIMITS, encodeIntMap(s.dailyLimits - pkg))
            .apply()
        refresh()
    }

    /**
     * Set an app's difficulty. Making it stricter is always free; relaxing
     * RED → YELLOW is gated behind a code by the caller.
     */
    fun setTier(pkg: String, tier: Tier) {
        val next = _snapshot.value.tiers + (pkg to tier)
        prefs.edit().putString(KEY_TIERS, encodeTiers(next)).apply()
        refresh()
    }

    fun setStrictMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STRICT, enabled).apply()
        refresh()
    }

    /**
     * Start a focus session: every blocked app stays shut for [durationMillis],
     * ignoring any active break. A commitment you make to yourself; there's no
     * way to end it early on purpose — that's the point.
     */
    fun startFocus(durationMillis: Long) {
        prefs.edit().putLong(KEY_FOCUS, System.currentTimeMillis() + durationMillis).apply()
        refresh()
    }

    fun isBlockedNow(pkg: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val s = _snapshot.value
        if (!s.setupComplete) return false
        val covered = s.blocked.contains(pkg) ||
            (s.strictMode && pkg in PROTECTED_WHEN_STRICT)
        if (!covered) return false
        // During a focus session, nothing blocked gets through — even active breaks.
        if (s.blocked.contains(pkg) && s.focusActive(nowMillis)) return true
        // A granted bonus break lets you back in for its window.
        if ((s.unlockUntil[pkg] ?: 0L) > nowMillis) return false
        // Still inside today's allowance? The app opens normally.
        if (s.blocked.contains(pkg) && s.remainingMillis(pkg) > 0L) return false
        return true
    }

    // -------------------------------------------------------------- unlocks

    /**
     * Grant a break. Records the unlock event (history + reflection queue),
     * updates daily stats and streaks, and arms the yellow cooldown so
     * self-unlocks can't be chained back to back.
     */
    fun unlockFor(
        pkg: String,
        durationMillis: Long,
        tier: Tier = Tier.RED,
        trigger: Trigger? = null,
    ) {
        val now = System.currentTimeMillis()
        val until = now + durationMillis
        val minutes = (durationMillis / 60_000L).toInt().coerceAtLeast(1)
        val s = _snapshot.value

        val event = UnlockEvent(now, pkg, tier, trigger, minutes, worthIt = null)
        val events = (s.events + event).takeLast(EVENTS_KEPT)

        val today = todayStats(s)
        val updatedDay = today.copy(
            unlocks = today.unlocks + 1,
            minutesUnlocked = today.minutesUnlocked + minutes,
        )

        val currentStreak = s.streakDays(now)
        val longest = maxOf(s.longestStreakDays, currentStreak)

        val edit = prefs.edit()
            .putString(KEY_UNLOCKS, encodeLongMap(s.unlockUntil + (pkg to until)))
            .putString(KEY_EVENTS, encodeEvents(events))
            .putString(KEY_DAYS, encodeDays(upsertDay(s.days, updatedDay)))
            .putLong(KEY_LAST_UNLOCK, now)
            .putInt(KEY_LONGEST_STREAK, longest)
        if (tier == Tier.YELLOW) {
            val cooldowns = s.yellowCooldownUntil + (pkg to until + YELLOW_COOLDOWN_MILLIS)
            edit.putString(KEY_COOLDOWNS, encodeLongMap(cooldowns))
        }
        edit.apply()
        refresh()
    }

    fun relock(pkg: String) {
        val unlocks = _snapshot.value.unlockUntil - pkg
        prefs.edit().putString(KEY_UNLOCKS, encodeLongMap(unlocks)).apply()
        refresh()
    }

    // ---------------------------------------------------------------- stats

    /** Called by the shield each time it steps in front of a blocked app. */
    fun recordBlock(pkg: String) {
        val s = _snapshot.value
        val today = todayStats(s)
        val perApp = today.blocksPerApp + (pkg to (today.blocksPerApp[pkg] ?: 0) + 1)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hours = s.hourHistogram.toMutableList().also { it[hour] = it[hour] + 1 }
        prefs.edit()
            .putString(KEY_DAYS, encodeDays(upsertDay(s.days, today.copy(blocksPerApp = perApp))))
            .putString(KEY_HOURS, hours.joinToString(","))
            .apply()
        refresh()
    }

    /** The user hit the wall and chose to leave — a small win worth counting. */
    fun recordWalkAway(pkg: String) {
        val s = _snapshot.value
        val today = todayStats(s)
        prefs.edit()
            .putString(KEY_DAYS, encodeDays(upsertDay(s.days, today.copy(walkaways = today.walkaways + 1))))
            .apply()
        refresh()
    }

    /** Reflection answer for the unlock event started at [eventAt]. */
    fun setWorthIt(eventAt: Long, worth: Boolean?) {
        val events = _snapshot.value.events.map {
            if (it.at == eventAt) {
                if (worth == null) it.copy(reflectionDismissed = true) else it.copy(worthIt = worth)
            } else it
        }
        prefs.edit().putString(KEY_EVENTS, encodeEvents(events)).apply()
        refresh()
    }

    // --------------------------------------------------------------- backup

    /**
     * Apply a decrypted backup. Restores apps, tiers, strict mode, and
     * statistics; the current sponsor pairing is intentionally untouched
     * (backups never carry the TOTP secret).
     */
    fun applyBackup(json: JSONObject): Boolean = runCatching {
        val blocked = mutableSetOf<String>()
        val blockedArr = json.getJSONArray("blocked")
        for (i in 0 until blockedArr.length()) blocked.add(blockedArr.getString(i))

        val tiers = mutableMapOf<String, Tier>()
        val tiersObj = json.optJSONObject("tiers") ?: JSONObject()
        for (k in tiersObj.keys()) {
            runCatching { Tier.valueOf(tiersObj.getString(k)) }.getOrNull()?.let { tiers[k] = it }
        }

        val hours = json.optJSONArray("hours")?.let { arr ->
            (0 until arr.length()).map { arr.getInt(it) }
        }?.takeIf { it.size == 24 } ?: List(24) { 0 }

        val limits = mutableMapOf<String, Int>()
        val limitsObj = json.optJSONObject("limits") ?: JSONObject()
        for (k in limitsObj.keys()) limits[k] = limitsObj.getInt(k)

        prefs.edit()
            .putStringSet(KEY_BLOCKED, blocked)
            .putString(KEY_TIERS, encodeTiers(tiers))
            .putString(KEY_LIMITS, encodeIntMap(limits))
            .putBoolean(KEY_STRICT, json.optBoolean("strictMode"))
            .putString(KEY_DAYS, json.optJSONArray("days")?.toString() ?: "")
            .putString(KEY_HOURS, hours.joinToString(","))
            .putString(KEY_EVENTS, json.optJSONArray("events")?.toString() ?: "")
            .putInt(KEY_LONGEST_STREAK, json.optInt("longestStreak"))
            .apply()
        refresh()
        true
    }.getOrDefault(false)

    // ------------------------------------------------------------- plumbing

    private fun todayStats(s: Snapshot): DayStats {
        val key = dayKey(System.currentTimeMillis())
        return s.days.lastOrNull()?.takeIf { it.day == key } ?: DayStats(key)
    }

    private fun upsertDay(days: List<DayStats>, day: DayStats): List<DayStats> {
        val without = days.filterNot { it.day == day.day }
        return (without + day).sortedBy { it.day }.takeLast(DAYS_KEPT)
    }

    private fun refresh() {
        _snapshot.value = read()
        onChanged?.invoke(_snapshot.value)
    }

    private fun read(): Snapshot = Snapshot(
        role = runCatching { Role.valueOf(prefs.getString(KEY_ROLE, null) ?: "") }
            .getOrElse { if (prefs.getBoolean(KEY_SETUP, false)) Role.USER else Role.UNSET },
        setupComplete = prefs.getBoolean(KEY_SETUP, false),
        guardianName = prefs.getString(KEY_GUARDIAN, "") ?: "",
        blocked = prefs.getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet(),
        tiers = decodeTiers(prefs.getString(KEY_TIERS, "") ?: ""),
        dailyLimits = decodeIntMap(prefs.getString(KEY_LIMITS, "") ?: ""),
        usedTodayMillis = if (prefs.getInt(KEY_USAGE_DAY, 0) == dayKey(System.currentTimeMillis()))
            decodeLongMap(prefs.getString(KEY_USAGE, "") ?: "") else emptyMap(),
        unlockUntil = decodeLongMap(prefs.getString(KEY_UNLOCKS, "") ?: ""),
        yellowCooldownUntil = decodeLongMap(prefs.getString(KEY_COOLDOWNS, "") ?: ""),
        strictMode = prefs.getBoolean(KEY_STRICT, false),
        focusUntil = prefs.getLong(KEY_FOCUS, 0L),
        days = decodeDays(prefs.getString(KEY_DAYS, "") ?: ""),
        hourHistogram = decodeHours(prefs.getString(KEY_HOURS, "") ?: ""),
        events = decodeEvents(prefs.getString(KEY_EVENTS, "") ?: ""),
        lastUnlockAt = prefs.getLong(KEY_LAST_UNLOCK, 0L),
        setupAt = prefs.getLong(KEY_SETUP_AT, 0L),
        longestStreakDays = prefs.getInt(KEY_LONGEST_STREAK, 0),
    )

    // ----------------------------------------------------------- encodings

    private fun encodeLongMap(map: Map<String, Long>): String {
        val now = System.currentTimeMillis()
        return map.entries
            .filter { it.value > now }
            .joinToString(";") { "${it.key}=${it.value}" }
    }

    private fun decodeLongMap(raw: String): Map<String, Long> =
        raw.split(";").mapNotNull { entry ->
            val i = entry.lastIndexOf('=')
            if (i <= 0) null
            else entry.substring(0, i) to (entry.substring(i + 1).toLongOrNull() ?: return@mapNotNull null)
        }.toMap()

    private fun encodeIntMap(map: Map<String, Int>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value}" }

    private fun decodeIntMap(raw: String): Map<String, Int> =
        raw.split(";").mapNotNull { entry ->
            val i = entry.lastIndexOf('=')
            if (i <= 0) null
            else entry.substring(0, i) to (entry.substring(i + 1).toIntOrNull() ?: return@mapNotNull null)
        }.toMap()

    /** Like [encodeLongMap] but keeps every entry — usage millis are small, not timestamps. */
    private fun encodeUsage(map: Map<String, Long>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value}" }

    private fun encodeTiers(map: Map<String, Tier>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value.name}" }

    private fun decodeTiers(raw: String): Map<String, Tier> =
        raw.split(";").mapNotNull { entry ->
            val i = entry.lastIndexOf('=')
            if (i <= 0) null
            else entry.substring(0, i) to (runCatching { Tier.valueOf(entry.substring(i + 1)) }.getOrNull()
                ?: return@mapNotNull null)
        }.toMap()

    private fun encodeDays(days: List<DayStats>): String {
        val arr = JSONArray()
        for (d in days) {
            arr.put(
                JSONObject()
                    .put("d", d.day)
                    .put("b", JSONObject(d.blocksPerApp.mapValues { it.value as Any }))
                    .put("u", d.unlocks)
                    .put("w", d.walkaways)
                    .put("m", d.minutesUnlocked)
            )
        }
        return arr.toString()
    }

    private fun decodeDays(raw: String): List<DayStats> = runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val blocks = mutableMapOf<String, Int>()
            val b = o.optJSONObject("b") ?: JSONObject()
            for (k in b.keys()) blocks[k] = b.getInt(k)
            DayStats(
                day = o.getInt("d"),
                blocksPerApp = blocks,
                unlocks = o.optInt("u"),
                walkaways = o.optInt("w"),
                minutesUnlocked = o.optInt("m"),
            )
        }
    }.getOrDefault(emptyList())

    private fun decodeHours(raw: String): List<Int> {
        val parts = raw.split(",").mapNotNull { it.toIntOrNull() }
        return if (parts.size == 24) parts else List(24) { 0 }
    }

    private fun encodeEvents(events: List<UnlockEvent>): String {
        val arr = JSONArray()
        for (e in events) {
            arr.put(
                JSONObject()
                    .put("at", e.at)
                    .put("p", e.pkg)
                    .put("t", e.tier.name)
                    .put("tr", e.trigger?.name ?: JSONObject.NULL)
                    .put("m", e.durationMinutes)
                    .put("w", e.worthIt ?: JSONObject.NULL)
                    .put("rd", e.reflectionDismissed)
            )
        }
        return arr.toString()
    }

    private fun decodeEvents(raw: String): List<UnlockEvent> = runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            UnlockEvent(
                at = o.getLong("at"),
                pkg = o.getString("p"),
                tier = runCatching { Tier.valueOf(o.getString("t")) }.getOrDefault(Tier.RED),
                trigger = o.optString("tr").takeIf { it.isNotEmpty() && it != "null" }
                    ?.let { runCatching { Trigger.valueOf(it) }.getOrNull() },
                durationMinutes = o.getInt("m"),
                worthIt = if (o.isNull("w")) null else o.getBoolean("w"),
                reflectionDismissed = o.optBoolean("rd"),
            )
        }
    }.getOrDefault(emptyList())

    companion object {
        const val SETTINGS_PACKAGE = "com.android.settings"

        /** Strict mode also guards the doors used to disable or uninstall Pact. */
        val PROTECTED_WHEN_STRICT = setOf(
            SETTINGS_PACKAGE,
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
        )

        /** After a yellow break ends, self-unlock rests this long. */
        const val YELLOW_COOLDOWN_MILLIS = 30 * 60 * 1000L

        /** Seconds of mindful waiting before a yellow unlock. */
        const val YELLOW_WAIT_SECONDS = 30

        const val DAYS_KEPT = 14
        const val EVENTS_KEPT = 100

        /** A friendly default allowance for a newly added app — an hour a day would be lax, a few minutes harsh. */
        const val DEFAULT_LIMIT_MINUTES = 30

        private const val KEY_SETUP = "setup_complete"
        private const val KEY_SETUP_AT = "setup_at"
        private const val KEY_GUARDIAN = "guardian_name"
        private const val KEY_BLOCKED = "blocked_packages"
        private const val KEY_TIERS = "tiers"
        private const val KEY_LIMITS = "daily_limits"
        private const val KEY_USAGE = "usage_today"
        private const val KEY_USAGE_DAY = "usage_day"
        private const val KEY_UNLOCKS = "unlock_until"
        private const val KEY_COOLDOWNS = "yellow_cooldowns"
        private const val KEY_STRICT = "strict_mode"
        private const val KEY_FOCUS = "focus_until"
        private const val KEY_ROLE = "role"
        private const val KEY_DAYS = "stat_days"
        private const val KEY_HOURS = "stat_hours"
        private const val KEY_EVENTS = "unlock_events"
        private const val KEY_LAST_UNLOCK = "last_unlock_at"
        private const val KEY_LONGEST_STREAK = "longest_streak"

        @Volatile
        private var instance: PactState? = null

        fun get(context: Context): PactState =
            instance ?: synchronized(this) {
                instance ?: PactState(context).also { instance = it }
            }

        fun dayKey(nowMillis: Long): Int {
            val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            return cal.get(Calendar.YEAR) * 10000 +
                (cal.get(Calendar.MONTH) + 1) * 100 +
                cal.get(Calendar.DAY_OF_MONTH)
        }

        fun untilMidnightMillis(nowMillis: Long = System.currentTimeMillis()): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis - nowMillis
        }
    }
}
