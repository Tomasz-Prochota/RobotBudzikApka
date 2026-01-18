package com.example.robotbudzik

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.*
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.robotbudzik.ui.theme.RobotBudzikTheme
import kotlinx.coroutines.delay
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft

import androidx.compose.ui.graphics.vector.ImageVector
import java.util.*

class MainActivity : ComponentActivity() {
    private var screenState = mutableStateOf("dashboard")
    private var editingAlarm by mutableStateOf<Alarm?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        checkIntent(intent)

        setContent {
            val viewModel: AlarmViewModel = viewModel()
            val context = LocalContext.current

            RobotBudzikTheme(darkTheme = viewModel.isDarkMode.value) {
                val view = androidx.compose.ui.platform.LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as android.app.Activity).window
                        androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !viewModel.isDarkMode.value
                    }
                }
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (screenState.value) {
                        "dashboard" -> DashboardScreen(
                            viewModel = viewModel,
                            onGoToAlarms = { screenState.value = "alarms" },
                            onGoToStats = { screenState.value = "stats" },
                            onGoToSettings = { screenState.value = "settings" },
                            onGoToSport = { screenState.value = "sport" }
                        )
                        "sport" -> SportModeScreen(viewModel = viewModel, onBack = { screenState.value = "dashboard" })
                        "alarms" -> AlarmsScreen(
                            viewModel = viewModel,
                            onBack = { screenState.value = "dashboard" },
                            onAddAlarm = {
                                editingAlarm = null
                                screenState.value = "add_alarm"
                            },
                            onEditAlarm = { alarm -> // NAPRAWA BŁĘDU 2
                                editingAlarm = alarm
                                screenState.value = "add_alarm"
                            }
                        )
                        "add_alarm" -> AddAlarmScreen(
                            initialAlarm = editingAlarm,
                            onBack = { screenState.value = "alarms" },
                            onSave = { h, m, days ->
                                if (editingAlarm == null) {
                                    // Dodajemy nowy i planujemy po otrzymaniu ID
                                    viewModel.addAlarm(h, m, days) { newAlarm ->
                                        scheduleSystemAlarm(context, newAlarm)
                                    }
                                } else {
                                    val updated = editingAlarm!!.copy(hour = h, minute = m, days = days)
                                    viewModel.updateAlarm(updated)
                                    scheduleSystemAlarm(context, updated)
                                }
                                screenState.value = "alarms"
                            }
                        )
                        "stats" -> StatsScreen(viewModel = viewModel, onBack = { screenState.value = "dashboard" })
                        "settings" -> SettingsScreen(viewModel = viewModel, onBack = { screenState.value = "dashboard" })
                        "alarm" -> AlarmPuzzleScreen(viewModel = viewModel, onDismiss = {
                            screenState.value = "dashboard"
                            finishAndRemoveTask()
                        })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkIntent(intent)
    }

    private fun checkIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("from_alarm", false) == true) {
            screenState.value = "alarm"
        }
    }
}

// --- EKRANY ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: AlarmViewModel,
    onGoToAlarms: () -> Unit,
    onGoToStats: () -> Unit,
    onGoToSettings: () -> Unit,
    onGoToSport: () -> Unit
) {
    val alarms by viewModel.allAlarms.collectAsState(initial = emptyList())

    LaunchedEffect(alarms) {
        viewModel.seedDatabase()
        while (true) {
            viewModel.updateNextAlarmDisplay(alarms)
            delay(1000)
        }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("ROBOT BUDZIK") }) }
    ) { pad ->
        // GŁÓWNA KOLUMNA (trzyma wszystko wewnątrz Scaffold)
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. STATUS POŁĄCZENIA (Na samej górze)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (viewModel.isBluetoothConnected.value) Color.Green else Color.Red,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (viewModel.isBluetoothConnected.value) "Bot Connected" else "Robot Disconnected",
                    fontSize = 12.sp,
                    color = if (viewModel.isBluetoothConnected.value) Color(0xFF2E7D32) else Color.Red
                )
            }

            // 2. INFO O ALARMIE
            Text(
                viewModel.nextAlarmInfo.value,
                fontSize = 20.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Text(
                viewModel.nextAlarmCountdown.value,
                fontSize = 16.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 3. WYBRANA PIOSENKA
            Text("Wybrany dźwięk: ${viewModel.selectedSong.value}", fontSize = 14.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(16.dp))

            // 4. KARTA BATERII
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        viewModel.batteryLevel.intValue < 20 -> Color(0xFFFFEBEE)
                        viewModel.batteryLevel.intValue < 50 -> Color(0xFFFFF3E0)
                        else -> Color(0xFFE8F5E9)
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Bateria Robota: ${viewModel.batteryLevel.intValue}%",
                        fontSize = 18.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = when {
                            viewModel.batteryLevel.intValue < 20 -> Color.Red
                            viewModel.batteryLevel.intValue < 50 -> Color(0xFFE65100)
                            else -> Color(0xFF2E7D32)
                        }
                    )
                    LinearProgressIndicator(
                        progress = viewModel.batteryLevel.intValue / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(8.dp),
                        color = if (viewModel.batteryLevel.intValue < 20) Color.Red else Color(0xFF4CAF50)
                    )
                }
            }

            // 5. PRZYCISKI GŁÓWNE
            Button(onClick = onGoToAlarms, modifier = Modifier.fillMaxWidth(0.8f).height(60.dp)) {
                Text("ALARMY")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onGoToStats, modifier = Modifier.fillMaxWidth(0.8f).height(60.dp)) {
                Text("STATYSTYKI")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onGoToSettings, modifier = Modifier.fillMaxWidth(0.8f).height(60.dp)) {
                Text("USTAWIENIA")
            }

            // 6. TRYB SPORTOWY (Na samym dole dzięki weight)
            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onGoToSport,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Icon(Icons.Default.SportsEsports, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("TRYB SPORTOWY", fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportModeScreen(viewModel: AlarmViewModel, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tryb Sportowy - Kontroler") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "") } }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier.padding(pad).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("STEROWANIE ROBOTEM", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(modifier = Modifier.height(40.dp))

            // Panel sterowania (Strzałki)
            // GÓRA
            ControlButton(Icons.Default.KeyboardArrowUp, "GÓRA") { viewModel.controlRobot("FORWARD") }

            Row {
                // LEWO
                ControlButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "LEWO") { viewModel.controlRobot("LEFT") }
                Spacer(modifier = Modifier.width(80.dp))
                // PRAWO
                ControlButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "PRAWO") { viewModel.controlRobot("RIGHT") }
            }

            // DÓŁ
            ControlButton(Icons.Default.KeyboardArrowDown, "DÓŁ") { viewModel.controlRobot("BACKWARD") }

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = { viewModel.toggleRobotMusic() },
                modifier = Modifier.fillMaxWidth(0.7f).height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (viewModel.isRobotMusicPlaying.value) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    if (viewModel.isRobotMusicPlaying.value) Icons.Default.MusicOff else Icons.Default.MusicNote,
                    contentDescription = null
                )
                Spacer(Modifier.width(12.dp))
                Text(if (viewModel.isRobotMusicPlaying.value) "WYŁĄCZ MUZYKĘ" else "WŁĄCZ MUZYKĘ")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.controlRobot("STOP") },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("STOP", fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun ControlButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(80.dp).padding(8.dp),
        shape = androidx.compose.foundation.shape.CircleShape
    ) {
        Icon(icon, contentDescription = desc, modifier = Modifier.size(40.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsScreen(
    viewModel: AlarmViewModel,
    onBack: () -> Unit,
    onAddAlarm: () -> Unit,
    onEditAlarm: (Alarm) -> Unit // DODANE
) {
    val alarms by viewModel.allAlarms.collectAsState(initial = emptyList())
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Twoje Budziki") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddAlarm) {
                Icon(Icons.Default.Add, "Dodaj")
            }
        }
    ) { pad ->
        LazyColumn(modifier = Modifier.padding(pad).fillMaxSize()) {
            items(alarms) { alarm ->
                AlarmItem(
                    alarm = alarm,
                    onDelete = { alarmToDelete ->
                        // Najpierw usuń z systemu używając ID
                        scheduleSystemAlarm(context, alarmToDelete, cancelOnly = true)
                        // Potem usuń z bazy
                        viewModel.deleteAlarm(alarmToDelete)
                    },
                    onToggle = { isActive ->
                        viewModel.toggleAlarm(alarm, isActive)
                        scheduleSystemAlarm(context, alarm.copy(isActive = isActive))
                    },
                    onEdit = { onEditAlarm(alarm) }
                )
            }
        }
    }
}

@Composable
fun AlarmItem(alarm: Alarm, onDelete: (Alarm) -> Unit, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        onClick = onEdit
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = alarm.isActive, onCheckedChange = { onToggle(it) })
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute), fontSize = 32.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(text = if (alarm.days.isEmpty()) "Jutro" else alarm.days, fontSize = 14.sp, color = Color.Gray)
            }
            IconButton(onClick = { onDelete(alarm) }) { Icon(Icons.Default.Delete, "", tint = Color.Red) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAlarmScreen(
    initialAlarm: Alarm? = null,
    onBack: () -> Unit,
    onSave: (Int, Int, String) -> Unit
) {
    // Jeśli edytujemy, bierzemy dane z budzika, jeśli nie - domyślne
    var selectedHour by remember { mutableIntStateOf(initialAlarm?.hour ?: 7) }
    var selectedMinute by remember { mutableIntStateOf(initialAlarm?.minute ?: 0) }

    val daysOfWeek = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd")

    // Inicjalizacja zaznaczonych dni przy edycji
    val selectedDays = remember {
        val list = mutableStateListOf<String>()
        initialAlarm?.days?.split(", ")?.filter { it.isNotEmpty() }?.forEach { list.add(it) }
        list
    }

    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initialAlarm == null) "Dodaj Budzik" else "Edytuj Budzik") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "") } }
            )
        }
    ) { pad ->
        Column(modifier = Modifier.padding(pad).padding(16.dp).fillMaxSize()) {
            Button(
                onClick = {
                    TimePickerDialog(context, { _, h, m ->
                        selectedHour = h
                        selectedMinute = m
                    }, selectedHour, selectedMinute, true).show()
                },
                modifier = Modifier.fillMaxWidth().height(80.dp)
            ) {
                Text(text = String.format("Godzina: %02d:%02d", selectedHour, selectedMinute), fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Powtarzaj w dni:", fontSize = 18.sp)

            daysOfWeek.forEach { day ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = selectedDays.contains(day),
                        onCheckedChange = { if (it) selectedDays.add(day) else selectedDays.remove(day) }
                    )
                    Text(day)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    val daysString = if (selectedDays.isEmpty()) "Jutro" else selectedDays.joinToString(", ")
                    onSave(selectedHour, selectedMinute, daysString)
                },
                modifier = Modifier.fillMaxWidth().height(60.dp)
            ) {
                Text("ZAPISZ BUDZIK")
            }
        }
    }
}

// --- LOGIKA SYSTEMOWA ---

fun scheduleSystemAlarm(context: Context, alarm: Alarm, cancelOnly: Boolean = false) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java).apply {
        putExtra("from_alarm", true)
        action = "com.example.robotbudzik.ALARM_TRIGGER"
    }

    // KLUCZOWA ZMIANA: Używamy alarm.id zamiast liczyć minuty.
    // To sprawia, że edycja i usuwanie działają precyzyjnie.
    val pendingIntent = PendingIntent.getBroadcast(
        context, alarm.id, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Jeśli budzik jest nieaktywny LUB chcemy go tylko skasować (przy usuwaniu z listy)
    if (cancelOnly || !alarm.isActive) {
        alarmManager.cancel(pendingIntent)
        Log.d("RobotAlarm", "Budzik ID ${alarm.id} został usunięty z kalendarza systemowego")
        return
    }

    val now = Calendar.getInstance()
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, alarm.hour)
        set(Calendar.MINUTE, alarm.minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    // Mapa dni tygodnia (Nd=1, Pn=2, ...)
    val dMap = mapOf("Nd" to 1, "Pn" to 2, "Wt" to 3, "Śr" to 4, "Cz" to 5, "Pt" to 6, "So" to 7)
    val selectedDays = alarm.days.split(", ").filter { it.isNotEmpty() }

    if (selectedDays.isEmpty() || alarm.days == "Jutro") {
        // Jeśli godzina już minęła, ustawiamy na jutro
        if (cal.before(now)) cal.add(Calendar.DATE, 1)
    } else {
        var minTime = Long.MAX_VALUE
        var bestCal = cal.clone() as Calendar

        for (day in selectedDays) {
            val target = dMap[day] ?: continue
            val temp = cal.clone() as Calendar

            // Szukamy najbliższego wystąpienia tego dnia
            while (temp.get(Calendar.DAY_OF_WEEK) != target || temp.before(now)) {
                temp.add(Calendar.DATE, 1)
            }

            if (temp.timeInMillis < minTime) {
                minTime = temp.timeInMillis
                bestCal = temp
            }
        }
        cal.timeInMillis = bestCal.timeInMillis
    }

    // Ustawienie dokładnego alarmu
    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        cal.timeInMillis,
        pendingIntent
    )
    Log.d("RobotAlarm", "Zaplanowano budzik ID ${alarm.id} na: ${cal.time}")
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: AlarmViewModel, onBack: () -> Unit) {
    val stats by viewModel.allStats.collectAsState(initial = emptyList())
    Scaffold(
        topBar = { TopAppBar(title = { Text("Statystyki") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "") } }) }
    ) { pad ->
        LazyColumn(modifier = Modifier.padding(pad).fillMaxSize()) {
            items(stats) { stat ->
                ListItem(headlineContent = { Text("${stat.date} | Czas: ${stat.seconds}s") }, supportingContent = { Text("Próby: ${stat.attempts}") })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AlarmViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var showWifiDialog by remember { mutableStateOf(false) }
    var selectedSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var showRobotSongsDialog by remember { mutableStateOf(false) }

    val pickMp3Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val cursor = context.contentResolver.query(it, null, null, null, null)
            cursor?.use { c ->
                val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst()) {
                    val fileName = c.getString(nameIndex)
                    if (fileName.lowercase().endsWith(".mp3")) {
                        viewModel.updateSong(fileName)
                        android.widget.Toast.makeText(context, "Przesłano: $fileName", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Błąd: Wybierz plik MP3!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Wstecz")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text("Głośność alarmu: ${viewModel.volume.value.toInt()}%", style = MaterialTheme.typography.labelLarge)
            Slider(value = viewModel.volume.value, onValueChange = { viewModel.updateVolume(it) }, valueRange = 0f..100f)

            Spacer(modifier = Modifier.height(20.dp))

            Text("Szybkość ucieczki robota: ${viewModel.robotSpeed.value.toInt()}", style = MaterialTheme.typography.labelLarge)
            Slider(value = viewModel.robotSpeed.value, onValueChange = { viewModel.updateSpeed(it) }, valueRange = 1f..10f)

            Spacer(modifier = Modifier.height(30.dp))

            Text("ZARZĄDZANIE DŹWIĘKIEM", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))

            Button(onClick = { pickMp3Launcher.launch("audio/mpeg") }, modifier = Modifier.fillMaxWidth()) {
                Text("PRZEŚLIJ NOWĄ PIOSENKĘ (.MP3)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = { showRobotSongsDialog = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                Text("WYBIERZ Z PAMIĘCI ROBOTA")
            }

            Spacer(modifier = Modifier.height(30.dp))

            Button(onClick = { viewModel.toggleTheme() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = if (viewModel.isDarkMode.value) Color.White else Color.Black, contentColor = if (viewModel.isDarkMode.value) Color.Black else Color.White)) {
                Text(if (viewModel.isDarkMode.value) "TRYB JASNY" else "TRYB CIEMNY")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { showWifiDialog = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF607D8B))) {
                Icon(Icons.Default.Wifi, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("POŁĄCZ ROBOTA Z WIFI")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = { viewModel.connectToRobot() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = if (viewModel.isBluetoothConnected.value) Color(0xFF4CAF50) else Color.Gray)) {
                Text(if (viewModel.isBluetoothConnected.value) "POŁĄCZONO Z ROBOTEM" else "POŁĄCZ Z ROBOTEM (BT)")
            }

            Spacer(modifier = Modifier.weight(1f))
            Text(text = "Autorzy: Bartosz W. i Tomasz P.", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.Gray.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        }
    }

    // --- DIALOGI PRZENIESIONE DO ŚRODKA FUNKCJI ---

    if (showRobotSongsDialog) {
        val robotSongs = listOf("Pobudka_Standard.mp3", "Heavy_Metal_Robot.mp3", "Ptaki_Lasy.mp3", "Syrena_Alarmowa.mp3")
        AlertDialog(
            onDismissRequest = { showRobotSongsDialog = false },
            title = { Text("Piosenki na robocie") },
            text = {
                Column {
                    robotSongs.forEach { song ->
                        TextButton(onClick = { viewModel.updateSong(song); showRobotSongsDialog = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(song, textAlign = androidx.compose.ui.text.style.TextAlign.Left, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRobotSongsDialog = false }) { Text("ZAMKNIJ") } }
        )
    }

    if (showWifiDialog) {
        AlertDialog(
            onDismissRequest = { showWifiDialog = false },
            title = { Text("Konfiguracja WiFi Robota") },
            text = {
                Column {
                    Text("Wybierz sieć:")
                    viewModel.availableWifi.forEach { ssid ->
                        Row(Modifier.fillMaxWidth().clickable { selectedSsid = ssid }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = (selectedSsid == ssid), onClick = { selectedSsid = ssid })
                            Text(ssid)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = wifiPassword,
                        onValueChange = { wifiPassword = it },
                        label = { Text("Hasło WiFi") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (selectedSsid.isNotEmpty() && wifiPassword.isNotEmpty()) {
                        viewModel.connectRobotToWifi(selectedSsid, wifiPassword) { success ->
                            if (success) {
                                android.widget.Toast.makeText(context, "Dane wysłane do robota!", android.widget.Toast.LENGTH_SHORT).show()
                                showWifiDialog = false
                            }
                        }
                    }
                }) { Text("POŁĄCZ") }
            },
            dismissButton = { TextButton(onClick = { showWifiDialog = false }) { Text("ANULUJ") } }
        )
    }
}
@Composable
fun AlarmPuzzleScreen(viewModel: AlarmViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var attempts by remember { mutableIntStateOf(1) }
    var timeLeft by remember { mutableIntStateOf(20) }
    var isRobotMuted by remember { mutableStateOf(false) } // Czy przycisk na robocie został wciśnięty
    var currentQuestion by remember { mutableStateOf<Question?>(null) }
    val startTime = remember { System.currentTimeMillis() }

    // Ładowanie pytania (tylko poprawna odpowiedź nas interesuje na telefonie)
    LaunchedEffect(attempts) {
        currentQuestion = viewModel.getRandomQuestion()
        isRobotMuted = false // Po błędzie lub starcie robot znowu ucieka
        timeLeft = 20
    }

    // Wibracje działają tylko gdy robot NIE jest uśpiony
    LaunchedEffect(isRobotMuted) {
        if (!isRobotMuted) startVibration(context) else stopVibration(context)
    }

    // Licznik 20 sekund (działa tylko gdy robot uśpiony)
    LaunchedEffect(isRobotMuted) {
        if (isRobotMuted) {
            while (timeLeft > 0) {
                delay(1000)
                timeLeft--
            }
            // Koniec czasu = błąd
            attempts++
            isRobotMuted = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.triggerAlarmSequence() // To wyśle pytanie do robota przez BT
        startVibration(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (!isRobotMuted) Color.Red else Color(0xFF1A1A1A))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (!isRobotMuted) "ROBOT UCIEKA!" else "ROBOT WYCISZONY",
            fontSize = 32.sp, color = Color.White
        )
        Text("Próba: $attempts/10", fontSize = 20.sp, color = Color.LightGray)

        if (isRobotMuted) {
            Text("Czas na odpowiedź: $timeLeft s", fontSize = 24.sp, color = Color.Yellow)
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Przyciski A, B, C, D (działają tylko gdy robot uśpiony)
        val labels = listOf("A", "B", "C", "D")
        labels.chunked(2).forEach { row ->
            Row {
                row.forEach { label ->
                    Button(
                        enabled = isRobotMuted,
                        onClick = {
                            if (label == currentQuestion?.correct) {
                                val totalSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                                viewModel.saveStat(totalSec, attempts)
                                stopVibration(context)
                                onDismiss()
                            } else {
                                attempts++
                                if (attempts > 10) onDismiss() // Fail-safe
                                isRobotMuted = false // Robot znowu ucieka
                            }
                        },
                        modifier = Modifier.padding(10.dp).size(120.dp, 80.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        Text(label, fontSize = 24.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // SYMULACJA PRZYCISKU NA ROBOCIE
        if (!isRobotMuted) {
            Button(
                onClick = { isRobotMuted = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Green, contentColor = Color.Black)
            ) {
                Text("[ SYMULUJ PRZYCISK NA ROBOCIE ]")
            }
        }
    }
}

fun startVibration(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), 0))
}

fun stopVibration(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    vibrator.cancel()
}