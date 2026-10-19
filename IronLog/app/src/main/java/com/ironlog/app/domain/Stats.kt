package com.ironlog.app.domain

import com.ironlog.app.model.MuscleGroup
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Per-workout totals, computed once from stored sets. */
data class WorkoutTotals(
  val workoutId: Long,
  val startTime: Long,
  val endTime: Long,
  val volumeKg: Double,
  val reps: Int,
  val sets: Int,
) {
  val durationSec: Long
    get() = ((endTime - startTime) / 1000).coerceAtLeast(0)
}

data class WeekBucket(val weekStart: LocalDate, val workouts: Int, val durationSec: Long, val volumeKg: Double, val reps: Int)

enum class WeeklyMetric(val label: String) {
  DURATION("Duration"),
  VOLUME("Volume"),
  REPS("Reps"),
  WORKOUTS("Workouts"),
}

object Stats {
  fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

  fun localDate(epochMillis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

  /** The last [weeks] Monday-based weeks ending with the week containing [today], oldest first. */
  fun weeklyBuckets(workouts: List<WorkoutTotals>, weeks: Int, today: LocalDate, zone: ZoneId): List<WeekBucket> {
    val currentWeek = weekStart(today)
    val starts = (weeks - 1 downTo 0).map { currentWeek.minusWeeks(it.toLong()) }
    val byWeek = workouts.groupBy { weekStart(localDate(it.startTime, zone)) }
    return starts.map { start ->
      val ws = byWeek[start].orEmpty()
      WeekBucket(start, ws.size, ws.sumOf { it.durationSec }, ws.sumOf { it.volumeKg }, ws.sumOf { it.reps })
    }
  }

  fun metricValue(bucket: WeekBucket, metric: WeeklyMetric): Double =
    when (metric) {
      WeeklyMetric.DURATION -> bucket.durationSec / 3600.0
      WeeklyMetric.VOLUME -> bucket.volumeKg
      WeeklyMetric.REPS -> bucket.reps.toDouble()
      WeeklyMetric.WORKOUTS -> bucket.workouts.toDouble()
    }

  /**
   * Consecutive weeks with at least one workout, counting back from this week. An empty current
   * week doesn't break the streak yet — the week isn't over.
   */
  fun weekStreak(workoutDates: Collection<LocalDate>, today: LocalDate): Int {
    val weeks = workoutDates.map { weekStart(it) }.toSet()
    var cursor = weekStart(today)
    if (cursor !in weeks) cursor = cursor.minusWeeks(1)
    var streak = 0
    while (cursor in weeks) {
      streak++
      cursor = cursor.minusWeeks(1)
    }
    return streak
  }

  /**
   * Sets per muscle group: a set counts 1 for the exercise's primary muscle and 0.5 for each
   * secondary muscle. Sorted descending.
   */
  fun muscleSetCounts(sets: List<Pair<MuscleGroup, List<MuscleGroup>>>): List<Pair<MuscleGroup, Double>> {
    val counts = mutableMapOf<MuscleGroup, Double>()
    for ((primary, secondary) in sets) {
      counts[primary] = (counts[primary] ?: 0.0) + 1.0
      for (m in secondary) if (m != primary) counts[m] = (counts[m] ?: 0.0) + 0.5
    }
    return counts.entries.sortedByDescending { it.value }.map { it.key to it.value }
  }

  fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)
}
