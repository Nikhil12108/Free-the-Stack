package com.ironlog.app.domain

import com.ironlog.app.model.ExerciseType
import com.ironlog.app.model.MuscleGroup
import com.ironlog.app.model.SetType
import com.ironlog.app.model.WeightUnit
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsTest {
  @Test
  fun number_stripsZerosAndFloatNoise() {
    assertEquals("100", Units.number(99.99999))
    assertEquals("62.5", Units.number(62.5))
    assertEquals("0.25", Units.number(0.25))
  }

  @Test
  fun weight_roundTripsThroughPounds() {
    val kg = Units.fromDisplayWeight(225.0, WeightUnit.LBS)
    assertEquals("225 lbs", Units.weight(kg, WeightUnit.LBS))
  }

  @Test
  fun parsing() {
    assertEquals(12.5, Units.parseDecimal("12,5")!!, 1e-9)
    assertNull(Units.parseDecimal("abc"))
    assertNull(Units.parseDecimal("-3"))
    assertEquals(90, Units.parseClock("1:30"))
    assertEquals(45, Units.parseClock("45"))
    assertEquals("1:02:05", Units.clock(3725))
    assertEquals("1:15", Units.clock(75))
  }
}

class RecordsTest {
  private val wr = ExerciseType.WEIGHT_REPS

  @Test
  fun epley() {
    assertEquals(100.0, Lifting.estimatedOneRepMax(100.0, 1)!!, 1e-9)
    assertEquals(120.0, Lifting.estimatedOneRepMax(100.0, 6)!!, 1e-9)
    assertNull(Lifting.estimatedOneRepMax(100.0, 25))
    assertNull(Lifting.estimatedOneRepMax(0.0, 5))
  }

  @Test
  fun personalBests_ignoreWarmupsAndTrackSessionVolume() {
    val sessions =
      listOf(
        listOf(SetValues(SetType.WARMUP, 140.0, 5), SetValues(weightKg = 100.0, reps = 5), SetValues(weightKg = 100.0, reps = 5)),
        listOf(SetValues(weightKg = 110.0, reps = 3)),
      )
    val b = Lifting.personalBests(wr, sessions)
    assertEquals(110.0, b.heaviestWeightKg!!, 1e-9)
    assertEquals(1000.0, b.bestSessionVolumeKg!!, 1e-9)
    assertEquals(500.0, b.bestSetVolumeKg!!, 1e-9)
    assertEquals(121.0, b.bestOneRepMaxKg!!, 1e-9)
  }

  @Test
  fun noPrsTheFirstTime() {
    val prs = Lifting.prsInSession(PersonalBests(), wr, listOf(SetValues(weightKg = 50.0, reps = 5)))
    assertEquals(listOf(emptySet<PrKind>()), prs)
  }

  @Test
  fun onlyTheSetThatRaisesTheRecordGetsIt() {
    val history = PersonalBests(heaviestWeightKg = 100.0, bestOneRepMaxKg = 116.0, bestSetVolumeKg = 500.0)
    val prs =
      Lifting.prsInSession(
        history,
        wr,
        listOf(SetValues(weightKg = 105.0, reps = 5), SetValues(weightKg = 105.0, reps = 5), SetValues(SetType.WARMUP, 200.0, 1)),
      )
    assertEquals(setOf(PrKind.WEIGHT, PrKind.ONE_RM, PrKind.SET_VOLUME), prs[0])
    assertTrue(prs[1].isEmpty())
    assertTrue(prs[2].isEmpty())
  }
}

class StatsTest {
  private val zone = ZoneOffset.UTC

  private fun millis(date: String) = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()

  @Test
  fun weeklyBuckets_groupByMondayWeeks() {
    val today = LocalDate.parse("2026-10-18") // a Sunday
    val workouts =
      listOf(
        WorkoutTotals(1, millis("2026-10-12"), millis("2026-10-12") + 3_600_000, 1000.0, 50, 10), // Mon, this week
        WorkoutTotals(2, millis("2026-10-18"), millis("2026-10-18") + 1_800_000, 500.0, 20, 5), // Sun, this week
        WorkoutTotals(3, millis("2026-10-11"), millis("2026-10-11") + 1_800_000, 200.0, 10, 2), // Sun, last week
      )
    val buckets = Stats.weeklyBuckets(workouts, 3, today, zone)
    assertEquals(listOf("2026-09-28", "2026-10-05", "2026-10-12"), buckets.map { it.weekStart.toString() })
    assertEquals(2, buckets[2].workouts)
    assertEquals(5400L, buckets[2].durationSec)
    assertEquals(1, buckets[1].workouts)
  }

  @Test
  fun streak_emptyCurrentWeekDoesNotBreakIt() {
    val today = LocalDate.parse("2026-10-14")
    val dates = listOf("2026-10-08", "2026-10-01", "2026-09-22").map(LocalDate::parse)
    assertEquals(3, Stats.weekStreak(dates, today))
    assertEquals(0, Stats.weekStreak(listOf(LocalDate.parse("2026-09-01")), today))
  }

  @Test
  fun muscleCounts_secondaryCountsHalf() {
    val counts = Stats.muscleSetCounts(listOf(MuscleGroup.CHEST to listOf(MuscleGroup.TRICEPS), MuscleGroup.TRICEPS to emptyList()))
    assertEquals(listOf(MuscleGroup.TRICEPS to 1.5, MuscleGroup.CHEST to 1.0), counts)
  }
}

class CalculatorsTest {
  @Test
  fun plates_greedyPerSide() {
    val r = PlateCalculator.calculate(102.5, 20.0, PlateCalculator.defaultPlates(WeightUnit.KG))
    assertEquals(listOf(25.0 to 1, 15.0 to 1, 1.25 to 1), r.platesPerSide)
    assertEquals(102.5, r.achievedTotal, 1e-9)
    assertEquals(0.0, r.remainder, 1e-9)
  }

  @Test
  fun plates_reportsRemainder() {
    val r = PlateCalculator.calculate(101.0, 20.0, PlateCalculator.defaultPlates(WeightUnit.KG))
    assertEquals(100.0, r.achievedTotal, 1e-9)
    assertEquals(1.0, r.remainder, 1e-9)
  }

  @Test
  fun warmups_rampBelowWorkingWeight() {
    val sets = WarmupCalculator.warmups(100.0, 20.0, WeightUnit.KG)
    assertEquals(listOf(20.0, 40.0, 60.0, 80.0), sets.map { it.weight })
    assertTrue(WarmupCalculator.warmups(20.0, 0.0, WeightUnit.KG).none { it.weight == 0.0 })
  }
}
