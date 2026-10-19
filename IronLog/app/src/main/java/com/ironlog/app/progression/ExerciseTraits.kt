package com.ironlog.app.progression

import com.ironlog.app.data.db.ExerciseEntity
import com.ironlog.app.model.Equipment
import com.ironlog.app.model.ExerciseType
import com.ironlog.app.model.MuscleGroup

enum class Mechanics {
  COMPOUND,
  ISOLATION,
}

enum class Region {
  UPPER,
  LOWER,
  CORE,
  FULL,
}

/** Movement families, used to keep a week varied and to find sensible substitutes. */
enum class Pattern {
  HORIZONTAL_PUSH,
  VERTICAL_PUSH,
  DIP,
  HORIZONTAL_PULL,
  VERTICAL_PULL,
  SQUAT,
  HINGE,
  LUNGE,
  HIP_THRUST,
  KNEE_EXTENSION,
  KNEE_FLEXION,
  CHEST_FLY,
  LATERAL_RAISE,
  REAR_DELT,
  FRONT_RAISE,
  ELBOW_FLEXION,
  ELBOW_EXTENSION,
  SHRUG,
  CALF_RAISE,
  HIP_ABDUCTION,
  HIP_ADDUCTION,
  CORE_FLEXION,
  CORE_STABILITY,
  WRIST,
  CARRY,
  POWER,
  OTHER,
}

/** How the weight on an exercise is changed, which decides the steps a target can take. */
enum class LoadKind {
  BARBELL,
  PLATE_LOADED,
  DUMBBELL,
  KETTLEBELL,
  MACHINE,
  CABLE,
  PLATE,
  ADDED_LOAD,
  ASSISTANCE,
  FIXED_STEP,
  NONE,
}

/**
 * What IronLog knows about an exercise beyond its stored fields. Derived from the name, equipment and
 * muscles, so custom and imported exercises get traits too.
 */
data class ExerciseTraits(
  val mechanics: Mechanics,
  val region: Region,
  val pattern: Pattern,
  /** 1 light … 3 heavy whole-body fatigue (barbell squats, deadlifts). */
  val systemicCost: Int,
  val muscle: TrainingMuscle?,
  val loadKind: LoadKind,
) {
  val isCompound: Boolean
    get() = mechanics == Mechanics.COMPOUND

  companion object {
    fun of(e: ExerciseEntity): ExerciseTraits = of(e.name, e.type, e.equipment, e.primaryMuscle)

    fun of(name: String, type: ExerciseType, equipment: Equipment, primary: MuscleGroup): ExerciseTraits {
      val n = name.lowercase()
      val pattern = pattern(n, primary)
      val mechanics = mechanics(n, pattern)
      return ExerciseTraits(
        mechanics = mechanics,
        region = region(primary),
        pattern = pattern,
        systemicCost = systemicCost(n, pattern, mechanics, equipment),
        muscle = TrainingMuscle.of(primary),
        loadKind = loadKind(n, type, equipment),
      )
    }

    private fun String.hasAny(vararg words: String) = words.any { it in this }

    private val rowWord = Regex("""\brows?\b""")

    internal fun pattern(n: String, primary: MuscleGroup): Pattern =
      when {
        n.hasAny("clean", "snatch", "jerk", "thruster", "get-up", "get up") -> Pattern.POWER
        n.hasAny("carry", "farmer", "suitcase walk") -> Pattern.CARRY
        n.hasAny("hip thrust", "glute bridge", "bridge") -> Pattern.HIP_THRUST
        n.hasAny("pull-through", "pull through", "swing", "good morning", "back extension", "hyperextension", "reverse hyper") -> Pattern.HINGE
        n.hasAny("deadlift", "rdl", "stiff leg", "stiff-leg") -> Pattern.HINGE
        n.hasAny("split squat", "lunge", "step-up", "step up") -> Pattern.LUNGE
        n.hasAny("squat", "leg press", "pendulum", "belt squat") -> Pattern.SQUAT
        n.hasAny("leg extension", "knee extension", "sissy") -> Pattern.KNEE_EXTENSION
        n.hasAny("leg curl", "hamstring curl", "nordic", "glute ham") -> Pattern.KNEE_FLEXION
        n.hasAny("calf") -> Pattern.CALF_RAISE
        n.hasAny("abduction", "abductor") -> Pattern.HIP_ABDUCTION
        n.hasAny("adduction", "adductor") -> Pattern.HIP_ADDUCTION
        n.hasAny("shrug") -> Pattern.SHRUG
        n.hasAny("face pull", "reverse fly", "reverse pec", "rear delt", "rear-delt") -> Pattern.REAR_DELT
        n.hasAny("lateral raise", "side raise", "lateral", "upright row", "y-raise", "y raise") -> Pattern.LATERAL_RAISE
        n.hasAny("front raise") -> Pattern.FRONT_RAISE
        n.hasAny("fly", "flye", "crossover", "pec deck", "butterfly") -> Pattern.CHEST_FLY
        n.hasAny("pulldown", "pull-down", "pull down", "pull-up", "pull up", "pullup", "chin-up", "chin up", "chinup", "lat pull", "pullover") -> Pattern.VERTICAL_PULL
        rowWord.containsMatchIn(n) -> Pattern.HORIZONTAL_PULL // a word, so "narrow-grip" isn't a row
        n.hasAny("dip") -> Pattern.DIP
        n.hasAny("overhead press", "shoulder press", "military", "arnold", "push press", "landmine press", "z press", "seated press") -> Pattern.VERTICAL_PUSH
        n.hasAny("bench", "chest press", "push-up", "push up", "pushup", "floor press", "incline press", "decline press") -> Pattern.HORIZONTAL_PUSH
        n.hasAny("wrist", "grip", "forearm") -> Pattern.WRIST
        n.hasAny("skull", "pushdown", "pressdown", "push down", "kickback", "triceps", "tricep", "french press", "jm press") -> Pattern.ELBOW_EXTENSION
        n.hasAny("curl") -> Pattern.ELBOW_FLEXION
        n.hasAny("plank", "pallof", "dead bug", "hollow", "bird dog", "anti-rotation", "rollout", "ab wheel") -> Pattern.CORE_STABILITY
        n.hasAny("crunch", "sit-up", "sit up", "situp", "leg raise", "knee raise", "twist", "v-up", "toes to bar", "woodchop") -> Pattern.CORE_FLEXION
        n.hasAny("press") ->
          when (primary) {
            MuscleGroup.SHOULDERS -> Pattern.VERTICAL_PUSH
            MuscleGroup.TRICEPS -> Pattern.ELBOW_EXTENSION
            else -> Pattern.HORIZONTAL_PUSH
          }
        n.hasAny("extension") ->
          when (primary) {
            MuscleGroup.TRICEPS -> Pattern.ELBOW_EXTENSION
            MuscleGroup.QUADRICEPS -> Pattern.KNEE_EXTENSION
            MuscleGroup.LOWER_BACK,
            MuscleGroup.GLUTES,
            MuscleGroup.HAMSTRINGS -> Pattern.HINGE
            else -> Pattern.OTHER
          }
        n.hasAny("raise") ->
          when (primary) {
            MuscleGroup.SHOULDERS -> Pattern.LATERAL_RAISE
            MuscleGroup.ABDOMINALS -> Pattern.CORE_FLEXION
            MuscleGroup.CALVES -> Pattern.CALF_RAISE
            else -> Pattern.OTHER
          }
        else -> fallbackPattern(primary)
      }

    private fun fallbackPattern(primary: MuscleGroup): Pattern =
      when (primary) {
        MuscleGroup.CHEST -> Pattern.HORIZONTAL_PUSH
        MuscleGroup.LATS -> Pattern.VERTICAL_PULL
        MuscleGroup.UPPER_BACK -> Pattern.HORIZONTAL_PULL
        MuscleGroup.SHOULDERS -> Pattern.LATERAL_RAISE
        MuscleGroup.BICEPS -> Pattern.ELBOW_FLEXION
        MuscleGroup.TRICEPS -> Pattern.ELBOW_EXTENSION
        MuscleGroup.FOREARMS -> Pattern.WRIST
        MuscleGroup.TRAPS -> Pattern.SHRUG
        MuscleGroup.QUADRICEPS -> Pattern.SQUAT
        MuscleGroup.HAMSTRINGS -> Pattern.KNEE_FLEXION
        MuscleGroup.GLUTES -> Pattern.HIP_THRUST
        MuscleGroup.CALVES -> Pattern.CALF_RAISE
        MuscleGroup.ABDUCTORS -> Pattern.HIP_ABDUCTION
        MuscleGroup.ADDUCTORS -> Pattern.HIP_ADDUCTION
        MuscleGroup.LOWER_BACK -> Pattern.HINGE
        MuscleGroup.ABDOMINALS -> Pattern.CORE_FLEXION
        MuscleGroup.NECK,
        MuscleGroup.CARDIO,
        MuscleGroup.FULL_BODY,
        MuscleGroup.OTHER -> Pattern.OTHER
      }

    private val compoundPatterns =
      setOf(
        Pattern.HORIZONTAL_PUSH,
        Pattern.VERTICAL_PUSH,
        Pattern.DIP,
        Pattern.HORIZONTAL_PULL,
        Pattern.VERTICAL_PULL,
        Pattern.SQUAT,
        Pattern.HINGE,
        Pattern.LUNGE,
        Pattern.HIP_THRUST,
        Pattern.CARRY,
        Pattern.POWER,
      )

    private fun mechanics(n: String, p: Pattern): Mechanics =
      when {
        n.hasAny("straight-arm", "straight arm", "pullover", "back extension", "hyperextension", "reverse hyper") -> Mechanics.ISOLATION
        n.hasAny("upright row", "close-grip bench", "close grip bench") -> Mechanics.COMPOUND
        p in compoundPatterns -> Mechanics.COMPOUND
        else -> Mechanics.ISOLATION
      }

    private fun region(primary: MuscleGroup): Region =
      when (primary) {
        MuscleGroup.CHEST,
        MuscleGroup.SHOULDERS,
        MuscleGroup.BICEPS,
        MuscleGroup.TRICEPS,
        MuscleGroup.FOREARMS,
        MuscleGroup.LATS,
        MuscleGroup.UPPER_BACK,
        MuscleGroup.TRAPS,
        MuscleGroup.NECK -> Region.UPPER
        MuscleGroup.QUADRICEPS,
        MuscleGroup.HAMSTRINGS,
        MuscleGroup.GLUTES,
        MuscleGroup.CALVES,
        MuscleGroup.ABDUCTORS,
        MuscleGroup.ADDUCTORS,
        MuscleGroup.LOWER_BACK -> Region.LOWER
        MuscleGroup.ABDOMINALS -> Region.CORE
        MuscleGroup.CARDIO,
        MuscleGroup.FULL_BODY,
        MuscleGroup.OTHER -> Region.FULL
      }

    private fun systemicCost(n: String, p: Pattern, m: Mechanics, equipment: Equipment): Int {
      val free = equipment == Equipment.BARBELL
      return when {
        p == Pattern.POWER -> 3
        free && (p == Pattern.SQUAT || (p == Pattern.HINGE && n.hasAny("deadlift", "good morning") && !n.hasAny("romanian", "rdl", "stiff"))) -> 3
        m == Mechanics.COMPOUND && (free || p in setOf(Pattern.SQUAT, Pattern.HINGE, Pattern.LUNGE)) -> 2
        else -> 1
      }
    }

    private fun loadKind(n: String, type: ExerciseType, equipment: Equipment): LoadKind =
      when (type) {
        ExerciseType.WEIGHTED_BODYWEIGHT -> LoadKind.ADDED_LOAD
        ExerciseType.ASSISTED_BODYWEIGHT -> LoadKind.ASSISTANCE
        ExerciseType.REPS_ONLY,
        ExerciseType.DURATION,
        ExerciseType.DISTANCE_DURATION -> LoadKind.NONE
        ExerciseType.WEIGHT_REPS,
        ExerciseType.WEIGHT_DURATION,
        ExerciseType.WEIGHT_DISTANCE ->
          when (equipment) {
            Equipment.BARBELL -> LoadKind.BARBELL
            Equipment.DUMBBELL -> LoadKind.DUMBBELL
            Equipment.KETTLEBELL -> LoadKind.KETTLEBELL
            Equipment.CABLE -> LoadKind.CABLE
            Equipment.PLATE -> LoadKind.PLATE
            Equipment.MACHINE ->
              if (n.hasAny("plate-loaded", "plate loaded", "leg press", "hack squat", "t-bar", "t bar", "pendulum", "belt squat")) LoadKind.PLATE_LOADED else LoadKind.MACHINE
            Equipment.NONE,
            Equipment.RESISTANCE_BAND,
            Equipment.SUSPENSION,
            Equipment.OTHER -> if (n.hasAny("smith")) LoadKind.BARBELL else LoadKind.FIXED_STEP
          }
      }
  }
}
