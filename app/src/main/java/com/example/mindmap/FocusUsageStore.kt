package com.example.mindmap

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class FocusSession(
    val pkg: String,
    val name: String,
    val start: Long,
    val planned: Long,
    val end: Long // 0 = session ekhono open
)

data class FocusAppDayUsage(val pkg: String, val name: String, val millis: Long, val count: Int)

object FocusUsageStore {
    const val HISTORY_DAYS = 30
    private const val PREFS = "focus_usage_prefs"
    private const val KEY = "sessions"

    private var cache: MutableList<FocusSession>? = null

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(ctx: Context): MutableList<FocusSession> {
        cache?.let { return it }
        val list: MutableList<FocusSession> = runCatching {
            val arr = JSONArray(prefs(ctx).getString(KEY, "[]") ?: "[]")
            val out = mutableListOf<FocusSession>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    FocusSession(
                        pkg = o.getString("pkg"),
                        name = o.optString("name"),
                        start = o.getLong("start"),
                        planned = o.optLong("planned"),
                        end = o.optLong("end", 0L)
                    )
                )
            }
            out
        }.getOrElse { mutableListOf() }
        cache = list
        return list
    }

    private fun save(ctx: Context, list: List<FocusSession>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("pkg", it.pkg)
                    .put("name", it.name)
                    .put("start", it.start)
                    .put("planned", it.planned)
                    .put("end", it.end)
            )
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    private fun effectiveEnd(s: FocusSession, now: Long): Long =
        if (s.end > 0L) s.end else minOf(now, maxOf(s.planned, s.start))

    private fun windowStartMs(zone: ZoneId): Long =
        LocalDate.now(zone).minusDays(HISTORY_DAYS - 1L).atStartOfDay(zone).toInstant().toEpochMilli()

    /** 30 diner age-r record gulo muche dey. Cholte thaka/recent session kokhono muche na. */
    private fun prune(list: MutableList<FocusSession>, now: Long): Boolean {
        val cutoff = windowStartMs(ZoneId.systemDefault())
        return list.removeAll { effectiveEnd(it, now) < cutoff }
    }

    private fun closeOpen(list: MutableList<FocusSession>, pkg: String, at: Long): Boolean {
        var changed = false
        for (i in list.indices) {
            val s = list[i]
            if (s.pkg == pkg && s.end == 0L) {
                val capped = minOf(maxOf(at, s.start), maxOf(s.planned, s.start))
                list[i] = s.copy(end = capped)
                changed = true
            }
        }
        return changed
    }

    /** Notun block activation = 1 ta session. */
    @Synchronized
    fun recordStart(ctx: Context, cfg: FocusAppConfig) {
        val list = load(ctx)
        closeOpen(list, cfg.packageName, cfg.startAt)
        list.add(FocusSession(cfg.packageName, cfg.appName, cfg.startAt, cfg.expiresAt, 0L))
        prune(list, System.currentTimeMillis())
        save(ctx, list)
    }

    /** Block off/expire hole session bondho kore (planned-er beshi hoy na). */
    @Synchronized
    fun recordEnd(ctx: Context, pkg: String, at: Long) {
        val list = load(ctx)
        if (closeOpen(list, pkg, at)) save(ctx, list)
    }

    /** Date -> (app-wise usage). Shob 30 din thake, activity na thakle list faka. */
    @Synchronized
    fun compute(ctx: Context, now: Long = System.currentTimeMillis()): Map<LocalDate, List<FocusAppDayUsage>> {
        val zone = ZoneId.systemDefault()
        val list = load(ctx)
        if (prune(list, now)) save(ctx, list)

        val today = LocalDate.now(zone)
        val firstDay = today.minusDays(HISTORY_DAYS - 1L)
        val ms = HashMap<Pair<LocalDate, String>, Long>()
        val cnt = HashMap<Pair<LocalDate, String>, Int>()
        val names = LinkedHashMap<String, String>()

        for (s in list) {
            val end = effectiveEnd(s, now)
            names[s.pkg] = s.name
            val startDay = Instant.ofEpochMilli(s.start).atZone(zone).toLocalDate()
            if (!startDay.isBefore(firstDay) && !startDay.isAfter(today)) {
                val k = startDay to s.pkg
                cnt[k] = (cnt[k] ?: 0) + 1
            }
            if (end <= s.start) continue
            var day = startDay
            while (!day.isAfter(today)) {
                val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
                if (dayStart >= end) break
                val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val overlap = minOf(end, dayEnd) - maxOf(s.start, dayStart)
                if (overlap > 0L && !day.isBefore(firstDay)) {
                    val k = day to s.pkg
                    ms[k] = (ms[k] ?: 0L) + overlap
                }
                day = day.plusDays(1)
            }
        }

        val result = LinkedHashMap<LocalDate, List<FocusAppDayUsage>>()
        var d = firstDay
        while (!d.isAfter(today)) {
            val day = d
            result[day] = names.keys.mapNotNull { pkg ->
                val m = ms[day to pkg] ?: 0L
                val c = cnt[day to pkg] ?: 0
                if (m > 0L || c > 0) {
                    FocusAppDayUsage(pkg, names[pkg].orEmpty().ifBlank { pkg }, m, c)
                } else null
            }.sortedByDescending { it.millis }
            d = d.plusDays(1)
        }
        return result
    }
}