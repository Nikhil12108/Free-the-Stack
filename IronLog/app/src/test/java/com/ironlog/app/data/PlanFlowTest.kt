package com.ironlog.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ironlog.app.data.db.FeedbackMode
import com.ironlog.app.data.db.IronLogDatabase
import com.ironlog.app.data.db.PlanSessionSlotEntity
import com.ironlog.app.data.db.PlanStatus
import com.ironlog.app.data.db.SessionStatus
import com.ironlog.app.model.SetType
import com.ironlog.app.progression.HistorySession
import com.ironlog.app.progression.Periodization
import com.ironlog.app.progression.Priority
import com.ironlog.app.progression.ReasonCode
import com.ironlog.app.progression.TrainingMuscle
import com.ironlog.app.workout.ActiveWorkout
import com.ironlog.app.workout.MuscleAnswers
import com.ironlog.app.workout.SessionExercise
import com.ironlog.app.workout.SessionSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A whole training block on a real (in-memory) database: create, train, adapt, deload, finish. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlanFlowTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private lateinit var db: IronLogDatabase
  private lateinit var exercises: ExerciseRepository
  private lateinit var routines: RoutineRepository
  private lateinit var workouts: WorkoutRepository
  private lateinit var plans: PlanRepository
  private lateinit var settings: SettingsStore

  @Before
  fun setUp() = runBlocking {
    db = Room.inMemoryDatabaseBuilder(context, IronLogDatabase::class.java).build()
    exercises = ExerciseRepository(db)
    routines = RoutineRepository(db)
    workouts = WorkoutRepository(db)
    settings = SettingsStore(context)
    plans = PlanRepository(db, workouts, routines, settings, MeasurementRepository(db))
    exercises.seedIfEmpty()
  }

  @After
  fun tearDown() {
    db.close()
  }

  private suspend fun exercise(name: String) = db.exercises().observeAll().first().first { it.name == name }

  private suspend fun routine(name: String, vararg names: String): Long {
    val exs = names.map { SessionExercise.from(exercise(it), 120).copy(sets = List(3) { SessionSet(repMin = 8, repMax = 12) }) }
    return routines.save(null, name, null, "", exs)
  }

  /** Logs a prepared session with every working set done at [weight] × [reps]. */
  private suspend fun train(sessionId: Long, weight: Double, reps: Int, answers: List<MuscleAnswers> = emptyList(), at: Long = System.currentTimeMillis()): ActiveWorkout {
    val p = plans.prepare(sessionId)!!
    val w =
      ActiveWorkout(
        name = p.name,
        startTime = at,
        exercises = p.exercises.map { ex -> ex.copy(sets = ex.sets.map { s -> if (s.type == SetType.WARMUP) s else s.copy(weightKg = weight, reps = reps, completed = true) }) },
        plan = p.link,
        feedback = answers,
      )
    val id = workouts.save(w, System.currentTimeMillis())
    plans.completeSession(w, id)
    return w
  }

  @Test
  fun blockFromRoutines_adaptsWeekByWeek_andDeloads() = runBlocking {
    val push = routine("Push", "Barbell Bench Press", "Dumbbell Lateral Raise")
    val pull = routine("Pull", "Barbell Bent-Over Row", "Dumbbell Curl")
    val draft = plans.draftFromRoutines("Test block", listOf(push, pull), weeks = 5, mode = FeedbackMode.LIGHT).copy(priorities = mapOf(TrainingMuscle.CHEST to Priority.EMPHASIZE))
    val planId = plans.create(draft)

    var ov = plans.overview(planId)!!
    assertEquals(PlanStatus.ACTIVE, ov.plan.status)
    assertEquals(10, ov.sessions.size)
    assertEquals(listOf(3, 2, 1, 0), ov.schedule)
    assertEquals(Priority.EMPHASIZE, ov.priorities[TrainingMuscle.CHEST])
    assertEquals(Priority.GROW, ov.priorities[TrainingMuscle.BACK])
    val first = ov.next!!
    assertEquals(0, first.week)
    assertEquals(mapOf(TrainingMuscle.CHEST to 3, TrainingMuscle.SHOULDERS to 3, TrainingMuscle.BACK to 3, TrainingMuscle.BICEPS to 3), ov.weeklySets(0))

    // Week 1: no history, so targets ask for a weight at 3 RIR.
    val prepared = plans.prepare(first.id)!!
    assertEquals(3, prepared.link.rir)
    assertEquals(ReasonCode.CALIBRATE, prepared.exercises.first().reason?.code)
    assertTrue(prepared.exercises.all { it.sets.count { s -> s.type != SetType.WARMUP } == 3 })

    // Chest felt easy: an emphasized muscle gains a set next week; shoulders were at the limit and hold.
    train(first.id, 60.0, 10, listOf(MuscleAnswers(TrainingMuscle.CHEST, workload = 0), MuscleAnswers(TrainingMuscle.SHOULDERS, workload = 2)))
    ov = plans.overview(planId)!!
    assertEquals(SessionStatus.DONE, ov.sessions.first { it.id == first.id }.status)
    val pushWeek2 = ov.week(1).first { it.planDayId == first.planDayId }
    val day = ov.day(first.planDayId)!!
    val bench = day.orderedSlots.first { it.exercise.name == "Barbell Bench Press" }.slot
    val laterals = day.orderedSlots.first { it.exercise.name == "Dumbbell Lateral Raise" }.slot
    assertEquals(4, ov.plannedSets(pushWeek2, bench))
    assertEquals(3, ov.plannedSets(pushWeek2, laterals)) // "at my limit" holds

    // Week 2 targets come from week 1: heavier or more reps, now at 2 RIR.
    val second = plans.prepare(pushWeek2.id)!!
    assertEquals(2, second.link.rir)
    val benchTargets = second.exercises.first { it.name == "Barbell Bench Press" }
    assertEquals(4, benchTargets.sets.count { it.type != SetType.WARMUP })
    val t = benchTargets.sets.first { it.type != SetType.WARMUP }
    assertNotNull(t.targetWeightKg)
    assertTrue("load or reps should go up", t.targetWeightKg!! > 60.0 || (t.targetReps ?: 0) > 10)

    // Finish the pull day, then skip through the remaining hard weeks.
    train(ov.week(0).first { it.planDayId != first.planDayId }.id, 50.0, 10)
    while (true) {
      ov = plans.overview(planId)!!
      val next = ov.next ?: break
      if (ov.isDeload(next.week)) break
      plans.skip(next.id)
    }
    ov = plans.overview(planId)!!
    assertTrue(plans.canAddWeek(ov))
    assertFalse(plans.canRemoveWeek(ov)) // the last hard week has been done (skipped)

    // Deload: half the sets, week-1 load, easy.
    val deload = plans.prepare(ov.next!!.id)!!
    assertTrue(deload.link.deload)
    assertEquals(Periodization.DELOAD_RIR, deload.link.rir)
    val dl = deload.exercises.first { it.name == "Barbell Bench Press" }
    assertEquals(2, dl.sets.count { it.type != SetType.WARMUP })
    assertEquals(60.0, dl.sets.first { it.type != SetType.WARMUP }.targetWeightKg!!, 1e-9)
    assertEquals(ReasonCode.DELOAD, dl.reason?.code)

    // Finishing both deload days completes the plan.
    for (s in ov.week(ov.plan.weeks - 1)) plans.skip(s.id)
    assertEquals(PlanStatus.COMPLETE, plans.overview(planId)!!.plan.status)
  }

  @Test
  fun addAndRemoveWeeks() = runBlocking {
    val push = routine("Push", "Barbell Bench Press")
    val planId = plans.create(plans.draftFromRoutines("Block", listOf(push), weeks = 4, mode = FeedbackMode.OFF))
    var ov = plans.overview(planId)!!
    assertFalse(plans.canRemoveWeek(ov)) // already at the minimum
    plans.addWeek(planId)
    ov = plans.overview(planId)!!
    assertEquals(5, ov.plan.weeks)
    assertEquals(listOf(3, 2, 1, 0), ov.schedule)
    assertEquals(5, ov.sessions.size)
    assertTrue(ov.isDeload(ov.sessions.maxOf { it.week }))
    plans.removeWeek(planId)
    ov = plans.overview(planId)!!
    assertEquals(4, ov.plan.weeks)
    assertEquals(4, ov.sessions.size)
  }

  @Test
  fun stillSoreReportedLaterCutsTheEarlierSessionsNextWeek() = runBlocking {
    // Two days that both train chest, so soreness about day 1 can be reported on day 2.
    val a = routine("Day A", "Barbell Bench Press")
    val b = routine("Day B", "Dumbbell Bench Press")
    val planId = plans.create(plans.draftFromRoutines("Block", listOf(a, b), weeks = 5, mode = FeedbackMode.FULL))
    var ov = plans.overview(planId)!!
    val dayA = ov.next!!
    train(dayA.id, 60.0, 10)
    ov = plans.overview(planId)!!
    val dayB = ov.next!!
    assertEquals(listOf(TrainingMuscle.CHEST), plans.sorenessCandidates(dayB.id))
    val nextA = ov.week(1).first { it.planDayId == dayA.planDayId }
    val bench = ov.day(dayA.planDayId)!!.orderedSlots.single().slot
    assertEquals(4, ov.plannedSets(nextA, bench)) // no answers yet: steady ramp
    train(dayB.id, 25.0, 10, listOf(MuscleAnswers(TrainingMuscle.CHEST, soreness = 4)))
    ov = plans.overview(planId)!!
    assertEquals(2, ov.plannedSets(nextA, bench)) // still sore → one set fewer than the 3 it had
  }

  /** A prepared session with only [done]'s working sets ticked, at [weight] × [reps]. */
  private suspend fun partly(sessionId: Long, done: String, weight: Double, reps: Int): ActiveWorkout {
    val p = plans.prepare(sessionId)!!
    return ActiveWorkout(
      name = p.name,
      startTime = System.currentTimeMillis(),
      exercises = p.exercises.map { ex -> if (ex.name != done) ex else ex.copy(sets = ex.sets.map { s -> if (s.type.isWorking) s.copy(weightKg = weight, reps = reps, completed = true) else s }) },
      plan = p.link,
    )
  }

  @Test
  fun aMuscleLeftUndoneHoldsItsSetsWhileTheTrainedOneMovesOn() = runBlocking {
    val push = routine("Push", "Barbell Bench Press", "Dumbbell Lateral Raise")
    val planId = plans.create(plans.draftFromRoutines("Block", listOf(push), weeks = 5, mode = FeedbackMode.LIGHT))
    val first = plans.overview(planId)!!.next!!
    val w = partly(first.id, "Barbell Bench Press", 60.0, 10)
    plans.completeSession(w, workouts.save(w, System.currentTimeMillis()))
    val ov = plans.overview(planId)!!
    assertEquals(SessionStatus.PARTIAL, ov.sessions.first { it.id == first.id }.status)
    val next = ov.week(1).single()
    val slots = ov.day(first.planDayId)!!.orderedSlots
    assertEquals(4, ov.plannedSets(next, slots.first { it.exercise.name == "Barbell Bench Press" }.slot)) // trained, no answers: steady ramp
    assertEquals(3, ov.plannedSets(next, slots.first { it.exercise.name == "Dumbbell Lateral Raise" }.slot)) // not trained: held
  }

  @Test
  fun finishingAfterAnExerciseWasDeletedMidWorkoutStillRecordsTheSession() = runBlocking {
    val push = routine("Push", "Barbell Bench Press", "Dumbbell Lateral Raise")
    val planId = plans.create(plans.draftFromRoutines("Block", listOf(push), weeks = 5, mode = FeedbackMode.LIGHT))
    val first = plans.overview(planId)!!.next!!
    val w = partly(first.id, "Barbell Bench Press", 60.0, 10)
    // The untouched exercise is deleted while the workout runs; its plan slot goes with it.
    exercises.delete(exercise("Dumbbell Lateral Raise"))
    plans.completeSession(w, workouts.save(w, System.currentTimeMillis()))
    val ov = plans.overview(planId)!!
    assertEquals(SessionStatus.PARTIAL, ov.sessions.first { it.id == first.id }.status)
    assertEquals(listOf("Barbell Bench Press"), ov.day(first.planDayId)!!.orderedSlots.map { it.exercise.name })
  }

  @Test
  fun skippingARecoverySessionStillRestoresTheSets() = runBlocking {
    val push = routine("Push", "Barbell Bench Press")
    val planId = plans.create(plans.draftFromRoutines("Block", listOf(push), weeks = 6, mode = FeedbackMode.OFF))
    train(plans.overview(planId)!!.next!!.id, 60.0, 10)
    var ov = plans.overview(planId)!!
    val week2 = ov.week(1).single()
    val bench = ov.day(week2.planDayId)!!.orderedSlots.single().slot
    // Week 2 became a recovery session (half the sets); the lifter skips it.
    db.plans().upsertSessionSlots(listOf(PlanSessionSlotEntity(week2.id, bench.id, 2, recovery = true)))
    plans.skip(week2.id)
    ov = plans.overview(planId)!!
    val row = ov.sessionSlots[ov.week(2).single().id]!![bench.id]!!
    assertFalse(row.recovery)
    assertEquals(3, row.sets) // back to the pre-recovery level, not stuck at half
  }

  @Test
  fun routineNotesReachThePlannedSessionAndACopiedPlan() = runBlocking {
    val bench = SessionExercise.from(exercise("Barbell Bench Press"), 120).copy(notes = "Pause on the chest", sets = List(3) { SessionSet(repMin = 8, repMax = 12) })
    val push = routines.save(null, "Push", null, "", listOf(bench))
    val planId = plans.create(plans.draftFromRoutines("Block", listOf(push), weeks = 4, mode = FeedbackMode.OFF))
    assertEquals("Pause on the chest", plans.prepare(plans.overview(planId)!!.next!!.id)!!.exercises.single().notes)
    assertEquals("Pause on the chest", plans.draftFromPlan(planId, "Again", 4, FeedbackMode.OFF)!!.days.single().slots.single().notes)
  }

  @Test
  fun deloadWorkoutsAreMarkedLightAndAreNotTheNextBaseline() = runBlocking {
    val push = routine("Push", "Barbell Bench Press")
    val planId = plans.create(plans.draftFromRoutines("Block", listOf(push), weeks = 4, mode = FeedbackMode.OFF))
    val start = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
    repeat(3) { i -> train(plans.overview(planId)!!.next!!.id, 60.0, 10, at = start + i * 7L * 24 * 60 * 60 * 1000) }
    val deload = plans.overview(planId)!!.next!!
    assertTrue(plans.overview(planId)!!.isDeload(deload.week))
    train(deload.id, 30.0, 5, at = start + 21L * 24 * 60 * 60 * 1000)
    val history = workouts.historySessions(exercise("Barbell Bench Press").id)
    assertTrue(history.first().light)
    assertFalse(history[1].light)
    assertEquals(60.0, HistorySession.baselines(history).first().working.first().weightKg!!, 1e-9)
  }
}
