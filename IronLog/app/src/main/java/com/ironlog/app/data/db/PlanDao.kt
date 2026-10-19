package com.ironlog.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {
  @Query("SELECT * FROM plan ORDER BY createdAt DESC") fun observePlans(): Flow<List<PlanEntity>>

  @Query("SELECT * FROM plan WHERE status = 'ACTIVE' ORDER BY createdAt DESC LIMIT 1") fun observeActive(): Flow<PlanEntity?>

  @Query("SELECT * FROM plan WHERE status = 'ACTIVE' ORDER BY createdAt DESC LIMIT 1") suspend fun getActive(): PlanEntity?

  @Query("SELECT * FROM plan WHERE id = :id") fun observePlan(id: Long): Flow<PlanEntity?>

  @Query("SELECT * FROM plan WHERE id = :id") suspend fun getPlan(id: Long): PlanEntity?

  @Insert suspend fun insertPlan(plan: PlanEntity): Long

  @Update suspend fun updatePlan(plan: PlanEntity)

  @Query("DELETE FROM plan WHERE id = :id") suspend fun deletePlan(id: Long)

  @Insert suspend fun insertDay(day: PlanDayEntity): Long

  @Update suspend fun updateDays(days: List<PlanDayEntity>)

  @Transaction @Query("SELECT * FROM plan_day WHERE planId = :planId ORDER BY position") fun observeDays(planId: Long): Flow<List<PlanDayWithSlots>>

  @Transaction @Query("SELECT * FROM plan_day WHERE planId = :planId ORDER BY position") suspend fun getDays(planId: Long): List<PlanDayWithSlots>

  @Insert suspend fun insertSlot(slot: PlanSlotEntity): Long

  @Update suspend fun updateSlot(slot: PlanSlotEntity)

  @Query("DELETE FROM plan_slot WHERE id = :id") suspend fun deleteSlot(id: Long)

  @Upsert suspend fun upsertPriorities(priorities: List<PlanPriorityEntity>)

  @Query("SELECT * FROM plan_priority WHERE planId = :planId") fun observePriorities(planId: Long): Flow<List<PlanPriorityEntity>>

  @Query("SELECT * FROM plan_priority WHERE planId = :planId") suspend fun getPriorities(planId: Long): List<PlanPriorityEntity>

  @Insert suspend fun insertSessions(sessions: List<PlanSessionEntity>): List<Long>

  @Update suspend fun updateSession(session: PlanSessionEntity)

  @Query("SELECT * FROM plan_session WHERE planId = :planId ORDER BY week, id") fun observeSessions(planId: Long): Flow<List<PlanSessionEntity>>

  @Query("SELECT * FROM plan_session WHERE planId = :planId ORDER BY week, id") suspend fun getSessions(planId: Long): List<PlanSessionEntity>

  @Query("SELECT * FROM plan_session WHERE id = :id") suspend fun getSession(id: Long): PlanSessionEntity?

  @Query("DELETE FROM plan_session WHERE planId = :planId AND week = :week") suspend fun deleteWeek(planId: Long, week: Int)

  @Query("UPDATE plan_session SET week = week + :delta WHERE planId = :planId AND week >= :fromWeek")
  suspend fun shiftWeeks(planId: Long, fromWeek: Int, delta: Int)

  @Upsert suspend fun upsertSessionSlots(slots: List<PlanSessionSlotEntity>)

  @Query("SELECT * FROM plan_session_slot WHERE sessionId = :sessionId") suspend fun getSessionSlots(sessionId: Long): List<PlanSessionSlotEntity>

  @Query("DELETE FROM plan_session_slot WHERE sessionId = :sessionId") suspend fun clearSessionSlots(sessionId: Long)

  @Query("SELECT pss.* FROM plan_session_slot pss JOIN plan_session s ON s.id = pss.sessionId WHERE s.planId = :planId")
  fun observeAllSessionSlots(planId: Long): Flow<List<PlanSessionSlotEntity>>

  @Query("SELECT pss.* FROM plan_session_slot pss JOIN plan_session s ON s.id = pss.sessionId WHERE s.planId = :planId")
  suspend fun getAllSessionSlots(planId: Long): List<PlanSessionSlotEntity>

  /** Feedback rows are unique per session and muscle; a newer answer replaces the old row. */
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putFeedback(feedback: List<MuscleFeedbackEntity>)

  @Query("SELECT * FROM muscle_feedback WHERE sessionId = :sessionId") suspend fun getFeedback(sessionId: Long): List<MuscleFeedbackEntity>

  @Query("SELECT f.* FROM muscle_feedback f JOIN plan_session s ON s.id = f.sessionId WHERE s.planId = :planId")
  fun observePlanFeedback(planId: Long): Flow<List<MuscleFeedbackEntity>>

  @Query("SELECT f.* FROM muscle_feedback f JOIN plan_session s ON s.id = f.sessionId WHERE s.planId = :planId")
  suspend fun getPlanFeedback(planId: Long): List<MuscleFeedbackEntity>
}
