package com.ironlog.app.progression

/** Week layout of a training block: accumulation weeks with a falling RIR, then a deload. */
object Periodization {
  const val MIN_WEEKS = 4
  const val MAX_WEEKS = 8

  /** Reps in reserve written on deload sets: "very easy", well short of failure. */
  const val DELOAD_RIR = 5

  /**
   * Reps-in-reserve target for each accumulation week of a block of [weeks] weeks (deload included).
   * Ends at 0 in the last hard week; longer blocks spend more weeks at 3 and 2.
   */
  fun rirSchedule(weeks: Int): List<Int> =
    when (weeks.coerceIn(MIN_WEEKS, MAX_WEEKS)) {
      4 -> listOf(2, 1, 0)
      5 -> listOf(3, 2, 1, 0)
      6 -> listOf(3, 2, 2, 1, 0)
      7 -> listOf(3, 3, 2, 2, 1, 0)
      else -> listOf(3, 3, 2, 2, 1, 1, 0)
    }

  fun encode(schedule: List<Int>): String = schedule.joinToString(",")

  fun decode(text: String): List<Int> = text.split(',').mapNotNull { it.trim().toIntOrNull() }

  fun isDeload(week: Int, weeks: Int): Boolean = week == weeks - 1

  /** RIR target for [week] (0-based); the deload week gets [DELOAD_RIR]. */
  fun rirFor(week: Int, weeks: Int, schedule: List<Int>): Int =
    if (isDeload(week, weeks)) DELOAD_RIR else schedule.getOrNull(week) ?: schedule.lastOrNull() ?: Strength.DEFAULT_RIR

  /**
   * Lifts where a failed rep can pin you (barbell squats and presses) stop one rep short even in
   * the hardest week.
   */
  fun safeRir(rir: Int, traits: ExerciseTraits): Int =
    if (rir == 0 && traits.loadKind == LoadKind.BARBELL && traits.pattern in setOf(Pattern.SQUAT, Pattern.HORIZONTAL_PUSH, Pattern.VERTICAL_PUSH, Pattern.LUNGE)) 1 else rir

  /** Small muscles skip the deload entirely so it frees as much recovery as possible. */
  fun restsInDeload(muscle: TrainingMuscle?): Boolean = muscle == TrainingMuscle.TRAPS || muscle == TrainingMuscle.FOREARMS

  /** Deload volume for a slot: about half its last hard week, at least one set. */
  fun deloadSets(lastAccumulationSets: Int, muscle: TrainingMuscle?): Int =
    if (restsInDeload(muscle)) 0 else ((lastAccumulationSets + 1) / 2).coerceAtLeast(1)

  /** A sensible default block length for an experience level. */
  fun defaultWeeks(experience: Experience): Int =
    when (experience) {
      Experience.BEGINNER -> 5
      Experience.INTERMEDIATE -> 5
      Experience.ADVANCED -> 4
    }
}
