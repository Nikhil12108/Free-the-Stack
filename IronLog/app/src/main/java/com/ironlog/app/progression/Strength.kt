package com.ironlog.app.progression

import com.ironlog.app.model.SetType
import kotlinx.serialization.Serializable

/** One logged set as the engines see it. Weights are kilograms. */
data class LoggedSet(
  val type: SetType = SetType.NORMAL,
  val weightKg: Double? = null,
  val reps: Int? = null,
  val rir: Int? = null,
  val rpe: Double? = null,
  val targetReps: Int? = null,
  val targetRir: Int? = null,
) {
  val isWorking: Boolean
    get() = type.isWorking
}

/**
 * One past session of one exercise; [sets] in logged order. [light] marks a deliberately easy session
 * (deload week or recovery session), which is never the baseline for the next targets or verdicts.
 */
data class HistorySession(val startTime: Long, val sets: List<LoggedSet>, val light: Boolean = false) {
  val working: List<LoggedSet>
    get() = sets.filter { it.isWorking && it.reps != null }

  companion object {
    /** The sessions to progress from, newest first: hard ones, or the light ones when that is all there is. */
    fun baselines(history: List<HistorySession>): List<HistorySession> {
      val done = history.filter { it.working.isNotEmpty() }
      return done.filterNot { it.light }.ifEmpty { done }
    }
  }
}

@Serializable
enum class Verdict {
  IMPROVED,
  HELD,
  REGRESSED,
}

object Strength {
  /** Assumed reps in reserve when nothing else is known. */
  const val DEFAULT_RIR = 2

  /**
   * Reps left in the tank for a logged set: logged RIR, else from RPE (RIR ≈ 10 − RPE), else 0 for a
   * failure set, else the RIR the set was aiming for, else [default].
   */
  fun effectiveRir(s: LoggedSet, default: Int = DEFAULT_RIR): Int =
    s.rir
      ?: s.rpe?.let { (10.0 - it).coerceIn(0.0, 6.0).toInt() }
      ?: if (s.type == SetType.FAILURE) 0 else s.targetRir ?: default

  /** Strength estimate (e1RM) from load × reps with [rir] left: Epley on reps-to-failure. */
  fun estimate(load: Double, reps: Int, rir: Int): Double = load * (1 + (reps + rir) / 30.0)

  /** Reps achievable at [load] stopping with [rir] left, for strength [e]. */
  fun repsAt(e: Double, load: Double, rir: Int): Double = 30.0 * (e / load - 1) - rir

  /** Load that gives [reps] with [rir] left, for strength [e]. */
  fun loadFor(e: Double, reps: Int, rir: Int): Double = e / (1 + (reps + rir) / 30.0)

  /**
   * The lifter picked [userLoad] instead of the target: the reps that make it the same effort as
   * [targetReps] at [targetLoad], both stopping at [rir].
   */
  fun rematchReps(targetLoad: Double, targetReps: Int, rir: Int, userLoad: Double): Int =
    Math.round(repsAt(estimate(targetLoad, targetReps, rir), userLoad, rir)).toInt().coerceAtLeast(1)

  enum class Outcome {
    PROGRESS,
    HOLD,
    REDUCE,
  }

  /**
   * Was a set easier or harder than planned? Reps plus reps in reserve against the middle of the rep
   * range plus the planned reps in reserve: one or more above means progress, below means ease off.
   */
  fun classify(reps: Int, rir: Int, repMin: Int, repMax: Int, targetRir: Int): Outcome {
    val expected = (repMin + repMax) / 2.0 + targetRir
    val d = reps + rir - expected
    return when {
      d >= 1 - 1e-9 -> Outcome.PROGRESS
      d <= -1 + 1e-9 -> Outcome.REDUCE
      else -> Outcome.HOLD
    }
  }

  /**
   * Compares a set with the same set last time on reps-to-failure-adjusted strength, so 105 × 9 can
   * beat 100 × 10. [totalLoad] maps a set to the load that matters (bodyweight-aware); null = reps only.
   */
  fun verdict(current: LoggedSet, previous: LoggedSet?, totalLoad: (LoggedSet) -> Double?): Verdict? {
    previous ?: return null
    val cr = current.reps ?: return null
    val pr = previous.reps ?: return null
    val cl = totalLoad(current)
    val pl = totalLoad(previous)
    if (cl == null || pl == null || cl <= 0 || pl <= 0) {
      return when {
        cr > pr -> Verdict.IMPROVED
        cr < pr -> Verdict.REGRESSED
        else -> Verdict.HELD
      }
    }
    val ce = estimate(cl, cr, effectiveRir(current))
    val pe = estimate(pl, pr, effectiveRir(previous))
    val change = ce / pe - 1
    return when {
      change >= 0.005 -> Verdict.IMPROVED
      change <= -0.02 -> Verdict.REGRESSED
      else -> Verdict.HELD
    }
  }
}
