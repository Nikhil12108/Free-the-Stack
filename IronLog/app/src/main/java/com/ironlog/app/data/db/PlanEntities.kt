package com.ironlog.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.ironlog.app.progression.Priority
import com.ironlog.app.progression.TrainingMuscle

enum class PlanStatus {
  ACTIVE,
  COMPLETE,
  ENDED,
}

/** How much the app asks after sets: nothing, effort + joints, or everything. */
enum class FeedbackMode(val label: String, val description: String) {
  OFF("Off", "No questions — sets change only from your performance."),
  LIGHT("Light", "How hard the work felt, and joint pain per exercise."),
  FULL("Full", "Also soreness before a session and the pump after it."),
}

enum class SessionStatus {
  PENDING,
  DONE,
  PARTIAL,
  SKIPPED,
}

/**
 * A training block (mesocycle): a fixed set of training days repeated for [weeks] weeks, the last of
 * which is a deload. [rirSchedule] holds the reps-in-reserve target per accumulation week, e.g. "3,2,1,0".
 */
@Entity(tableName = "plan")
data class PlanEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val weeks: Int,
  val status: PlanStatus = PlanStatus.ACTIVE,
  val rirSchedule: String,
  val feedbackMode: FeedbackMode = FeedbackMode.LIGHT,
  val createdAt: Long,
  val finishedAt: Long? = null,
  val sourcePlanId: Long? = null,
  val notes: String = "",
)

@Entity(
  tableName = "plan_day",
  foreignKeys = [ForeignKey(entity = PlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE)],
  indices = [Index("planId")],
)
data class PlanDayEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val planId: Long,
  val position: Int,
  val name: String,
  /** 0 = Monday … 6 = Sunday; null for a floating "Day N". */
  val weekday: Int? = null,
)

/** One exercise in a plan day, with the sets it starts the block at. */
@Entity(
  tableName = "plan_slot",
  foreignKeys =
    [
      ForeignKey(entity = PlanDayEntity::class, parentColumns = ["id"], childColumns = ["planDayId"], onDelete = ForeignKey.CASCADE),
      ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
  indices = [Index("planDayId"), Index("exerciseId")],
)
data class PlanSlotEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val planDayId: Long,
  val position: Int,
  val exerciseId: Long,
  val muscle: TrainingMuscle? = null,
  val repMin: Int,
  val repMax: Int,
  val startSets: Int,
  val supersetGroup: Int? = null,
  val restSeconds: Int? = null,
  /** The exercise's notes from the routine the plan was made from (cues, setup, alternatives). */
  @ColumnInfo(defaultValue = "") val notes: String = "",
)

@Entity(
  tableName = "plan_priority",
  primaryKeys = ["planId", "muscle"],
  foreignKeys = [ForeignKey(entity = PlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE)],
)
data class PlanPriorityEntity(val planId: Long, val muscle: TrainingMuscle, val priority: Priority)

/** One planned workout: day [planDayId] in week [week] (0-based; the last week is the deload). */
@Entity(
  tableName = "plan_session",
  foreignKeys =
    [
      ForeignKey(entity = PlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE),
      ForeignKey(entity = PlanDayEntity::class, parentColumns = ["id"], childColumns = ["planDayId"], onDelete = ForeignKey.CASCADE),
    ],
  indices = [Index(value = ["planId", "week", "planDayId"], unique = true), Index("planDayId")],
)
data class PlanSessionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val planId: Long,
  val week: Int,
  val planDayId: Long,
  val status: SessionStatus = SessionStatus.PENDING,
  /** The logged workout; no foreign key so deleting it from History keeps the plan intact. */
  val workoutId: Long? = null,
  val finishedAt: Long? = null,
)

/** How many sets a slot gets in one session; written as the block unfolds. */
@Entity(
  tableName = "plan_session_slot",
  primaryKeys = ["sessionId", "slotId"],
  foreignKeys =
    [
      ForeignKey(entity = PlanSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
      ForeignKey(entity = PlanSlotEntity::class, parentColumns = ["id"], childColumns = ["slotId"], onDelete = ForeignKey.CASCADE),
    ],
  indices = [Index("slotId")],
)
data class PlanSessionSlotEntity(val sessionId: Long, val slotId: Long, val sets: Int, val recovery: Boolean = false)

/** What one session told the app about one muscle, and what it decided. */
@Entity(
  tableName = "muscle_feedback",
  foreignKeys = [ForeignKey(entity = PlanSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
  indices = [Index(value = ["sessionId", "muscle"], unique = true)],
)
data class MuscleFeedbackEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val sessionId: Long,
  val muscle: TrainingMuscle,
  /** 1 never sore · 2 healed early · 3 healed just in time · 4 still sore. */
  val soreness: Int? = null,
  /** 0 low · 1 moderate · 2 great. */
  val pump: Int? = null,
  /** 0 easy · 1 about right · 2 at my limit · 3 too much. */
  val workload: Int? = null,
  /** 1 exceeded · 2 as planned · 3 struggled · 4 regressed (computed from the sets). */
  val performance: Int? = null,
  val createdAt: Long,
)

data class PlanDayWithSlots(
  @Embedded val day: PlanDayEntity,
  @Relation(entity = PlanSlotEntity::class, parentColumn = "id", entityColumn = "planDayId") val slots: List<PlanSlotWithExercise>,
) {
  val orderedSlots: List<PlanSlotWithExercise>
    get() = slots.sortedBy { it.slot.position }
}

data class PlanSlotWithExercise(
  @Embedded val slot: PlanSlotEntity,
  @Relation(parentColumn = "exerciseId", entityColumn = "id") val exercise: ExerciseEntity,
)
