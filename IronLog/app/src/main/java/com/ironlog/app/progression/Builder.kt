package com.ironlog.app.progression

/**
 * Turns "how many days" and "which muscles matter" into a weekly split: how often each muscle is
 * trained, how many exercises it gets, and on which days, spaced for recovery.
 */
object Builder {
  data class SlotPlan(val muscle: TrainingMuscle, val sets: Int)

  data class DayPlan(val weekday: Int, val slots: List<SlotPlan>) {
    val name: String
      get() = dayName(slots.map { it.muscle })
  }

  /** Training weekdays (0 = Monday) for each frequency. */
  fun skeleton(days: Int): List<Int> =
    when (days.coerceIn(2, 6)) {
      2 -> listOf(0, 3)
      3 -> listOf(0, 2, 4)
      4 -> listOf(0, 1, 3, 4)
      5 -> listOf(0, 1, 2, 3, 4)
      else -> listOf(0, 1, 2, 3, 4, 5)
    }

  /** Sessions per week for a muscle: faster-recovering muscles and higher priorities train more often. */
  fun sessions(m: TrainingMuscle, p: Priority, days: Int): Int {
    val base =
      when (p) {
        Priority.EMPHASIZE ->
          when (m.recovery) {
            Recovery.FAST -> 4
            Recovery.MODERATE -> 3
            Recovery.SLOW -> 2
          }
        Priority.GROW ->
          when (m.recovery) {
            Recovery.FAST -> 3
            else -> 2
          }
        Priority.MAINTAIN -> if (days <= 3) 1 else 2
      }
    val cap = minOf(days, m.maxSessions)
    val floor = if (p == Priority.MAINTAIN) 1 else minOf(m.minSessions, days)
    return base.coerceIn(floor, cap.coerceAtLeast(floor))
  }

  /** Exercises per session: one when a muscle is trained three or more times, else up to two for emphasis. */
  fun slotsPerSession(p: Priority, sessions: Int): Int = if (sessions >= 3 || p != Priority.EMPHASIZE) 1 else 2

  private fun gapPenalty(a: Int, b: Int): Int {
    val d = kotlin.math.abs(a - b)
    val gap = minOf(d, 7 - d)
    return when (gap) {
      1 -> 3
      2 -> 2
      else -> 1
    }
  }

  private fun <T> combinations(items: List<T>, k: Int): List<List<T>> {
    if (k == 0) return listOf(emptyList())
    if (items.size < k) return emptyList()
    val head = items.first()
    val rest = items.drop(1)
    return combinations(rest, k - 1).map { listOf(head) + it } + combinations(rest, k)
  }

  /**
   * Builds the week. [order] lists muscles to train (most important first within each priority);
   * muscles missing from [priorities] are left out.
   */
  fun generate(days: Int, priorities: Map<TrainingMuscle, Priority>, order: List<TrainingMuscle>, experience: Experience): List<DayPlan> {
    val weekdays = skeleton(days)
    val load = weekdays.associateWith { mutableListOf<SlotPlan>() }
    val muscles = order.filter { it in priorities }.sortedBy { priorities.getValue(it).ordinal }
    for (m in muscles) {
      val p = priorities.getValue(m)
      val n = sessions(m, p, weekdays.size)
      val choices = combinations(weekdays, n)
      val best =
        choices.minWithOrNull(
          compareBy<List<Int>> { c -> c.indices.sumOf { i -> (i + 1 until c.size).sumOf { j -> gapPenalty(c[i], c[j]) } } }
            .thenBy { c -> c.sumOf { load.getValue(it).size } }
            .thenBy { c -> c.joinToString() }
        ) ?: continue
      val perSession = Volume.startingSessionSets(p, m, n, experience)
      val slots = slotsPerSession(p, n)
      for (d in best) {
        val each = (perSession / slots).coerceAtLeast(1)
        val extra = perSession - each * slots
        repeat(slots) { i -> load.getValue(d) += SlotPlan(m, each + if (i < extra) 1 else 0) }
      }
    }
    return weekdays.map { d -> DayPlan(d, load.getValue(d).sortedWith(compareBy({ priorities.getValue(it.muscle).ordinal }, { sizeRank(it.muscle) }))) }.filter { it.slots.isNotEmpty() }
  }

  /** Big muscles first within a day so heavy compounds come while fresh. */
  private fun sizeRank(m: TrainingMuscle): Int =
    when (m) {
      TrainingMuscle.QUADS,
      TrainingMuscle.BACK,
      TrainingMuscle.CHEST,
      TrainingMuscle.HAMSTRINGS,
      TrainingMuscle.GLUTES -> 0
      TrainingMuscle.SHOULDERS,
      TrainingMuscle.TRICEPS,
      TrainingMuscle.BICEPS -> 1
      else -> 2
    }

  private val push = setOf(TrainingMuscle.CHEST, TrainingMuscle.SHOULDERS, TrainingMuscle.TRICEPS)
  private val pull = setOf(TrainingMuscle.BACK, TrainingMuscle.BICEPS, TrainingMuscle.TRAPS, TrainingMuscle.FOREARMS)
  private val legs = setOf(TrainingMuscle.QUADS, TrainingMuscle.HAMSTRINGS, TrainingMuscle.GLUTES, TrainingMuscle.CALVES, TrainingMuscle.ADDUCTORS)

  /** A plain name from what a day trains: Push, Pull, Legs, Upper, Lower or Full body. */
  fun dayName(muscles: List<TrainingMuscle>): String {
    val set = muscles.toSet()
    val hasPush = set.any { it in push }
    val hasPull = set.any { it in pull }
    val hasLegs = set.any { it in legs }
    return when {
      hasLegs && (hasPush || hasPull) -> "Full body"
      hasLegs -> "Legs"
      hasPush && hasPull -> "Upper"
      hasPush -> "Push"
      hasPull -> "Pull"
      else -> "Core & extras"
    }
  }

  /** Days with more than this many exercises get a warning. */
  const val MAX_SLOTS_PER_DAY = 9
}
