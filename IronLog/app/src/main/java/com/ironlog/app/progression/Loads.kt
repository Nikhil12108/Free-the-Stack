package com.ironlog.app.progression

import com.ironlog.app.model.WeightUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.serialization.Serializable

/**
 * The weights a lifter can actually load, in the unit their gym uses. Targets are snapped to these,
 * so IronLog never prescribes 101.3 kg. All values are in [unit].
 */
@Serializable
data class EquipmentProfile(
  val unit: WeightUnit = WeightUnit.KG,
  val bar: Double = 20.0,
  /** Plate sizes available in pairs; the smallest one sets the barbell step. */
  val plates: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25),
  val dumbbellStep: Double = 2.5,
  val machineStep: Double = 5.0,
  val cableStep: Double = 2.5,
  val kettlebellStep: Double = 4.0,
) {
  companion object {
    fun defaultFor(unit: WeightUnit): EquipmentProfile =
      if (unit == WeightUnit.KG) EquipmentProfile()
      else EquipmentProfile(unit = WeightUnit.LBS, bar = 45.0, plates = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5), dumbbellStep = 5.0, machineStep = 10.0, cableStep = 5.0, kettlebellStep = 9.0)
  }
}

/** An evenly spaced run of loads from [start] (inclusive) upward in [step]s. */
data class Ladder(val start: Double, val step: Double) {
  private val eps = 1e-6

  /** Largest load on the ladder that is ≤ [x] (never below [start]). */
  fun snapDown(x: Double): Double = if (x <= start + eps) start else start + floor((x - start) / step + eps) * step

  fun snapUp(x: Double): Double = if (x <= start + eps) start else start + ceil((x - start) / step - eps) * step

  fun nearest(x: Double): Double {
    val down = snapDown(x)
    val up = snapUp(x)
    return if (x - down <= up - x) down else up
  }

  /** The next load strictly above [x]. */
  fun nextUp(x: Double): Double = snapDown(x) + step

  /** The next load strictly below [x], or [start]. */
  fun nextDown(x: Double): Double {
    val d = snapUp(x) - step
    return if (d < start - eps) start else d
  }

  fun isOn(x: Double): Boolean = abs(nearest(x) - x) < 1e-3
}

object Loads {
  /** The ladder for one way of loading, in the profile's unit. Null when the exercise takes no load. */
  fun ladder(kind: LoadKind, p: EquipmentProfile): Ladder? {
    val smallestPlate = p.plates.filter { it > 0 }.minOrNull() ?: if (p.unit == WeightUnit.KG) 2.5 else 5.0
    return when (kind) {
      LoadKind.BARBELL -> Ladder(p.bar, smallestPlate * 2)
      LoadKind.PLATE_LOADED -> Ladder(0.0, smallestPlate * 2)
      LoadKind.DUMBBELL -> Ladder(p.dumbbellStep, p.dumbbellStep)
      LoadKind.KETTLEBELL -> Ladder(p.kettlebellStep, p.kettlebellStep)
      LoadKind.MACHINE -> Ladder(p.machineStep, p.machineStep)
      LoadKind.ASSISTANCE -> Ladder(0.0, p.machineStep)
      LoadKind.CABLE -> Ladder(p.cableStep, p.cableStep)
      LoadKind.PLATE -> Ladder(smallestPlate, smallestPlate)
      LoadKind.ADDED_LOAD -> Ladder(0.0, smallestPlate)
      LoadKind.FIXED_STEP -> Ladder(0.0, if (p.unit == WeightUnit.KG) 1.0 else 2.5)
      LoadKind.NONE -> null
    }
  }
}
