package com.ironlog.app.workout

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ironlog.app.data.ExerciseRepository
import com.ironlog.app.data.MeasurementRepository
import com.ironlog.app.data.RoutineRepository
import com.ironlog.app.data.SettingsStore
import com.ironlog.app.data.WorkoutRepository
import com.ironlog.app.data.db.IronLogDatabase
import com.ironlog.app.workout.SessionOps.updateSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The live-workout manager on a real (in-memory) database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActiveWorkoutTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private lateinit var db: IronLogDatabase
  private lateinit var routines: RoutineRepository
  private lateinit var manager: ActiveWorkoutManager

  @Before
  fun setUp() = runBlocking {
    db = Room.inMemoryDatabaseBuilder(context, IronLogDatabase::class.java).build()
    ExerciseRepository(db).seedIfEmpty()
    routines = RoutineRepository(db)
    manager = ActiveWorkoutManager(context, scope, WorkoutRepository(db), routines, SettingsStore(context), RestTimer(context, scope), MeasurementRepository(db))
  }

  @After
  fun tearDown() {
    manager.discard()
    scope.cancel()
    db.close()
  }

  private suspend fun exercise(name: String) = db.exercises().observeAll().first().first { it.name == name }

  private fun waitUntil(what: String, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 10_000
    while (!condition()) {
      check(System.currentTimeMillis() < deadline) { "timed out waiting for $what" }
      Thread.sleep(20)
    }
  }

  @Test
  fun updatingTheRoutineKeepsWhatWasLeftUndoneAndItsRepScheme() = runBlocking {
    val bench = exercise("Barbell Bench Press")
    val curl = exercise("Dumbbell Curl")
    val id =
      routines.save(
        null,
        "Push",
        null,
        "",
        listOf(
          SessionExercise.from(bench, 120).copy(sets = List(3) { SessionSet(weightKg = 60.0, reps = 8) }),
          SessionExercise.from(curl, 60).copy(sets = List(2) { SessionSet(weightKg = 12.5, reps = 10, repMin = 10, repMax = 12) }),
        ),
      )
    manager.startRoutine(routines.get(id)!!)
    waitUntil("targets") { manager.state.value!!.exercises.all { it.targetsReady } }
    // Smart targets turned the routine's numbers into suggestions. Only the bench is done, a bit heavier.
    val ex = manager.state.value!!.exercises.first { it.exerciseId == bench.id }
    for (s in ex.sets) {
      manager.updateExercises { it.updateSet(ex.key, s.key) { set -> set.copy(weightKg = 62.5) } }
      assertEquals(CompleteResult.OK, manager.toggleComplete(ex.key, s.key))
    }
    manager.finish("Push", "", updateRoutine = true)

    val saved = routines.get(id)!!.orderedExercises
    val benchSets = saved.first { it.exercise.id == bench.id }.sets
    assertTrue("today's weight, and the routine's exact reps stay exact", benchSets.all { it.weightKg == 62.5 && it.reps != null && it.repMin == null && it.repMax == null })
    val curlSets = saved.first { it.exercise.id == curl.id }.sets
    assertEquals(2, curlSets.size)
    assertTrue("the undone exercise keeps the routine's numbers", curlSets.all { it.weightKg == 12.5 && it.reps == 10 && it.repMin == 10 && it.repMax == 12 })
  }

  @Test
  fun aDifferentWeightStoresTheRematchedTargetForNextTime() {
    val bench = runBlocking { exercise("Barbell Bench Press") }
    val link = PlanLink(planId = 1, sessionId = 1, week = 0, weeks = 5, rir = 2, deload = false, dayName = "Push")
    manager.startPlanned("Push", link, listOf(SessionExercise.from(bench, 120).copy(targetsReady = true, sets = listOf(SessionSet(targetWeightKg = 100.0, targetReps = 10, targetRir = 2)))))
    val ex = manager.state.value!!.exercises.single()
    val set = ex.sets.single()
    manager.updateExercises { it.updateSet(ex.key, set.key) { s -> s.copy(weightKg = 105.0) } }
    assertEquals(CompleteResult.OK, manager.toggleComplete(ex.key, set.key))
    val done = manager.state.value!!.exercises.single().sets.single()
    // Heavier than asked: fewer reps suggested and logged, and that pair is now the set's target.
    assertTrue(done.reps!! < 10)
    assertEquals(105.0, done.targetWeightKg!!, 1e-9)
    assertEquals(done.reps, done.targetReps)
  }
}
