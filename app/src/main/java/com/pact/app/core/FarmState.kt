package com.pact.app.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.random.Random

/**
 * The farm: the positive, low-stress heart of the app. You don't get punished
 * for your phone — you grow something for taking care of yourself. Do your
 * habits and stay off the doomscroll and the farm thrives: crops sprout, animals
 * arrive, buildings go up. Neglect it and it slowly wilts. Nothing here blocks
 * anything; it just rewards the good days.
 *
 * The whole simulation is deterministic and local — a tiny idle game driven by
 * habit check-ins, advanced one "day" at a time.
 */
class FarmState private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("pact_farm", Context.MODE_PRIVATE)

    /** Each habit belongs to a category, and that category grows one structure in your world. */
    data class Habit(val id: String, val emoji: String, val name: String, val category: String = "garden")

    /** A structure your habits build. Every category maps to one part of the world. */
    data class Category(val id: String, val emoji: String, val structure: String)

    /** A villager off exploring. Returns a one-off cosmetic decoration for your world. */
    data class Expedition(val type: String, val emoji: String, val name: String, val endsAt: Long, val reward: String)

    /** A kind of trip a villager can take: how long it runs and what it can bring home. */
    data class ExpeditionType(val id: String, val emoji: String, val name: String, val minutes: Int, val rewards: List<String>)

    data class Snapshot(
        val points: Int = 0,
        val health: Int = 70,             // 0..100 — how lush the farm looks
        val plots: List<Int> = List(3) { 0 },   // crop stage per plot: 0 soil → 3 bloom
        val habits: List<Habit> = emptyList(),
        val doneToday: Set<String> = emptySet(),
        val goodStreak: Int = 0,          // consecutive good days
        val categoryPoints: Map<String, Int> = emptyMap(),
        val activeExpedition: Expedition? = null,
        val decor: Set<String> = emptySet(),
    ) {
        /** True once a villager on a trip is due back and ready to collect. */
        val expeditionReady: Boolean get() = activeExpedition != null && System.currentTimeMillis() >= activeExpedition.endsAt
        val level: Int get() = 1 + points / LEVEL_POINTS
        val animals: Int get() = (level / 2).coerceIn(0, 6)
        val hasBarn: Boolean get() = level >= 3
        val hasHouse: Boolean get() = level >= 5
        val progressInLevel: Float get() = (points % LEVEL_POINTS).toFloat() / LEVEL_POINTS
        fun habitsDoneToday(): Int = doneToday.size

        /** How grown a structure is (0 = not started). */
        fun structureLevel(category: String): Int = (categoryPoints[category] ?: 0) / STRUCTURE_POINTS
        fun structureProgress(category: String): Float =
            ((categoryPoints[category] ?: 0) % STRUCTURE_POINTS).toFloat() / STRUCTURE_POINTS
        /** How many distinct structures have been started — drives the world stage. */
        fun structuresBuilt(): Int = categoryPoints.count { it.value >= STRUCTURE_POINTS }

        /** The world's current era, from a clearing up to a fantasy kingdom. */
        val stageIndex: Int get() = STAGES.indexOfLast { points >= it.first }.coerceAtLeast(0)
        val stageName: String get() = STAGES[stageIndex].second
        val nextStagePoints: Int? get() = STAGES.getOrNull(stageIndex + 1)?.first
    }

    private val _snapshot = MutableStateFlow(read())
    val snapshot: StateFlow<Snapshot> = _snapshot

    // ------------------------------------------------------------- habits

    fun toggleHabit(id: String) {
        tickIfNeeded()
        val category = loadHabits().firstOrNull { it.id == id }?.category ?: "garden"
        val done = loadDone().toMutableSet()
        val delta: Int
        if (done.contains(id)) {
            done.remove(id); delta = -HABIT_POINTS
        } else {
            done.add(id); delta = HABIT_POINTS
        }
        val points = (prefs.getInt(KEY_POINTS, 0) + delta).coerceAtLeast(0)
        // The habit's category grows its own structure in the world.
        val cat = loadCat().toMutableMap()
        cat[category] = ((cat[category] ?: 0) + delta).coerceAtLeast(0)
        // Tending the world today gives it a little life back immediately.
        val health = (prefs.getInt(KEY_HEALTH, 70) + if (delta > 0) 3 else -3).coerceIn(0, 100)
        prefs.edit()
            .putInt(KEY_POINTS, points)
            .putInt(KEY_HEALTH, health)
            .putStringSet(KEY_DONE, done)
            .putInt(KEY_DONE_DAY, today())
            .putString(KEY_CAT, encodeCat(cat))
            .apply()
        refresh()
    }

    fun addHabit(emoji: String, name: String, category: String = "garden") {
        val habits = loadHabits().toMutableList()
        if (habits.size >= 12) return
        habits += Habit(java.util.UUID.randomUUID().toString(), emoji, name.trim(), category)
        saveHabits(habits)
        refresh()
    }

    fun removeHabit(id: String) {
        saveHabits(loadHabits().filterNot { it.id == id })
        val done = loadDone().toMutableSet().also { it.remove(id) }
        prefs.edit().putStringSet(KEY_DONE, done).apply()
        refresh()
    }

    // ------------------------------------------------------- expeditions

    /** Send a villager exploring. One trip at a time; needs a habit done today. */
    fun startExpedition(typeId: String) {
        tickIfNeeded()
        if (loadExpedition() != null) return
        val today = prefs.getInt(KEY_DONE_DAY, 0) == today() && loadDone().isNotEmpty()
        if (!today) return
        val type = EXPEDITIONS.firstOrNull { it.id == typeId } ?: return
        val fresh = type.rewards.filterNot { loadDecor().contains(it) }
        val reward = (fresh.ifEmpty { type.rewards }).random()
        val exp = Expedition(type.id, type.emoji, type.name, System.currentTimeMillis() + type.minutes * 60_000L, reward)
        prefs.edit().putString(KEY_EXP, encodeExp(exp)).apply()
        refresh()
    }

    /** Collect a returned villager. Adds the decoration (or points if a duplicate). */
    fun collectExpedition(): String? {
        val exp = loadExpedition() ?: return null
        if (System.currentTimeMillis() < exp.endsAt) return null
        val decor = loadDecor().toMutableSet()
        val isNew = decor.add(exp.reward)
        val e = prefs.edit().remove(KEY_EXP)
        if (isNew) e.putStringSet(KEY_DECOR, decor)
        else e.putInt(KEY_POINTS, prefs.getInt(KEY_POINTS, 0) + 15)
        e.apply()
        refresh()
        return if (isNew) exp.reward else null
    }

    private fun loadExpedition(): Expedition? = runCatching {
        val s = prefs.getString(KEY_EXP, null) ?: return null
        val o = JSONObject(s)
        Expedition(o.getString("t"), o.getString("e"), o.getString("n"), o.getLong("end"), o.getString("r"))
    }.getOrNull()

    private fun encodeExp(e: Expedition): String = JSONObject()
        .put("t", e.type).put("e", e.emoji).put("n", e.name).put("end", e.endsAt).put("r", e.reward).toString()

    private fun loadDecor(): Set<String> = prefs.getStringSet(KEY_DECOR, emptySet()) ?: emptySet()

    // --------------------------------------------------------- simulation

    /** Advance the farm for every whole day that has passed since we last did. */
    private fun tickIfNeeded() {
        val last = prefs.getInt(KEY_TICK_DAY, 0)
        val now = today()
        if (last == 0) { prefs.edit().putInt(KEY_TICK_DAY, now).apply(); return }
        if (now <= last) return

        var health = prefs.getInt(KEY_HEALTH, 70)
        var points = prefs.getInt(KEY_POINTS, 0)
        var plots = loadPlots().toMutableList()
        var streak = prefs.getInt(KEY_STREAK, 0)
        val doneDay = prefs.getInt(KEY_DONE_DAY, 0)
        val doneCount = if (doneDay == last) loadDone().size else 0

        // The day that just ended, plus any fully-skipped days, resolve now.
        val elapsed = (now - last).coerceAtMost(30)
        for (d in 0 until elapsed) {
            val good = d == 0 && doneCount > 0     // only the most recent finished day had check-ins
            if (good) {
                health = (health + 12).coerceIn(0, 100)
                points += PASSIVE_POINTS + doneCount * 2
                streak++
                growOne(plots)
            } else {
                health = (health - 16).coerceIn(0, 100)
                streak = 0
                if (health < 45) witherOne(plots)
            }
            // A fully bloomed plot harvests itself and replants.
            for (i in plots.indices) if (plots[i] >= 3) { points += HARVEST_POINTS; plots[i] = 1 }
        }

        // Unlock new plots as the level climbs.
        val level = 1 + points / LEVEL_POINTS
        val want = (2 + level).coerceAtMost(GRID)
        while (plots.size < want) plots.add(0)

        prefs.edit()
            .putInt(KEY_HEALTH, health)
            .putInt(KEY_POINTS, points)
            .putString(KEY_PLOTS, plots.joinToString(","))
            .putInt(KEY_STREAK, streak)
            .putInt(KEY_TICK_DAY, now)
            .putStringSet(KEY_DONE, emptySet())      // fresh day
            .putInt(KEY_DONE_DAY, now)
            .apply()
    }

    private fun growOne(plots: MutableList<Int>) {
        val candidates = plots.indices.filter { plots[it] < 3 }
        if (candidates.isNotEmpty()) {
            val i = candidates[Random.nextInt(candidates.size)]
            plots[i] = plots[i] + 1
        }
    }

    private fun witherOne(plots: MutableList<Int>) {
        val candidates = plots.indices.filter { plots[it] > 0 }
        if (candidates.isNotEmpty()) {
            val i = candidates[Random.nextInt(candidates.size)]
            plots[i] = plots[i] - 1
        }
    }

    // -------------------------------------------------------- persistence

    private fun refresh() { _snapshot.value = read() }

    private fun read(): Snapshot {
        tickIfNeeded()
        // Seed a few starter habits on first ever read.
        if (!prefs.getBoolean(KEY_SEEDED, false)) {
            saveHabits(DEFAULT_HABITS)
            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
        }
        return Snapshot(
            points = prefs.getInt(KEY_POINTS, 0),
            health = prefs.getInt(KEY_HEALTH, 70),
            plots = loadPlots(),
            habits = loadHabits(),
            doneToday = if (prefs.getInt(KEY_DONE_DAY, 0) == today()) loadDone() else emptySet(),
            goodStreak = prefs.getInt(KEY_STREAK, 0),
            categoryPoints = loadCat(),
            activeExpedition = loadExpedition(),
            decor = loadDecor(),
        )
    }

    private fun loadCat(): Map<String, Int> = runCatching {
        val o = JSONObject(prefs.getString(KEY_CAT, "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())

    private fun encodeCat(map: Map<String, Int>): String {
        val o = JSONObject()
        for ((k, v) in map) o.put(k, v)
        return o.toString()
    }

    private fun loadPlots(): List<Int> =
        (prefs.getString(KEY_PLOTS, null) ?: "0,0,0")
            .split(",").mapNotNull { it.trim().toIntOrNull() }

    private fun loadDone(): Set<String> =
        prefs.getStringSet(KEY_DONE, emptySet()) ?: emptySet()

    private fun loadHabits(): List<Habit> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_HABITS, "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Habit(o.getString("id"), o.getString("e"), o.getString("n"), o.optString("c", "garden"))
        }
    }.getOrDefault(emptyList())

    private fun saveHabits(habits: List<Habit>) {
        val arr = JSONArray()
        for (h in habits) arr.put(JSONObject().put("id", h.id).put("e", h.emoji).put("n", h.name).put("c", h.category))
        prefs.edit().putString(KEY_HABITS, arr.toString()).apply()
    }

    private fun today(): Int = PactState.dayKey(System.currentTimeMillis())

    companion object {
        const val LEVEL_POINTS = 120
        const val HABIT_POINTS = 10
        const val PASSIVE_POINTS = 6
        const val HARVEST_POINTS = 20
        const val GRID = 12
        const val STRUCTURE_POINTS = 40   // points to raise a structure one level

        /** Every category grows one specific structure in your world. */
        val CATEGORIES = listOf(
            Category("garden", "🌱", "Garden"),
            Category("gym", "🏋️", "Gym"),
            Category("library", "📚", "Library"),
            Category("temple", "🧘", "Temple"),
            Category("forest", "🌳", "Forest"),
            Category("workshop", "💻", "Workshop"),
            Category("bakery", "🍳", "Bakery"),
            Category("school", "🗣️", "School"),
            Category("moon", "😴", "Moon Garden"),
        )
        fun category(id: String): Category = CATEGORIES.firstOrNull { it.id == id } ?: CATEGORIES.first()

        /** The world's eras — reached purely by building, never by spending. */
        val STAGES = listOf(
            0 to "Clearing",
            80 to "Garden",
            220 to "Farm",
            480 to "Village",
            900 to "Town",
            1600 to "City",
            2800 to "Nature Reserve",
            4500 to "Floating Isles",
            7000 to "Fantasy Kingdom",
        )

        /** Trips a villager can take. Longer trips bring home rarer decorations. */
        val EXPEDITIONS = listOf(
            ExpeditionType("meadow", "🌾", "Meadow walk", 30, listOf("topiary", "lantern")),
            ExpeditionType("forest", "🌲", "Forest trek", 120, listOf("well", "statue")),
            ExpeditionType("peaks", "⛰️", "Mountain climb", 300, listOf("fountain", "banner")),
        )

        /** Every cosmetic a villager can bring back: id → emoji + friendly name. */
        val DECOR = listOf(
            Triple("topiary", "🌳", "Topiary"),
            Triple("lantern", "🏮", "Lantern"),
            Triple("well", "🪣", "Wishing well"),
            Triple("statue", "🗿", "Stone statue"),
            Triple("fountain", "⛲", "Fountain"),
            Triple("banner", "🚩", "Banner"),
        )
        fun decorName(id: String): String = DECOR.firstOrNull { it.first == id }?.let { "${it.second} ${it.third}" } ?: id

        val DEFAULT_HABITS = listOf(
            Habit("h_water", "💧", "Drink water", "garden"),
            Habit("h_move", "🏃", "Move my body", "gym"),
            Habit("h_read", "📖", "Read 10 min", "library"),
            Habit("h_sleep", "😴", "Sleep early", "moon"),
        )

        private const val KEY_POINTS = "points"
        private const val KEY_HEALTH = "health"
        private const val KEY_PLOTS = "plots"
        private const val KEY_HABITS = "habits"
        private const val KEY_DONE = "done_today"
        private const val KEY_DONE_DAY = "done_day"
        private const val KEY_TICK_DAY = "tick_day"
        private const val KEY_STREAK = "good_streak"
        private const val KEY_SEEDED = "seeded"
        private const val KEY_CAT = "category_points"
        private const val KEY_EXP = "expedition"
        private const val KEY_DECOR = "decor"

        @Volatile private var instance: FarmState? = null
        fun get(context: Context): FarmState =
            instance ?: synchronized(this) { instance ?: FarmState(context).also { instance = it } }
    }
}
