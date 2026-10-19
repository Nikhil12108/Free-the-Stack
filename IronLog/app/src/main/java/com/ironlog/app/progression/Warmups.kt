package com.ironlog.app.progression

/**
 * Warm-up sets before working sets, in the display unit. The first exercise for a muscle in a
 * session gets a fuller ramp; later ones need little or none.
 */
object Warmups {
  fun ramp(work: Double, ladder: Ladder?, compound: Boolean, firstForMuscle: Boolean, barStart: Double? = null): List<Pair<Double, Int>> {
    val plan =
      when {
        firstForMuscle && compound -> listOf(0.4 to 8, 0.6 to 5, 0.8 to 3)
        firstForMuscle -> listOf(0.6 to 8)
        compound -> listOf(0.7 to 3)
        else -> emptyList()
      }
    val floor = barStart ?: ladder?.start ?: 0.0
    val sets = mutableListOf<Pair<Double, Int>>()
    for ((pct, reps) in plan) {
      val raw = work * pct
      val load = (ladder?.snapDown(raw) ?: raw).coerceAtLeast(floor)
      if (load <= 0 || load >= work - 1e-6 || sets.any { kotlin.math.abs(it.first - load) < 1e-6 }) continue
      sets += load to reps
    }
    return sets
  }
}
