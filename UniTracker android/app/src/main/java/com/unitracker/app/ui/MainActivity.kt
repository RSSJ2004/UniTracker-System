package com.unitracker.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import com.unitracker.app.UniTrackerApp
import com.unitracker.app.alarm.WheelPicker
import com.unitracker.app.alarm.scheduleTestAlarmInOneMinute
import com.unitracker.app.data.QuickStats
import com.unitracker.app.data.local.TaskWithReminders
import com.unitracker.app.ui.login.CampusLoginWebView
import com.unitracker.app.ui.theme.UniTrackerTheme
import com.unitracker.app.util.formatMillis
import com.unitracker.app.util.formatOffset
import com.unitracker.app.util.phaseLabel
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel
    private var canExact by mutableStateOf(true)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as UniTrackerApp).container.repository
        viewModel = ViewModelProvider(this, MainViewModel.factory(repository))[MainViewModel::class.java]

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val prefs = getPreferences(Context.MODE_PRIVATE)

        setContent {
            var isDarkMode by remember {
                mutableStateOf(prefs.getBoolean("dark_mode_enabled", false))
            }

            var showOnboarding by remember {
                mutableStateOf(!prefs.getBoolean("onboarding_completed", false))
            }

            UniTrackerTheme(darkTheme = isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainAppScaffold(
                        viewModel = viewModel,
                        canScheduleExact = canExact,
                        isDarkMode = isDarkMode,
                        onDarkModeChanged = { enabled ->
                            isDarkMode = enabled
                            prefs.edit { putBoolean("dark_mode_enabled", enabled) }
                        },
                        onOpenExactAlarmSettings = ::openExactAlarmSettings,
                        onOpenBatterySettings = ::openBatterySettings
                    )

                    if (showOnboarding) {
                        OnboardingDialog(
                            onDismiss = {
                                prefs.edit { putBoolean("onboarding_completed", true) }
                                showOnboarding = false
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        canExact = viewModel.canScheduleExactAlarms()
        viewModel.refreshAlarms()
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri())
                )
            }
        }
    }

    private fun openBatterySettings() {
        runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}

@Composable
fun OnboardingDialog(onDismiss: () -> Unit) {
    var currentPage by remember { mutableIntStateOf(0) }

    val steps = listOf(
        Triple(
            "🎓",
            "Bienvenido a UniTracker",
            "Tu compañero inteligente para el campus del INTEC. Organiza tus tareas, entregas y alarmas de forma automática en un solo lugar."
        ),
        Triple(
            "⚙️",
            "Paso 1: Configura tu Correo",
            "Ve a la sección de Configuración e ingresa tu correo institucional (ej. tu_matricula@est.intec.edu.do) para autocompletar tus accesos."
        ),
        Triple(
            "🔐",
            "Paso 2: Renueva tu Sesión",
            "Entra al menú 'Renovar Sesión', introduce tu contraseña institucional y aprueba rápidamente la notificación de tu Microsoft Authenticator."
        ),
        Triple(
            "💾",
            "Paso 3: Mantén la Sesión Activa",
            "Es crucial que marques 'Sí' en la pantalla de Microsoft de '¿Mantener la sesión iniciada?'. Esto guarda cookies de larga duración para evitar el MFA diario."
        ),
        Triple(
            "🚀",
            "Paso 4: ¡Todo Listo!",
            "Regresa al inicio y presiona 'Sincronizar ahora'. La app descargará tus materias y podrás programar alarmas personalizadas con el selector de rueda."
        )
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text("✕", style = MaterialTheme.typography.titleMedium)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(Modifier.height(16.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = steps[currentPage].first,
                            style = MaterialTheme.typography.displayLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            text = steps[currentPage].second,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = steps[currentPage].third,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            steps.indices.forEach { index ->
                                Box(
                                    modifier = Modifier
                                        .size(if (index == currentPage) 10.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (index == currentPage) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (currentPage > 0) {
                                OutlinedButton(onClick = { currentPage-- }) {
                                    Text("Anterior")
                                }
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }

                            Button(onClick = {
                                if (currentPage < steps.size - 1) {
                                    currentPage++
                                } else {
                                    onDismiss()
                                }
                            }) {
                                Text(if (currentPage == steps.size - 1) "Comenzar" else "Siguiente")
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class Screen {
    HOME, LOGIN_WEBVIEW, SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppScaffold(
    viewModel: MainViewModel,
    canScheduleExact: Boolean,
    isDarkMode: Boolean,
    onDarkModeChanged: (Boolean) -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                Text("UniTracker Panel", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                HorizontalDivider()

                NavigationDrawerItem(
                    icon = { Text("🏠") },
                    label = { Text("Inicio (Alarmas y Tareas)") },
                    selected = currentScreen == Screen.HOME,
                    onClick = {
                        currentScreen = Screen.HOME
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Text("🔄") },
                    label = { Text("Renovar Sesión (Microsoft)") },
                    selected = currentScreen == Screen.LOGIN_WEBVIEW,
                    onClick = {
                        currentScreen = Screen.LOGIN_WEBVIEW
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Text("⚙️") },
                    label = { Text("Configuración") },
                    selected = currentScreen == Screen.SETTINGS,
                    onClick = {
                        currentScreen = Screen.SETTINGS
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (currentScreen) {
                                Screen.HOME -> "Mis Alarmas y Tareas"
                                Screen.LOGIN_WEBVIEW -> "Renovar Sesión del Campus"
                                Screen.SETTINGS -> "Configuración General"
                            }
                        )
                    },
                    navigationIcon = {
                        TextButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("☰", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                when (currentScreen) {
                    Screen.HOME -> HomeScreen(
                        viewModel = viewModel,
                        canScheduleExact = canScheduleExact,
                        onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                        onOpenBatterySettings = onOpenBatterySettings
                    )
                    Screen.LOGIN_WEBVIEW -> CampusLoginWebView(
                        onCookiesCaptured = { _ ->
                            currentScreen = Screen.HOME
                        }
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        isDarkMode = isDarkMode,
                        onDarkModeChanged = onDarkModeChanged,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    isDarkMode: Boolean,
    onDarkModeChanged: (Boolean) -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("unitracker_settings", Context.MODE_PRIVATE) }

    var defaultEmail by remember {
        mutableStateOf(prefs.getString("default_email", "") ?: "")
    }

    val sessionFile = File(context.filesDir, "storageState.json")
    val sessionStatus = if (!sessionFile.exists()) {
        "Expirada (No hay credenciales)"
    } else {
        val content = sessionFile.readText()
        if (content.contains("MoodleSession")) {
            "Activa (Sesión Persistente Válida)"
        } else {
            "Próxima a caducar o incompleta"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Apariencia y Cuenta", style = MaterialTheme.typography.titleMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Modo Oscuro", style = MaterialTheme.typography.bodyLarge)
                        Text("Activa o desactiva el tema permanentemente", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = isDarkMode, onCheckedChange = onDarkModeChanged)
                }

                HorizontalDivider()

                Text("Correo Institucional Predeterminado", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = defaultEmail,
                    onValueChange = {
                        defaultEmail = it
                        prefs.edit { putString("default_email", it) }
                    },
                    label = { Text("ejemplo@intec.edu.do") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Text("Gestión de Sesión", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Estado actual:", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = sessionStatus,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (sessionStatus.startsWith("Activa")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )

                OutlinedButton(
                    onClick = {
                        if (sessionFile.exists()) sessionFile.delete()
                        Toast.makeText(context, "Sesión y cookies revocadas correctamente", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Revocar Sesión / Borrar Cookies")
                }
            }
        }

        Text("Ayuda y Funcionamiento", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("¿Cómo funciona UniTracker?", style = MaterialTheme.typography.titleSmall)
                Text("• Sincronización: Descarga automáticamente tus tareas del campus virtual usando tu sesión segura.", style = MaterialTheme.typography.bodySmall)
                Text("• Alarmas y Recordatorios: Configura avisos con el selector de rueda para no perder ninguna entrega.", style = MaterialTheme.typography.bodySmall)
                Text("• Renovación de Sesión: Si el campus pide credenciales de nuevo, usa el menú para renovar el acceso mediante Microsoft y MFA.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Text("Datos y Almacenamiento", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        viewModel.sync()
                        Toast.makeText(context, "Resincronización limpia iniciada", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Forzar Resincronización Limpia")
                }

                Spacer(Modifier.height(4.dp))
                Text("Versión de la App: 1.0.0 (Build 2026.10)", style = MaterialTheme.typography.bodySmall)
                Text("Desarrollado para INTEC - UniTracker Companion", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private const val FILTER_PENDING = 0
private const val FILTER_DONE = 1
private const val FILTER_ALL = 2

@Composable
private fun HomeScreen(
    viewModel: MainViewModel,
    canScheduleExact: Boolean,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    val tasks by viewModel.tasks.collectAsState()
    val quick by viewModel.quickStats.collectAsState()
    val sync by viewModel.syncState.collectAsState()
    var filter by rememberSaveable { mutableIntStateOf(FILTER_PENDING) }
    val context = LocalContext.current

    val pendingCount = tasks.count { !it.task.completed }
    val doneCount = tasks.size - pendingCount

    val visible = when (filter) {
        FILTER_PENDING -> tasks.filter { !it.task.completed }
        FILTER_DONE -> tasks.filter { it.task.completed }
        else -> tasks
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            modifier = Modifier
                .widthIn(max = 1200.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "UniTracker",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onOpenBatterySettings) { Text("Batería") }
                    }

                    if (!canScheduleExact) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "Para que las alarmas suenen a la hora exacta, permite \"Alarmas y recordatorios\".",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                OutlinedButton(onClick = onOpenExactAlarmSettings) { Text("Abrir ajuste") }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    QuickScanCard(quick)
                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.sync() },
                        enabled = !sync.isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (sync.isSyncing) "Sincronizando…" else "Sincronizar ahora")
                    }

                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { scheduleTestAlarmInOneMinute(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("⏱ Probar Alarma en 1 Minuto")
                    }

                    sync.message?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(12.dp))
                    FilterRow(
                        selected = filter,
                        labels = listOf(
                            "Pendientes ($pendingCount)",
                            "Hechas ($doneCount)",
                            "Todas (${tasks.size})"
                        ),
                        onSelect = { filter = it }
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            if (visible.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (tasks.isEmpty()) "Aún no hay tareas. Toca \"Sincronizar ahora\"." else "No hay tareas en esta vista.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }

            items(visible, key = { it.task.uid }) { item ->
                TaskCard(
                    item = item,
                    onCompletedChange = { viewModel.setCompleted(item.task.uid, it) },
                    onAddReminder = { viewModel.addReminder(item.task.uid, it) },
                    onReminderEnabledChange = { id, enabled -> viewModel.setReminderEnabled(id, enabled) },
                    onDeleteReminder = { viewModel.deleteReminder(it) }
                )
            }
        }
    }
}

@Composable
private fun FilterRow(selected: Int, labels: List<String>, onSelect: (Int) -> Unit) {
    val padding = PaddingValues(horizontal = 12.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            if (index == selected) {
                Button(onClick = { onSelect(index) }, contentPadding = padding) { Text(label, maxLines = 1) }
            } else {
                OutlinedButton(onClick = { onSelect(index) }, contentPadding = padding) { Text(label, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun QuickScanCard(stats: QuickStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Escaneo rápido", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("${stats.pending} pendientes · ${stats.dueIn24h} en las próximas 24 h")
            Text("Próxima: ${stats.nextDueAt?.let(::formatMillis) ?: "—"}")
        }
    }
}

private fun alarmSummary(item: TaskWithReminders): String {
    val task = item.task
    if (task.completed) return "Hecha"
    val due = task.dueAt ?: return "Sin fecha: no puede tener alarmas"
    val active = item.reminders.filter { it.enabled }
    if (active.isEmpty()) return "Sin alarmas activas"
    val now = System.currentTimeMillis()
    val next = active.map { due - it.minutesBefore * 60_000L }.filter { it > now }.minOrNull()
    val count = if (active.size == 1) "1 alarma" else "${active.size} alarmas"
    return if (next == null) "$count · ya pasaron" else "$count · próxima: ${formatMillis(next)}"
}

@Composable
private fun TaskCard(
    item: TaskWithReminders,
    onCompletedChange: (Boolean) -> Unit,
    onAddReminder: (Int) -> Unit,
    onReminderEnabledChange: (Long, Boolean) -> Unit,
    onDeleteReminder: (Long) -> Unit
) {
    val task = item.task
    var expanded by rememberSaveable(task.uid) { mutableStateOf(false) }
    var showAddDialog by rememberSaveable(task.uid) { mutableStateOf(false) }
    var reminderToEdit by remember { mutableStateOf<com.unitracker.app.data.local.ReminderEntity?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(checked = task.completed, onCheckedChange = onCompletedChange)
                Column(Modifier.weight(1f).padding(top = 12.dp)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleSmall,
                        textDecoration = if (task.completed) TextDecoration.LineThrough else null
                    )
                    task.course?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(
                        "${phaseLabel(task.phase)} · " +
                                (task.dueAt?.let(::formatMillis) ?: (task.dueAtRaw ?: "sin fecha")),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    alarmSummary(item),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Cerrar" else "Ver alarmas")
                }
            }

            if (expanded) {
                item.reminders.sortedByDescending { it.minutesBefore }.forEach { reminder ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${formatOffset(reminder.minutesBefore)} antes", style = MaterialTheme.typography.bodyMedium)
                            task.dueAt?.let {
                                Text(
                                    "Suena: ${formatMillis(it - reminder.minutesBefore * 60_000L)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        TextButton(onClick = { reminderToEdit = reminder }) {
                            Text("Editar")
                        }

                        Switch(
                            checked = reminder.enabled,
                            onCheckedChange = { onReminderEnabledChange(reminder.id, it) }
                        )
                        TextButton(
                            onClick = { onDeleteReminder(reminder.id) },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("Quitar") }
                    }
                }
                TextButton(
                    onClick = { showAddDialog = true },
                    enabled = task.dueAt != null
                ) { Text("+ Nueva alarma") }
            }
        }
    }

    if (showAddDialog) {
        AddReminderDialog(
            dueAt = task.dueAt,
            existing = item.reminders.map { it.minutesBefore }.toSet(),
            onDismiss = { showAddDialog = false },
            onConfirm = { minutes ->
                onAddReminder(minutes)
                showAddDialog = false
            }
        )
    }

    reminderToEdit?.let { reminder ->
        com.unitracker.app.alarm.EditAlarmDialog(
            taskTitle = task.title,
            initialMinutes = reminder.minutesBefore,
            initialEnabled = reminder.enabled,
            onDismiss = { reminderToEdit = null },
            onSave = { newMinutes, newEnabled ->
                onReminderEnabledChange(reminder.id, newEnabled)
                if (newMinutes != reminder.minutesBefore) {
                    onDeleteReminder(reminder.id)
                    onAddReminder(newMinutes)
                }
                reminderToEdit = null
            }
        )
    }
}

@Composable
private fun AddReminderDialog(
    dueAt: Long?,
    existing: Set<Int>,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var days by rememberSaveable { mutableIntStateOf(0) }
    var hours by rememberSaveable { mutableIntStateOf(3) }
    var minutes by rememberSaveable { mutableIntStateOf(0) }

    val total = (days * 1440) + (hours * 60) + minutes

    val error = when {
        total <= 0 -> "Elige al menos 1 minuto"
        total in existing -> "Ya tienes una alarma con ese tiempo"
        dueAt != null && dueAt - total * 60_000L <= System.currentTimeMillis() -> "Esa hora ya pasó"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva alarma") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("¿Cuánto antes de la fecha?", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WheelPicker(
                        range = 0..30,
                        initialValue = days,
                        label = "Días",
                        onSelect = { days = it }
                    )
                    WheelPicker(
                        range = 0..23,
                        initialValue = hours,
                        label = "Horas",
                        onSelect = { hours = it }
                    )
                    WheelPicker(
                        range = 0..59,
                        initialValue = minutes,
                        label = "Min",
                        onSelect = { minutes = it }
                    )
                }

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(total) }, enabled = error == null) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}