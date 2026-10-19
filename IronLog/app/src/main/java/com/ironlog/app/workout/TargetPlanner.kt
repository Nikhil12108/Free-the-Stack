package com.ironlog.app.workout

import com.ironlog.app.data.Settings
import com.ironlog.app.domain.Units
import com.ironlog.app.model.ExerciseType
import com.ironlog.app.model.SetType
import com.ironlog.app.progression.EquipmentProfile
import com.ironlog.app.progression.ExerciseTraits
import com.ironlog.app.progression.HistorySession
import com.ironlog.app.progression.LoadKind
import com.ironlog.app.progression.LoggedSet
import com.ironlog.app.progression.ProgressionRule
import com.ironlog.app.progression.Strength
import com.ironlog.app.progression.TargetMode
import com.ironlog.app.progression.TargetRequest
import com.ironlog.app.progression.Targets
import kotlin.math.abs

/** Bridges the live workout model and the progression engine. */
object TargetPlanner {
  private val supported = setOf(ExerciseType.WEIGHT_REPS, ExerciseType.WEIGHTED_BODYWEIGHT, ExerciseType.ASSISTED_BODYWEIGHT, ExerciseType.REPS_ONLY)

  fun supports(type: ExerciseType) = type in supported

  /** The user's equipment with the bar from the bar-weight setting, in the display unit. */
  fun profile(settings: Settings): EquipmentProfile = settings.equipment.copy(unit = settings.weightUnit, bar = Units.toDisplayWeight(settings.barWeightKg, settings.weightUnit))

  fun traits(ex: SessionExercise): ExerciseTraits = ExerciseTraits.of(ex.name, ex.type, ex.equipment, ex.primaryMuscle)

  /** The working sets' rep range: as written in the routine, else a sensible default for the movement. */
  fun repRange(ex: SessionExercise, traits: ExerciseTraits = traits(ex)): IntRange {
    val w = ex.sets.firstOrNull { it.type.isWorking }
    val min = w?.repMin
    val max = w?.repMax
    val exact = w?.targetReps?.takeIf { !ex.targetsReady } ?: w?.reps
    return when {
      min != null && max != null && max >= min -> min..max
      exact != null && exact > 0 -> exact..(exact + 2)
      else -> Targets.defaultRange(traits)
    }
  }

  /**
   * Works out targets for [ex]'s working sets from [history] (newest first). Values the user has typed
   * are never touched; only the target fields and the reason change.
   */
  fun apply(
    ex: SessionExercise,
    history: List<HistorySession>,
    bodyweightKg: Double?,
    settings: Settings,
    nowMillis: Long,
    mode: TargetMode,
    weekRir: Int?,
    deloadReference: List<LoggedSet> = emptyList(),
  ): SessionExercise {
    if (!supports(ex.type) || !settings.smartTargets) return ex.copy(targetsReady = true)
    val traits = traits(ex)
    val range = repRange(ex, traits)
    val rir = weekRir ?: ex.targetRir ?: settings.defaultRir
    val working = ex.sets.withIndex().filter { it.value.type.isWorking }
    val result =
      Targets.compute(
        TargetRequest(
          type = ex.type,
          traits = traits,
          rule = ex.rule ?: ProgressionRule.SMART,
          repMin = range.first,
          repMax = range.last,
          rir = rir,
          workingSets = working.size,
          history = history,
          nowMillis = nowMillis,
          bodyweightKg = bodyweightKg,
          profile = profile(settings),
          experience = settings.experience,
          mode = if (ex.recovery) TargetMode.RECOVERY else mode,
          fallbackWeightsKg = working.map { it.value.targetWeightKg ?: it.value.weightKg },
          deloadReference = deloadReference,
        )
      )
    val byIndex = working.map { it.index }.zip(result.sets).toMap()
    val sets =
      ex.sets.mapIndexed { i, s ->
        // A ticked set keeps the target it was done against; only sets still to do are re-aimed.
        val t = byIndex[i]?.takeUnless { s.completed } ?: return@mapIndexed s
        s.copy(targetWeightKg = t.weightKg, targetReps = t.reps ?: s.targetReps.takeIf { result.sets.all { it.reps == null } }, targetRir = t.rir, repMin = s.repMin ?: range.first, repMax = s.repMax ?: range.last)
      }
    return ex.copy(sets = sets, reason = result.reason, targetsReady = true)
  }

  /** Load for the strength model: bodyweight plus or minus what was entered, for bodyweight exercises. */
  fun totalLoad(type: ExerciseType, weightKg: Double?, bodyweightKg: Double?): Double? =
    when (type) {
      ExerciseType.WEIGHTED_BODYWEIGHT -> bodyweightKg?.let { it + (weightKg ?: 0.0) }
      ExerciseType.ASSISTED_BODYWEIGHT -> bodyweightKg?.let { it - (weightKg ?: 0.0) }
      ExerciseType.REPS_ONLY -> bodyweightKg
      else -> weightKg
    }

  /**
   * The reps to aim for, re-matched when the lifter typed a different weight than the target: same
   * effort at the new load. Beyond a 20 % change the estimate is too rough, so it falls back to the target.
   */
  fun effectiveTargetReps(set: SessionSet, type: ExerciseType, bodyweightKg: Double?): Int? {
    val reps = set.targetReps ?: return null
    val target = set.targetWeightKg ?: return reps
    val chosen = set.weightKg ?: return reps
    if (abs(chosen - target) < 1e-6) return reps
    val tTotal = totalLoad(type, target, bodyweightKg) ?: return reps
    val cTotal = totalLoad(type, chosen, bodyweightKg) ?: return reps
    if (tTotal <= 0 || cTotal <= 0 || abs(cTotal / tTotal - 1) > 0.2) return reps
    return Strength.rematchReps(tTotal, reps, set.targetRir ?: Strength.DEFAULT_RIR, cTotal)
  }

  fun logged(s: SessionSet): LoggedSet = LoggedSet(s.type, s.weightKg, s.reps, s.rir, s.rpe, s.targetReps, s.targetRir)

  /** Moves pre-filled numbers into the target fields so they show as suggestions, not as logged values. */
  fun asSuggestions(exercises: List<SessionExercise>): List<SessionExercise> =
    exercises.map { ex ->
      if (!supports(ex.type)) ex
      else ex.copy(sets = ex.sets.map { s -> if (s.type == SetType.WARMUP) s else s.copy(weightKg = null, reps = null, targetWeightKg = s.weightKg ?: s.targetWeightKg, targetReps = s.reps ?: s.targetReps) })
    }

  fun loadKind(ex: SessionExercise): LoadKind = traits(ex).loadKind

  /** Rest for a newly added exercise: by movement when that setting is on (and rest isn't switched off). */
  fun restFor(exercise: com.ironlog.app.data.db.ExerciseEntity, settings: Settings): Int =
    if (settings.restByMovement && settings.defaultRestSeconds > 0) com.ironlog.app.progression.Rest.seconds(ExerciseTraits.of(exercise)) else settings.defaultRestSeconds

  /**
   * Adds warm-up sets before [ex] when it has a working weight to ramp toward and none yet. [firstForMuscle]
   * is true when no earlier exercise in the session trains the same muscle.
   */
  fun withWarmups(ex: SessionExercise, firstForMuscle: Boolean, settings: Settings): SessionExercise {
    if (ex.type != ExerciseType.WEIGHT_REPS || ex.sets.any { it.type == SetType.WARMUP }) return ex
    val work = ex.sets.firstOrNull { it.type.isWorking }?.let { it.weightKg ?: it.targetWeightKg } ?: return ex
    val traits = traits(ex)
    val unit = settings.weightUnit
    val profile = profile(settings)
    val ladder = com.ironlog.app.progression.Loads.ladder(traits.loadKind, profile)
    val ramp =
      com.ironlog.app.progression.Warmups.ramp(
        Units.toDisplayWeight(work, unit),
        ladder,
        traits.isCompound,
        firstForMuscle,
        if (traits.loadKind == LoadKind.BARBELL) profile.bar else null,
      )
    if (ramp.isEmpty()) return ex
    return ex.copy(sets = ramp.map { (w, r) -> SessionSet(type = SetType.WARMUP, weightKg = Units.fromDisplayWeight(w, unit), reps = r) } + ex.sets)
  }

  /** Warm-ups for a whole session in order, so only the first exercise per muscle gets the full ramp. */
  fun withWarmups(exercises: List<SessionExercise>, settings: Settings): List<SessionExercise> {
    if (!settings.smartWarmups) return exercises
    val seen = mutableSetOf<Any>()
    return exercises.map { ex ->
      val key: Any = traits(ex).muscle ?: ex.exerciseId
      withWarmups(ex, seen.add(key), settings)
    }
  }
}
