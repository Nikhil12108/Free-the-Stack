package com.ironlog.app.progression

import com.ironlog.app.data.db.ExerciseEntity
import com.ironlog.app.model.Equipment

/** What IronLog knows about how an exercise has gone for this lifter. */
data class ExerciseHistoryFacts(val timesDone: Int = 0, val lastJointPain: Int? = null)

/**
 * Ranks exercises for one slot of a plan. Deterministic: the same inputs give the same order, and
 * "another one" simply moves down the list. Compound free-weight lifts are favoured early in the week
 * and early in a session; variety, familiarity and joint comfort shift the order.
 */
object Suggest {
  data class Slot(
    val muscle: TrainingMuscle,
    val dayIndex: Int,
    val days: Int,
    val slotIndex: Int,
    val slots: Int,
    /** Exercises already chosen for this day. */
    val usedToday: Set<Long>,
    /** Exercises already chosen elsewhere in the week. */
    val usedThisWeek: Set<Long>,
    /** Movement patterns already used for this muscle this week. */
    val patternsForMuscle: Set<Pattern>,
    /** Whether the day already has a heavy whole-body lift. */
    val heavyToday: Boolean,
  )

  fun rank(
    slot: Slot,
    exercises: List<ExerciseEntity>,
    facts: Map<Long, ExerciseHistoryFacts>,
    equipment: Set<Equipment>,
  ): List<ExerciseEntity> {
    val early = (1.5 - slot.dayIndex.toDouble() / slot.days.coerceAtLeast(1)) * (1.5 - slot.slotIndex.toDouble() / slot.slots.coerceAtLeast(1))
    return exercises
      .asSequence()
      .filter { it.id !in slot.usedToday && it.equipment in equipment }
      .map { it to ExerciseTraits.of(it) }
      .filter { (_, t) -> t.muscle == slot.muscle }
      .map { (e, t) -> e to score(e, t, slot, early, facts[e.id] ?: ExerciseHistoryFacts()) }
      .filter { it.second > 0 }
      .sortedWith(compareByDescending<Pair<ExerciseEntity, Double>> { it.second }.thenBy { it.first.name })
      .map { it.first }
      .toList()
  }

  private fun score(e: ExerciseEntity, t: ExerciseTraits, slot: Slot, early: Double, f: ExerciseHistoryFacts): Double {
    var s = 1.0
    // Compounds early, isolation later: early ranges ~0.25 (late) to ~2.25 (first slot, first day).
    s *= if (t.isCompound) 0.7 + 0.4 * early else 1.3 - 0.2 * early
    s *=
      when (e.equipment) {
        Equipment.BARBELL -> 1.0 + 0.10 * early
        Equipment.DUMBBELL -> 1.0 + 0.06 * early
        Equipment.MACHINE,
        Equipment.CABLE -> 1.0 + 0.08 * (1.5 - early).coerceAtLeast(0.0)
        else -> 0.9
      }
    // Loadable exercises progress best; pure bodyweight and timed holds come later in the list.
    s *=
      when (e.type) {
        com.ironlog.app.model.ExerciseType.WEIGHT_REPS -> 1.0
        com.ironlog.app.model.ExerciseType.WEIGHTED_BODYWEIGHT,
        com.ironlog.app.model.ExerciseType.ASSISTED_BODYWEIGHT -> 0.9
        com.ironlog.app.model.ExerciseType.REPS_ONLY -> 0.6
        else -> 0.3
      }
    if (t.pattern in slot.patternsForMuscle) s *= 0.5
    if (e.id in slot.usedThisWeek) s *= 0.7
    if (slot.heavyToday && t.systemicCost >= 3) s *= 0.3
    if (f.timesDone > 0) s *= 1.2
    s *=
      when (f.lastJointPain) {
        3 -> 0.2
        2 -> 0.5
        else -> 1.0
      }
    return s
  }

  /** Every exercise that can work a plan slot in this gym, best first. */
  fun forMuscle(muscle: TrainingMuscle, exercises: List<ExerciseEntity>, equipment: Set<Equipment>): List<ExerciseEntity> =
    exercises.filter { it.equipment in equipment && ExerciseTraits.of(it).muscle == muscle }.sortedBy { it.name }
}
