package com.gymbro.local

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
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
import com.gymbro.local.data.SetCompletionEntity
import com.gymbro.local.data.WeightEntryEntity
import com.gymbro.local.data.WorkoutSessionEntity
import com.gymbro.local.data.displayCalories
import com.gymbro.local.data.displayProteinGrams
import com.gymbro.local.data.totalCalories
import com.gymbro.local.data.totalProteinGrams
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

class MainActivity : ComponentActivity() {
    private val vm by viewModels<MainViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GymBroTheme { GymBroApp(vm) } }
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
@Composable fun GymBroApp(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val active by vm.active.collectAsState(); val sessions by vm.sessions.collectAsState()
    val completions by vm.completions.collectAsState(); val foods by vm.foodEntries.collectAsState()
    val weights by vm.weights.collectAsState(); val settings by vm.settings.collectAsState()
    val search by vm.foodSearch.collectAsState(); val update by vm.update.collectAsState()
    val hasKey by vm.hasPersonalUsdaKey.collectAsState()
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
                        Tab.Today -> TodayScreen(active, sessions, foods, weights, settings?.calorieGoal ?: 2000, settings?.proteinGoal ?: 160, vm, { tab = it }, { showSettings = true })
                        Tab.Train -> TrainScreen(active, completions, vm, { showSettings = true })
                        Tab.Eat -> EatScreen(foods, settings?.calorieGoal ?: 2000, settings?.proteinGoal ?: 160, search, vm, { showSettings = true })
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
    if (showSettings) SettingsDialog(hasKey, update, settings?.calorieGoal ?: 2000, settings?.proteinGoal ?: 160, vm, onDismiss = { showSettings = false })
}

@Composable private fun Page(title: String, eyebrow: String, onSettings: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
private fun startOfToday(): Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
private fun displayDate(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))

@Composable private fun TodayScreen(active: WorkoutSessionEntity?, sessions: List<WorkoutSessionEntity>, foods: List<FoodEntryEntity>, weights: List<WeightEntryEntity>, calorieGoal: Int, proteinGoal: Int, vm: MainViewModel, navigate: (Tab) -> Unit, settings: () -> Unit) = Page("Today", "Your daily brief", settings) {
    val todayFoods = foods.filter { it.loggedAt >= startOfToday() }; val calories = todayFoods.totalCalories(); val protein = todayFoods.totalProteinGrams()
    val weekSessions = sessions.filter { it.completedAt != null && it.completedAt >= startOfWeek() }; val recommended = com.gymbro.local.domain.WorkoutRecommendation.nextDay(weekSessions.map { it.dayId })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Metric("ENERGY", "$calories kcal", if (calories in (calorieGoal * .9).roundToInt()..(calorieGoal * 1.1).roundToInt()) "On target" else "${(calorieGoal - calories).coerceAtLeast(0)} remaining", calories >= calorieGoal * .9, Modifier.weight(1f))
        Metric("PROTEIN", "$protein g", if (protein >= proteinGoal) "Goal complete" else "${proteinGoal - protein} remaining", protein >= proteinGoal, Modifier.weight(1f))
    }
    Panel(if (active == null) "Next session" else "Workout in progress") {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.FitnessCenter, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column { Text(active?.dayName ?: recommended.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("GymBro PPL 45 V2 · about 45 min", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        Button(onClick = { if (active == null) vm.startWorkout(recommended.id, recommended.name) else navigate(Tab.Train) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if (active == null) "Start ${recommended.name}" else "Resume on Train") }
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

@Composable private fun TrainScreen(active: WorkoutSessionEntity?, completions: List<SetCompletionEntity>, vm: MainViewModel, settings: () -> Unit) = Page(if (active == null) "Train" else active.dayName, if (active == null) "PPL · 3 days · 45 min" else "Session active · saved locally", settings) {
    if (active == null) SeedData.plan.days.forEachIndexed { index, day ->
        Panel("0${index + 1}  ${day.name.uppercase()}") { Text(day.exercises.joinToString("  •  ") { it.name }, color = MaterialTheme.colorScheme.onSurfaceVariant); Button({ vm.startWorkout(day.id, day.name) }, Modifier.fillMaxWidth()) { Text("Start ${day.name}") } }
    } else {
        val day = SeedData.plan.days.firstOrNull { it.id == active.dayId } ?: SeedData.plan.days.first()
        Text("Started ${displayDate(active.startedAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        day.exercises.forEach { exercise -> Panel(exercise.name) {
            Text("${exercise.sets} sets  ·  ${exercise.reps} reps  ·  ${exercise.restSeconds}s rest", color = MaterialTheme.colorScheme.secondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { repeat(exercise.sets) { index ->
                val checked = completions.any { it.sessionId == active.id && it.exerciseId == exercise.id && it.setIndex == index }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Checkbox(checked, { vm.setChecked(active.id, exercise.id, index, it) }); Text("SET ${index + 1}", style = MaterialTheme.typography.labelSmall) }
            } }
        } }
        Button({ vm.completeWorkout(active.id) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text("Complete workout") }
        Text("Active workout and checked sets save automatically on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun EatScreen(entries: List<FoodEntryEntity>, calorieGoal: Int, proteinGoal: Int, search: FoodSearchUiState, vm: MainViewModel, settings: () -> Unit) = Page("Eat", "Fuel the work", settings) {
    val today = entries.filter { it.loggedAt >= startOfToday() }; var query by rememberSaveable { mutableStateOf("") }; var grams by rememberSaveable { mutableStateOf("100") }
    var queryError by remember { mutableStateOf<String?>(null) }; var gramsError by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("CALORIES", "${today.totalCalories()} / $calorieGoal", if (today.totalCalories() >= calorieGoal * .9) "On target" else "In progress", today.totalCalories() >= calorieGoal * .9, Modifier.weight(1f)); Metric("PROTEIN", "${today.totalProteinGrams()} / $proteinGoal g", if (today.totalProteinGrams() >= proteinGoal) "Complete" else "In progress", today.totalProteinGrams() >= proteinGoal, Modifier.weight(1f)) }
    Panel("USDA FoodData Central") {
        Text("Search when you submit. DEMO_KEY works out of the box (30 requests/hour, 50/day); add your own key in Settings for higher limits.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(query, { query = it; queryError = null }, Modifier.fillMaxWidth(), label = { Text("Food search") }, leadingIcon = { Icon(Icons.Default.Search, null) }, isError = queryError != null, supportingText = { queryError?.let { Text(it) } }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { queryError = FoodSearchValidation.queryError(query); if (queryError == null) vm.searchFood(query) }))
        Button({ queryError = FoodSearchValidation.queryError(query); if (queryError == null) vm.searchFood(query) }, enabled = search !is FoodSearchUiState.Loading, modifier = Modifier.fillMaxWidth()) { if (search is FoodSearchUiState.Loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Search USDA") }
        when (search) {
            FoodSearchUiState.Idle -> Text("Enter a food name to find nutrition per 100 g.")
            FoodSearchUiState.Loading -> Text("Searching USDA…")
            is FoodSearchUiState.Error -> Text(search.message, color = MaterialTheme.colorScheme.error)
            is FoodSearchUiState.Results -> if (search.foods.isEmpty()) Text("No matching foods found. Try a broader term.") else {
                OutlinedTextField(grams, { grams = it.filter(Char::isDigit); gramsError = null }, Modifier.fillMaxWidth(), label = { Text("Grams to log") }, isError = gramsError != null, supportingText = { gramsError?.let { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                search.foods.forEach { food -> OutlinedButton({ gramsError = FoodSearchValidation.gramsError(grams); if (gramsError == null) vm.logFood(food, grams.toInt()) }, Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) { Text(food.description, fontWeight = FontWeight.SemiBold); Text("${food.caloriesPer100g.roundToInt()} kcal · ${"%.1f".format(food.proteinPer100g)} g protein / 100 g", style = MaterialTheme.typography.bodySmall) } } }
            }
        }
    }
    Panel("Quick log") { OutlinedTextField(grams, { grams = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Grams") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); SeedData.foods.forEach { food -> FilledTonalButton({ vm.logFood(food, grams.toIntOrNull() ?: 0) }, Modifier.fillMaxWidth()) { Text("${food.name} · ${food.caloriesPer100g.toInt()} kcal/100g") } } }
    if (today.isNotEmpty()) Panel("Logged today") { today.forEach { entry -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(entry.name, fontWeight = FontWeight.SemiBold); Text("${entry.grams} g · ${entry.displayCalories()} kcal · ${entry.displayProteinGrams()} g protein", style = MaterialTheme.typography.bodySmall) }; OutlinedButton({ vm.deleteFood(entry.id) }) { Text("Remove") } }; HorizontalDivider() } }
}

@Composable private fun ProgressScreen(sessions: List<WorkoutSessionEntity>, weights: List<WeightEntryEntity>, vm: MainViewModel, settings: () -> Unit) = Page("Progress", "Consistency compounds", settings) {
    val weekCount = sessions.count { it.completedAt != null && it.completedAt >= startOfWeek() }; var kg by rememberSaveable { mutableStateOf("") }; var error by remember { mutableStateOf(false) }
    Metric("WORKOUTS THIS WEEK", "$weekCount / 3", if (weekCount >= 3) "Weekly target complete" else "${3 - weekCount} sessions to go", weekCount >= 3, Modifier.fillMaxWidth())
    Panel("Body weight") { Text("Progress is the canonical place to record weigh-ins.", color = MaterialTheme.colorScheme.onSurfaceVariant); OutlinedTextField(kg, { kg = it.filter { c -> c.isDigit() || c == '.' }; error = false }, Modifier.fillMaxWidth(), label = { Text("Weight (kg)") }, leadingIcon = { Icon(Icons.Default.Scale, null) }, isError = error, supportingText = { if (error) Text("Enter a weight from 25 to 400 kg.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true); Button({ val value = kg.toDoubleOrNull(); error = value == null || value !in 25.0..400.0; if (!error) { vm.logWeight(value!!); kg = "" } }, Modifier.fillMaxWidth()) { Text("Save weigh-in") } }
    Panel("Recent weigh-ins") { if (weights.isEmpty()) Text("No weigh-ins yet. Your first entry will appear here."); weights.take(12).forEach { Text("${it.kilograms} kg  ·  ${displayDate(it.loggedAt)}") } }
    Panel("Recent workouts") { val completed = sessions.filter { it.completedAt != null }.take(12); if (completed.isEmpty()) Text("No completed workouts yet."); completed.forEach { Text("${it.dayName}  ·  ${displayDate(it.completedAt!!)}") } }
}

@Composable private fun SupportingPane(tab: Tab, active: WorkoutSessionEntity?, foods: List<FoodEntryEntity>, weights: List<WeightEntryEntity>, modifier: Modifier = Modifier) {
    Surface(modifier.padding(16.dp), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(28.dp)) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) { Text("AT A GLANCE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text(when (tab) { Tab.Today -> "Build momentum"; Tab.Train -> "Session guide"; Tab.Eat -> "Today’s fuel"; Tab.Progress -> "Trend line" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); HorizontalDivider(); Text(when (tab) { Tab.Today -> active?.let { "${it.dayName} is ready to resume." } ?: "No active workout. Start the recommended session when ready."; Tab.Train -> "Every checked set is persisted immediately, so changing window size or posture will not lose progress."; Tab.Eat -> "${foods.filter { it.loggedAt >= startOfToday() }.size} food entries logged today."; Tab.Progress -> weights.firstOrNull()?.let { "Latest weigh-in: ${it.kilograms} kg." } ?: "Log a weigh-in to begin your trend." }, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@Composable private fun SettingsDialog(hasKey: Boolean, update: UpdateUiState, calorieGoal: Int, proteinGoal: Int, vm: MainViewModel, onDismiss: () -> Unit) {
    var apiKey by rememberSaveable { mutableStateOf("") }; var calories by rememberSaveable(calorieGoal) { mutableStateOf(calorieGoal.toString()) }; var protein by rememberSaveable(proteinGoal) { mutableStateOf(proteinGoal.toString()) }; val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { Button(onClick = onDismiss) { Text("Done") } }, title = { Text("Settings & updates") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("USDA API key", fontWeight = FontWeight.Bold); Text(if (hasKey) "A personal key is saved only in this app’s private storage and excluded from backup." else "Using DEMO_KEY: 30 requests/hour and 50/day. Add a personal key for higher limits.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text(if (hasKey) "Replace personal key" else "Personal key (optional)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ vm.saveUsdaKey(apiKey); apiKey = "" }, enabled = apiKey.isNotBlank()) { Text("Save key") }; if (hasKey) OutlinedButton({ vm.saveUsdaKey("") }) { Text("Use DEMO_KEY") } }
        HorizontalDivider(); Text("Daily nutrition goals", fontWeight = FontWeight.Bold)
        OutlinedTextField(calories, { calories = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Calories (800–6000)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        OutlinedTextField(protein, { protein = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Protein grams (20–400)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        OutlinedButton({ vm.saveGoals(calories.toIntOrNull() ?: 0, protein.toIntOrNull() ?: 0) }, Modifier.fillMaxWidth(), enabled = calories.toIntOrNull() in 800..6000 && protein.toIntOrNull() in 20..400) { Text("Save nutrition goals") }
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
