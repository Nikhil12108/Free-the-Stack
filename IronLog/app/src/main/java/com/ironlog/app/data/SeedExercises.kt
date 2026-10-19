package com.ironlog.app.data

import com.ironlog.app.data.db.ExerciseEntity
import com.ironlog.app.model.Equipment
import com.ironlog.app.model.Equipment.BARBELL
import com.ironlog.app.model.Equipment.CABLE
import com.ironlog.app.model.Equipment.DUMBBELL
import com.ironlog.app.model.Equipment.KETTLEBELL
import com.ironlog.app.model.Equipment.MACHINE
import com.ironlog.app.model.Equipment.NONE
import com.ironlog.app.model.Equipment.OTHER
import com.ironlog.app.model.Equipment.RESISTANCE_BAND
import com.ironlog.app.model.ExerciseType
import com.ironlog.app.model.ExerciseType.ASSISTED_BODYWEIGHT
import com.ironlog.app.model.ExerciseType.DISTANCE_DURATION
import com.ironlog.app.model.ExerciseType.DURATION
import com.ironlog.app.model.ExerciseType.REPS_ONLY
import com.ironlog.app.model.ExerciseType.WEIGHTED_BODYWEIGHT
import com.ironlog.app.model.ExerciseType.WEIGHT_DISTANCE
import com.ironlog.app.model.ExerciseType.WEIGHT_REPS
import com.ironlog.app.model.MuscleGroup
import com.ironlog.app.model.MuscleGroup.ABDOMINALS
import com.ironlog.app.model.MuscleGroup.ADDUCTORS
import com.ironlog.app.model.MuscleGroup.BICEPS
import com.ironlog.app.model.MuscleGroup.CALVES
import com.ironlog.app.model.MuscleGroup.CARDIO
import com.ironlog.app.model.MuscleGroup.CHEST
import com.ironlog.app.model.MuscleGroup.FOREARMS
import com.ironlog.app.model.MuscleGroup.FULL_BODY
import com.ironlog.app.model.MuscleGroup.GLUTES
import com.ironlog.app.model.MuscleGroup.HAMSTRINGS
import com.ironlog.app.model.MuscleGroup.LATS
import com.ironlog.app.model.MuscleGroup.LOWER_BACK
import com.ironlog.app.model.MuscleGroup.QUADRICEPS
import com.ironlog.app.model.MuscleGroup.SHOULDERS
import com.ironlog.app.model.MuscleGroup.TRAPS
import com.ironlog.app.model.MuscleGroup.TRICEPS
import com.ironlog.app.model.MuscleGroup.UPPER_BACK
import com.ironlog.app.model.MuscleGroup.ABDUCTORS

/** IronLog's built-in exercise list, written for this app. Naming scheme: "<Equipment> <Movement>". */
object SeedExercises {
  private fun ex(name: String, type: ExerciseType, equipment: Equipment, primary: MuscleGroup, vararg secondary: MuscleGroup) =
    ExerciseEntity(name = name, type = type, equipment = equipment, primaryMuscle = primary, secondaryMuscles = secondary.toList())

  val all: List<ExerciseEntity> =
    listOf(
      // Chest
      ex("Barbell Bench Press", WEIGHT_REPS, BARBELL, CHEST, TRICEPS, SHOULDERS),
      ex("Barbell Incline Bench Press", WEIGHT_REPS, BARBELL, CHEST, SHOULDERS, TRICEPS),
      ex("Barbell Decline Bench Press", WEIGHT_REPS, BARBELL, CHEST, TRICEPS),
      ex("Barbell Close-Grip Bench Press", WEIGHT_REPS, BARBELL, TRICEPS, CHEST),
      ex("Dumbbell Bench Press", WEIGHT_REPS, DUMBBELL, CHEST, TRICEPS, SHOULDERS),
      ex("Dumbbell Incline Bench Press", WEIGHT_REPS, DUMBBELL, CHEST, SHOULDERS, TRICEPS),
      ex("Dumbbell Fly", WEIGHT_REPS, DUMBBELL, CHEST, SHOULDERS),
      ex("Cable Crossover", WEIGHT_REPS, CABLE, CHEST, SHOULDERS),
      ex("Machine Chest Press", WEIGHT_REPS, MACHINE, CHEST, TRICEPS),
      ex("Machine Pec Deck", WEIGHT_REPS, MACHINE, CHEST),
      ex("Push-Up", REPS_ONLY, NONE, CHEST, TRICEPS, SHOULDERS),
      ex("Weighted Push-Up", WEIGHTED_BODYWEIGHT, OTHER, CHEST, TRICEPS),
      ex("Chest Dip", WEIGHTED_BODYWEIGHT, NONE, CHEST, TRICEPS, SHOULDERS),
      // Back
      ex("Barbell Deadlift", WEIGHT_REPS, BARBELL, LOWER_BACK, GLUTES, HAMSTRINGS, TRAPS),
      ex("Barbell Romanian Deadlift", WEIGHT_REPS, BARBELL, HAMSTRINGS, GLUTES, LOWER_BACK),
      ex("Barbell Bent-Over Row", WEIGHT_REPS, BARBELL, UPPER_BACK, LATS, BICEPS),
      ex("Barbell Pendlay Row", WEIGHT_REPS, BARBELL, UPPER_BACK, LATS),
      ex("Dumbbell One-Arm Row", WEIGHT_REPS, DUMBBELL, LATS, UPPER_BACK, BICEPS),
      ex("Dumbbell Chest-Supported Row", WEIGHT_REPS, DUMBBELL, UPPER_BACK, LATS),
      ex("Cable Seated Row", WEIGHT_REPS, CABLE, UPPER_BACK, LATS, BICEPS),
      ex("Cable Lat Pulldown", WEIGHT_REPS, CABLE, LATS, BICEPS),
      ex("Cable Close-Grip Pulldown", WEIGHT_REPS, CABLE, LATS, BICEPS),
      ex("Cable Straight-Arm Pulldown", WEIGHT_REPS, CABLE, LATS),
      ex("Machine Row", WEIGHT_REPS, MACHINE, UPPER_BACK, LATS),
      ex("Machine T-Bar Row", WEIGHT_REPS, MACHINE, UPPER_BACK, LATS),
      ex("Pull-Up", WEIGHTED_BODYWEIGHT, NONE, LATS, BICEPS, UPPER_BACK),
      ex("Chin-Up", WEIGHTED_BODYWEIGHT, NONE, LATS, BICEPS),
      ex("Assisted Pull-Up", ASSISTED_BODYWEIGHT, MACHINE, LATS, BICEPS),
      ex("Inverted Row", REPS_ONLY, NONE, UPPER_BACK, BICEPS),
      ex("Back Extension", WEIGHTED_BODYWEIGHT, MACHINE, LOWER_BACK, GLUTES, HAMSTRINGS),
      ex("Barbell Shrug", WEIGHT_REPS, BARBELL, TRAPS),
      ex("Dumbbell Shrug", WEIGHT_REPS, DUMBBELL, TRAPS),
      ex("Cable Face Pull", WEIGHT_REPS, CABLE, SHOULDERS, UPPER_BACK, TRAPS),
      // Shoulders
      ex("Barbell Overhead Press", WEIGHT_REPS, BARBELL, SHOULDERS, TRICEPS),
      ex("Barbell Push Press", WEIGHT_REPS, BARBELL, SHOULDERS, TRICEPS, QUADRICEPS),
      ex("Dumbbell Shoulder Press", WEIGHT_REPS, DUMBBELL, SHOULDERS, TRICEPS),
      ex("Dumbbell Arnold Press", WEIGHT_REPS, DUMBBELL, SHOULDERS, TRICEPS),
      ex("Dumbbell Lateral Raise", WEIGHT_REPS, DUMBBELL, SHOULDERS),
      ex("Dumbbell Front Raise", WEIGHT_REPS, DUMBBELL, SHOULDERS),
      ex("Dumbbell Rear Delt Fly", WEIGHT_REPS, DUMBBELL, SHOULDERS, UPPER_BACK),
      ex("Cable Lateral Raise", WEIGHT_REPS, CABLE, SHOULDERS),
      ex("Machine Shoulder Press", WEIGHT_REPS, MACHINE, SHOULDERS, TRICEPS),
      ex("Machine Reverse Fly", WEIGHT_REPS, MACHINE, SHOULDERS, UPPER_BACK),
      ex("Barbell Upright Row", WEIGHT_REPS, BARBELL, SHOULDERS, TRAPS),
      // Arms
      ex("Barbell Curl", WEIGHT_REPS, BARBELL, BICEPS, FOREARMS),
      ex("EZ-Bar Curl", WEIGHT_REPS, BARBELL, BICEPS, FOREARMS),
      ex("Dumbbell Curl", WEIGHT_REPS, DUMBBELL, BICEPS, FOREARMS),
      ex("Dumbbell Hammer Curl", WEIGHT_REPS, DUMBBELL, BICEPS, FOREARMS),
      ex("Dumbbell Incline Curl", WEIGHT_REPS, DUMBBELL, BICEPS),
      ex("Dumbbell Concentration Curl", WEIGHT_REPS, DUMBBELL, BICEPS),
      ex("Cable Curl", WEIGHT_REPS, CABLE, BICEPS),
      ex("Machine Preacher Curl", WEIGHT_REPS, MACHINE, BICEPS),
      ex("Barbell Skull Crusher", WEIGHT_REPS, BARBELL, TRICEPS),
      ex("Dumbbell Overhead Triceps Extension", WEIGHT_REPS, DUMBBELL, TRICEPS),
      ex("Cable Triceps Pushdown", WEIGHT_REPS, CABLE, TRICEPS),
      ex("Cable Rope Pushdown", WEIGHT_REPS, CABLE, TRICEPS),
      ex("Cable Overhead Triceps Extension", WEIGHT_REPS, CABLE, TRICEPS),
      ex("Triceps Dip", WEIGHTED_BODYWEIGHT, NONE, TRICEPS, CHEST),
      ex("Bench Dip", REPS_ONLY, NONE, TRICEPS),
      ex("Barbell Wrist Curl", WEIGHT_REPS, BARBELL, FOREARMS),
      ex("Dumbbell Farmer's Carry", WEIGHT_DISTANCE, DUMBBELL, FOREARMS, TRAPS, FULL_BODY),
      // Legs
      ex("Barbell Back Squat", WEIGHT_REPS, BARBELL, QUADRICEPS, GLUTES, HAMSTRINGS),
      ex("Barbell Front Squat", WEIGHT_REPS, BARBELL, QUADRICEPS, GLUTES, ABDOMINALS),
      ex("Barbell Hip Thrust", WEIGHT_REPS, BARBELL, GLUTES, HAMSTRINGS),
      ex("Barbell Lunge", WEIGHT_REPS, BARBELL, QUADRICEPS, GLUTES),
      ex("Barbell Good Morning", WEIGHT_REPS, BARBELL, HAMSTRINGS, LOWER_BACK),
      ex("Dumbbell Goblet Squat", WEIGHT_REPS, DUMBBELL, QUADRICEPS, GLUTES),
      ex("Dumbbell Bulgarian Split Squat", WEIGHT_REPS, DUMBBELL, QUADRICEPS, GLUTES),
      ex("Dumbbell Walking Lunge", WEIGHT_REPS, DUMBBELL, QUADRICEPS, GLUTES),
      ex("Dumbbell Romanian Deadlift", WEIGHT_REPS, DUMBBELL, HAMSTRINGS, GLUTES),
      ex("Dumbbell Step-Up", WEIGHT_REPS, DUMBBELL, QUADRICEPS, GLUTES),
      ex("Machine Leg Press", WEIGHT_REPS, MACHINE, QUADRICEPS, GLUTES),
      ex("Machine Hack Squat", WEIGHT_REPS, MACHINE, QUADRICEPS, GLUTES),
      ex("Machine Leg Extension", WEIGHT_REPS, MACHINE, QUADRICEPS),
      ex("Machine Seated Leg Curl", WEIGHT_REPS, MACHINE, HAMSTRINGS),
      ex("Machine Lying Leg Curl", WEIGHT_REPS, MACHINE, HAMSTRINGS),
      ex("Machine Hip Abduction", WEIGHT_REPS, MACHINE, ABDUCTORS, GLUTES),
      ex("Machine Hip Adduction", WEIGHT_REPS, MACHINE, ADDUCTORS),
      ex("Machine Standing Calf Raise", WEIGHT_REPS, MACHINE, CALVES),
      ex("Machine Seated Calf Raise", WEIGHT_REPS, MACHINE, CALVES),
      ex("Cable Pull-Through", WEIGHT_REPS, CABLE, GLUTES, HAMSTRINGS),
      ex("Kettlebell Swing", WEIGHT_REPS, KETTLEBELL, GLUTES, HAMSTRINGS, LOWER_BACK),
      ex("Bodyweight Squat", REPS_ONLY, NONE, QUADRICEPS, GLUTES),
      ex("Glute Bridge", WEIGHTED_BODYWEIGHT, NONE, GLUTES, HAMSTRINGS),
      ex("Box Jump", REPS_ONLY, OTHER, QUADRICEPS, GLUTES, CALVES),
      // Core
      ex("Plank", DURATION, NONE, ABDOMINALS),
      ex("Side Plank", DURATION, NONE, ABDOMINALS),
      ex("Crunch", REPS_ONLY, NONE, ABDOMINALS),
      ex("Hanging Leg Raise", REPS_ONLY, NONE, ABDOMINALS),
      ex("Cable Crunch", WEIGHT_REPS, CABLE, ABDOMINALS),
      ex("Ab Wheel Rollout", REPS_ONLY, OTHER, ABDOMINALS),
      ex("Russian Twist", WEIGHTED_BODYWEIGHT, NONE, ABDOMINALS),
      ex("Dead Bug", REPS_ONLY, NONE, ABDOMINALS),
      ex("Band Pallof Press", REPS_ONLY, RESISTANCE_BAND, ABDOMINALS),
      // Full body & cardio
      ex("Barbell Power Clean", WEIGHT_REPS, BARBELL, FULL_BODY, QUADRICEPS, TRAPS),
      ex("Kettlebell Turkish Get-Up", WEIGHT_REPS, KETTLEBELL, FULL_BODY, SHOULDERS, ABDOMINALS),
      ex("Burpee", REPS_ONLY, NONE, FULL_BODY, CARDIO),
      ex("Treadmill Run", DISTANCE_DURATION, MACHINE, CARDIO),
      ex("Outdoor Run", DISTANCE_DURATION, NONE, CARDIO),
      ex("Stationary Bike", DISTANCE_DURATION, MACHINE, CARDIO),
      ex("Rowing Machine", DISTANCE_DURATION, MACHINE, CARDIO, UPPER_BACK),
      ex("Elliptical", DURATION, MACHINE, CARDIO),
      ex("Jump Rope", DURATION, OTHER, CARDIO, CALVES),
      ex("Sled Push", WEIGHT_DISTANCE, OTHER, QUADRICEPS, GLUTES, CARDIO),
    )
}
