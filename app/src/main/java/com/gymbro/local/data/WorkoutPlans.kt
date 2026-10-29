package com.gymbro.local.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "exercises", indices = [Index(value = ["name"])])
@Serializable
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val muscleGroup: String,
    val category: String,
    val isSeeded: Boolean = false,
)

@Entity(tableName = "workout_plans")
@Serializable
data class WorkoutPlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val sessionsPerWeek: Int,
    val estimatedMinutes: Int,
    val isSeeded: Boolean = false,
)

@Entity(
    tableName = "workout_plan_days",
    indices = [Index(value = ["planId", "position"], unique = true)],
)
@Serializable
data class WorkoutPlanDayEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val name: String,
    val position: Int,
    val isSeeded: Boolean = false,
)

@Entity(
    tableName = "exercise_prescriptions",
    indices = [
        Index(value = ["dayId", "position"], unique = true),
        Index(value = ["dayId", "exerciseId"], unique = true),
        Index(value = ["exerciseId"]),
    ],
)
@Serializable
data class ExercisePrescriptionEntity(
    @PrimaryKey val id: String,
    val dayId: String,
    val exerciseId: String,
    val position: Int,
    val sets: Int,
    val minReps: Int,
    val maxReps: Int,
    val restSeconds: Int,
    val isSeeded: Boolean = false,
) {
    fun userEdited(sets: Int, minReps: Int, maxReps: Int, restSeconds: Int, position: Int = this.position) =
        copy(position = position, sets = sets, minReps = minReps, maxReps = maxReps, restSeconds = restSeconds, isSeeded = false)
}

data class WorkoutPlanSeed(
    val exercises: List<ExerciseEntity>,
    val plan: WorkoutPlanEntity,
    val days: List<WorkoutPlanDayEntity>,
    val prescriptions: List<ExercisePrescriptionEntity>,
)

fun <T> refreshSeedRecord(existing: T?, previousCanonical: T, currentCanonical: T): T =
    if (existing == null || existing == previousCanonical) currentCanonical else existing

object WorkoutPlanSeedData {
    private fun exercise(id: String, name: String, muscle: String, category: String) =
        ExerciseEntity(id, name, muscle, category, true)

    private fun prescription(day: String, exercise: String, position: Int, sets: Int, min: Int, max: Int, rest: Int) =
        ExercisePrescriptionEntity("$day-$exercise", day, exercise, position, sets, min, max, rest, true)

    val ppl45v2 = WorkoutPlanSeed(
        exercises = listOf(
            exercise("smith-incline-bench-press", "Smith Incline Bench Press", "chest", "smith"),
            exercise("incline-press", "Incline Chest Press Machine", "chest", "machine"),
            exercise("chest-press", "Chest Press Machine", "chest", "machine"),
            exercise("pec-deck", "Pec Deck", "chest", "machine"),
            exercise("shoulder-press", "DB Shoulder Press", "shoulders", "dumbbell"),
            exercise("shoulder-press-machine", "Shoulder Press Machine", "shoulders", "machine"),
            exercise("lateral-raise", "DB Lateral Raises", "shoulders", "dumbbell"),
            exercise("cable-lateral-raise", "Cable Lateral Raise", "shoulders", "cable"),
            exercise("triceps-pushdown", "Rope Triceps Pushdown", "triceps", "cable"),
            exercise("triceps-machine", "Triceps Machine", "triceps", "machine"),
            exercise("cable-lat-pulldown", "Cable Lat Pulldown", "back", "cable"),
            exercise("lat-pulldown", "Lat Pulldown", "back", "machine"),
            exercise("cable-seated-row", "Cable Seated Row", "back", "cable"),
            exercise("seated-row", "Seated Row", "back", "machine"),
            exercise("face-pull-to-chest", "Face Pull to Chest", "shoulders", "cable"),
            exercise("rear-delt", "Rear Delt Fly Machine", "shoulders", "machine"),
            exercise("db-biceps-curl", "DB Biceps Curl", "biceps", "dumbbell"),
            exercise("cable-curl", "EZ Bar Cable Curl", "biceps", "cable"),
            exercise("hammer-curl", "Hammer Curls", "biceps", "dumbbell"),
            exercise("rope-hammer-curl", "Rope Hammer Curl", "biceps", "cable"),
            exercise("smith-squat", "Smith Squat", "legs", "smith"),
            exercise("leg-press", "Leg Press", "legs", "machine"),
            exercise("smith-rdl", "Smith RDL", "glutes", "smith"),
            exercise("hip-thrust", "Smith Hip Thrust", "glutes", "smith"),
            exercise("hip-abductor", "Hip Abductor Machine", "glutes", "machine"),
            exercise("leg-extension", "Leg Extension", "legs", "machine"),
            exercise("leg-curl-machine", "Leg Curl Machine", "legs", "machine"),
            exercise("calf-raise", "Calf Raise Machine", "calves", "machine"),
            exercise("leg-press-calf-raise", "Leg Press Calf Raise", "calves", "machine"),
        ),
        plan = WorkoutPlanEntity(
            "gymbro-ppl-45-v2", "GymBro PPL 45 V2",
            "Three focused Planet Fitness sessions for building muscle while staying lean.", 3, 45, true,
        ),
        days = listOf(
            WorkoutPlanDayEntity("push", "gymbro-ppl-45-v2", "Push", 0, true),
            WorkoutPlanDayEntity("pull", "gymbro-ppl-45-v2", "Pull", 1, true),
            WorkoutPlanDayEntity("legs", "gymbro-ppl-45-v2", "Legs", 2, true),
        ),
        prescriptions = listOf(
            prescription("push", "chest-press", 0, 3, 6, 10, 120),
            prescription("push", "incline-press", 1, 2, 8, 12, 90),
            prescription("push", "shoulder-press", 2, 2, 8, 12, 90),
            prescription("push", "lateral-raise", 3, 3, 12, 20, 60),
            prescription("push", "triceps-pushdown", 4, 2, 10, 15, 60),
            prescription("pull", "lat-pulldown", 0, 3, 6, 10, 120),
            prescription("pull", "seated-row", 1, 3, 8, 12, 120),
            prescription("pull", "rear-delt", 2, 3, 12, 20, 60),
            prescription("pull", "cable-curl", 3, 2, 8, 12, 60),
            prescription("pull", "hammer-curl", 4, 2, 10, 15, 60),
            prescription("legs", "smith-squat", 0, 2, 6, 8, 120),
            prescription("legs", "smith-rdl", 1, 2, 8, 10, 120),
            prescription("legs", "hip-thrust", 2, 3, 10, 15, 90),
            prescription("legs", "hip-abductor", 3, 2, 12, 20, 60),
            prescription("legs", "calf-raise", 4, 3, 12, 20, 60),
        ),
    )
}
