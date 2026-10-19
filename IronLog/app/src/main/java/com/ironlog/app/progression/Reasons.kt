package com.ironlog.app.progression

import com.ironlog.app.domain.Units
import com.ironlog.app.model.WeightUnit

/** One plain sentence explaining a target, shown under the exercise name. */
fun TargetReason.describe(unit: WeightUnit, rir: Int?): String {
  val d = delta?.let { kotlin.math.abs(it) }?.let { "${Units.number(it)} ${unit.label}" }
  return when (code) {
    ReasonCode.CALIBRATE ->
      when (val r = rir ?: Strength.DEFAULT_RIR) {
        0 -> "First time here: pick a weight you can only just finish the reps with."
        1 -> "First time here: pick a weight that leaves about 1 rep in the tank."
        else -> "First time here: pick a weight that leaves about $r reps in the tank."
      }
    ReasonCode.LOAD_UP -> "Up ${d ?: "a step"} — you earned it last time."
    ReasonCode.REPS_UP -> "One more rep than last time."
    ReasonCode.BIG_STEP -> "One more rep — the next weight up is a big jump${percent?.let { " (+$it%)" } ?: ""}."
    ReasonCode.CATCH_UP -> "Up ${d ?: "a step"} — last time was much easier than planned."
    ReasonCode.RANGE_DOWN -> "Down ${d ?: "a step"} to get back into your rep range."
    ReasonCode.HOLD -> "Same as last time — add a rep if it moves well."
    ReasonCode.RETRY -> "Same weight — another go at last time's target."
    ReasonCode.REDUCE -> "One step lighter after two sessions short of target."
    ReasonCode.REPEAT -> "Last time's numbers (targets are off for this exercise)."
    ReasonCode.DELOAD -> "Deload: week-1 weight, about half the reps. Finish fresh."
    ReasonCode.RECOVERY -> "Recovery session: half the work, keep every set easy."
  }
}
