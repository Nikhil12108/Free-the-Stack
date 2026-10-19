package com.ironlog.app.workout

import com.ironlog.app.data.Settings
import com.ironlog.app.data.db.ExerciseEntity
import com.ironlog.app.model.Equipment
import com.ironlog.app.model.ExerciseType
import com.ironlog.app.model.MuscleGroup
import com.ironlog.app.model.SetType
import com.ironlog.app.progression.HistorySession
import com.ironlog.app.progression.LoggedSet
import com.ironlog.app.progression.TargetMode
import com.ironlog.app.ui.workout.parseRange
import com.ironlog.app.ui.workout.targetSummary
import com.ironlog.app.workout.SessionOps.addSet
import com.ironlog.app.workout.SessionOps.leaveSuperset
import com.ironlog.app.workout.SessionOps.move
import com.ironlog.app.workout.SessionOps.removeExercise
import com.ironlog.app.workout.SessionOps.replace
import com.ironlog.app.workout.SessionOps.restDeferred
import com.ironlog.app.workout.SessionOps.setWarmups
import com.ironlog.app.workout.SessionOps.supersetWithNext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionOpsTest {
  private fun exercise(id: Long, type: ExerciseType = ExerciseType.WEIGHT_REPS) =
    ExerciseEntity(id = id, name = "Ex $id", type = type, equipment = Equipment.BARBELL, primaryMuscle = MuscleGroup.CHEST)

  private fun session(vararg ids: Long) = ids.map { SessionExercise.from(exercise(it), restSeconds = 90) }

  @Test
  fun addSet_copiesLastSetButNotCompletionOrWarmupType() {
    val ex = SessionExercise.from(exercise(1), 90).copy(sets = listOf(SessionSet(type = SetType.WARMUP, weightKg = 40.0, reps = 5, completed = true, rpe = 7.0)))
    val after = listOf(ex).addSet(ex.key).single().sets
    assertEquals(2, after.size)
    val added = after[1]
    assertEquals(SetType.NORMAL, added.type)
    assertEquals(40.0, added.weightKg!!, 1e-9)
    assertEquals(5, added.reps)
    assertFalse(added.completed)
    assertNull(added.rpe)
    assertTrue(added.key != after[0].key)
  }

  @Test
  fun superset_joinsNeighboursAndDissolvesWhenBroken() {
    val list = session(1, 2, 3)
    val joined = list.supersetWithNext(list[0].key)
    assertEquals(joined[0].supersetGroup, joined[1].supersetGroup)
    assertTrue(joined[0].supersetGroup != null)
    assertNull(joined[2].supersetGroup)
    assertTrue(joined.restDeferred(joined[0].key))
    assertFalse(joined.restDeferred(joined[1].key))

    // Removing one member leaves a single-member group, which must dissolve.
    assertTrue(joined.removeExercise(joined[1].key).all { it.supersetGroup == null })
    // Moving a member away from its partner breaks contiguity.
    assertTrue(joined.move(joined[1].key, 1).all { it.supersetGroup == null })
    assertTrue(joined.leaveSuperset(joined[0].key).all { it.supersetGroup == null })
  }

  @Test
  fun superset_extendsExistingGroup() {
    val list = session(1, 2, 3)
    val two = list.supersetWithNext(list[0].key)
    val three = two.supersetWithNext(two[1].key)
    assertEquals(1, three.map { it.supersetGroup }.toSet().size)
    assertTrue(three[0].supersetGroup != null)
  }

  @Test
  fun move_ignoresOutOfRange() {
    val list = session(1, 2)
    assertEquals(list, list.move(list[0].key, -1))
    assertEquals(listOf(2L, 1L), list.move(list[0].key, 1).map { it.exerciseId })
  }

  @Test
  fun replace_keepsValuesOnlyForSameType() {
    val base = SessionExercise.from(exercise(1), 90).copy(sets = listOf(SessionSet(weightKg = 60.0, reps = 8, completed = true)))
    val same = listOf(base).replace(base.key, exercise(2)).single()
    assertEquals(2L, same.exerciseId)
    assertEquals(60.0, same.sets[0].weightKg!!, 1e-9)
    assertFalse(same.sets[0].completed)

    val other = listOf(base).replace(base.key, exercise(3, ExerciseType.DURATION)).single()
    assertNull(other.sets[0].weightKg)
    assertEquals(ExerciseType.DURATION, other.type)
  }

  @Test
  fun setWarmups_replacesOldWarmupsAndGoesFirst() {
    val ex =
      SessionExercise.from(exercise(1), 90)
        .copy(sets = listOf(SessionSet(type = SetType.WARMUP, weightKg = 20.0, reps = 10), SessionSet(weightKg = 100.0, reps = 5)))
    val sets = listOf(ex).setWarmups(ex.key, listOf(40.0 to 8, 60.0 to 5)).single().sets
    assertEquals(listOf(SetType.WARMUP, SetType.WARMUP, SetType.NORMAL), sets.map { it.type })
    assertEquals(listOf(40.0, 60.0, 100.0), sets.map { it.weightKg })
  }

  @Test
  fun requiredValues() {
    assertFalse(ActiveWorkoutManager.hasRequiredValues(ExerciseType.WEIGHT_REPS, SessionSet(reps = 5)))
    assertTrue(ActiveWorkoutManager.hasRequiredValues(ExerciseType.WEIGHT_REPS, SessionSet(weightKg = 0.0, reps = 5)))
    assertTrue(ActiveWorkoutManager.hasRequiredValues(ExerciseType.WEIGHTED_BODYWEIGHT, SessionSet(reps = 5)))
    assertFalse(ActiveWorkoutManager.hasRequiredValues(ExerciseType.DURATION, SessionSet(reps = 5)))
  }

  @Test
  fun repRangeParsing() {
    assertEquals(Triple(10, null, null), parseRange("10"))
    assertEquals(Triple(null, 8, 12), parseRange("8-12"))
    assertEquals(Triple(null, null, null), parseRange("8-"))
    assertEquals(Triple(null, null, null), parseRange("12-8"))
  }

  private fun lateral(sets: List<SessionSet>) =
    SessionExercise(exerciseId = 1, name = "Dumbbell Lateral Raise", type = ExerciseType.WEIGHT_REPS, equipment = Equipment.DUMBBELL, primaryMuscle = MuscleGroup.SHOULDERS, sets = sets)

  private val lastTime = listOf(HistorySession(0L, List(3) { LoggedSet(weightKg = 8.0, reps = 15) }))
  private val aWeekLater = 7L * 24 * 60 * 60 * 1000

  @Test
  fun dropSetsAreNotWorkingSets() {
    val ex = lateral(listOf(SessionSet(repMin = 12, repMax = 15), SessionSet(repMin = 12, repMax = 15), SessionSet(type = SetType.DROPSET)))
    val out = TargetPlanner.apply(ex, lastTime, null, Settings(), aWeekLater, TargetMode.ROUTINE, null)
    assertTrue(out.sets.take(2).all { it.targetWeightKg != null && it.targetReps != null })
    assertNull(out.sets[2].targetWeightKg)
    assertNull(out.sets[2].targetReps)
    assertTrue(targetSummary(out, Settings())!!.startsWith("2 ×"))
  }

  @Test
  fun reTargetingLeavesTickedSetsAlone() {
    val ticked = SessionSet(repMin = 12, repMax = 15, weightKg = 9.0, reps = 12, targetWeightKg = 9.0, targetReps = 12, targetRir = 2, completed = true)
    val out = TargetPlanner.apply(lateral(listOf(ticked, SessionSet(repMin = 12, repMax = 15))), lastTime, null, Settings(), aWeekLater, TargetMode.ROUTINE, null)
    assertEquals(ticked, out.sets[0])
    assertTrue(out.sets[1].targetReps != null)
  }
}
