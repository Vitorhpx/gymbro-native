package com.gymbro.local

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gymbro.local.data.FoodEntryEntity
import com.gymbro.local.data.CustomFoodEntity
import com.gymbro.local.data.ExerciseCompletionEntity
import com.gymbro.local.data.ExerciseEntity
import com.gymbro.local.data.PlanExerciseRow
import com.gymbro.local.data.PreparedMealEntity

import com.gymbro.local.data.MealSlot
import com.gymbro.local.data.SavedMealItemEntity
import com.gymbro.local.data.SavedMealWithItems
import com.gymbro.local.data.WeightEntryEntity
import com.gymbro.local.data.WorkoutSessionEntity
import com.gymbro.local.data.displayCalories
import com.gymbro.local.data.displayProteinGrams
import com.gymbro.local.data.datedHistory
import com.gymbro.local.data.loggedOn
import com.gymbro.local.data.isRemovableFromUi
import com.gymbro.local.data.totalCalories
import com.gymbro.local.data.totalProteinGrams
import com.gymbro.local.data.planningCompleteFor
import com.gymbro.local.data.reachablePools
import com.gymbro.local.domain.SeedData
import com.gymbro.local.network.FoodSearchValidation
import com.gymbro.local.ui.AdaptiveLayoutPolicy
import com.gymbro.local.ui.NavigationMode
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

class MainActivity : ComponentActivity() {
    private val vm by viewModels<MainViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scanner = GmsBarcodeScanning.getClient(
            this,
            GmsBarcodeScannerOptions.Builder().setBarcodeFormats(
                Barcode.FORMAT_EAN_8, Barcode.FORMAT_EAN_13, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
            ).enableAutoZoom().build(),
        )
        setContent { GymBroTheme { GymBroApp(vm) {
            scanner.startScan()
                .addOnSuccessListener { code -> code.rawValue?.let(vm::resolveBarcode) ?: vm.barcodeScanFailed("The scanner did not return a product code.") }
                .addOnFailureListener { vm.barcodeScanFailed(it.message ?: "Barcode scanning failed.") }
        } } }
    }
}

private val GymBroColors = darkColorScheme(
    primary = Color(0xFFB6F36B), onPrimary = Color(0xFF172700),
    primaryContainer = Color(0xFF304B12), onPrimaryContainer = Color(0xFFD5FF9E),
    secondary = Color(0xFF65D9FF), onSecondary = Color(0xFF003544),
    tertiary = Color(0xFFFFB95C), background = Color(0xFF090C0E),
    surface = Color(0xFF101518), surfaceVariant = Color(0xFF1A2226),
    onSurface = Color(0xFFF1F5F6), onSurfaceVariant = Color(0xFFB7C4C9),
    error = Color(0xFFFFB4AB),
)

@Composable private fun GymBroTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GymBroColors, content = content)
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Today("Today", Icons.Default.Home), Train("Train", Icons.Default.FitnessCenter),
    Eat("Eat", Icons.Default.Fastfood), Progress("Progress", Icons.Default.BarChart),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable fun GymBroApp(vm: MainViewModel = viewModel(), scanBarcode: () -> Unit = {}) {
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val active by vm.active.collectAsState(); val sessions by vm.sessions.collectAsState()
    val exerciseCompletions by vm.exerciseCompletions.collectAsState(); val foods by vm.foodEntries.collectAsState()
    val customFoods by vm.customFoods.collectAsState(); val savedMeals by vm.savedMeals.collectAsState()
    val preparedMeals by vm.preparedMeals.collectAsState()
    val weights by vm.weights.collectAsState(); val settings by vm.settings.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val planExercises by vm.planExercises.collectAsState()
    val search by vm.foodSearch.collectAsState(); val update by vm.update.collectAsState()
    val backup by vm.backup.collectAsState()
    val barcode by vm.barcode.collectAsState()
    val hasKey by vm.hasPersonalUsdaKey.collectAsState()
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::exportBackup) }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importBackup) }
    val adaptiveInfo = currentWindowAdaptiveInfo()

    NavigationSuiteScaffold(
        layoutType = when (AdaptiveLayoutPolicy.forWidth(adaptiveInfo.windowSizeClass.minWidthDp).navigation) {
            NavigationMode.BOTTOM -> NavigationSuiteType.NavigationBar
            NavigationMode.RAIL -> NavigationSuiteType.NavigationRail
        },
        navigationSuiteItems = {
            Tab.entries.forEach { item ->
                item(
                    selected = tab == item,
                    onClick = { tab = item },
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label) },
                )
            }
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val mode = AdaptiveLayoutPolicy.forWidth(maxWidth.value.roundToInt())
            val directive = calculatePaneScaffoldDirective(adaptiveInfo)
            val scaffoldValue = calculateThreePaneScaffoldValue(
                maxHorizontalPartitions = directive.maxHorizontalPartitions,
                adaptStrategies = SupportingPaneScaffoldDefaults.adaptStrategies(),
                currentDestination = ThreePaneScaffoldDestinationItem<Nothing?>(
                    pane = SupportingPaneScaffoldRole.Main,
                    contentKey = null,
                ),
            )
            SupportingPaneScaffold(
                directive = directive,
                value = scaffoldValue,
                modifier = Modifier.fillMaxSize(),
                mainPane = {
                    AnimatedPane(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .then(mode.maxContentWidthDp?.let { Modifier.widthIn(max = it.dp) } ?: Modifier)
                                .fillMaxWidth()
                                .align(Alignment.Center),
                        ) {
                    when (tab) {
                        Tab.Today -> TodayScreen(active, sessions, foods, weights, planExercises, settings?.calorieGoal ?: 2000, settings?.proteinGoal ?: 160, vm, { tab = it }, { showSettings = true })
                        Tab.Train -> TrainScreen(active, exerciseCompletions, exercises, planExercises, vm, { showSettings = true })
                        Tab.Eat -> EatScreen(foods, customFoods, savedMeals, preparedMeals, settings?.calorieGoal ?: 2000, settings?.proteinGoal ?: 160, search, barcode, vm, scanBarcode, { showSettings = true })
                        Tab.Progress -> ProgressScreen(sessions, weights, vm, { showSettings = true })
                    }
                        }
                    }
                },
                supportingPane = {
                    AnimatedPane(Modifier.fillMaxSize()) {
                        SupportingPane(tab, active, foods, weights, Modifier.fillMaxSize())
                    }
                }
            )
        }
    }
    if (showSettings) SettingsDialog(
        hasKey, update, backup, settings?.calorieGoal ?: 2000, settings?.proteinGoal ?: 160, vm,
        exportBackup = { createBackup.launch("gymbro-backup.json") },
        importBackup = { openBackup.launch(arrayOf("application/json", "text/json", "text/plain")) },
        onDismiss = { showSettings = false },
    )
}

@Composable private fun Page(title: String, eyebrow: String, onSettings: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(eyebrow.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
        }
        content(); Spacer(Modifier.height(24.dp))
    }
}

@Composable private fun Panel(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); content()
        }
    }
}

@Composable private fun Metric(label: String, value: String, status: String, good: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier, color = if (good) MaterialTheme.colorScheme.primaryContainer else Color(0xFF3B2B13), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium); Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(status, style = MaterialTheme.typography.labelMedium, color = if (good) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.tertiary)
        }
    }
}

private fun startOfWeek(): Long = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
private fun startOfWeekEpochDay(): Long = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
private fun displayDate(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))

@Composable private fun TodayScreen(active: WorkoutSessionEntity?, sessions: List<WorkoutSessionEntity>, foods: List<FoodEntryEntity>, weights: List<WeightEntryEntity>, planRows: List<PlanExerciseRow>, calorieGoal: Int, proteinGoal: Int, vm: MainViewModel, navigate: (Tab) -> Unit, settings: () -> Unit) = Page("Today", "Your daily brief", settings) {
    val todayFoods = foods.loggedOn(LocalDate.now()); val calories = todayFoods.totalCalories(); val protein = todayFoods.totalProteinGrams()
    val weekSessions = sessions.filter { it.completedAt != null && it.completedAt >= startOfWeek() }
    val persistedDays = planRows.groupBy { it.dayId }.values.mapNotNull { it.firstOrNull() }.sortedBy { it.dayPosition }
    val recommended = persistedDays.firstOrNull { day -> weekSessions.none { it.dayId == day.dayId } } ?: persistedDays.firstOrNull()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Metric("ENERGY", "$calories kcal", if (calories in (calorieGoal * .9).roundToInt()..(calorieGoal * 1.1).roundToInt()) "On target" else "${(calorieGoal - calories).coerceAtLeast(0)} remaining", calories >= calorieGoal * .9, Modifier.weight(1f))
        Metric("PROTEIN", "$protein g", if (protein >= proteinGoal) "Goal complete" else "${proteinGoal - protein} remaining", protein >= proteinGoal, Modifier.weight(1f))
    }
    Panel(if (active == null) "Next session" else "Workout in progress") {
        val dayName = active?.dayName ?: recommended?.dayName ?: "Workout"
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.FitnessCenter, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column { Text(dayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("GymBro PPL 45 V2 · about 45 min", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        Button(onClick = { if (active == null) recommended?.let { vm.startWorkout(it.dayId, it.dayName) } else navigate(Tab.Train) }, Modifier.fillMaxWidth(), enabled = active != null || recommended != null) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if (active == null) "Start $dayName" else "Resume on Train") }
    }
    Panel("This week") {
        Text("${weekSessions.size} of 3 workouts completed", fontWeight = FontWeight.SemiBold); LinearProgressIndicator({ (weekSessions.size / 3f).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
        Text(if (weights.any { it.loggedAt >= startOfWeek() }) "✓ Weekly weigh-in complete" else "Weight check-in due — log it on Progress", color = if (weights.any { it.loggedAt >= startOfWeek() }) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalButton(onClick = { navigate(Tab.Eat) }) { Icon(Icons.Default.AddCircle, null); Spacer(Modifier.width(6.dp)); Text("Log food") }
        FilledTonalButton(onClick = { navigate(Tab.Progress) }) { Icon(Icons.Default.Scale, null); Spacer(Modifier.width(6.dp)); Text("Log weight") }
    }
}

@Composable private fun TrainScreen(active: WorkoutSessionEntity?, completions: List<ExerciseCompletionEntity>, exercises: List<ExerciseEntity>, planRows: List<PlanExerciseRow>, vm: MainViewModel, settings: () -> Unit) = Page(if (active == null) "Train" else active.dayName, if (active == null) "PPL · 3 days · 45 min" else "Session active · saved locally", settings) {
    var view by rememberSaveable { mutableStateOf("Plan") }
    var editing by remember { mutableStateOf<PlanExerciseRow?>(null) }
    if (active == null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (view == "Plan") Button({ view = "Plan" }, Modifier.weight(1f)) { Text("Plan") } else OutlinedButton({ view = "Plan" }, Modifier.weight(1f)) { Text("Plan") }
            if (view == "Library") Button({ view = "Library" }, Modifier.weight(1f)) { Text("Exercise library") } else OutlinedButton({ view = "Library" }, Modifier.weight(1f)) { Text("Exercise library") }
        }
        if (view == "Plan") {
            planRows.groupBy { it.dayId }.values.forEachIndexed { index, dayRows ->
                val day = dayRows.first()
                Panel("0${index + 1}  ${day.dayName.uppercase()}") {
                    dayRows.forEachIndexed { rowIndex, row ->
                        Text(row.exerciseName, fontWeight = FontWeight.Bold)
                        Text("${row.sets} sets · ${row.minReps}–${row.maxReps} reps · ${row.restSeconds}s rest", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton({ vm.moveExercise(row.dayId, row.prescriptionId, -1) }, enabled = rowIndex > 0) { Text("↑") }
                            OutlinedButton({ vm.moveExercise(row.dayId, row.prescriptionId, 1) }, enabled = rowIndex < dayRows.lastIndex) { Text("↓") }
                            OutlinedButton({ editing = row }) { Text("Edit") }
                        }
                    }
                    Button({ vm.startWorkout(day.dayId, day.dayName) }, Modifier.fillMaxWidth()) { Text("Start ${day.dayName}") }
                }
            }
        } else ExerciseLibrary(exercises, vm)
    } else {
        val dayRows = planRows.filter { it.dayId == active.dayId }.sortedBy { it.exercisePosition }
        Text("Started ${displayDate(active.startedAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        dayRows.forEach { exercise ->
            val done = completions.firstOrNull { it.sessionId == active.id && it.exerciseId == exercise.exerciseId }
            val previous = completions.filter { it.exerciseId == exercise.exerciseId && it.sessionId != active.id }.maxWithOrNull(compareBy<ExerciseCompletionEntity> { it.weightMilliUnits }.thenBy { it.reps })
            var weight by rememberSaveable(active.id, exercise.exerciseId) { mutableStateOf(done?.let { "%.1f".format(it.weightMilliUnits / 1000.0) } ?: previous?.let { "%.1f".format(it.weightMilliUnits / 1000.0) } ?: "") }
            var reps by rememberSaveable(active.id, exercise.exerciseId) { mutableStateOf(done?.reps?.toString() ?: previous?.reps?.toString() ?: "") }
            Panel(exercise.exerciseName) {
                Text("${exercise.sets} sets · ${exercise.minReps}–${exercise.maxReps} reps · ${exercise.restSeconds}s rest", color = MaterialTheme.colorScheme.secondary)
                previous?.let { Text("Previous best: ${"%.1f".format(it.weightMilliUnits / 1000.0)} lb × ${it.reps}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1f), label = { Text("Weight lb") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = done == null, singleLine = true)
                    OutlinedTextField(reps, { reps = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Reps") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = done == null, singleLine = true)
                }
                if (done == null) Button({ vm.setExerciseCompleted(active.id, exercise.exerciseId, weight.toDoubleOrNull() ?: -1.0, reps.toIntOrNull() ?: 0, true) }, Modifier.fillMaxWidth(), enabled = weight.toDoubleOrNull()?.let { it in 0.0..5000.0 } == true && reps.toIntOrNull()?.let { it in 1..100 } == true) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text("Complete exercise") }
                else OutlinedButton({ vm.setExerciseCompleted(active.id, exercise.exerciseId, 0.0, 0, false) }, Modifier.fillMaxWidth()) { Text("✓ Completed · Edit") }
            }
        }
        Button({ vm.completeWorkout(active.id) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text("Complete workout") }
    }
    editing?.let { row -> PrescriptionEditor(row, vm) { editing = null } }
}

@Composable private fun ExerciseLibrary(exercises: List<ExerciseEntity>, vm: MainViewModel) {
    var name by rememberSaveable { mutableStateOf("") }; var muscle by rememberSaveable { mutableStateOf("") }; var category by rememberSaveable { mutableStateOf("") }
    Panel("Create custom exercise") {
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(muscle, { muscle = it }, Modifier.weight(1f), label = { Text("Muscle group") }, singleLine = true)
            OutlinedTextField(category, { category = it }, Modifier.weight(1f), label = { Text("Category") }, singleLine = true)
        }
        Button({ vm.createExercise(name, muscle, category); name = ""; muscle = ""; category = "" }, Modifier.fillMaxWidth(), enabled = name.trim().length >= 2) { Text("Add exercise") }
    }
    Panel("Exercise library · ${exercises.size}") {
        exercises.forEach { Text("${it.name} · ${it.muscleGroup} · ${it.category}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable private fun PrescriptionEditor(row: PlanExerciseRow, vm: MainViewModel, dismiss: () -> Unit) {
    var sets by rememberSaveable(row.prescriptionId) { mutableStateOf(row.sets.toString()) }; var min by rememberSaveable(row.prescriptionId) { mutableStateOf(row.minReps.toString()) }
    var max by rememberSaveable(row.prescriptionId) { mutableStateOf(row.maxReps.toString()) }; var rest by rememberSaveable(row.prescriptionId) { mutableStateOf(row.restSeconds.toString()) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Edit ${row.exerciseName}") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(sets, { sets = it.filter(Char::isDigit) }, label = { Text("Sets") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(min, { min = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Min reps") }, singleLine = true); OutlinedTextField(max, { max = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Max reps") }, singleLine = true) }
        OutlinedTextField(rest, { rest = it.filter(Char::isDigit) }, label = { Text("Rest seconds") }, singleLine = true)
    } }, confirmButton = { Button({ vm.editPrescription(row.prescriptionId, sets.toIntOrNull() ?: 0, min.toIntOrNull() ?: 0, max.toIntOrNull() ?: 0, rest.toIntOrNull() ?: -1); dismiss() }) { Text("Save") } }, dismissButton = { OutlinedButton(dismiss) { Text("Cancel") } })
}

@Composable private fun EatScreen(entries: List<FoodEntryEntity>, customFoods: List<CustomFoodEntity>, savedMeals: List<SavedMealWithItems>, preparedMeals: List<PreparedMealEntity>, calorieGoal: Int, proteinGoal: Int, search: FoodSearchUiState, barcode: BarcodeUiState, vm: MainViewModel, scanBarcode: () -> Unit, settings: () -> Unit) = Page("Eat", "Fuel the work", settings) {
    val today = entries.loggedOn(LocalDate.now())
    var query by rememberSaveable { mutableStateOf("") }; var grams by rememberSaveable { mutableStateOf("100") }; var slot by rememberSaveable { mutableStateOf(MealSlot.LUNCH) }
    var queryError by remember { mutableStateOf<String?>(null) }; var gramsError by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("CALORIES", "${today.totalCalories()} / $calorieGoal", if (today.totalCalories() >= calorieGoal * .9) "On target" else "In progress", today.totalCalories() >= calorieGoal * .9, Modifier.weight(1f)); Metric("PROTEIN", "${today.totalProteinGrams()} / $proteinGoal g", if (today.totalProteinGrams() >= proteinGoal) "Complete" else "In progress", today.totalProteinGrams() >= proteinGoal, Modifier.weight(1f)) }
    Panel("Meal slot") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MealSlot.entries.forEach { item -> if (slot == item) Button({ slot = item }) { Text(item.name.lowercase().replaceFirstChar(Char::uppercase)) } else OutlinedButton({ slot = item }) { Text(item.name.lowercase().replaceFirstChar(Char::uppercase)) } } }
        Text("The selected slot applies to quick logs, search, custom foods, recipes, and barcode products.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Panel("Scan packaged food") {
        Text("Scanning starts only when you tap the button. Google Code Scanner handles the camera without granting GymBro camera permission; the product code is then looked up over HTTPS at Open Food Facts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(scanBarcode, Modifier.fillMaxWidth(), enabled = barcode !is BarcodeUiState.Loading) { Text(if (barcode is BarcodeUiState.Loading) "Looking up product…" else "Scan barcode") }
        when (barcode) {
            BarcodeUiState.Idle -> Unit
            BarcodeUiState.Loading -> StatusRow("Looking up Open Food Facts…")
            is BarcodeUiState.Error -> { Text(barcode.message, color = MaterialTheme.colorScheme.error); OutlinedButton(vm::clearBarcode) { Text("Dismiss") } }
            is BarcodeUiState.Found -> { Text(barcode.product.name, fontWeight = FontWeight.Bold); Text("${barcode.product.caloriesPer100g.roundToInt()} kcal · ${"%.1f".format(barcode.product.proteinPer100g)} g protein / 100 g"); Button({ vm.logBarcodeProduct(barcode.product, grams.toIntOrNull() ?: 0, slot) }, Modifier.fillMaxWidth(), enabled = grams.toIntOrNull() in 1..5000) { Text("Log scanned product · $grams g") } }
        }
    }
    Panel("USDA FoodData Central") {
        Text("Search when you submit. DEMO_KEY works out of the box (30 requests/hour, 50/day); add your own key in Settings for higher limits.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(query, { query = it; queryError = null }, Modifier.fillMaxWidth(), label = { Text("Food search") }, leadingIcon = { Icon(Icons.Default.Search, null) }, isError = queryError != null, supportingText = { queryError?.let { Text(it) } }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { queryError = FoodSearchValidation.queryError(query); if (queryError == null) vm.searchFood(query) }))
        Button({ queryError = FoodSearchValidation.queryError(query); if (queryError == null) vm.searchFood(query) }, enabled = search !is FoodSearchUiState.Loading, modifier = Modifier.fillMaxWidth()) { if (search is FoodSearchUiState.Loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Search USDA") }
        when (search) {
            FoodSearchUiState.Idle -> Text("Enter a food name to find nutrition per 100 g.")
            FoodSearchUiState.Loading -> Text("Searching USDA…")
            is FoodSearchUiState.Error -> Text(search.message, color = MaterialTheme.colorScheme.error)
            is FoodSearchUiState.Results -> if (search.foods.isEmpty()) Text("No matching foods found. Try a broader term.") else search.foods.forEach { food -> OutlinedButton({ gramsError = FoodSearchValidation.gramsError(grams); if (gramsError == null) vm.logFood(food, grams.toInt(), slot) }, Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) { Text(food.description, fontWeight = FontWeight.SemiBold); Text("${food.caloriesPer100g.roundToInt()} kcal · ${"%.1f".format(food.proteinPer100g)} g protein / 100 g", style = MaterialTheme.typography.bodySmall) } } }
        }
    }
    Panel("Amount & quick log") {
        OutlinedTextField(grams, { grams = it.filter(Char::isDigit); gramsError = null }, Modifier.fillMaxWidth(), label = { Text("Grams") }, isError = gramsError != null, supportingText = { gramsError?.let { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        SeedData.foods.forEach { food -> FilledTonalButton({ vm.logFood(food, grams.toIntOrNull() ?: 0, slot) }, Modifier.fillMaxWidth()) { Text("${food.name} · ${food.caloriesPer100g.toInt()} kcal/100g") } }
    }
    CustomFoodsPanel(customFoods, grams, slot, vm)
    PreparedMealsPanel(savedMeals, preparedMeals, slot, vm)
    SavedMealsPanel(savedMeals, slot, vm)
    if (today.isNotEmpty()) Panel("Logged today") { MealSlot.entries.forEach { meal ->
        val mealEntries = today.filter { it.mealSlot == meal }; if (mealEntries.isNotEmpty()) { Text(meal.name.lowercase().replaceFirstChar(Char::uppercase), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold); mealEntries.forEach { entry -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(entry.name, fontWeight = FontWeight.SemiBold); Text("${entry.grams} g · ${entry.displayCalories()} kcal · ${entry.displayProteinGrams()} g protein${if (entry.preparedMealId != null) " · prepared portion" else ""}", style = MaterialTheme.typography.bodySmall) }; if (entry.isRemovableFromUi) OutlinedButton({ vm.deleteFood(entry.id) }) { Text("Remove") } }; HorizontalDivider() } }
    } }
    val history = entries.datedHistory(LocalDate.now())
    if (history.isNotEmpty()) Panel("Dated history") {
        history.forEach { (date, rows) ->
            Text(date.format(DateTimeFormatter.ofPattern("MMM d, yyyy")), fontWeight = FontWeight.Bold)
            Text("${rows.size} item${if (rows.size == 1) "" else "s"} · ${rows.totalCalories()} kcal · ${rows.totalProteinGrams()} g protein", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun CustomFoodsPanel(customFoods: List<CustomFoodEntity>, grams: String, slot: MealSlot, vm: MainViewModel) = Panel("Custom foods") {
    var name by rememberSaveable { mutableStateOf("") }; var calories by rememberSaveable { mutableStateOf("") }; var protein by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Food name") }, singleLine = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(calories, { calories = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1f), label = { Text("kcal/100 g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        OutlinedTextField(protein, { protein = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1f), label = { Text("protein/100 g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
    }
    Button({ vm.createCustomFood(name, calories.toDoubleOrNull() ?: -1.0, protein.toDoubleOrNull() ?: -1.0); name = ""; calories = ""; protein = "" }, Modifier.fillMaxWidth(), enabled = name.trim().length >= 2 && calories.toDoubleOrNull()?.let { it in 0.0..2000.0 } == true && protein.toDoubleOrNull()?.let { it in 0.0..100.0 } == true) { Text("Save custom food") }
    customFoods.forEach { food -> OutlinedButton({ vm.logFood(food, grams.toIntOrNull() ?: 0, slot) }, Modifier.fillMaxWidth(), enabled = grams.toIntOrNull() in 1..5000) { Text("Log ${food.name} · ${food.calorieMilliUnitsPer100g / 1000.0} kcal/100 g") } }
}

@Composable private fun PreparedMealsPanel(savedMeals: List<SavedMealWithItems>, preparedMeals: List<PreparedMealEntity>, slot: MealSlot, vm: MainViewModel) = Panel("Weekly prepared meals") {
    val weekStart = startOfWeekEpochDay()
    var portions by rememberSaveable { mutableStateOf("4") }
    var logDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val date = runCatching { LocalDate.parse(logDate) }.getOrNull()
    val actionStatus by vm.preparedMealAction.collectAsState()
    Text(
        if (preparedMeals.planningCompleteFor(weekStart)) "✓ Planning complete for this week" else "Weekly planning is not complete",
        color = if (preparedMeals.planningCompleteFor(weekStart)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
    )
    Text("Create a flexible portion pool from a saved recipe; no days are assigned.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(portions, { portions = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Planned portions (1–100)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
    savedMeals.forEach { recipe ->
        OutlinedButton(
            { vm.createPreparedMeal(recipe.meal.id, weekStart, portions.toIntOrNull() ?: 0) },
            Modifier.fillMaxWidth(), enabled = portions.toIntOrNull() in 1..100,
        ) { Text("Prepare ${recipe.meal.name}") }
    }
    HorizontalDivider()
    OutlinedTextField(logDate, { logDate = it }, Modifier.fillMaxWidth(), label = { Text("Log date (YYYY-MM-DD)") }, isError = date == null, singleLine = true)
    Text("Logging uses the ${slot.name.lowercase()} slot selected above.", style = MaterialTheme.typography.bodySmall)
    when (val status = actionStatus) {
        PreparedMealActionStatus.Idle -> Unit
        is PreparedMealActionStatus.Success -> Text(status.message, color = MaterialTheme.colorScheme.primary)
        is PreparedMealActionStatus.Error -> Text(status.message, color = MaterialTheme.colorScheme.error)
    }
    preparedMeals.reachablePools().forEach { prepared ->
        val recipeName = savedMeals.firstOrNull { it.meal.id == prepared.savedMealId }?.meal?.name ?: "Missing recipe"
        val poolMonday = LocalDate.ofEpochDay(prepared.weekStartEpochDay)
        val poolSunday = poolMonday.plusDays(6)
        Text("${poolMonday.format(DateTimeFormatter.ofPattern("MMM d"))}–${poolSunday.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        Text("$recipeName · ${prepared.remainingPortions} remaining", fontWeight = FontWeight.Bold)
        val loggedAt = date?.atTime(12, 0)?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        val loggedDateEpochDay = date?.toEpochDay()
        val dateAllowed = loggedAt != null && loggedDateEpochDay != null && prepared.containsLogDate(loggedDateEpochDay)
        if (prepared.remainingPortions > 0) {
            Button({ vm.logPreparedMeal(prepared.id, slot, loggedAt ?: -1, loggedDateEpochDay ?: -1) }, Modifier.fillMaxWidth(), enabled = dateAllowed) { Text("Log 1 portion on ${date ?: "selected date"}") }
        } else {
            Button({ vm.logPreparedMeal(prepared.id, slot, loggedAt ?: -1, loggedDateEpochDay ?: -1, addExtra = true) }, Modifier.fillMaxWidth(), enabled = dateAllowed && prepared.plannedPortions < 100) { Text("Add and log extra · 0 remains") }
        }
        if (!dateAllowed) Text("Choose a date from $poolMonday through $poolSunday to log this pool.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton({ vm.archivePreparedMeal(prepared.id) }, Modifier.fillMaxWidth()) { Text("Archive prepared meal") }
    }
}

@Composable private fun SavedMealsPanel(savedMeals: List<SavedMealWithItems>, slot: MealSlot, vm: MainViewModel) = Panel("Saved meals & recipes") {
    var mealName by rememberSaveable { mutableStateOf("") }; var itemName by rememberSaveable { mutableStateOf("") }; var itemGrams by rememberSaveable { mutableStateOf("100") }; var itemCalories by rememberSaveable { mutableStateOf("") }; var itemProtein by rememberSaveable { mutableStateOf("") }
    var draft by remember { mutableStateOf(emptyList<SavedMealItemEntity>()) }
    savedMeals.forEach { meal -> FilledTonalButton({ vm.logSavedMeal(meal, slot) }, Modifier.fillMaxWidth()) { Text("Log ${meal.meal.name} · ${meal.items.size} item${if (meal.items.size == 1) "" else "s"}") } }
    HorizontalDivider(); Text("Build recipe", fontWeight = FontWeight.Bold)
    OutlinedTextField(mealName, { mealName = it }, Modifier.fillMaxWidth(), label = { Text("Recipe name") }, singleLine = true)
    OutlinedTextField(itemName, { itemName = it }, Modifier.fillMaxWidth(), label = { Text("Ingredient name") }, singleLine = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(itemGrams, { itemGrams = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("grams") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        OutlinedTextField(itemCalories, { itemCalories = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1f), label = { Text("kcal/100 g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        OutlinedTextField(itemProtein, { itemProtein = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1f), label = { Text("protein/100 g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
    }
    OutlinedButton({
        draft = draft + SavedMealItemEntity(0, draft.size, "recipe-${draft.size}", itemName.trim(), itemGrams.toInt(), (itemCalories.toDouble() * 1000).roundToInt().toLong(), (itemProtein.toDouble() * 1000).roundToInt().toLong())
        itemName = ""; itemGrams = "100"; itemCalories = ""; itemProtein = ""
    }, Modifier.fillMaxWidth(), enabled = itemName.trim().length >= 2 && itemGrams.toIntOrNull()?.let { it in 1..5000 } == true && itemCalories.toDoubleOrNull()?.let { it in 0.0..2000.0 } == true && itemProtein.toDoubleOrNull()?.let { it in 0.0..100.0 } == true) { Text("Add ingredient") }
    draft.forEach { Text("• ${it.name} · ${it.grams} g") }
    Button({ vm.createSavedMeal(mealName, draft); mealName = ""; draft = emptyList() }, Modifier.fillMaxWidth(), enabled = mealName.trim().length >= 2 && draft.isNotEmpty()) { Text("Save recipe") }
}

@Composable private fun ProgressScreen(sessions: List<WorkoutSessionEntity>, weights: List<WeightEntryEntity>, vm: MainViewModel, settings: () -> Unit) = Page("Progress", "Consistency compounds", settings) {
    val weekCount = sessions.count { it.completedAt != null && it.completedAt >= startOfWeek() }; var kg by rememberSaveable { mutableStateOf("") }; var error by remember { mutableStateOf(false) }
    Metric("WORKOUTS THIS WEEK", "$weekCount / 3", if (weekCount >= 3) "Weekly target complete" else "${3 - weekCount} sessions to go", weekCount >= 3, Modifier.fillMaxWidth())
    Panel("Body weight") { Text("Progress is the canonical place to record weigh-ins.", color = MaterialTheme.colorScheme.onSurfaceVariant); OutlinedTextField(kg, { kg = it.filter { c -> c.isDigit() || c == '.' }; error = false }, Modifier.fillMaxWidth(), label = { Text("Weight (kg)") }, leadingIcon = { Icon(Icons.Default.Scale, null) }, isError = error, supportingText = { if (error) Text("Enter a weight from 25 to 400 kg.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true); Button({ val value = kg.toDoubleOrNull(); error = value == null || value !in 25.0..400.0; if (!error) { vm.logWeight(value!!); kg = "" } }, Modifier.fillMaxWidth()) { Text("Save weigh-in") } }
    Panel("Recent weigh-ins") { if (weights.isEmpty()) Text("No weigh-ins yet. Your first entry will appear here."); weights.take(12).forEach { Text("${it.kilograms} kg  ·  ${displayDate(it.loggedAt)}") } }
    Panel("Recent workouts") { val completed = sessions.filter { it.completedAt != null }.take(12); if (completed.isEmpty()) Text("No completed workouts yet."); completed.forEach { Text("${it.dayName}  ·  ${displayDate(it.completedAt!!)}") } }
}

@Composable private fun SupportingPane(tab: Tab, active: WorkoutSessionEntity?, foods: List<FoodEntryEntity>, weights: List<WeightEntryEntity>, modifier: Modifier = Modifier) {
    Surface(modifier.padding(16.dp), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(28.dp)) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) { Text("AT A GLANCE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text(when (tab) { Tab.Today -> "Build momentum"; Tab.Train -> "Session guide"; Tab.Eat -> "Today’s fuel"; Tab.Progress -> "Trend line" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); HorizontalDivider(); Text(when (tab) { Tab.Today -> active?.let { "${it.dayName} is ready to resume." } ?: "No active workout. Start the recommended session when ready."; Tab.Train -> "Each exercise completion, weight, and reps are persisted immediately, so changing window size or posture will not lose progress."; Tab.Eat -> "${foods.loggedOn(LocalDate.now()).size} food entries logged today."; Tab.Progress -> weights.firstOrNull()?.let { "Latest weigh-in: ${it.kilograms} kg." } ?: "Log a weigh-in to begin your trend." }, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@Composable private fun SettingsDialog(hasKey: Boolean, update: UpdateUiState, backup: BackupUiState, calorieGoal: Int, proteinGoal: Int, vm: MainViewModel, exportBackup: () -> Unit, importBackup: () -> Unit, onDismiss: () -> Unit) {
    var apiKey by rememberSaveable { mutableStateOf("") }; var calories by rememberSaveable(calorieGoal) { mutableStateOf(calorieGoal.toString()) }; var protein by rememberSaveable(proteinGoal) { mutableStateOf(proteinGoal.toString()) }; val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { Button(onClick = onDismiss) { Text("Done") } }, title = { Text("Settings & updates") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("USDA API key", fontWeight = FontWeight.Bold); Text(if (hasKey) "A personal key is saved only in this app’s private storage and excluded from backup." else "Using DEMO_KEY: 30 requests/hour and 50/day. Add a personal key for higher limits.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text(if (hasKey) "Replace personal key" else "Personal key (optional)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ vm.saveUsdaKey(apiKey); apiKey = "" }, enabled = apiKey.isNotBlank()) { Text("Save key") }; if (hasKey) OutlinedButton({ vm.saveUsdaKey("") }) { Text("Use DEMO_KEY") } }
        HorizontalDivider(); Text("Daily nutrition goals", fontWeight = FontWeight.Bold)
        OutlinedTextField(calories, { calories = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Calories (800–6000)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        OutlinedTextField(protein, { protein = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Protein grams (20–400)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        OutlinedButton({ vm.saveGoals(calories.toIntOrNull() ?: 0, protein.toIntOrNull() ?: 0) }, Modifier.fillMaxWidth(), enabled = calories.toIntOrNull() in 800..6000 && protein.toIntOrNull() in 20..400) { Text("Save nutrition goals") }
        HorizontalDivider(); Text("Portable local backup", fontWeight = FontWeight.Bold)
        Text("Exports every local data domain, including active workouts and legacy set completions. Private USDA credentials are never included. Import validates the complete file before replacing data, then verifies exact read-back.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(exportBackup, Modifier.weight(1f), enabled = backup !is BackupUiState.Working) { Text("Export") }
            OutlinedButton(importBackup, Modifier.weight(1f), enabled = backup !is BackupUiState.Working) { Text("Import") }
        }
        when (backup) {
            BackupUiState.Idle -> Unit
            BackupUiState.Working -> StatusRow("Reading and verifying local data…")
            is BackupUiState.Success -> Text(backup.message, color = MaterialTheme.colorScheme.primary)
            is BackupUiState.Error -> Text(backup.message, color = MaterialTheme.colorScheme.error)
        }
        HorizontalDivider(); Text("App update", fontWeight = FontWeight.Bold); Text("Version ${BuildConfig.VERSION_NAME}. Updates come only from the public Vitorhpx/gymbro-native GitHub release.", style = MaterialTheme.typography.bodySmall)
        when (update) {
            UpdateUiState.Idle -> Button({ vm.checkForUpdate() }, Modifier.fillMaxWidth()) { Icon(Icons.Default.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text("Check for update") }
            UpdateUiState.Checking -> StatusRow("Checking GitHub…")
            UpdateUiState.Current -> { Text("You’re up to date.", color = MaterialTheme.colorScheme.primary); OutlinedButton({ vm.checkForUpdate() }) { Text("Check again") } }
            is UpdateUiState.Available -> { Text("${update.release.tagName} is available.", color = MaterialTheme.colorScheme.tertiary); Button({ vm.downloadUpdate(update.release) }, Modifier.fillMaxWidth()) { Text(UpdateUiCopy.downloadButton(update.release)) } }
            UpdateUiState.Downloading -> StatusRow("Downloading to private cache…")
            is UpdateUiState.Downloaded -> { Text(UpdateUiCopy.downloadedMessage(update), color = MaterialTheme.colorScheme.primary); Button({ launchInstaller(context, update.file) }, Modifier.fillMaxWidth()) { Text("Open system installer") }; if (!context.packageManager.canRequestPackageInstalls()) Text("Android blocks this source. Tap the installer button, allow installs for GymBro in system settings, then return and tap again.", color = MaterialTheme.colorScheme.tertiary) }
            is UpdateUiState.Error -> { Text(update.message, color = MaterialTheme.colorScheme.error); OutlinedButton({ vm.checkForUpdate() }) { Text("Try again") } }
        }
    } })
}

@Composable private fun StatusRow(text: String) { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text(text) } }

private fun launchInstaller(context: android.content.Context, apk: File) {
    if (!context.packageManager.canRequestPackageInstalls()) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
    context.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/vnd.android.package-archive"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK) })
}
