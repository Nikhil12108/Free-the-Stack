package com.ironlog.app.progression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodizationTest {
  @Test
  fun g15_rirSchedules() {
    assertEquals(listOf(2, 1, 0), Periodization.rirSchedule(4))
    assertEquals(listOf(3, 2, 1, 0), Periodization.rirSchedule(5))
    assertEquals(listOf(3, 2, 2, 1, 0), Periodization.rirSchedule(6))
    assertEquals(7, Periodization.rirSchedule(8).size)
    assertEquals(0, Periodization.rirSchedule(8).last())
    for (w in 4..8) assertEquals(w - 1, Periodization.rirSchedule(w).size)
  }

  @Test
  fun weekRirAndDeload() {
    val s = Periodization.rirSchedule(5)
    assertEquals(3, Periodization.rirFor(0, 5, s))
    assertEquals(0, Periodization.rirFor(3, 5, s))
    assertEquals(Periodization.DELOAD_RIR, Periodization.rirFor(4, 5, s))
    assertTrue(Periodization.isDeload(4, 5))
    assertEquals(listOf(3, 2, 1, 0), Periodization.decode(Periodization.encode(s)))
  }

  @Test
  fun deloadHalvesSetsAndRestsSmallMuscles() {
    assertEquals(2, Periodization.deloadSets(4, TrainingMuscle.CHEST))
    assertEquals(2, Periodization.deloadSets(3, TrainingMuscle.CHEST))
    assertEquals(1, Periodization.deloadSets(1, TrainingMuscle.BACK))
    assertEquals(0, Periodization.deloadSets(4, TrainingMuscle.FOREARMS))
  }

  @Test
  fun barbellSquatsAndPressesStopShortOfFailure() {
    val squat = ExerciseTraits.of("Barbell Back Squat", com.ironlog.app.model.ExerciseType.WEIGHT_REPS, com.ironlog.app.model.Equipment.BARBELL, com.ironlog.app.model.MuscleGroup.QUADRICEPS)
    val curl = ExerciseTraits.of("Cable Curl", com.ironlog.app.model.ExerciseType.WEIGHT_REPS, com.ironlog.app.model.Equipment.CABLE, com.ironlog.app.model.MuscleGroup.BICEPS)
    assertEquals(1, Periodization.safeRir(0, squat))
    assertEquals(0, Periodization.safeRir(0, curl))
  }
}

class VolumeTest {
  private fun d(
    soreness: Int? = null,
    performance: Int = Volume.PERF_AS_PLANNED,
    pump: Int? = null,
    workload: Int? = null,
    priority: Priority = Priority.EMPHASIZE,
    sessionSets: Int = 4,
    weeklySets: Int = 4,
    sessions: Int = 1,
    previous: Int? = null,
  ) = Volume.decide(Volume.Inputs(priority, sessionSets, weeklySets, sessions, TrainingMuscle.CHEST, soreness, pump, workload, performance, previous))

  @Test
  fun g9_neverSoreAndExceededAddsTwo() {
    assertEquals(2, d(soreness = Volume.SORE_NEVER, performance = Volume.PERF_EXCEEDED).delta)
    assertEquals(1, d(soreness = Volume.SORE_NEVER, performance = Volume.PERF_EXCEEDED, priority = Priority.GROW).delta)
    assertEquals(0, d(soreness = Volume.SORE_NEVER, performance = Volume.PERF_EXCEEDED, priority = Priority.MAINTAIN).delta)
  }

  @Test
  fun g10_recoveredAndOnTrackAddsOne() {
    assertEquals(1, d(soreness = Volume.SORE_HEALED_EARLY, performance = Volume.PERF_AS_PLANNED).delta)
    assertEquals(1, d(soreness = Volume.SORE_NEVER, performance = Volume.PERF_AS_PLANNED).delta)
  }

  @Test
  fun g11_struggledHoldsAndStillSoreCuts() {
    assertEquals(0, d(soreness = Volume.SORE_ON_TIME, performance = Volume.PERF_STRUGGLED).delta)
    assertEquals(-1, d(soreness = Volume.SORE_STILL, performance = Volume.PERF_STRUGGLED).delta)
  }

  @Test
  fun g12_regressedTwiceMeansRecovery() {
    val once = d(performance = Volume.PERF_REGRESSED)
    assertFalse(once.recovery)
    assertEquals(0, once.delta)
    assertTrue(d(performance = Volume.PERF_REGRESSED, previous = Volume.PERF_REGRESSED).recovery)
  }

  @Test
  fun g13_atMyLimitIsAHardBrake() {
    assertEquals(0, d(soreness = Volume.SORE_NEVER, performance = Volume.PERF_EXCEEDED, workload = Volume.WORKLOAD_LIMIT).delta)
    assertEquals(-1, d(workload = Volume.WORKLOAD_TOO_MUCH).delta)
  }

  @Test
  fun g14_stillSoreRemovesASet() {
    assertEquals(-1, d(soreness = Volume.SORE_STILL, sessionSets = 6, weeklySets = 6).delta)
  }

  @Test
  fun noAnswersRampsUntilTheCeiling() {
    assertEquals(1, d().delta)
    assertEquals(0, d(weeklySets = 18, sessionSets = 9).delta) // chest MRV at 1–2 sessions is 18
    assertEquals(0, d(performance = Volume.PERF_STRUGGLED).delta)
  }

  @Test
  fun lightModeUsesWorkloadAndPump() {
    assertEquals(1, d(workload = Volume.WORKLOAD_EASY).delta)
    assertEquals(0, d(workload = Volume.WORKLOAD_RIGHT).delta)
    assertEquals(1, d(workload = Volume.WORKLOAD_RIGHT, pump = Volume.PUMP_LOW).delta)
    assertEquals(0, d(workload = Volume.WORKLOAD_EASY, pump = Volume.PUMP_GREAT).delta)
  }

  @Test
  fun severalSessionsAWeekGrowOneSetEach() {
    assertEquals(1, d(soreness = Volume.SORE_NEVER, performance = Volume.PERF_EXCEEDED, sessions = 2).delta)
  }

  @Test
  fun performanceScore() {
    val onTarget = List(3) { LoggedSet(reps = 10, targetReps = 10, targetRir = 2) }
    assertEquals(Volume.PERF_AS_PLANNED, Volume.performance(onTarget, 0.0))
    val easy = List(3) { LoggedSet(reps = 12, rir = 3, targetReps = 10, targetRir = 2) }
    assertEquals(Volume.PERF_EXCEEDED, Volume.performance(easy, 0.0))
    val short = List(3) { LoggedSet(reps = 8, targetReps = 10, targetRir = 2) }
    assertEquals(Volume.PERF_STRUGGLED, Volume.performance(short, 0.0))
    assertEquals(Volume.PERF_REGRESSED, Volume.performance(short, 0.67))
  }

  @Test
  fun distributeAddsToTheLightestAndKeepsOneEach() {
    assertEquals(listOf(3, 3), Volume.distribute(listOf(3, 2), 1))
    assertEquals(listOf(3, 2), Volume.distribute(listOf(3, 3), -1))
    assertEquals(listOf(1, 1), Volume.distribute(listOf(1, 1), -1))
    assertEquals(3, Volume.afterRecovery(2, 4))
  }
}
