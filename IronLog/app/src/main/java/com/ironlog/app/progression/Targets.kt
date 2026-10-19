package com.ironlog.app.progression

import com.ironlog.app.model.ExerciseType
import com.ironlog.app.model.WeightUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/** How an exercise's targets move from one session to the next. */
@Serializable
enum class ProgressionRule(val label: String, val description: String) {
  SMART("Smart", "Adds reps, then weight when you reach the top of the range — sized to your equipment and how hard the sets were."),
  DOUBLE("Double progression", "Add a rep each time; once every set hits the top of the range, add weight and start again at the bottom."),
  LINEAR("Linear", "Add weight every time all sets hit their reps."),
  OFF("Off", "No targets — repeat last time's numbers."),
}

@Serializable
enum class Experience(val label: String, val weeklyGain: Double, val stepScale: Double) {
  BEGINNER("Beginner (under a year)", 0.04, 1.5),
  INTERMEDIATE("Intermediate (1–4 years)", 0.025, 1.0),
  ADVANCED("Advanced (4+ years)", 0.01, 0.75),
}

enum class TargetMode {
  /** Free routines: reps first, weight once the top of the range is reached. */
  ROUTINE,
  /** Planned weeks with a falling RIR: keep reps and add load when possible. */
  PLAN,
  DELOAD,
  RECOVERY,
}

/** A target for one working set. [weightKg] uses the exercise's own convention (added load, assistance). */
@Serializable
data class SetTarget(val weightKg: Double? = null, val reps: Int? = null, val rir: Int? = null)

@Serializable
enum class ReasonCode {
  CALIBRATE,
  LOAD_UP,
  REPS_UP,
  BIG_STEP,
  CATCH_UP,
  RANGE_DOWN,
  HOLD,
  RETRY,
  REDUCE,
  REPEAT,
  DELOAD,
  RECOVERY,
}

/** Why the targets are what they are; [delta] is in the display unit, [percent] a load step. */
@Serializable
data class TargetReason(val code: ReasonCode, val delta: Double? = null, val percent: Int? = null, val reps: Int? = null)

data class TargetRequest(
  val type: ExerciseType,
  val traits: ExerciseTraits,
  val rule: ProgressionRule,
  val repMin: Int,
  val repMax: Int,
  val rir: Int,
  val workingSets: Int,
  /** Past sessions of this exercise, newest first. */
  val history: List<HistorySession>,
  val nowMillis: Long,
  val bodyweightKg: Double?,
  val profile: EquipmentProfile,
  val experience: Experience,
  val mode: TargetMode = TargetMode.ROUTINE,
  /** Weights stored in the routine, used when there is no history yet. */
  val fallbackWeightsKg: List<Double?> = emptyList(),
  /** Week-1 sets of the current plan, for the deload's loads. */
  val deloadReference: List<LoggedSet> = emptyList(),
)

data class TargetResult(val sets: List<SetTarget>, val reason: TargetReason)

object Targets {
  private const val WEEK_MS = 7 * 24 * 60 * 60 * 1000.0
  /** How far past the top of the range reps may go when the next weight is too big a jump. */
  private const val EXPAND = 4

  /** Default rep range when a routine doesn't set one. */
  fun defaultRange(traits: ExerciseTraits): IntRange =
    when {
      traits.pattern in setOf(Pattern.CALF_RAISE, Pattern.LATERAL_RAISE, Pattern.CORE_FLEXION, Pattern.WRIST, Pattern.REAR_DELT) -> 12..20
      traits.systemicCost >= 3 -> 5..8
      traits.isCompound -> 6..10
      else -> 10..15
    }

  /** Largest load jump (fraction) one step may make before IronLog adds reps instead. */
  fun stepCap(kind: LoadKind, experience: Experience): Double {
    val base =
      when (kind) {
        LoadKind.BARBELL -> 0.05
        LoadKind.PLATE_LOADED,
        LoadKind.MACHINE,
        LoadKind.CABLE -> 0.075
        LoadKind.DUMBBELL,
        LoadKind.ADDED_LOAD,
        LoadKind.ASSISTANCE,
        LoadKind.FIXED_STEP -> 0.10
        LoadKind.KETTLEBELL,
        LoadKind.PLATE -> 0.15
        LoadKind.NONE -> 0.0
      }
    return base * experience.stepScale
  }

  fun compute(req: TargetRequest): TargetResult {
    val factor = if (req.profile.unit == WeightUnit.LBS) LB_PER_KG else 1.0
    val toDisplay = { kg: Double -> kg * factor }
    val toKg = { d: Double -> d / factor }
    val ladder = Loads.ladder(req.traits.loadKind, req.profile)
    val bw = req.bodyweightKg?.let(toDisplay)
    val axis = ladder?.let { Axis(req.traits.loadKind, it, bw) }
    // Deload and recovery sessions are deliberately easy: progress from the last real one.
    val sessions = HistorySession.baselines(req.history)
    val prev = sessions.getOrNull(0)?.working.orEmpty()
    val prev2 = sessions.getOrNull(1)?.working.orEmpty()
    val n = req.workingSets.coerceAtLeast(0)
    val lo = req.repMin.coerceAtLeast(1)
    val hi = req.repMax.coerceAtLeast(lo)
    val mid = (lo + hi) / 2
    val weeks = sessions.firstOrNull()?.let { ((req.nowMillis - it.startTime) / WEEK_MS).coerceIn(0.0, 2.0) } ?: 0.0
    val gainWeeks = if (req.mode == TargetMode.PLAN) weeks.coerceAtLeast(1.0) else weeks
    val growth = 1 + req.experience.weeklyGain * gainWeeks
    val cap = stepCap(req.traits.loadKind, req.experience) + 1e-9

    if (prev.isEmpty()) {
      val sets = List(n) { i -> SetTarget(req.fallbackWeightsKg.getOrNull(i) ?: req.fallbackWeightsKg.lastOrNull(), null, req.rir) }
      return TargetResult(sets, TargetReason(ReasonCode.CALIBRATE))
    }

    val reasons = mutableListOf<TargetReason>()
    val sets =
      List(n) { i ->
        val p = prev.getOrNull(i) ?: prev.last()
        val isExtra = i >= prev.size
        val pValue = p.weightKg?.let(toDisplay)
        val pReps = p.reps ?: 0
        val pTotal = pValue?.let { v -> axis?.total(v) }
        val hitTarget = p.targetReps == null || pReps >= p.targetReps - 1
        val p2 = prev2.getOrNull(i)
        val missedTwice = !hitTarget && p2 != null && p2.targetReps != null && (p2.reps ?: 0) < p2.targetReps - 1
        val result: Pair<SetTarget, TargetReason> =
          when {
            req.mode == TargetMode.DELOAD -> {
              val ref = req.deloadReference.getOrNull(i) ?: req.deloadReference.lastOrNull()
              val refValue = ref?.weightKg?.let(toDisplay) ?: pValue?.let { v -> axis?.lighterTotal(v, 0.9) ?: v }
              val refReps = ref?.reps ?: pReps
              SetTarget(refValue?.let(toKg), ceil(refReps / 2.0).toInt().coerceAtLeast(1), 5) to TargetReason(ReasonCode.DELOAD)
            }
            req.mode == TargetMode.RECOVERY -> {
              val v = pValue?.let { axis?.lighterTotal(it, 0.5) ?: it }
              SetTarget(v?.let(toKg), ceil(pReps / 2.0).toInt().coerceAtLeast(1), 4) to TargetReason(ReasonCode.RECOVERY)
            }
            req.rule == ProgressionRule.OFF -> SetTarget(p.weightKg, p.reps, null) to TargetReason(ReasonCode.REPEAT)
            isExtra -> SetTarget(p.weightKg, null, req.rir) to TargetReason(ReasonCode.HOLD)
            axis == null || pValue == null || pTotal == null || pTotal <= 0 -> repsOnly(p, pReps, hitTarget, req)
            else -> {
              val e = Strength.estimate(pTotal, pReps, Strength.effectiveRir(p, req.rir)) * growth
              when (req.rule) {
                ProgressionRule.DOUBLE -> double(prev, p, pValue, pReps, axis, lo, hi, toKg)
                ProgressionRule.LINEAR -> linear(prev, p, pValue, pReps, axis, hitTarget, missedTwice, toKg, req.rir)
                else ->
                  if (req.mode == TargetMode.PLAN) planStep(p, pValue, pReps, pTotal, e, axis, lo, hi, mid, cap, hitTarget, req.rir, toKg)
                  else routineStep(p, pValue, pReps, pTotal, e, axis, lo, hi, mid, cap, hitTarget, missedTwice, req.rir, toKg)
              }
            }
          }
        reasons += result.second
        result.first
      }
    return TargetResult(sets, summarize(reasons))
  }

  private fun repsOnly(p: LoggedSet, pReps: Int, hitTarget: Boolean, req: TargetRequest): Pair<SetTarget, TargetReason> =
    if (hitTarget) SetTarget(p.weightKg, pReps + 1, req.rir) to TargetReason(ReasonCode.REPS_UP, reps = pReps + 1)
    else SetTarget(p.weightKg, p.targetReps ?: pReps, req.rir) to TargetReason(ReasonCode.RETRY)

  private fun routineStep(
    p: LoggedSet,
    pValue: Double,
    pReps: Int,
    pTotal: Double,
    e: Double,
    axis: Axis,
    lo: Int,
    hi: Int,
    mid: Int,
    cap: Double,
    hitTarget: Boolean,
    missedTwice: Boolean,
    rir: Int,
    toKg: (Double) -> Double,
  ): Pair<SetTarget, TargetReason> {
    val loggedEffort = p.rir != null || p.rpe != null
    return when {
      pReps < lo -> rangeDown(pValue, pTotal, e, axis, lo, rir, toKg)
      missedTwice -> SetTarget(toKg(axis.lighter(pValue)), maxOf(lo, pReps), rir) to TargetReason(ReasonCode.REDUCE)
      !hitTarget -> SetTarget(toKg(pValue), p.targetReps ?: pReps, rir) to TargetReason(ReasonCode.RETRY)
      loggedEffort && Strength.effectiveRir(p) >= rir + 3 -> catchUp(pValue, pTotal, e, axis, lo, hi, mid, rir, toKg) ?: loadUp(pValue, pReps, pTotal, e, axis, lo, hi, cap, rir, toKg)
      pReps >= hi -> loadUp(pValue, pReps, pTotal, e, axis, lo, hi, cap, rir, toKg)
      else -> SetTarget(toKg(pValue), pReps + 1, rir) to TargetReason(ReasonCode.REPS_UP, reps = pReps + 1)
    }
  }

  private fun planStep(
    p: LoggedSet,
    pValue: Double,
    pReps: Int,
    pTotal: Double,
    e: Double,
    axis: Axis,
    lo: Int,
    hi: Int,
    mid: Int,
    cap: Double,
    hitTarget: Boolean,
    rir: Int,
    toKg: (Double) -> Double,
  ): Pair<SetTarget, TargetReason> {
    if (pReps > hi + 1) return catchUp(pValue, pTotal, e, axis, lo, hi, mid, rir, toKg) ?: loadUp(pValue, pReps, pTotal, e, axis, lo, hi, cap, rir, toKg)
    if (pReps < lo) return rangeDown(pValue, pTotal, e, axis, lo, rir, toKg)
    if (!hitTarget) return SetTarget(toKg(pValue), p.targetReps ?: pReps, rir) to TargetReason(ReasonCode.RETRY)
    val next = axis.heavier(pValue)
    // Nothing heavier on this axis (an assisted lift with no assistance left): only reps can grow.
    val canLoad = next != pValue
    val nextTotal = if (canLoad) axis.total(next) else null
    val step = if (nextTotal != null) nextTotal / pTotal - 1 else Double.MAX_VALUE
    // Keep the reps and take the heaviest step within the cap that the model says still allows them.
    var best: Double? = null
    var candidate = next
    var candidateTotal = nextTotal
    while (candidateTotal != null && candidateTotal / pTotal - 1 <= cap && Strength.repsAt(e, candidateTotal, rir) >= pReps - 0.5) {
      best = candidate
      val following = axis.heavier(candidate)
      if (following == candidate) break
      candidate = following
      candidateTotal = axis.total(candidate)
    }
    if (best != null) {
      val bestTotal = axis.total(best) ?: pTotal
      return SetTarget(toKg(best), pReps, rir) to TargetReason(ReasonCode.LOAD_UP, delta = best - pValue, percent = ((bestTotal / pTotal - 1) * 100).roundToInt())
    }
    if (Strength.repsAt(e, pTotal, rir) >= pReps + 0.5 && pReps + 1 <= hi + EXPAND) {
      val bigStep = canLoad && step > cap
      val code = if (bigStep) ReasonCode.BIG_STEP else ReasonCode.REPS_UP
      return SetTarget(toKg(pValue), pReps + 1, rir) to TargetReason(code, percent = if (bigStep && step < 10) (step * 100).roundToInt() else null, reps = pReps + 1)
    }
    return SetTarget(toKg(pValue), pReps, rir) to TargetReason(ReasonCode.HOLD)
  }

  /** Top of the range reached: the next weight if it is a sensible step, otherwise one more rep. */
  private fun loadUp(pValue: Double, pReps: Int, pTotal: Double, e: Double, axis: Axis, lo: Int, hi: Int, cap: Double, rir: Int, toKg: (Double) -> Double): Pair<SetTarget, TargetReason> {
    val next = axis.heavier(pValue)
    val canLoad = next != pValue
    val nextTotal = if (canLoad) axis.total(next) else null
    val step = if (nextTotal != null) nextTotal / pTotal - 1 else Double.MAX_VALUE
    if (nextTotal != null && step <= cap) {
      val reps = floor(Strength.repsAt(e, nextTotal, rir)).toInt().coerceIn(lo, hi)
      return SetTarget(toKg(next), reps, rir) to TargetReason(ReasonCode.LOAD_UP, delta = next - pValue, percent = (step * 100).roundToInt())
    }
    return if (pReps + 1 <= hi + EXPAND) {
      val reason = if (canLoad) TargetReason(ReasonCode.BIG_STEP, percent = if (step < 10) (step * 100).roundToInt() else null, reps = pReps + 1) else TargetReason(ReasonCode.REPS_UP, reps = pReps + 1)
      SetTarget(toKg(pValue), pReps + 1, rir) to reason
    } else {
      SetTarget(toKg(pValue), pReps, rir) to TargetReason(ReasonCode.HOLD)
    }
  }

  /** Far more in reserve than intended: jump to the load that lands mid-range, at most +15 %. */
  private fun catchUp(pValue: Double, pTotal: Double, e: Double, axis: Axis, lo: Int, hi: Int, mid: Int, rir: Int, toKg: (Double) -> Double): Pair<SetTarget, TargetReason>? {
    val wanted = minOf(Strength.loadFor(e, mid, rir), pTotal * 1.15)
    val value = axis.snapTotalDown(wanted) ?: return null
    val total = axis.total(value) ?: return null
    if (total <= pTotal + 1e-6) return null
    val reps = floor(Strength.repsAt(e, total, rir)).toInt().coerceIn(lo, hi)
    return SetTarget(toKg(value), reps, rir) to TargetReason(ReasonCode.CATCH_UP, delta = value - pValue, percent = ((total / pTotal - 1) * 100).roundToInt())
  }

  /** Reps fell below the range: the load that should give the bottom of it. */
  private fun rangeDown(pValue: Double, pTotal: Double, e: Double, axis: Axis, lo: Int, rir: Int, toKg: (Double) -> Double): Pair<SetTarget, TargetReason> {
    val v = axis.snapTotalDown(Strength.loadFor(e, lo, rir))
    val total = v?.let { axis.total(it) }
    return if (v != null && total != null && total < pTotal - 1e-6) SetTarget(toKg(v), lo, rir) to TargetReason(ReasonCode.RANGE_DOWN, delta = v - pValue)
    else SetTarget(toKg(pValue), lo, rir) to TargetReason(ReasonCode.RETRY)
  }

  private fun double(prev: List<LoggedSet>, p: LoggedSet, pValue: Double, pReps: Int, axis: Axis, lo: Int, hi: Int, toKg: (Double) -> Double): Pair<SetTarget, TargetReason> =
    if (prev.all { (it.reps ?: 0) >= hi }) {
      val next = axis.heavier(pValue)
      if (next != pValue) {
        SetTarget(toKg(next), lo, null) to TargetReason(ReasonCode.LOAD_UP, delta = next - pValue)
      } else {
        // Nothing heavier to move to: keep adding reps past the range, up to a limit.
        val reps = minOf(pReps + 1, hi + EXPAND)
        SetTarget(toKg(pValue), reps, null) to TargetReason(if (reps > pReps) ReasonCode.REPS_UP else ReasonCode.HOLD, reps = reps)
      }
    } else {
      SetTarget(toKg(pValue), minOf(pReps + 1, hi), null) to TargetReason(if (pReps >= hi) ReasonCode.HOLD else ReasonCode.REPS_UP, reps = minOf(pReps + 1, hi))
    }

  private fun linear(prev: List<LoggedSet>, p: LoggedSet, pValue: Double, pReps: Int, axis: Axis, hitTarget: Boolean, missedTwice: Boolean, toKg: (Double) -> Double, rir: Int): Pair<SetTarget, TargetReason> {
    val target = p.targetReps ?: pReps
    return when {
      missedTwice -> SetTarget(toKg(axis.lighter(pValue)), target, rir) to TargetReason(ReasonCode.REDUCE)
      prev.all { it.targetReps == null || (it.reps ?: 0) >= it.targetReps } && hitTarget -> {
        val next = axis.heavier(pValue)
        if (next != pValue) SetTarget(toKg(next), target, rir) to TargetReason(ReasonCode.LOAD_UP, delta = next - pValue)
        else SetTarget(toKg(pValue), target + 1, rir) to TargetReason(ReasonCode.REPS_UP, reps = target + 1)
      }
      else -> SetTarget(toKg(pValue), target, rir) to TargetReason(ReasonCode.RETRY)
    }
  }

  /** One reason for the whole exercise: the most important thing that happened to any set. */
  private fun summarize(reasons: List<TargetReason>): TargetReason {
    val order =
      listOf(ReasonCode.DELOAD, ReasonCode.RECOVERY, ReasonCode.REDUCE, ReasonCode.RANGE_DOWN, ReasonCode.RETRY, ReasonCode.CATCH_UP, ReasonCode.LOAD_UP, ReasonCode.BIG_STEP, ReasonCode.REPS_UP, ReasonCode.REPEAT, ReasonCode.HOLD, ReasonCode.CALIBRATE)
    return order.firstNotNullOfOrNull { code -> reasons.firstOrNull { it.code == code } } ?: TargetReason(ReasonCode.HOLD)
  }

  const val LB_PER_KG = 2.20462262185
}

/**
 * The load axis of one exercise in display units. The user enters a weight, an added load or an
 * assistance amount; the strength model needs the total. "Heavier" always means harder.
 */
internal class Axis(private val kind: LoadKind, private val ladder: Ladder, private val bodyweight: Double?) {
  fun total(value: Double): Double? =
    when (kind) {
      LoadKind.ADDED_LOAD -> bodyweight?.plus(value)
      LoadKind.ASSISTANCE -> bodyweight?.minus(value)
      else -> value
    }

  fun heavier(value: Double): Double = if (kind == LoadKind.ASSISTANCE) ladder.nextDown(value) else ladder.nextUp(value)

  fun lighter(value: Double): Double = if (kind == LoadKind.ASSISTANCE) ladder.nextUp(value) else ladder.nextDown(value)

  /** The heaviest feasible value whose total is ≤ [total]; null if bodyweight is needed but unknown. */
  fun snapTotalDown(total: Double): Double? =
    when (kind) {
      LoadKind.ADDED_LOAD -> bodyweight?.let { ladder.snapDown(total - it) }
      LoadKind.ASSISTANCE -> bodyweight?.let { ladder.snapUp(it - total) }
      else -> ladder.snapDown(total)
    }

  /**
   * A lighter version of [value]: its total times [fraction], snapped down. Without bodyweight the total
   * is unknown; an assisted lift then keeps its assistance (less would make it harder) and only the reps drop.
   */
  fun lighterTotal(value: Double, fraction: Double): Double =
    total(value)?.let { snapTotalDown(it * fraction) } ?: if (kind == LoadKind.ASSISTANCE) value else ladder.snapDown(value * fraction)
}
