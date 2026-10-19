package com.ironlog.app.workout

import com.ironlog.app.data.db.ExerciseEntity
import com.ironlog.app.domain.SetValues
import com.ironlog.app.model.Equipment
import com.ironlog.app.model.ExerciseType
import com.ironlog.app.model.MuscleGroup
import com.ironlog.app.model.SetType
import com.ironlog.app.progression.ProgressionRule
import com.ironlog.app.progression.TargetReason
import com.ironlog.app.progression.Verdict
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.Serializable

/**
 * Editable exercise/set tree shared by the live workout and the routine editor. In a routine the
 * values are targets and [SessionSet.completed]/[SessionSet.rir] are unused. In a workout the
 * target* fields hold what the set aims for (shown as placeholders, used when ✓ is tapped on an
 * empty set) and [SessionSet.verdict] compares the finished set with the same set last time.
 */
@Serializable
data class SessionSet(
  val key: Long = Keys.next(),
  val type: SetType = SetType.NORMAL,
  val weightKg: Double? = null,
  val reps: Int? = null,
  val durationSec: Int? = null,
  val distanceM: Double? = null,
  val rpe: Double? = null,
  val repMin: Int? = null,
  val repMax: Int? = null,
  val completed: Boolean = false,
  val rir: Int? = null,
  val targetWeightKg: Double? = null,
  val targetReps: Int? = null,
  val targetRir: Int? = null,
  val verdict: Verdict? = null,
) {
  fun values() = SetValues(type, weightKg, reps, durationSec, distanceM)
}

@Serializable
data class SessionExercise(
  val key: Long = Keys.next(),
  val exerciseId: Long,
  val name: String,
  val type: ExerciseType,
  val equipment: Equipment,
  val primaryMuscle: MuscleGroup,
  val secondaryMuscles: List<MuscleGroup> = emptyList(),
  val supersetGroup: Int? = null,
  /** 0 means the rest timer is off for this exercise. */
  val restSeconds: Int = 0,
  val notes: String = "",
  val sets: List<SessionSet> = listOf(SessionSet()),
  /** Null means the app default (Smart). */
  val rule: ProgressionRule? = null,
  /** Reps in reserve to aim for; null means the app (or plan week) default. */
  val targetRir: Int? = null,
  /** Why the targets are what they are, for the line under the exercise name. */
  val reason: TargetReason? = null,
  /** Targets have been worked out for this exercise (or deliberately left empty). */
  val targetsReady: Boolean = false,
  val planSlotId: Long? = null,
  /** A recovery session for this slot: half the work, very easy. */
  val recovery: Boolean = false,
  /** 0 none … 3 a lot; asked once the exercise's sets are done. */
  val jointPain: Int? = null,
) {
  companion object {
    fun from(exercise: ExerciseEntity, restSeconds: Int) =
      SessionExercise(
        exerciseId = exercise.id,
        name = exercise.name,
        type = exercise.type,
        equipment = exercise.equipment,
        primaryMuscle = exercise.primaryMuscle,
        secondaryMuscles = exercise.secondaryMuscles,
        restSeconds = restSeconds,
      )
  }
}

/** The plan session a workout belongs to. [rir] is this week's reps-in-reserve target. */
@Serializable
data class PlanLink(
  val planId: Long,
  val sessionId: Long,
  val week: Int,
  val weeks: Int,
  val rir: Int,
  val deload: Boolean,
  val dayName: String,
  val feedbackMode: com.ironlog.app.data.db.FeedbackMode = com.ironlog.app.data.db.FeedbackMode.LIGHT,
)

@Serializable
data class ActiveWorkout(
  val name: String,
  val notes: String = "",
  val routineId: Long? = null,
  val startTime: Long,
  val exercises: List<SessionExercise> = emptyList(),
  val plan: PlanLink? = null,
  /** Answers about how each muscle felt, collected during a planned session. */
  val feedback: List<MuscleAnswers> = emptyList(),
)

/** Per-muscle answers; see [com.ironlog.app.data.db.MuscleFeedbackEntity] for the scales. */
@Serializable
data class MuscleAnswers(val muscle: com.ironlog.app.progression.TrainingMuscle, val soreness: Int? = null, val pump: Int? = null, val workload: Int? = null)

object Keys {
  private val counter = AtomicLong(System.currentTimeMillis() * 1000)

  fun next(): Long = counter.incrementAndGet()
}

/** Pure edits on an exercise list; both editors call these so behaviour stays identical. */
object SessionOps {
  /**
   * Last session's set to show beside the set at [index]: warm-ups pair with last time's warm-ups and
   * the other sets with the other sets, each in order, so added or skipped warm-ups don't shift the column.
   */
  fun previousFor(sets: List<SessionSet>, previous: List<SetValues>, index: Int): SetValues? {
    val warmup = sets.getOrNull(index)?.type == SetType.WARMUP
    val ordinal = sets.take(index).count { (it.type == SetType.WARMUP) == warmup }
    return previous.filter { (it.type == SetType.WARMUP) == warmup }.getOrNull(ordinal)
  }

  fun List<SessionExercise>.updateExercise(key: Long, f: (SessionExercise) -> SessionExercise) = map { if (it.key == key) f(it) else it }

  fun List<SessionExercise>.updateSet(exerciseKey: Long, setKey: Long, f: (SessionSet) -> SessionSet) =
    updateExercise(exerciseKey) { ex -> ex.copy(sets = ex.sets.map { if (it.key == setKey) f(it) else it }) }

  /** New sets copy the previous set's values so adding a set is one tap. */
  fun List<SessionExercise>.addSet(exerciseKey: Long) =
    updateExercise(exerciseKey) { ex ->
      val last = ex.sets.lastOrNull()
      val newSet =
        last?.copy(key = Keys.next(), type = if (last.type == SetType.WARMUP) SetType.NORMAL else last.type, completed = false, rpe = null, rir = null, verdict = null)
          ?: SessionSet()
      ex.copy(sets = ex.sets + newSet)
    }

  fun List<SessionExercise>.removeSet(exerciseKey: Long, setKey: Long) =
    updateExercise(exerciseKey) { ex -> ex.copy(sets = ex.sets.filterNot { it.key == setKey }) }

  fun List<SessionExercise>.removeExercise(key: Long) = filterNot { it.key == key }.normalizeSupersets()

  fun List<SessionExercise>.move(key: Long, delta: Int): List<SessionExercise> {
    val i = indexOfFirst { it.key == key }
    val j = i + delta
    if (i < 0 || j !in indices) return this
    return toMutableList().apply { add(j, removeAt(i)) }.normalizeSupersets()
  }

  /** Swaps the exercise but keeps the set structure (values are cleared when the type changes). */
  fun List<SessionExercise>.replace(key: Long, exercise: ExerciseEntity) =
    updateExercise(key) { old ->
      val keepValues = old.type == exercise.type
      old.copy(
        exerciseId = exercise.id,
        name = exercise.name,
        type = exercise.type,
        equipment = exercise.equipment,
        primaryMuscle = exercise.primaryMuscle,
        secondaryMuscles = exercise.secondaryMuscles,
        sets =
          old.sets.map {
            val cleared = it.copy(completed = false, rir = null, verdict = null, targetWeightKg = null, targetReps = null, targetRir = null)
            if (keepValues) cleared else SessionSet(key = it.key, type = it.type, repMin = it.repMin, repMax = it.repMax)
          },
        reason = null,
        targetsReady = false,
        jointPain = null,
      )
    }

  /** Joins this exercise with the next one (or the next one's existing superset). */
  fun List<SessionExercise>.supersetWithNext(key: Long): List<SessionExercise> {
    val i = indexOfFirst { it.key == key }
    if (i < 0 || i == lastIndex) return this
    val group = this[i].supersetGroup ?: this[i + 1].supersetGroup ?: ((mapNotNull { it.supersetGroup }.maxOrNull() ?: 0) + 1)
    return mapIndexed { idx, ex -> if (idx == i || idx == i + 1) ex.copy(supersetGroup = group) else ex }.normalizeSupersets()
  }

  fun List<SessionExercise>.leaveSuperset(key: Long) = updateExercise(key) { it.copy(supersetGroup = null) }.normalizeSupersets()

  /** A superset must be a contiguous run of at least two exercises; anything else is dissolved. */
  fun List<SessionExercise>.normalizeSupersets(): List<SessionExercise> {
    val result = toMutableList()
    var i = 0
    val seen = mutableSetOf<Int>()
    while (i < result.size) {
      val g = result[i].supersetGroup
      if (g == null) {
        i++
        continue
      }
      var j = i
      while (j + 1 < result.size && result[j + 1].supersetGroup == g) j++
      if (j == i || g in seen) {
        for (k in i..j) result[k] = result[k].copy(supersetGroup = null)
      } else {
        seen += g
      }
      i = j + 1
    }
    return result
  }

  /** True when this exercise is in a superset and isn't its last member (rest comes after the round). */
  fun List<SessionExercise>.restDeferred(key: Long): Boolean {
    val i = indexOfFirst { it.key == key }
    if (i < 0) return false
    val g = this[i].supersetGroup ?: return false
    return i < lastIndex && this[i + 1].supersetGroup == g
  }

  /** Replaces existing warm-up sets with [weightsKgAndReps], placed before the working sets. */
  fun List<SessionExercise>.setWarmups(exerciseKey: Long, weightsKgAndReps: List<Pair<Double, Int>>) =
    updateExercise(exerciseKey) { ex ->
      val warmups = weightsKgAndReps.map { (w, r) -> SessionSet(type = SetType.WARMUP, weightKg = w, reps = r) }
      ex.copy(sets = warmups + ex.sets.filterNot { it.type == SetType.WARMUP })
    }
}
