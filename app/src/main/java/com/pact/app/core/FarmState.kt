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

    data class Habit(val id: String, val emoji: String, val name: String)

    data class Snapshot(
        val points: Int = 0,
        val health: Int = 70,             // 0..100 — how lush the farm looks
        val plots: List<Int> = List(3) { 0 },   // crop stage per plot: 0 soil → 3 bloom
        val habits: List<Habit> = emptyList(),
        val doneToday: Set<String> = emptySet(),
        val goodStreak: Int = 0,          // consecutive good days
    ) {
        val level: Int get() = 1 + points / LEVEL_POINTS
        val animals: Int get() = (level / 2).coerceIn(0, 6)
        val hasBarn: Boolean get() = level >= 3
        val hasHouse: Boolean get() = level >= 5
        val progressInLevel: Float get() = (points % LEVEL_POINTS).toFloat() / LEVEL_POINTS
        fun habitsDoneToday(): Int = doneToday.size
    }

    private val _snapshot = MutableStateFlow(read())
    val snapshot: StateFlow<Snapshot> = _snapshot

    // ------------------------------------------------------------- habits

    fun toggleHabit(id: String) {
        tickIfNeeded()
        val done = loadDone().toMutableSet()
        val delta: Int
        if (done.contains(id)) {
            done.remove(id); delta = -HABIT_POINTS
        } else {
            done.add(id); delta = HABIT_POINTS
        }
        val points = (prefs.getInt(KEY_POINTS, 0) + delta).coerceAtLeast(0)
        // Tending the farm today gives it a little life back immediately.
        val health = (prefs.getInt(KEY_HEALTH, 70) + if (delta > 0) 3 else -3).coerceIn(0, 100)
        prefs.edit()
            .putInt(KEY_POINTS, points)
            .putInt(KEY_HEALTH, health)
            .putStringSet(KEY_DONE, done)
            .putInt(KEY_DONE_DAY, today())
            .apply()
        refresh()
    }

    fun addHabit(emoji: String, name: String) {
        val habits = loadHabits().toMutableList()
        if (habits.size >= 12) return
        habits += Habit(java.util.UUID.randomUUID().toString(), emoji, name.trim())
        saveHabits(habits)
        refresh()
    }

    fun removeHabit(id: String) {
        saveHabits(loadHabits().filterNot { it.id == id })
        val done = loadDone().toMutableSet().also { it.remove(id) }
        prefs.edit().putStringSet(KEY_DONE, done).apply()
        refresh()
    }

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
        )
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
            Habit(o.getString("id"), o.getString("e"), o.getString("n"))
        }
    }.getOrDefault(emptyList())

    private fun saveHabits(habits: List<Habit>) {
        val arr = JSONArray()
        for (h in habits) arr.put(JSONObject().put("id", h.id).put("e", h.emoji).put("n", h.name))
        prefs.edit().putString(KEY_HABITS, arr.toString()).apply()
    }

    private fun today(): Int = PactState.dayKey(System.currentTimeMillis())

    companion object {
        const val LEVEL_POINTS = 120
        const val HABIT_POINTS = 10
        const val PASSIVE_POINTS = 6
        const val HARVEST_POINTS = 20
        const val GRID = 12

        val DEFAULT_HABITS = listOf(
            Habit("h_water", "💧", "Drink water"),
            Habit("h_move", "🏃", "Move my body"),
            Habit("h_read", "📖", "Read 10 min"),
            Habit("h_sleep", "😴", "Sleep early"),
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

        @Volatile private var instance: FarmState? = null
        fun get(context: Context): FarmState =
            instance ?: synchronized(this) { instance ?: FarmState(context).also { instance = it } }
    }
}
