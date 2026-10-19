package com.ironlog.app.domain

import com.ironlog.app.model.WeightUnit
import kotlin.math.floor
import kotlin.math.round

/** Works entirely in the user's display unit (kg or lbs). */
object PlateCalculator {
  data class Result(val platesPerSide: List<Pair<Double, Int>>, val achievedTotal: Double, val remainder: Double)

  fun defaultBar(unit: WeightUnit): Double = if (unit == WeightUnit.KG) 20.0 else 45.0

  fun defaultPlates(unit: WeightUnit): List<Double> =
    if (unit == WeightUnit.KG) listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25) else listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)

  /** Greedy from the heaviest plate; [remainder] is what couldn't be loaded (total, not per side). */
  fun calculate(target: Double, bar: Double, plates: List<Double>): Result {
    if (target <= bar) return Result(emptyList(), bar, (target - bar).coerceAtLeast(0.0))
    var perSide = (target - bar) / 2
    val loaded = mutableListOf<Pair<Double, Int>>()
    for (plate in plates.filter { it > 0 }.sortedDescending()) {
      val count = floor(perSide / plate + 1e-9).toInt()
      if (count > 0) {
        loaded += plate to count
        perSide -= plate * count
      }
    }
    val achieved = bar + 2 * loaded.sumOf { it.first * it.second }
    return Result(loaded, achieved, (target - achieved).coerceAtLeast(0.0))
  }
}

/** IronLog's warm-up ramp: empty bar, then 40% / 60% / 80% of the working weight. */
object WarmupCalculator {
  data class WarmupSet(val weight: Double, val reps: Int)

  fun increment(unit: WeightUnit): Double = if (unit == WeightUnit.KG) 2.5 else 5.0

  fun roundTo(value: Double, step: Double): Double = round(value / step) * step

  fun warmups(workingWeight: Double, bar: Double, unit: WeightUnit): List<WarmupSet> {
    if (workingWeight <= 0) return emptyList()
    val step = increment(unit)
    val ramp = listOf(0.4 to 8, 0.6 to 5, 0.8 to 3)
    val sets = mutableListOf<WarmupSet>()
    if (bar > 0 && workingWeight > bar * 1.5) sets += WarmupSet(bar, 10)
    for ((pct, reps) in ramp) {
      val w = roundTo(workingWeight * pct, step).coerceAtLeast(bar)
      if (w < workingWeight && sets.none { it.weight == w }) sets += WarmupSet(w, reps)
    }
    return sets
  }
}
