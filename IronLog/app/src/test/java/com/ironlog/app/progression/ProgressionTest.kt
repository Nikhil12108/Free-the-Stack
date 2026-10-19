package com.ironlog.app.progression

import com.ironlog.app.data.SeedExercises
import com.ironlog.app.data.db.ExerciseEntity
import com.ironlog.app.model.Equipment
import com.ironlog.app.model.ExerciseType
import com.ironlog.app.model.MuscleGroup
import com.ironlog.app.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DAY = 24 * 60 * 60 * 1000L
private const val NOW = 1_800_000_000_000L
private const val LB = Targets.LB_PER_KG

private fun lb(x: Double) = x / LB

/** Worked examples that pin down the progression maths; docs/PROGRESSION.md explains the model. */
class GoldenCasesTest {
  @Test
  fun g1to3_classifyAgainstExpectedRepsToFailure() {
    // 7–9 reps at 2 RIR: expected reps to failure = 8 + 2 = 10.
    fun c(reps: Int, rir: Int) = Strength.classify(reps, rir, 7, 9, 2)
    listOf(9 to 2, 8 to 3, 10 to 1, 11 to 0).forEach { (r, rir) -> assertEquals("$r@$rir", Strength.Outcome.PROGRESS, c(r, rir)) }
    assertEquals(Strength.Outcome.HOLD, c(8, 2))
    assertEquals(Strength.Outcome.REDUCE, c(8, 1))
    assertEquals(Strength.Outcome.REDUCE, c(6, 2))
  }

  private val barbell = ExerciseTraits.of("Barbell Bench Press", ExerciseType.WEIGHT_REPS, Equipment.BARBELL, MuscleGroup.CHEST)
  private val lbs5 = EquipmentProfile.defaultFor(WeightUnit.LBS) // smallest plate 2.5 → 5 lb steps
  private val lbs25 = lbs5.copy(plates = lbs5.plates + 1.25) // 2.5 lb steps

  private fun planTarget(profile: EquipmentProfile, load: Double, reps: Int, rir: Int, nextRir: Int, lo: Int, hi: Int): SetTarget =
    Targets.compute(
        TargetRequest(
          type = ExerciseType.WEIGHT_REPS,
          traits = barbell,
          rule = ProgressionRule.SMART,
          repMin = lo,
          repMax = hi,
          rir = nextRir,
          workingSets = 1,
          history = listOf(HistorySession(NOW - 7 * DAY, listOf(LoggedSet(weightKg = lb(load), reps = reps, rir = rir, targetRir = rir, targetReps = reps)))),
          nowMillis = NOW,
          bodyweightKg = null,
          profile = profile,
          experience = Experience.INTERMEDIATE,
          mode = TargetMode.PLAN,
        )
      )
      .sets
      .single()

  @Test
  fun g4_loadStep_100x10at2_becomes105x10at1() {
    for (p in listOf(lbs5, lbs25)) {
      val t = planTarget(p, 100.0, 10, 2, 1, 8, 12)
      assertEquals(105.0, t.weightKg!! * LB, 1e-6)
      assertEquals(10, t.reps)
      assertEquals(1, t.rir)
    }
  }

  @Test
  fun g5_loadStep_135x26_becomes140x26() {
    val t = planTarget(lbs5, 135.0, 26, 2, 1, 20, 30)
    assertEquals(140.0, t.weightKg!! * LB, 1e-6)
    assertEquals(26, t.reps)
  }

  @Test
  fun g6to8_rematchRepsWhenTheLifterPicksAnotherLoad() {
    for (rir in 0..3) {
      assertEquals(24, Strength.rematchReps(140.0, 26, rir, 145.0))
      assertEquals(16, Strength.rematchReps(230.0, 15, rir, 225.0))
      // Epley on reps to failure gives 28 here; other estimators land one lower, so ±1 is within the model's error.
      assertEquals(28, Strength.rematchReps(140.0, 26, rir, 135.0))
    }
  }
}

class LadderTest {
  @Test
  fun floatingPointStepsStayExact() {
    val l = Ladder(45.0, 5.0)
    assertEquals(105.0, l.nextUp(100.0), 1e-9)
    assertEquals(95.0, l.nextDown(100.0), 1e-9)
    assertEquals(100.0, l.snapDown(102.4), 1e-9)
    assertEquals(105.0, l.snapUp(102.4), 1e-9)
    assertEquals(45.0, l.snapDown(10.0), 1e-9)
    // 105/100 − 1 is 0.05000000000000004: the 5 % barbell cap must still allow it.
    assertTrue(105.0 / 100.0 - 1 <= Targets.stepCap(LoadKind.BARBELL, Experience.INTERMEDIATE) + 1e-9)
  }

  @Test
  fun barbellStepIsTwiceTheSmallestPlate() {
    val kg = EquipmentProfile()
    assertEquals(2.5, Loads.ladder(LoadKind.BARBELL, kg)!!.step, 1e-9)
    assertEquals(20.0, Loads.ladder(LoadKind.BARBELL, kg)!!.start, 1e-9)
    val noSmall = kg.copy(plates = kg.plates - 1.25)
    assertEquals(5.0, Loads.ladder(LoadKind.BARBELL, noSmall)!!.step, 1e-9)
    assertNull(Loads.ladder(LoadKind.NONE, kg))
  }

  @Test
  fun poundLoadsRoundTripThroughKilograms() {
    val l = Ladder(45.0, 5.0)
    val kg = 225.0 / LB
    assertTrue(l.isOn(kg * LB))
  }
}

class TargetsTest {
  private val dumbbell = ExerciseTraits.of("Dumbbell Lateral Raise", ExerciseType.WEIGHT_REPS, Equipment.DUMBBELL, MuscleGroup.SHOULDERS)
  private val kg = EquipmentProfile()

  private fun routine(traits: ExerciseTraits, vararg sessions: List<LoggedSet>, lo: Int = 8, hi: Int = 12, rule: ProgressionRule = ProgressionRule.SMART, sets: Int = 3) =
    Targets.compute(
      TargetRequest(
        type = ExerciseType.WEIGHT_REPS,
        traits = traits,
        rule = rule,
        repMin = lo,
        repMax = hi,
        rir = 2,
        workingSets = sets,
        history = sessions.mapIndexed { i, s -> HistorySession(NOW - (i + 1) * 3 * DAY, s) },
        nowMillis = NOW,
        bodyweightKg = null,
        profile = kg,
        experience = Experience.INTERMEDIATE,
      )
    )

  @Test
  fun noHistoryCalibrates() {
    val r = routine(dumbbell)
    assertEquals(ReasonCode.CALIBRATE, r.reason.code)
    assertEquals(3, r.sets.size)
    assertNull(r.sets[0].reps)
  }

  @Test
  fun belowTopOfRangeAddsARep() {
    val r = routine(dumbbell, List(3) { LoggedSet(weightKg = 10.0, reps = 10) })
    assertEquals(ReasonCode.REPS_UP, r.reason.code)
    assertTrue(r.sets.all { it.reps == 11 && it.weightKg == 10.0 })
  }

  @Test
  fun smallDumbbellJumpIsTooBigSoRepsGoUpInstead() {
    // 10 → 12.5 kg is +25 %, over the 10 % dumbbell cap.
    val r = routine(dumbbell, List(3) { LoggedSet(weightKg = 10.0, reps = 12) })
    assertEquals(ReasonCode.BIG_STEP, r.reason.code)
    assertTrue(r.sets.all { it.weightKg == 10.0 && it.reps == 13 })
  }

  @Test
  fun topOfRangeOnABarbellAddsTheNextPlates() {
    val bench = ExerciseTraits.of("Barbell Bench Press", ExerciseType.WEIGHT_REPS, Equipment.BARBELL, MuscleGroup.CHEST)
    val r = routine(bench, List(3) { LoggedSet(weightKg = 80.0, reps = 12) })
    assertEquals(ReasonCode.LOAD_UP, r.reason.code)
    assertEquals(82.5, r.sets[0].weightKg!!, 1e-9)
    assertTrue(r.sets[0].reps!! in 8..12)
  }

  @Test
  fun missingTheTargetTwiceDropsAStep() {
    val bench = ExerciseTraits.of("Barbell Bench Press", ExerciseType.WEIGHT_REPS, Equipment.BARBELL, MuscleGroup.CHEST)
    val miss = List(3) { LoggedSet(weightKg = 80.0, reps = 8, targetReps = 10) }
    val r = routine(bench, miss, miss)
    assertEquals(ReasonCode.REDUCE, r.reason.code)
    assertEquals(77.5, r.sets[0].weightKg!!, 1e-9)
    val once = routine(bench, miss, List(3) { LoggedSet(weightKg = 77.5, reps = 10, targetReps = 10) })
    assertEquals(ReasonCode.RETRY, once.reason.code)
    assertEquals(80.0, once.sets[0].weightKg!!, 1e-9)
  }

  @Test
  fun doubleProgression() {
    val notYet = routine(dumbbell, listOf(LoggedSet(weightKg = 10.0, reps = 12), LoggedSet(weightKg = 10.0, reps = 11)), rule = ProgressionRule.DOUBLE, sets = 2)
    assertEquals(listOf(12, 12), notYet.sets.map { it.reps })
    val done = routine(dumbbell, List(2) { LoggedSet(weightKg = 10.0, reps = 12) }, rule = ProgressionRule.DOUBLE, sets = 2)
    assertTrue(done.sets.all { it.weightKg == 12.5 && it.reps == 8 })
  }

  @Test
  fun weightedBodyweightWithoutBodyweightProgressesReps() {
    val pullUp = ExerciseTraits.of("Pull-Up", ExerciseType.WEIGHTED_BODYWEIGHT, Equipment.NONE, MuscleGroup.LATS)
    val r =
      Targets.compute(
        TargetRequest(ExerciseType.WEIGHTED_BODYWEIGHT, pullUp, ProgressionRule.SMART, 6, 10, 2, 1, listOf(HistorySession(NOW - DAY, listOf(LoggedSet(weightKg = 0.0, reps = 8)))), NOW, null, kg, Experience.INTERMEDIATE)
      )
    assertEquals(9, r.sets.single().reps)
  }

  @Test
  fun deloadUsesWeekOneLoadAndHalfTheReps() {
    val bench = ExerciseTraits.of("Barbell Bench Press", ExerciseType.WEIGHT_REPS, Equipment.BARBELL, MuscleGroup.CHEST)
    val r =
      Targets.compute(
        TargetRequest(
          ExerciseType.WEIGHT_REPS,
          bench,
          ProgressionRule.SMART,
          8,
          12,
          5,
          2,
          listOf(HistorySession(NOW - 7 * DAY, List(2) { LoggedSet(weightKg = 90.0, reps = 10) })),
          NOW,
          null,
          kg,
          Experience.INTERMEDIATE,
          mode = TargetMode.DELOAD,
          deloadReference = List(2) { LoggedSet(weightKg = 80.0, reps = 11) },
        )
      )
    assertEquals(ReasonCode.DELOAD, r.reason.code)
    assertTrue(r.sets.all { it.weightKg == 80.0 && it.reps == 6 })
  }

  @Test
  fun deloadAndRecoverySessionsAreNotTheBaseline() {
    val bench = ExerciseTraits.of("Barbell Bench Press", ExerciseType.WEIGHT_REPS, Equipment.BARBELL, MuscleGroup.CHEST)
    fun after(history: List<HistorySession>) = Targets.compute(TargetRequest(ExerciseType.WEIGHT_REPS, bench, ProgressionRule.SMART, 8, 12, 2, 2, history, NOW, null, kg, Experience.INTERMEDIATE))
    val hard = HistorySession(NOW - 10 * DAY, List(2) { LoggedSet(weightKg = 100.0, reps = 10, targetReps = 10) })
    val deload = HistorySession(NOW - 3 * DAY, List(2) { LoggedSet(weightKg = 50.0, reps = 5, targetReps = 5, targetRir = 5) }, light = true)
    // The deload is newer, but progress continues from the last hard session.
    val r = after(listOf(deload, hard))
    assertEquals(ReasonCode.REPS_UP, r.reason.code)
    assertTrue(r.sets.all { it.weightKg == 100.0 && it.reps == 11 })
    // With nothing but light sessions they still give a starting weight.
    assertEquals(50.0, after(listOf(deload)).sets[0].weightKg!!, 1e-9)
  }

  @Test
  fun assistedLiftWithNoAssistanceLeftAddsRepsInsteadOfZeroKgSteps() {
    val dip = ExerciseTraits.of("Assisted Dip", ExerciseType.ASSISTED_BODYWEIGHT, Equipment.MACHINE, MuscleGroup.TRICEPS)
    fun next(reps: Int, mode: TargetMode, rule: ProgressionRule = ProgressionRule.SMART) =
      Targets.compute(TargetRequest(ExerciseType.ASSISTED_BODYWEIGHT, dip, rule, 8, 12, 1, 1, listOf(HistorySession(NOW - 7 * DAY, listOf(LoggedSet(weightKg = 0.0, reps = reps)))), NOW, 80.0, kg, Experience.INTERMEDIATE, mode = mode))
    for (r in listOf(next(10, TargetMode.PLAN), next(12, TargetMode.ROUTINE), next(12, TargetMode.ROUTINE, ProgressionRule.DOUBLE))) {
      assertEquals(ReasonCode.REPS_UP, r.reason.code)
      assertEquals(0.0, r.sets.single().weightKg!!, 1e-9)
    }
    assertEquals(11, next(10, TargetMode.PLAN).sets.single().reps)
    assertEquals(13, next(12, TargetMode.ROUTINE).sets.single().reps)
  }

  @Test
  fun assistedLiftWithoutBodyweightKeepsItsAssistanceWhenEasing() {
    val dip = ExerciseTraits.of("Assisted Dip", ExerciseType.ASSISTED_BODYWEIGHT, Equipment.MACHINE, MuscleGroup.TRICEPS)
    val r = Targets.compute(TargetRequest(ExerciseType.ASSISTED_BODYWEIGHT, dip, ProgressionRule.SMART, 8, 12, 2, 1, listOf(HistorySession(NOW - 7 * DAY, listOf(LoggedSet(weightKg = 40.0, reps = 10)))), NOW, null, kg, Experience.INTERMEDIATE, mode = TargetMode.RECOVERY))
    // Less assistance would be harder; without bodyweight it stays and only the reps drop.
    assertEquals(40.0, r.sets.single().weightKg!!, 1e-9)
    assertEquals(5, r.sets.single().reps)
  }

  @Test
  fun verdictComparesStrengthNotJustReps() {
    val total = { s: LoggedSet -> s.weightKg }
    assertEquals(Verdict.IMPROVED, Strength.verdict(LoggedSet(weightKg = 105.0, reps = 9), LoggedSet(weightKg = 100.0, reps = 10), total))
    assertEquals(Verdict.HELD, Strength.verdict(LoggedSet(weightKg = 100.0, reps = 10), LoggedSet(weightKg = 100.0, reps = 10), total))
    assertEquals(Verdict.REGRESSED, Strength.verdict(LoggedSet(weightKg = 100.0, reps = 8), LoggedSet(weightKg = 100.0, reps = 10), total))
  }
}

class TraitsTest {
  /** Custom exercise names as imported from another tracker, alongside the built-in list. */
  private val custom =
    listOf(
      ExerciseEntity(name = "Leg Raise Parallel Bars", type = ExerciseType.REPS_ONLY, equipment = Equipment.MACHINE, primaryMuscle = MuscleGroup.ABDOMINALS),
      ExerciseEntity(name = "Behind the Back Curl (Cable)", type = ExerciseType.WEIGHT_REPS, equipment = Equipment.CABLE, primaryMuscle = MuscleGroup.BICEPS),
      ExerciseEntity(name = "Single Arm Lat Pulldown", type = ExerciseType.WEIGHT_REPS, equipment = Equipment.CABLE, primaryMuscle = MuscleGroup.LATS),
      ExerciseEntity(name = "Preacher Curl (Barbell)", type = ExerciseType.WEIGHT_REPS, equipment = Equipment.BARBELL, primaryMuscle = MuscleGroup.BICEPS),
      ExerciseEntity(name = "Glute Ham Raise", type = ExerciseType.REPS_ONLY, equipment = Equipment.MACHINE, primaryMuscle = MuscleGroup.HAMSTRINGS),
      ExerciseEntity(name = "JM Press (Barbell)", type = ExerciseType.WEIGHT_REPS, equipment = Equipment.BARBELL, primaryMuscle = MuscleGroup.TRICEPS),
      ExerciseEntity(name = "Reverse Crunch", type = ExerciseType.REPS_ONLY, equipment = Equipment.NONE, primaryMuscle = MuscleGroup.ABDOMINALS),
      ExerciseEntity(name = "Triceps Pressdown", type = ExerciseType.WEIGHT_REPS, equipment = Equipment.CABLE, primaryMuscle = MuscleGroup.TRICEPS),
    )

  @Test
  fun everyWeightedExerciseGetsAMovementPattern() {
    for (e in SeedExercises.all + custom) {
      val t = ExerciseTraits.of(e)
      if (e.type == ExerciseType.WEIGHT_REPS) assertNotEquals(e.name, Pattern.OTHER, t.pattern)
      if (e.primaryMuscle !in setOf(MuscleGroup.CARDIO, MuscleGroup.FULL_BODY, MuscleGroup.OTHER)) assertTrue(e.name, t.muscle != null)
    }
  }

  @Test
  fun spotChecks() {
    fun p(name: String, eq: Equipment = Equipment.BARBELL, m: MuscleGroup = MuscleGroup.CHEST) = ExerciseTraits.of(name, ExerciseType.WEIGHT_REPS, eq, m)
    assertEquals(Pattern.HORIZONTAL_PUSH, p("Narrow-Grip Bench Press").pattern)
    assertEquals(Pattern.HORIZONTAL_PULL, p("Barbell Bent-Over Row", m = MuscleGroup.UPPER_BACK).pattern)
    assertEquals(Pattern.LATERAL_RAISE, p("Barbell Upright Row", m = MuscleGroup.SHOULDERS).pattern)
    assertEquals(Pattern.LUNGE, p("Dumbbell Bulgarian Split Squat", Equipment.DUMBBELL, MuscleGroup.QUADRICEPS).pattern)
    assertEquals(3, p("Barbell Back Squat", m = MuscleGroup.QUADRICEPS).systemicCost)
    assertEquals(Mechanics.ISOLATION, p("Cable Straight-Arm Pulldown", Equipment.CABLE, MuscleGroup.LATS).mechanics)
    assertEquals(LoadKind.PLATE_LOADED, p("Machine Leg Press", Equipment.MACHINE, MuscleGroup.QUADRICEPS).loadKind)
    assertEquals(TrainingMuscle.BACK, TrainingMuscle.of(MuscleGroup.UPPER_BACK))
    assertNull(TrainingMuscle.of(MuscleGroup.CARDIO))
  }
}
