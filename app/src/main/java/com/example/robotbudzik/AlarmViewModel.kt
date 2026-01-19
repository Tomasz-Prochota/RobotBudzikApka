package com.example.robotbudzik

import android.app.Application
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.*

class AlarmViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val alarmDao = db.alarmDao()
    private val settings = SettingsManager(application)

    val allAlarms: Flow<List<Alarm>> = alarmDao.getAllAlarms()
    val allStats = alarmDao.getRecentStats()

    // --- STANY ---
    var isDarkMode = mutableStateOf(settings.isDarkTheme())
    var volume = mutableStateOf(settings.getVolume())
    var robotSpeed = mutableStateOf(settings.getRobotSpeed())
    var selectedSong = mutableStateOf(settings.getSelectedSong())
    var batteryLevel = mutableIntStateOf(75)
    var isBluetoothConnected = mutableStateOf(false)
    var isRobotMusicPlaying = mutableStateOf(false)
    var isRobotMuted = mutableStateOf(false)

    // Stan skanowania WiFi i Muzyki
    var availableWifi = mutableStateListOf<String>()
    var isScanningWifi = mutableStateOf(false)
    var robotSongs = mutableStateListOf<String>() // NAPRAWA BŁĘDU
    var isScanningSongs = mutableStateOf(false)    // NAPRAWA BŁĘDU

    // Napisy na Dashboardzie
    var nextAlarmInfo = mutableStateOf("Brak aktywnych budzików")
    var nextAlarmCountdown = mutableStateOf("")

    private var timeSyncJob: kotlinx.coroutines.Job? = null

    // --- KOMUNIKACJA (Bluetooth) ---

    fun connectToRobot() {
        viewModelScope.launch(Dispatchers.IO) {
            val success = RobotConnector.connectToRobot("Budzik_Robot")
            isBluetoothConnected.value = success
            if (success) {
                // Synchronizuj czas zaraz po połączeniu
                RobotConnector.sendCurrentTimeToRobot()
                startTimeSyncLoop()

                RobotConnector.listenForData(
                    onBatteryReceived = { batteryLevel.intValue = it },
                    onWifiListReceived = { list ->
                        availableWifi.clear()
                        availableWifi.addAll(list)
                        isScanningWifi.value = false
                    },
                    onSnoozeReceived = { isRobotMuted.value = true },
                    onSongsReceived = { list ->
                        robotSongs.clear()
                        robotSongs.addAll(list)
                        isScanningSongs.value = false
                    }
                )
            }
        }
    }

    private fun startTimeSyncLoop() {
        timeSyncJob?.cancel()
        timeSyncJob = viewModelScope.launch(Dispatchers.IO) {
            while (isBluetoothConnected.value) {
                delay(3600000) // Co godzinę
                RobotConnector.sendCurrentTimeToRobot()
            }
        }
    }

    fun syncAlarmWithRobot(hour: Int, minute: Int) {
        if (isBluetoothConnected.value) {
            RobotConnector.sendAlarmTimeToRobot(hour, minute)
        }
    }

    fun refreshRobotSongs() {
        if (isBluetoothConnected.value) {
            isScanningSongs.value = true
            RobotConnector.requestSongList()
        }
    }

    fun toggleRobotMusic() {
        isRobotMusicPlaying.value = !isRobotMusicPlaying.value
        val action = if (isRobotMusicPlaying.value) "PLAY" else "STOP"
        RobotConnector.setMusic(action, selectedSong.value)
    }

    fun controlRobot(direction: String) {
        RobotConnector.sendToRobot("MOVE:$direction")
    }

    fun triggerAlarmSequence() {
        isRobotMuted.value = false
        viewModelScope.launch {
            val question = alarmDao.getRandomQuestion()
            question?.let {
                RobotConnector.sendAlarmData(it, robotSpeed.value.toInt())
            }
        }
    }

    fun sendStopToRobot() {
        RobotConnector.sendAlarmStop()
    }

    fun connectRobotToWifi(ssid: String, pass: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            RobotConnector.sendWifiToRobot(ssid, pass)
            onResult(true) // Informujemy interfejs, że wysłano dane
        }
    }

    fun setupRobotWifi(ssid: String, pass: String) {
        viewModelScope.launch {
            // Ta funkcja wywołuje komendę w RobotConnector
            RobotConnector.sendWifiToRobot(ssid, pass)
        }
    }

    suspend fun getRandomQuestion(): Question? {
        return alarmDao.getRandomQuestion()
    }

    fun refreshWifiList() {
        if (isBluetoothConnected.value) {
            isScanningWifi.value = true
            RobotConnector.requestWifiScan()
        }
    }

    // --- BAZA DANYCH I USTAWIENIA ---

    fun toggleTheme() {
        isDarkMode.value = !isDarkMode.value
        settings.saveDarkTheme(isDarkMode.value)
    }

    fun updateVolume(newVol: Float) {
        volume.value = newVol
        settings.saveVolume(newVol)
    }

    fun updateSpeed(newSpeed: Float) {
        robotSpeed.value = newSpeed
        settings.saveRobotSpeed(newSpeed)
    }

    fun updateSong(name: String) {
        selectedSong.value = name
        settings.saveSelectedSong(name)
        RobotConnector.sendToRobot("SET_SONG:$name")
    }

    fun addAlarm(hour: Int, minute: Int, days: String, onComplete: (Alarm) -> Unit) {
        viewModelScope.launch {
            val alarm = Alarm(hour = hour, minute = minute, days = days)
            val id = alarmDao.insertAlarm(alarm).toInt()
            onComplete(alarm.copy(id = id))
        }
    }

    fun updateAlarm(alarm: Alarm) {
        viewModelScope.launch { alarmDao.updateAlarm(alarm) }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch { alarmDao.deleteAlarm(alarm) }
    }

    fun toggleAlarm(alarm: Alarm, isActive: Boolean) {
        viewModelScope.launch { alarmDao.updateAlarm(alarm.copy(isActive = isActive)) }
    }

    fun saveStat(seconds: Int, attempts: Int) {
        viewModelScope.launch {
            val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val stat = Statistic(date = date, seconds = seconds, attempts = attempts)
            alarmDao.insertStat(stat)
            RobotConnector.uploadStatsToServer(stat)
        }
    }

    fun seedDatabase() {
        viewModelScope.launch {
            val existing = alarmDao.getRandomQuestion()
            if (existing == null) {
                val pytania = listOf(
                    Question(content = "15 * 4 = ?", ansA = "50", ansB = "60", ansC = "70", ansD = "65", correct = "B"),
                    Question(content = "Stolica Polski?", ansA = "Kraków", ansB = "Gdańsk", ansC = "Warszawa", ansD = "Poznań", correct = "C")
                )
                pytania.forEach { alarmDao.insertQuestion(it) }
            }
        }
    }

    fun updateNextAlarmDisplay(alarms: List<Alarm>) {
        val activeAlarms = alarms.filter { it.isActive }
        if (activeAlarms.isEmpty()) {
            nextAlarmInfo.value = "Brak aktywnych budzików"
            nextAlarmCountdown.value = ""
            return
        }

        val now = Calendar.getInstance()
        var minTime = Long.MAX_VALUE
        var bestCal: Calendar? = null
        val dMap = mapOf("Nd" to 1, "Pn" to 2, "Wt" to 3, "Śr" to 4, "Cz" to 5, "Pt" to 6, "So" to 7)

        for (alarm in activeAlarms) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val selectedDays = alarm.days.split(", ").filter { it.isNotEmpty() }
            if (selectedDays.isEmpty() || alarm.days == "Jutro") {
                if (cal.before(now)) cal.add(Calendar.DATE, 1)
                if (cal.timeInMillis < minTime) {
                    minTime = cal.timeInMillis
                    bestCal = cal
                }
            } else {
                for (day in selectedDays) {
                    val target = dMap[day] ?: continue
                    val temp = cal.clone() as Calendar
                    while (temp.get(Calendar.DAY_OF_WEEK) != target || temp.before(now)) {
                        temp.add(Calendar.DATE, 1)
                    }
                    if (temp.timeInMillis < minTime) {
                        minTime = temp.timeInMillis
                        bestCal = temp
                    }
                }
            }
        }

        bestCal?.let {
            val dayNames = listOf("", "Nd", "Pn", "Wt", "Śr", "Cz", "Pt", "So")
            nextAlarmInfo.value = String.format("Najbliższy: %s, %02d:%02d", dayNames[it.get(Calendar.DAY_OF_WEEK)], it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE))
            val diff = minTime - now.timeInMillis
            val d = diff / 86400000
            val h = (diff / 3600000) % 24
            val m = (diff / 60000) % 60
            val s = (diff / 1000) % 60
            nextAlarmCountdown.value = String.format("za %dd, %02dh, %02dm, %02ds", d, h, m, s)
        }
    }
}