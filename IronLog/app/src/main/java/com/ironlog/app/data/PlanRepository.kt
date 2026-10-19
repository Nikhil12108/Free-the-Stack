package com.ironlog.app.data

import androidx.room.withTransaction
import com.ironlog.app.data.db.FeedbackMode
import com.ironlog.app.data.db.IronLogDatabase
import com.ironlog.app.data.db.MuscleFeedbackEntity
import com.ironlog.app.data.db.PlanDayEntity
import com.ironlog.app.data.db.PlanDayWithSlots
import com.ironlog.app.data.db.PlanEntity
import com.ironlog.app.data.db.PlanPriorityEntity
import com.ironlog.app.data.db.PlanSessionEntity
import com.ironlog.app.data.db.PlanSessionSlotEntity
import com.ironlog.app.data.db.PlanSlotEntity
import com.ironlog.app.data.db.PlanSlotWithExercise
import com.ironlog.app.data.db.PlanStatus
import com.ironlog.app.data.db.SessionStatus
import com.ironlog.app.progression.ExerciseTraits
import com.ironlog.app.progression.LoggedSet
import com.ironlog.app.progression.Periodization
import com.ironlog.app.progression.Priority
import com.ironlog.app.progression.TargetMode
import com.ironlog.app.progression.Targets
import com.ironlog.app.progression.TrainingMuscle
import com.ironlog.app.progression.Verdict
import com.ironlog.app.progression.Volume
import com.ironlog.app.workout.ActiveWorkout
import com.ironlog.app.workout.PlanLink
import com.ironlog.app.workout.SessionExercise
import com.ironlog.app.workout.SessionSet
import com.ironlog.app.workout.TargetPlanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

data class SlotDraft(
  val exerciseId: Long,
  val muscle: TrainingMuscle?,
  val repMin: Int,
  val repMax: Int,
  val sets: Int,
  val supersetGroup: Int? = null,
  val restSeconds: Int? = null,
  val notes: String = "",
)

data class DayDraft(val name: String, val weekday: Int?, val slots: List<SlotDraft>)

data class PlanDraft(
  val name: String,
  val weeks: Int,
  val feedbackMode: FeedbackMode,
  val days: List<DayDraft>,
  val priorities: Map<TrainingMuscle, Priority> = emptyMap(),
  val sourcePlanId: Long? = null,
)

/** A planned session ready to start: exercises already carry their targets. */
data class PreparedSession(val name: String, val link: PlanLink, val exercises: List<SessionExercise>)

/** Everything a plan screen shows, assembled from the plan tables. */
data class PlanOverview(
  val plan: PlanEntity,
  val days: List<PlanDayWithSlots>,
  val sessions: List<PlanSessionEntity>,
  /** sessionId -> slotId -> planned sets. */
  val sessionSlots: Map<Long, Map<Long, PlanSessionSlotEntity>>,
  val priorities: Map<TrainingMuscle, Priority>,
  val feedback: Map<Long, List<MuscleFeedbackEntity>>,
) {
  val schedule: List<Int> = Periodization.decode(plan.rirSchedule)
  private val dayPosition = days.associate { it.day.id to it.day.position }
  val ordered: List<PlanSessionEntity> = sessions.sortedWith(compareBy({ it.week }, { dayPosition[it.planDayId] ?: 0 }))
  val next: PlanSessionEntity? = ordered.firstOrNull { it.status == SessionStatus.PENDING }
  val currentWeek: Int = next?.week ?: (plan.weeks - 1)
  val isActive: Boolean
    get() = plan.status == PlanStatus.ACTIVE

  fun rir(week: Int): Int = Periodization.rirFor(week, plan.weeks, schedule)

  fun isDeload(week: Int): Boolean = Periodization.isDeload(week, plan.weeks)

  fun day(id: Long): PlanDayWithSlots? = days.firstOrNull { it.day.id == id }

  fun week(w: Int): List<PlanSessionEntity> = ordered.filter { it.week == w }

  fun plannedSets(session: PlanSessionEntity, slot: PlanSlotEntity): Int? = sessionSlots[session.id]?.get(slot.id)?.sets

  /** Muscles in this plan, in display order. */
  val muscles: List<TrainingMuscle> = days.flatMap { d -> d.slots.mapNotNull { it.slot.muscle } }.distinct().sortedBy { it.ordinal }

  fun sessionsPerWeek(m: TrainingMuscle): Int = days.count { d -> d.slots.any { it.slot.muscle == m } }

  /** Planned sets per muscle in week [w], counting only sessions whose sets are already decided. */
  fun weeklySets(w: Int): Map<TrainingMuscle, Int> {
    val out = mutableMapOf<TrainingMuscle, Int>()
    for (s in week(w)) {
      val rows = sessionSlots[s.id] ?: continue
      for (d in days) for (sw in d.slots) {
        val m = sw.slot.muscle ?: continue
        val r = rows[sw.slot.id] ?: continue
        out[m] = (out[m] ?: 0) + r.sets
      }
    }
    return out
  }
}

/**
 * Training blocks: creation, preparing each session with its week's targets, and shaping the next
 * week from what was logged and answered. Recalculations are pure functions of stored inputs, so they
 * can be re-run whenever a late answer (soreness) arrives.
 */
class PlanRepository(
  private val db: IronLogDatabase,
  private val workouts: WorkoutRepository,
  private val routines: RoutineRepository,
  private val settings: SettingsStore,
  private val measurements: MeasurementRepository,
) {
  private val dao = db.plans()

  val active: Flow<PlanEntity?> = dao.observeActive()
  val plans: Flow<List<PlanEntity>> = dao.observePlans()

  fun observeOverview(planId: Long): Flow<PlanOverview?> {
    val structure = combine(dao.observePlan(planId), dao.observeDays(planId), dao.observeSessions(planId)) { p, d, s -> Triple(p, d, s) }
    val state = combine(dao.observeAllSessionSlots(planId), dao.observePriorities(planId), dao.observePlanFeedback(planId)) { ss, pr, fb -> Triple(ss, pr, fb) }
    return combine(structure, state) { (p, d, s), (ss, pr, fb) -> p?.let { overviewOf(it, d, s, ss, pr, fb) } }
  }

  suspend fun overview(planId: Long): PlanOverview? {
    val p = dao.getPlan(planId) ?: return null
    return overviewOf(p, dao.getDays(planId), dao.getSessions(planId), dao.getAllSessionSlots(planId), dao.getPriorities(planId), dao.getPlanFeedback(planId))
  }

  private fun overviewOf(
    p: PlanEntity,
    d: List<PlanDayWithSlots>,
    s: List<PlanSessionEntity>,
    ss: List<PlanSessionSlotEntity>,
    pr: List<PlanPriorityEntity>,
    fb: List<MuscleFeedbackEntity>,
  ) = PlanOverview(p, d.sortedBy { it.day.position }, s, ss.groupBy { it.sessionId }.mapValues { e -> e.value.associateBy { it.slotId } }, pr.associate { it.muscle to it.priority }, fb.groupBy { it.sessionId })

  // ---- Creation --------------------------------------------------------------------------------

  /** One training day per routine, in the order given; the routines' set counts are week 1. */
  suspend fun draftFromRoutines(name: String, routineIds: List<Long>, weeks: Int, mode: FeedbackMode): PlanDraft {
    val days =
      routineIds.mapNotNull { routines.get(it) }.map { r ->
        DayDraft(
          name = r.routine.name,
          weekday = null,
          slots =
            r.orderedExercises.map { e ->
              val working = e.sets.filter { it.type.isWorking }.sortedBy { it.position }
              val first = working.firstOrNull()
              val range =
                when {
                  first?.repMin != null && first.repMax != null && first.repMax >= first.repMin -> first.repMin..first.repMax
                  first?.reps != null && first.reps > 0 -> first.reps..(first.reps + 2)
                  else -> Targets.defaultRange(ExerciseTraits.of(e.exercise))
                }
              SlotDraft(e.exercise.id, TrainingMuscle.of(e.exercise.primaryMuscle), range.first, range.last, working.size.coerceAtLeast(1), e.entry.supersetGroup, e.entry.restSeconds, e.entry.notes)
            },
        )
      }
    return PlanDraft(name, weeks, mode, days)
  }

  /** Copies a finished plan's structure; week 1 starts from its own week 1 sets. */
  suspend fun draftFromPlan(planId: Long, name: String, weeks: Int, mode: FeedbackMode): PlanDraft? {
    val ov = overview(planId) ?: return null
    val week0 = ov.week(0)
    val days =
      ov.days.map { d ->
        val session = week0.firstOrNull { it.planDayId == d.day.id }
        DayDraft(
          d.day.name,
          d.day.weekday,
          d.orderedSlots.map { sw ->
            val sets = session?.let { ov.plannedSets(it, sw.slot) } ?: sw.slot.startSets
            SlotDraft(sw.slot.exerciseId, sw.slot.muscle, sw.slot.repMin, sw.slot.repMax, sets.coerceAtLeast(1), sw.slot.supersetGroup, sw.slot.restSeconds, sw.slot.notes)
          },
        )
      }
    return PlanDraft(name, weeks, mode, days, ov.priorities, sourcePlanId = planId)
  }

  /** Saves a plan and makes it the active one (any previous active plan is ended). */
  suspend fun create(draft: PlanDraft): Long =
    db.withTransaction {
      dao.getActive()?.let { endPlanInTransaction(it) }
      val now = System.currentTimeMillis()
      val weeks = draft.weeks.coerceIn(Periodization.MIN_WEEKS, Periodization.MAX_WEEKS)
      val planId =
        dao.insertPlan(
          PlanEntity(
            name = draft.name.ifBlank { "Training block" },
            weeks = weeks,
            rirSchedule = Periodization.encode(Periodization.rirSchedule(weeks)),
            feedbackMode = draft.feedbackMode,
            createdAt = now,
            sourcePlanId = draft.sourcePlanId,
          )
        )
      val dayIds = draft.days.mapIndexed { i, d -> dao.insertDay(PlanDayEntity(planId = planId, position = i, name = d.name.ifBlank { "Day ${i + 1}" }, weekday = d.weekday)) }
      val slotSets =
        draft.days.mapIndexed { i, d ->
          d.slots.mapIndexed { j, s ->
            val sets = s.sets.coerceIn(1, 20)
            dao.insertSlot(
              PlanSlotEntity(
                planDayId = dayIds[i],
                position = j,
                exerciseId = s.exerciseId,
                muscle = s.muscle,
                repMin = s.repMin,
                repMax = s.repMax,
                startSets = sets,
                supersetGroup = s.supersetGroup,
                restSeconds = s.restSeconds,
                notes = s.notes,
              )
            ) to sets
          }
        }
      val muscles = draft.days.flatMap { d -> d.slots.mapNotNull { it.muscle } }.distinct()
      dao.upsertPriorities(muscles.map { PlanPriorityEntity(planId, it, draft.priorities[it] ?: Priority.GROW) })
      val sessionIds = dao.insertSessions((0 until weeks).flatMap { w -> dayIds.map { PlanSessionEntity(planId = planId, week = w, planDayId = it) } })
      val week0 = sessionIds.take(dayIds.size)
      dao.upsertSessionSlots(week0.flatMapIndexed { i, sid -> slotSets[i].map { (slotId, sets) -> PlanSessionSlotEntity(sid, slotId, sets) } })
      planId
    }

  // ---- Sessions ----------------------------------------------------------------------------------

  /** Builds the workout for [sessionId]: this week's sets per exercise and targets at this week's RIR. */
  suspend fun prepare(sessionId: Long): PreparedSession? {
    val session = dao.getSession(sessionId) ?: return null
    val plan = dao.getPlan(session.planId) ?: return null
    val days = dao.getDays(plan.id)
    val day = days.firstOrNull { it.day.id == session.planDayId } ?: return null
    ensureSessionSlots(session, plan, days)
    val rows = dao.getSessionSlots(sessionId).associateBy { it.slotId }
    val schedule = Periodization.decode(plan.rirSchedule)
    val deload = Periodization.isDeload(session.week, plan.weeks)
    val rir = Periodization.rirFor(session.week, plan.weeks, schedule)
    val s = settings.current
    val bw = measurements.latestBodyweightKg()
    val now = System.currentTimeMillis()
    val reference = if (deload) weekOneSets(plan.id, session.planDayId) else emptyMap()
    val exercises =
      day.orderedSlots.mapNotNull { sw ->
        val row = rows[sw.slot.id]
        val n = row?.sets ?: sw.slot.startSets
        if (n <= 0) return@mapNotNull null
        val traits = ExerciseTraits.of(sw.exercise)
        val rest = sw.slot.restSeconds ?: TargetPlanner.restFor(sw.exercise, s)
        val base =
          SessionExercise.from(sw.exercise, rest)
            .copy(
              supersetGroup = sw.slot.supersetGroup,
              notes = sw.slot.notes,
              planSlotId = sw.slot.id,
              recovery = row?.recovery == true,
              sets = List(n) { SessionSet(repMin = sw.slot.repMin, repMax = sw.slot.repMax) },
            )
        val weekRir = if (deload) Periodization.DELOAD_RIR else Periodization.safeRir(rir, traits)
        TargetPlanner.apply(base, workouts.historySessions(sw.exercise.id), bw, s, now, if (deload) TargetMode.DELOAD else TargetMode.PLAN, weekRir, reference[sw.exercise.id].orEmpty())
      }
    val link = PlanLink(plan.id, session.id, session.week, plan.weeks, rir, deload, day.day.name, plan.feedbackMode)
    return PreparedSession(day.day.name, link, TargetPlanner.withWarmups(exercises, s))
  }

  /** Working sets logged in week 1 of this day, per exercise: the deload's reference loads. */
  private suspend fun weekOneSets(planId: Long, dayId: Long): Map<Long, List<LoggedSet>> {
    val first = dao.getSessions(planId).firstOrNull { it.week == 0 && it.planDayId == dayId } ?: return emptyMap()
    val w = first.workoutId?.let { workouts.get(it) } ?: return emptyMap()
    return w.orderedExercises.associate { e ->
      e.exercise.id to e.orderedSets.filter { it.type.isWorking }.map { LoggedSet(it.type, it.weightKg, it.reps, it.rir, it.rpe, it.targetReps, it.targetRir) }
    }
  }

  /** Makes sure a session knows its sets, deriving them from the week before when needed. */
  private suspend fun ensureSessionSlots(session: PlanSessionEntity, plan: PlanEntity, days: List<PlanDayWithSlots>) {
    if (dao.getSessionSlots(session.id).isNotEmpty()) return
    val day = days.firstOrNull { it.day.id == session.planDayId } ?: return
    val previous = if (session.week > 0) dao.getSessions(plan.id).firstOrNull { it.week == session.week - 1 && it.planDayId == session.planDayId } else null
    if (previous != null && (previous.status == SessionStatus.DONE || previous.status == SessionStatus.PARTIAL)) {
      recomputeNext(previous)
      if (dao.getSessionSlots(session.id).isNotEmpty()) return
    }
    val before = previous?.let { dao.getSessionSlots(it.id).associateBy { r -> r.slotId } }.orEmpty()
    val deload = Periodization.isDeload(session.week, plan.weeks)
    dao.upsertSessionSlots(
      day.orderedSlots.map { sw ->
        val base = before[sw.slot.id]?.sets ?: sw.slot.startSets
        PlanSessionSlotEntity(session.id, sw.slot.id, if (deload) Periodization.deloadSets(base, sw.slot.muscle) else base)
      }
    )
  }

  /**
   * Records a finished planned workout: session status, the sets actually done (so manual changes
   * carry forward), how each muscle went, late soreness answers about earlier sessions, and next
   * week's sets for this day.
   */
  suspend fun completeSession(w: ActiveWorkout, workoutId: Long) {
    val link = w.plan ?: return
    db.withTransaction {
      val session = dao.getSession(link.sessionId) ?: return@withTransaction
      val now = System.currentTimeMillis()
      val working = w.exercises.flatMap { ex -> ex.sets.filter { it.type.isWorking } }
      val status = if (working.isNotEmpty() && working.all { it.completed }) SessionStatus.DONE else SessionStatus.PARTIAL
      dao.updateSession(session.copy(status = status, workoutId = workoutId, finishedAt = now))

      // Sets present at the end are the new baseline for this day (user-added sets persist). A slot whose
      // exercise was deleted during the workout has left the plan, so it is skipped.
      val existing = dao.getSessionSlots(session.id).associateBy { it.slotId }
      val slotIds = dao.getDays(session.planId).firstOrNull { it.day.id == session.planDayId }?.slots?.map { it.slot.id }?.toSet().orEmpty()
      val present =
        w.exercises.filter { it.planSlotId in slotIds }.groupBy { it.planSlotId!! }.map { (slotId, exs) ->
          val count = exs.sumOf { ex -> ex.sets.count { it.type.isWorking } }
          PlanSessionSlotEntity(session.id, slotId, count.coerceAtLeast(1), existing[slotId]?.recovery == true)
        }
      if (present.isNotEmpty()) dao.upsertSessionSlots(present)

      // How each trained muscle went, plus this session's pump and workload answers. A muscle with no
      // completed working set gets no row, so next week holds its sets instead of reading it as "fine".
      val old = dao.getFeedback(session.id).associateBy { it.muscle }
      val byMuscle = w.exercises.groupBy { TargetPlanner.traits(it).muscle }
      dao.putFeedback(
        byMuscle.mapNotNull { (m, exs) ->
          if (m == null) return@mapNotNull null
          val sets = exs.flatMap { ex -> ex.sets.filter { it.type.isWorking && it.completed } }
          if (sets.isEmpty()) return@mapNotNull null
          val judged = sets.filter { it.verdict != null }
          val regressed = if (judged.isEmpty()) 0.0 else judged.count { it.verdict == Verdict.REGRESSED }.toDouble() / judged.size
          val answers = w.feedback.firstOrNull { it.muscle == m }
          MuscleFeedbackEntity(
            sessionId = session.id,
            muscle = m,
            soreness = old[m]?.soreness,
            pump = answers?.pump,
            workload = answers?.workload,
            performance = Volume.performance(sets.map { TargetPlanner.logged(it) }, regressed),
            createdAt = now,
          )
        }
      )

      // Soreness answers are about the previous session for that muscle: store them there and re-plan it.
      val ov = overview(session.planId)
      if (ov != null) {
        for (a in w.feedback.filter { it.soreness != null }) {
          val prev = previousExposure(ov, session, a.muscle, w.startTime) ?: continue
          val row = dao.getFeedback(prev.id).firstOrNull { it.muscle == a.muscle }
          dao.putFeedback(listOf((row ?: MuscleFeedbackEntity(sessionId = prev.id, muscle = a.muscle, createdAt = now)).copy(soreness = a.soreness)))
          recomputeNext(dao.getSession(prev.id) ?: prev)
        }
      }
      recomputeNext(dao.getSession(session.id) ?: session)
      finishIfDone(session.planId)
    }
  }

  /** Muscles in [sessionId] worth asking soreness about: trained in another session in the last 8 days. */
  suspend fun sorenessCandidates(sessionId: Long): List<TrainingMuscle> {
    val session = dao.getSession(sessionId) ?: return emptyList()
    val ov = overview(session.planId) ?: return emptyList()
    val today = ov.day(session.planDayId)?.slots?.mapNotNull { it.slot.muscle }?.distinct().orEmpty()
    val now = System.currentTimeMillis()
    return today.filter { previousExposure(ov, session, it, now) != null }.sortedBy { it.ordinal }
  }

  /**
   * The latest finished session before [before] that trained [m]. When that is just this same day a
   * week ago (a muscle trained once a week), soreness says little about recovery, so none is returned.
   */
  private fun previousExposure(ov: PlanOverview, session: PlanSessionEntity, m: TrainingMuscle, before: Long): PlanSessionEntity? =
    ov.sessions
      .filter {
        it.id != session.id &&
          (it.status == SessionStatus.DONE || it.status == SessionStatus.PARTIAL) &&
          it.finishedAt != null &&
          it.finishedAt <= before &&
          before - it.finishedAt <= 8L * 24 * 60 * 60 * 1000 &&
          ov.day(it.planDayId)?.slots?.any { s -> s.slot.muscle == m } == true
      }
      .maxByOrNull { it.finishedAt!! }
      ?.takeUnless { it.planDayId == session.planDayId }

  /**
   * Sets for the same day next week, decided from this session's sets, answers and performance.
   * Idempotent: running it again with the same inputs writes the same rows. Leaves started or
   * finished sessions alone.
   */
  suspend fun recomputeNext(session: PlanSessionEntity) {
    val plan = dao.getPlan(session.planId) ?: return
    val sessions = dao.getSessions(plan.id)
    val next = sessions.firstOrNull { it.week == session.week + 1 && it.planDayId == session.planDayId } ?: return
    if (next.status != SessionStatus.PENDING) return
    val ov = overview(plan.id) ?: return
    val day = ov.day(session.planDayId) ?: return
    val current = ov.sessionSlots[session.id].orEmpty()
    val slots = day.orderedSlots
    val setsOf = { sw: PlanSlotWithExercise -> current[sw.slot.id]?.sets ?: sw.slot.startSets }
    val isRecovery = { sw: PlanSlotWithExercise -> current[sw.slot.id]?.recovery == true }
    // This day's sets before its latest recovery session: the newest earlier week that wasn't one.
    val earlier = sessions.filter { it.planDayId == session.planDayId && it.week < session.week }.sortedByDescending { it.week }.map { ov.sessionSlots[it.id].orEmpty() }
    val beforeRecovery = { sw: PlanSlotWithExercise -> earlier.firstNotNullOfOrNull { rows -> rows[sw.slot.id]?.takeIf { !it.recovery }?.sets } ?: sw.slot.startSets }

    if (Periodization.isDeload(next.week, plan.weeks)) {
      dao.upsertSessionSlots(slots.map { sw -> PlanSessionSlotEntity(next.id, sw.slot.id, Periodization.deloadSets(if (isRecovery(sw)) beforeRecovery(sw) else setsOf(sw), sw.slot.muscle)) })
      return
    }

    val skipped = session.status == SessionStatus.SKIPPED
    val feedback = ov.feedback[session.id].orEmpty().associateBy { it.muscle }
    val previous = sessions.firstOrNull { it.week == session.week - 1 && it.planDayId == session.planDayId }
    val previousFeedback = previous?.let { ov.feedback[it.id] }.orEmpty().associateBy { it.muscle }
    val weekly = ov.weeklySets(session.week)
    val out = mutableListOf<PlanSessionSlotEntity>()
    for ((muscle, group) in slots.groupBy { it.slot.muscle }) {
      val sets = group.map(setsOf)
      if (group.any(isRecovery)) {
        // Back from a recovery session, done or skipped: resume halfway between the block's start and where it was.
        group.forEach { sw -> out += PlanSessionSlotEntity(next.id, sw.slot.id, Volume.afterRecovery(sw.slot.startSets, beforeRecovery(sw))) }
        continue
      }
      val fb = muscle?.let { feedback[it] }
      val performance = fb?.performance
      if (muscle == null || skipped || performance == null) {
        // Not trained this time (session skipped, or the muscle's sets left undone): hold its sets.
        group.forEachIndexed { i, sw -> out += PlanSessionSlotEntity(next.id, sw.slot.id, sets[i]) }
        continue
      }
      val decision =
        Volume.decide(
          Volume.Inputs(
            priority = ov.priorities[muscle] ?: Priority.GROW,
            sessionSets = sets.sum(),
            weeklySets = weekly[muscle] ?: sets.sum(),
            sessionsPerWeek = ov.sessionsPerWeek(muscle),
            muscle = muscle,
            soreness = fb?.soreness,
            pump = fb?.pump,
            workload = fb?.workload,
            performance = performance,
            previousPerformance = previousFeedback[muscle]?.performance,
          )
        )
      if (decision.recovery) {
        group.forEachIndexed { i, sw -> out += PlanSessionSlotEntity(next.id, sw.slot.id, ((sets[i] + 1) / 2).coerceAtLeast(1), recovery = true) }
      } else {
        val newSets = Volume.distribute(sets, decision.delta)
        group.forEachIndexed { i, sw -> out += PlanSessionSlotEntity(next.id, sw.slot.id, newSets[i]) }
      }
    }
    dao.upsertSessionSlots(out)
  }

  /** Skips a planned session; next week's sets for that day stay as they were. */
  suspend fun skip(sessionId: Long) =
    db.withTransaction {
      val s = dao.getSession(sessionId) ?: return@withTransaction
      val plan = dao.getPlan(s.planId) ?: return@withTransaction
      ensureSessionSlots(s, plan, dao.getDays(plan.id))
      val skipped = s.copy(status = SessionStatus.SKIPPED, finishedAt = System.currentTimeMillis())
      dao.updateSession(skipped)
      recomputeNext(skipped)
      finishIfDone(s.planId)
    }

  private suspend fun finishIfDone(planId: Long) {
    val plan = dao.getPlan(planId) ?: return
    if (plan.status == PlanStatus.ACTIVE && dao.getSessions(planId).none { it.status == SessionStatus.PENDING }) {
      dao.updatePlan(plan.copy(status = PlanStatus.COMPLETE, finishedAt = System.currentTimeMillis()))
    }
  }

  suspend fun endPlan(planId: Long) = db.withTransaction { dao.getPlan(planId)?.let { endPlanInTransaction(it) } }

  private suspend fun endPlanInTransaction(plan: PlanEntity) {
    val now = System.currentTimeMillis()
    dao.getSessions(plan.id).filter { it.status == SessionStatus.PENDING }.forEach { dao.updateSession(it.copy(status = SessionStatus.SKIPPED, finishedAt = now)) }
    dao.updatePlan(plan.copy(status = PlanStatus.ENDED, finishedAt = now))
  }

  suspend fun delete(planId: Long) = dao.deletePlan(planId)

  suspend fun rename(planId: Long, name: String) {
    dao.getPlan(planId)?.let { dao.updatePlan(it.copy(name = name)) }
  }

  suspend fun setPriority(planId: Long, muscle: TrainingMuscle, priority: Priority) = dao.upsertPriorities(listOf(PlanPriorityEntity(planId, muscle, priority)))

  suspend fun setFeedbackMode(planId: Long, mode: FeedbackMode) {
    dao.getPlan(planId)?.let { dao.updatePlan(it.copy(feedbackMode = mode)) }
  }

  /** True when another accumulation week can be added: under the cap and the deload not started. */
  fun canAddWeek(ov: PlanOverview): Boolean = ov.plan.weeks < Periodization.MAX_WEEKS && ov.week(ov.plan.weeks - 1).all { it.status == SessionStatus.PENDING }

  /** True when the last accumulation week can be removed: above the minimum and that week not started. */
  fun canRemoveWeek(ov: PlanOverview): Boolean = ov.plan.weeks > Periodization.MIN_WEEKS && ov.week(ov.plan.weeks - 2).all { it.status == SessionStatus.PENDING }

  /** Adds an accumulation week before the deload; the RIR targets are re-spread over the new length. */
  suspend fun addWeek(planId: Long) =
    db.withTransaction {
      val ov = overview(planId) ?: return@withTransaction
      if (!canAddWeek(ov)) return@withTransaction
      val deloadWeek = ov.plan.weeks - 1
      dao.shiftWeeks(planId, deloadWeek, 1)
      dao.insertSessions(ov.days.map { PlanSessionEntity(planId = planId, week = deloadWeek, planDayId = it.day.id) })
      val weeks = ov.plan.weeks + 1
      dao.updatePlan(ov.plan.copy(weeks = weeks, rirSchedule = Periodization.encode(Periodization.rirSchedule(weeks))))
      // The new week's sets come from the week before it; the deload is re-derived from the new week.
      dao.getSessions(planId).filter { it.week >= deloadWeek }.forEach { s -> clearSlots(s.id) }
      dao.getSessions(planId).filter { it.week == deloadWeek - 1 && (it.status == SessionStatus.DONE || it.status == SessionStatus.PARTIAL || it.status == SessionStatus.SKIPPED) }.forEach { recomputeNext(it) }
    }

  /** Removes the last accumulation week (if it hasn't started); the deload moves up a week. */
  suspend fun removeWeek(planId: Long) =
    db.withTransaction {
      val ov = overview(planId) ?: return@withTransaction
      if (!canRemoveWeek(ov)) return@withTransaction
      val lastHard = ov.plan.weeks - 2
      dao.deleteWeek(planId, lastHard)
      dao.shiftWeeks(planId, lastHard + 1, -1)
      val weeks = ov.plan.weeks - 1
      dao.updatePlan(ov.plan.copy(weeks = weeks, rirSchedule = Periodization.encode(Periodization.rirSchedule(weeks))))
      dao.getSessions(planId).filter { it.week == weeks - 1 }.forEach { clearSlots(it.id) }
      dao.getSessions(planId).filter { it.week == weeks - 2 && it.status != SessionStatus.PENDING }.forEach { recomputeNext(it) }
    }

  /** Forgets a session's sets; they are re-derived from the week before when it is prepared. */
  private suspend fun clearSlots(sessionId: Long) = dao.clearSessionSlots(sessionId)
}
