package com.gymbro.local.data


import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PortableBackupTest {
    private val codec = PortableBackupCodec()

    private fun validBackup() = PortableBackup(
        version = PortableBackup.CURRENT_VERSION,
        workoutSessions = listOf(WorkoutSessionEntity(7, "push", "Push", 100, null)),
        setCompletions = listOf(SetCompletionEntity(7, "chest-press", 0)),
        exerciseCompletions = listOf(ExerciseCompletionEntity(7, "chest-press", 100_000, 8, 200)),
        foodEntries = listOf(FoodEntryEntity(8, "rice", "Rice", 100, 130_000, 2_700, 300, MealSlot.LUNCH)),
        customFoods = listOf(CustomFoodEntity(9, "My food", 100_000, 10_000, 400)),
        savedMeals = listOf(SavedMealEntity(10, "Lunch", 500)),
        savedMealItems = listOf(SavedMealItemEntity(10, 0, "rice", "Rice", 100, 130_000, 2_700)),
        weightEntries = listOf(WeightEntryEntity(11, 80.5, 600)),
        settings = listOf(SettingsEntity(1, 2_100, 170)),
        exercises = WorkoutPlanSeedData.ppl45v2.exercises.sortedBy { it.id },
        workoutPlans = listOf(WorkoutPlanSeedData.ppl45v2.plan),
        workoutPlanDays = WorkoutPlanSeedData.ppl45v2.days.sortedWith(compareBy({ it.planId }, { it.position }, { it.id })),
        exercisePrescriptions = WorkoutPlanSeedData.ppl45v2.prescriptions.sortedWith(compareBy({ it.dayId }, { it.position }, { it.id })),
    )

    private fun preparedBackup(
        prepared: PreparedMealEntity = PreparedMealEntity("prep-stable", 10, 20_388, 4, 1, false, 700),
        logIds: List<String> = listOf("log-stable"),
    ): PortableBackup {
        val base = validBackup()
        val loggedAt = LocalDate.ofEpochDay(prepared.weekStartEpochDay)
            .atTime(12, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val recipeItems = listOf(
            SavedMealItemEntity(10, 0, "rice", "Rice", 100, 130_000, 2_700),
            SavedMealItemEntity(10, 1, "beans", "Beans", 80, 90_000, 6_000),
        )
        val rows = logIds.flatMapIndexed { logIndex, logId ->
            recipeItems.mapIndexed { itemIndex, item ->
                FoodEntryEntity(
                    id = 100L + logIndex * 10 + itemIndex,
                    foodId = "meal-10-${item.foodId}",
                    name = item.name,
                    grams = item.grams,
                    calorieMilliUnits = (item.calorieMilliUnitsPer100g * item.grams + 50) / 100,
                    proteinMilligrams = (item.proteinMilligramsPer100g * item.grams + 50) / 100,
                    loggedAt = loggedAt,
                    mealSlot = MealSlot.LUNCH,
                    preparedMealId = prepared.id,
                    preparedLogId = logId,
                    loggedDateEpochDay = prepared.weekStartEpochDay,
                )
            }
        }
        return base.copy(
            foodEntries = rows,
            savedMealItems = recipeItems,
            preparedMeals = listOf(prepared),
        )
    }

    private fun validationMessage(backup: PortableBackup): String? =
        runCatching { codec.decodeAndValidate(codec.encode(backup)) }.exceptionOrNull()?.message

    @Test fun `round trip preserves every Room domain and active session`() {
        val expected = preparedBackup()

        val decoded = codec.decodeAndValidate(codec.encode(expected))

        assertEquals(expected, decoded)
        assertEquals(null, decoded.workoutSessions.single().completedAt)
        assertEquals(1, decoded.setCompletions.size)
        assertEquals("prep-stable", decoded.preparedMeals.single().id)
    }

    @Test fun `format v2 remains compatible without prepared meal domain`() {
        val legacy = codec.encode(validBackup().copy(version = 2, preparedMeals = emptyList()))
            .replace("\"preparedMeals\":[],", "")
        val decoded = codec.decodeAndValidate(legacy)
        assertEquals(2, decoded.version)
        assertTrue(decoded.preparedMeals.isEmpty())
    }

    @Test fun `format v2 ignores prepared meal fields from newer writers`() {
        val legacy = validBackup().copy(
            version = 2,
            preparedMeals = listOf(PreparedMealEntity("newer-field", 999, 20_388, 4, 1, false, 700)),
            foodEntries = listOf(validBackup().foodEntries.single().copy(
                preparedMealId = "newer-field", preparedLogId = "newer-log", loggedDateEpochDay = 20_388,
            )),
        )
        val decoded = codec.decodeAndValidate(codec.encode(legacy))
        assertTrue(decoded.preparedMeals.isEmpty())
        assertEquals(null, decoded.foodEntries.single().preparedMealId)
        assertEquals(null, decoded.foodEntries.single().preparedLogId)
        assertEquals(null, decoded.foodEntries.single().loggedDateEpochDay)
    }

    @Test fun `format v3 rejects invalid prepared counts without rewriting supplied backup`() {
        val backup = validBackup().copy(preparedMeals = listOf(
            PreparedMealEntity("invalid-count", 10, 20_388, 0, 9, false, 700),
        ))
        val error = runCatching { codec.decodeAndValidate(codec.encode(backup)) }.exceptionOrNull()
        assertTrue(error is BackupValidationException)
        assertTrue(error?.message.orEmpty().contains("Invalid prepared meal invalid-count"))
    }

    @Test fun `format v3 rejects prepared meal referencing missing recipe`() {
        val backup = validBackup().copy(preparedMeals = listOf(
            PreparedMealEntity("dangling", 999, 20_388, 4, 1, false, 700),
        ))
        val error = runCatching { codec.decodeAndValidate(codec.encode(backup)) }.exceptionOrNull()
        assertEquals("Prepared meal references missing recipe: dangling/999.", error?.message)
    }

    @Test fun `format v3 rejects prepared weeks outside renderable LocalDate range`() {
        listOf(Long.MIN_VALUE, Long.MAX_VALUE - 3).forEach { epochDay ->
            val backup = validBackup().copy(preparedMeals = listOf(
                PreparedMealEntity("bad-week", 10, epochDay, 1, 0, false, 700),
            ))

            val error = runCatching { codec.decodeAndValidate(codec.encode(backup)) }.exceptionOrNull()

            assertEquals(
                "Invalid prepared meal bad-week: Prepared meal week is outside the supported date range.",
                error?.message,
            )
        }
    }

    @Test fun `format v3 preserves prepared food lineage exactly`() {
        val expected = preparedBackup()
        assertEquals(expected, codec.decodeAndValidate(codec.encode(expected)))
    }

    @Test fun `format v3 requires prepared log group count to equal logged portions`() {
        val missing = preparedBackup(logIds = emptyList())
        val extra = preparedBackup(logIds = listOf("log-one", "log-two"))

        assertEquals("Prepared meal prep-stable has 0 log groups but records 1 logged portions.", validationMessage(missing))
        assertEquals("Prepared meal prep-stable has 2 log groups but records 1 logged portions.", validationMessage(extra))
    }

    @Test fun `format v3 validates prepared log group by stable selected date`() {
        val valid = preparedBackup()
        val shiftedTimestamp = valid.foodEntries.map { it.copy(loggedAt = it.loggedAt + 7 * 24 * 60 * 60 * 1_000L) }

        assertEquals(
            valid.copy(foodEntries = shiftedTimestamp),
            codec.decodeAndValidate(codec.encode(valid.copy(foodEntries = shiftedTimestamp))),
        )

        val outsideWeek = valid.foodEntries.map { it.copy(loggedDateEpochDay = 20_395) }

        assertEquals(
            "Prepared food log group log-stable is outside prepared meal week prep-stable.",
            validationMessage(valid.copy(foodEntries = outsideWeek)),
        )
    }

    @Test fun `format v3 rejects inconsistent prepared selected dates within one log group`() {
        val valid = preparedBackup()
        val inconsistent = valid.foodEntries.mapIndexed { index, row ->
            if (index == 0) row else row.copy(loggedDateEpochDay = row.loggedDateEpochDay!! + 1)
        }

        assertEquals(
            "Inconsistent prepared food log group: log-stable.",
            validationMessage(valid.copy(foodEntries = inconsistent)),
        )
    }

    @Test fun `format v3 rejects truncated prepared recipe group`() {
        val valid = preparedBackup()

        assertEquals(
            "Prepared food log group log-stable does not match recipe 10.",
            validationMessage(valid.copy(foodEntries = valid.foodEntries.dropLast(1))),
        )
    }

    @Test fun `format v3 rejects duplicated prepared recipe row`() {
        val valid = preparedBackup()
        val duplicate = valid.foodEntries.first().copy(id = 999)

        assertEquals(
            "Prepared food log group log-stable does not match recipe 10.",
            validationMessage(valid.copy(foodEntries = valid.foodEntries + duplicate)),
        )
    }

    @Test fun `format v3 rejects fabricated prepared recipe row fields`() {
        val valid = preparedBackup()
        val original = valid.foodEntries.first()
        val mutations = listOf(
            original.copy(foodId = "meal-10-fabricated"),
            original.copy(name = "Fabricated"),
            original.copy(grams = original.grams + 1),
            original.copy(calorieMilliUnits = original.calorieMilliUnits + 1),
            original.copy(proteinMilligrams = original.proteinMilligrams + 1),
        )

        mutations.forEach { mutation ->
            val rows = valid.foodEntries.map { if (it.id == mutation.id) mutation else it }
            assertEquals(
                "Prepared food log group log-stable does not match recipe 10.",
                validationMessage(valid.copy(foodEntries = rows)),
            )
        }
    }

    @Test fun `format v1 remains import compatible with absent workout plan domains`() {
        val legacy = """{"version":1,"workoutSessions":[],"setCompletions":[],"exerciseCompletions":[],"foodEntries":[],"customFoods":[],"savedMeals":[],"savedMealItems":[],"weightEntries":[],"settings":[{"id":1,"calorieGoal":2000,"proteinGoal":160}]}"""

        val decoded = codec.decodeAndValidate(legacy)

        assertEquals(1, decoded.version)
        assertTrue(decoded.exercises.isEmpty())
    }

    @Test fun `export JSON is deterministic regardless of input order`() {
        val first = validBackup().copy(weightEntries = listOf(WeightEntryEntity(12, 81.0, 700), WeightEntryEntity(11, 80.5, 600)))
        val second = first.copy(weightEntries = first.weightEntries.reversed())

        assertEquals(codec.encode(first), codec.encode(second))
    }

    @Test fun `malformed JSON is rejected with deterministic error`() {
        val error = runCatching { codec.decodeAndValidate("{not-json") }.exceptionOrNull()

        assertTrue(error is BackupValidationException)
        assertEquals("Malformed backup JSON.", error?.message)
    }

    @Test fun `unsupported version is rejected`() {
        val json = codec.encode(validBackup()).replace("\"version\":3", "\"version\":4")

        val error = runCatching { codec.decodeAndValidate(json) }.exceptionOrNull()

        assertEquals("Unsupported backup version: 4.", error?.message)
    }

    @Test fun `duplicate primary identity is rejected`() {
        val backup = validBackup().let { it.copy(foodEntries = it.foodEntries + it.foodEntries.single()) }

        val error = runCatching { codec.decodeAndValidate(codec.encode(backup)) }.exceptionOrNull()

        assertEquals("Duplicate food entry identity: 8.", error?.message)
    }

    @Test fun `v2 rejects ambiguous persisted workout graph identities`() {
        val seed = WorkoutPlanSeedData.ppl45v2
        val cases = listOf(
            validBackup().copy(
                exercisePrescriptions = seed.prescriptions.mapIndexed { index, row ->
                    if (index == 0) row.copy(id = "") else row
                },
            ) to "Invalid exercise prescription .",
            validBackup().copy(
                workoutPlanDays = seed.days.mapIndexed { index, row ->
                    if (index == 1) row.copy(position = seed.days.first().position) else row
                },
            ) to "Duplicate workout plan day position: gymbro-ppl-45-v2/0.",
            validBackup().copy(
                exercisePrescriptions = seed.prescriptions.mapIndexed { index, row ->
                    if (index == 1) row.copy(position = seed.prescriptions.first().position) else row
                },
            ) to "Duplicate exercise prescription position: push/0.",
            validBackup().copy(
                exercisePrescriptions = seed.prescriptions.mapIndexed { index, row ->
                    if (index == 1) row.copy(exerciseId = seed.prescriptions.first().exerciseId) else row
                },
            ) to "Duplicate exercise prescription exercise: push/chest-press.",
        )

        cases.forEach { (backup, expectedMessage) ->
            val error = runCatching { codec.decodeAndValidate(codec.encode(backup)) }.exceptionOrNull()
            assertEquals(expectedMessage, error?.message)
        }
    }

    @Test fun `broken saved meal relationship is rejected`() {
        val backup = validBackup().copy(savedMealItems = listOf(SavedMealItemEntity(99, 0, "rice", "Rice", 100, 130_000, 2_700)))

        val error = runCatching { codec.decodeAndValidate(codec.encode(backup)) }.exceptionOrNull()

        assertEquals("Saved meal item references missing meal: 99.", error?.message)
    }

    @Test fun `invalid values are rejected before restore`() {
        val backup = validBackup().copy(weightEntries = listOf(WeightEntryEntity(11, 401.0, 600)))
        val json = codec.encode(backup)

        val error = runCatching { codec.decodeAndValidate(json) }.exceptionOrNull()

        assertEquals("Invalid weight entry 11.", error?.message)
    }

    @Test fun `workout completions must belong to the session day prescription`() {
        val wrongExercise = validBackup().copy(
            setCompletions = listOf(SetCompletionEntity(7, "lat-pulldown", 0)),
            exerciseCompletions = emptyList(),
        )

        val error = runCatching { codec.decodeAndValidate(codec.encode(wrongExercise)) }.exceptionOrNull()

        assertEquals("Set completion references an exercise outside session 7: lat-pulldown.", error?.message)
    }

    @Test fun `persisted workout completion validates against persisted prescription`() {
        val backup = validBackup().copy(
            setCompletions = emptyList(),
            exerciseCompletions = listOf(ExerciseCompletionEntity(7, "chest-press", 100_000, 8, 200)),
        )

        assertEquals(backup, codec.decodeAndValidate(codec.encode(backup)))
    }

    @Test fun `format v2 session relationships never fall back to legacy seed data`() {
        val withoutChestPress = validBackup().let { backup ->
            backup.copy(
                setCompletions = listOf(SetCompletionEntity(7, "chest-press", 0)),
                exerciseCompletions = emptyList(),
                exercises = backup.exercises + ExerciseEntity("custom-push", "Custom push", "chest", "machine"),
                exercisePrescriptions = backup.exercisePrescriptions.map {
                    if (it.id == "push-chest-press") it.copy(exerciseId = "custom-push", isSeeded = false) else it
                },
            )
        }

        val error = runCatching { codec.decodeAndValidate(codec.encode(withoutChestPress)) }.exceptionOrNull()

        assertEquals("Set completion references an exercise outside session 7: chest-press.", error?.message)
    }

    @Test fun `format v1 session relationships retain legacy seed fallback`() {
        val legacy = validBackup().copy(
            version = 1,
            exercises = emptyList(),
            workoutPlans = emptyList(),
            workoutPlanDays = emptyList(),
            exercisePrescriptions = emptyList(),
        )

        assertEquals(legacy, codec.decodeAndValidate(codec.encode(legacy)))
    }

    @Test fun `v2 rejects modified seeded records and missing canonical seed identities`() {
        val modifiedSeed = validBackup().copy(
            exercises = validBackup().exercises.map {
                if (it.id == "chest-press") it.copy(name = "Tampered seed") else it
            },
        )
        val missingSeed = validBackup().copy(
            exercisePrescriptions = validBackup().exercisePrescriptions.filterNot { it.id == "push-chest-press" },
            setCompletions = emptyList(),
            exerciseCompletions = emptyList(),
        )

        val modifiedError = runCatching { codec.decodeAndValidate(codec.encode(modifiedSeed)) }.exceptionOrNull()
        val missingError = runCatching { codec.decodeAndValidate(codec.encode(missingSeed)) }.exceptionOrNull()

        assertEquals("Modified seeded exercise: chest-press.", modifiedError?.message)
        assertEquals("Missing required seeded exercise prescription: push-chest-press.", missingError?.message)
    }

    @Test fun `v2 accepts user edited canonical seed identity when marked unseeded`() {
        val edited = validBackup().copy(
            exercisePrescriptions = validBackup().exercisePrescriptions.map {
                if (it.id == "push-chest-press") it.copy(sets = 4, isSeeded = false) else it
            },
        )

        assertEquals(edited, codec.decodeAndValidate(codec.encode(edited)))
    }

    @Test fun `format v2 legacy set completion remains valid after prescription sets are lowered`() {
        val edited = validBackup().copy(
            setCompletions = listOf(SetCompletionEntity(7, "chest-press", 2)),
            exerciseCompletions = emptyList(),
            exercisePrescriptions = validBackup().exercisePrescriptions.map {
                if (it.id == "push-chest-press") it.copy(sets = 2, isSeeded = false) else it
            },
        )

        assertEquals(edited, codec.decodeAndValidate(codec.encode(edited)))
    }

    @Test fun `format v2 legacy set index must be nonnegative and conservatively bounded`() {
        listOf(-1, 20).forEach { setIndex ->
            val invalid = validBackup().copy(
                setCompletions = listOf(SetCompletionEntity(7, "chest-press", setIndex)),
                exerciseCompletions = emptyList(),
            )

            val error = runCatching { codec.decodeAndValidate(codec.encode(invalid)) }.exceptionOrNull()

            assertEquals("Invalid set completion 7/chest-press/$setIndex.", error?.message)
        }
    }

    @Test fun `format v1 set index retains static plan bound`() {
        val legacy = validBackup().copy(
            version = 1,
            setCompletions = listOf(SetCompletionEntity(7, "chest-press", 3)),
            exerciseCompletions = emptyList(),
            exercises = emptyList(),
            workoutPlans = emptyList(),
            workoutPlanDays = emptyList(),
            exercisePrescriptions = emptyList(),
        )

        val error = runCatching { codec.decodeAndValidate(codec.encode(legacy)) }.exceptionOrNull()

        assertEquals("Invalid set completion 7/chest-press/3.", error?.message)
    }

    @Test fun `exercise completion time must fall within its workout session`() {
        val beforeStart = validBackup().copy(
            setCompletions = emptyList(),
            exerciseCompletions = listOf(ExerciseCompletionEntity(7, "chest-press", 100_000, 8, 99)),
        )

        val error = runCatching { codec.decodeAndValidate(codec.encode(beforeStart)) }.exceptionOrNull()

        assertEquals("Exercise completion time is outside session 7: chest-press.", error?.message)
    }

    @Test fun `invalid UTF-8 backup bytes are rejected instead of replaced`() {
        val invalid = byteArrayOf('{'.code.toByte(), '"'.code.toByte(), 0xC3.toByte(), 0x28, '"'.code.toByte(), '}'.code.toByte())

        val error = runCatching { decodeStrictUtf8(invalid) }.exceptionOrNull()

        assertTrue(error is BackupValidationException)
        assertEquals("Backup is not valid UTF-8.", error?.message)
    }

    @Test fun `credential-like fields are not part of the format`() {
        val json = codec.encode(validBackup())

        assertTrue("USDA credential must never be exported", !json.contains("api", ignoreCase = true) && !json.contains("key", ignoreCase = true))
    }
}
