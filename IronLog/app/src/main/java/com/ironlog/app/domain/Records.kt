package com.ironlog.app.domain

import com.ironlog.app.model.ExerciseType
import com.ironlog.app.model.SetType

/** The numbers of one performed set, independent of where it is stored. */
data class SetValues(
  val type: SetType = SetType.NORMAL,
  val weightKg: Double? = null,
  val reps: Int? = null,
  val durationSec: Int? = null,
  val distanceM: Double? = null,
)

enum class PrKind(val label: String) {
  WEIGHT("Heaviest weight"),
  ONE_RM("Best est. 1RM"),
  SET_VOLUME("Best set volume"),
  REPS("Most reps"),
  DURATION("Longest duration"),
  DISTANCE("Longest distance"),
}

data class PersonalBests(
  val heaviestWeightKg: Double? = null,
  val bestOneRepMaxKg: Double? = null,
  val bestSetVolumeKg: Double? = null,
  val bestSessionVolumeKg: Double? = null,
  val mostReps: Int? = null,
  val longestDurationSec: Int? = null,
  val longestDistanceM: Double? = null,
) {
  val isEmpty: Boolean
    get() = this == PersonalBests()
}

object Lifting {
  /** Epley estimate. Only meaningful for low/moderate reps, so anything above 20 reps is ignored. */
  fun estimatedOneRepMax(weightKg: Double, reps: Int): Double? =
    when {
      weightKg <= 0 || reps <= 0 || reps > 20 -> null
      reps == 1 -> weightKg
      else -> weightKg * (1 + reps / 30.0)
    }

  fun setVolume(type: ExerciseType, set: SetValues): Double {
    if (!type.countsVolume) return 0.0
    val w = set.weightKg ?: return 0.0
    val r = set.reps ?: return 0.0
    return w * r
  }

  fun prKindsFor(type: ExerciseType): Set<PrKind> =
    when (type) {
      ExerciseType.WEIGHT_REPS,
      ExerciseType.WEIGHTED_BODYWEIGHT -> setOf(PrKind.WEIGHT, PrKind.ONE_RM, PrKind.SET_VOLUME)
      ExerciseType.REPS_ONLY,
      ExerciseType.ASSISTED_BODYWEIGHT -> setOf(PrKind.REPS)
      ExerciseType.DURATION -> setOf(PrKind.DURATION)
      ExerciseType.WEIGHT_DURATION -> setOf(PrKind.WEIGHT, PrKind.DURATION)
      ExerciseType.DISTANCE_DURATION -> setOf(PrKind.DISTANCE, PrKind.DURATION)
      ExerciseType.WEIGHT_DISTANCE -> setOf(PrKind.WEIGHT, PrKind.DISTANCE)
    }

  /** Folds one set into the running bests. Warm-up sets never count. */
  fun include(bests: PersonalBests, type: ExerciseType, set: SetValues): PersonalBests {
    if (set.type == SetType.WARMUP) return bests
    val kinds = prKindsFor(type)
    var b = bests
    if (PrKind.WEIGHT in kinds) set.weightKg?.takeIf { it > 0 }?.let { b = b.copy(heaviestWeightKg = maxOf(it, b.heaviestWeightKg ?: 0.0)) }
    if (PrKind.ONE_RM in kinds) {
      val e = estimatedOneRepMax(set.weightKg ?: 0.0, set.reps ?: 0)
      if (e != null) b = b.copy(bestOneRepMaxKg = maxOf(e, b.bestOneRepMaxKg ?: 0.0))
    }
    if (PrKind.SET_VOLUME in kinds) {
      val v = setVolume(type, set)
      if (v > 0) b = b.copy(bestSetVolumeKg = maxOf(v, b.bestSetVolumeKg ?: 0.0))
    }
    if (PrKind.REPS in kinds) set.reps?.takeIf { it > 0 }?.let { b = b.copy(mostReps = maxOf(it, b.mostReps ?: 0)) }
    if (PrKind.DURATION in kinds) set.durationSec?.takeIf { it > 0 }?.let { b = b.copy(longestDurationSec = maxOf(it, b.longestDurationSec ?: 0)) }
    if (PrKind.DISTANCE in kinds) set.distanceM?.takeIf { it > 0 }?.let { b = b.copy(longestDistanceM = maxOf(it, b.longestDistanceM ?: 0.0)) }
    return b
  }

  /** Bests across past sessions (each inner list is one workout's sets of this exercise). */
  fun personalBests(type: ExerciseType, sessions: List<List<SetValues>>): PersonalBests {
    var bests = PersonalBests()
    for (session in sessions) {
      for (set in session) bests = include(bests, type, set)
      val sessionVolume = session.filter { it.type != SetType.WARMUP }.sumOf { setVolume(type, it) }
      if (sessionVolume > 0) bests = bests.copy(bestSessionVolumeKg = maxOf(sessionVolume, bests.bestSessionVolumeKg ?: 0.0))
    }
    return bests
  }

  /** Which records [set] strictly beats. Nothing counts as a PR the first time an exercise is done. */
  fun prsBeaten(before: PersonalBests, type: ExerciseType, set: SetValues): Set<PrKind> {
    if (set.type == SetType.WARMUP || before.isEmpty) return emptySet()
    val after = include(before, type, set)
    val beaten = mutableSetOf<PrKind>()
    fun beats(new: Number?, old: Number?) = new != null && old != null && new.toDouble() > old.toDouble() + 1e-9
    if (beats(after.heaviestWeightKg, before.heaviestWeightKg)) beaten += PrKind.WEIGHT
    if (beats(after.bestOneRepMaxKg, before.bestOneRepMaxKg)) beaten += PrKind.ONE_RM
    if (beats(after.bestSetVolumeKg, before.bestSetVolumeKg)) beaten += PrKind.SET_VOLUME
    if (beats(after.mostReps, before.mostReps)) beaten += PrKind.REPS
    if (beats(after.longestDurationSec, before.longestDurationSec)) beaten += PrKind.DURATION
    if (beats(after.longestDistanceM, before.longestDistanceM)) beaten += PrKind.DISTANCE
    return beaten
  }

  /**
   * PRs for each set of one exercise in one workout, in order. Each set is compared against the
   * historical bests plus the sets before it, so only the set that actually raised a record gets it.
   */
  fun prsInSession(history: PersonalBests, type: ExerciseType, sets: List<SetValues>): List<Set<PrKind>> {
    var running = history
    return sets.map { set ->
      val prs = prsBeaten(running, type, set)
      if (!running.isEmpty) running = include(running, type, set)
      prs
    }
  }
}
