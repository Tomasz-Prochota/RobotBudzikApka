package com.example.robotbudzik

import android.app.Application
import androidx.compose.runtime.mutableIntStateOf
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

    val availableWifi = listOf("Dom_Router_1", "Moja_Siec_2G", "Robot_Hotspot", "TP-Link_Guest")

    fun connectRobotToWifi(ssid: String, pass: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            // Wysyłamy dane przez Bluetooth do ESP32
            RobotConnector.sendWifiToRobot(ssid, pass)
            // Zakładamy sukces wysyłki danych do robota
            onResult(true)
        }
    }

    // Napisy na Dashboardzie
    var nextAlarmInfo = mutableStateOf("Brak aktywnych budzików")
    var nextAlarmCountdown = mutableStateOf("")

    // --- KOMUNIKACJA (Bluetooth) ---

    fun connectToRobot() {
        viewModelScope.launch(Dispatchers.IO) {
            // "Robot_Budzik" to nazwa Twojego ESP32
            val success = RobotConnector.connectToRobot("Robot_Budzik")
            isBluetoothConnected.value = success
            if (success) {
                // Zacznij słuchać danych o baterii
                RobotConnector.listenForData { level ->
                    batteryLevel.intValue = level
                }
            }
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
        viewModelScope.launch {
            val question = alarmDao.getRandomQuestion()
            question?.let {
                RobotConnector.sendAlarmData(it, robotSpeed.value.toInt())
            }
        }
    }

    fun sendStopToRobot() {
        RobotConnector.sendToRobot("ALARM_STOP")
    }

    fun setupRobotWifi(ssid: String, pass: String) {
        RobotConnector.sendWifiToRobot(ssid, pass)
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
            // Opcjonalnie wyślij na serwer jeśli Tailscale jest włączony
            RobotConnector.uploadStatsToServer(stat)
        }
    }

    suspend fun getRandomQuestion(): Question? = alarmDao.getRandomQuestion()

    fun seedDatabase() {
        viewModelScope.launch {
            val existing = alarmDao.getRandomQuestion()
            if (existing == null) {
                val pytania = listOf(
                    Question(content = "Ile to jest 15 * 4?", ansA = "50", ansB = "60", ansC = "70", ansD = "65", correct = "B"),
                    Question(content = "Który pierwiastek ma symbol Fe?", ansA = "Złoto", ansB = "Srebro", ansC = "Żelazo", ansD = "Miedź", correct = "C"),
                    Question(content = "Stolica Polski to...", ansA = "Kraków", ansB = "Gdańsk", ansC = "Warszawa", ansD = "Wrocław", correct = "C"),
                    Question(content = "Ile minut ma 2,5 godziny?", ansA = "120", ansB = "150", ansC = "180", ansD = "140", correct = "B"),
                    Question(content = "Dokończ ciąg: 2, 4, 8, 16...", ansA = "24", ansB = "30", ansC = "32", ansD = "64", correct = "C"),
                    Question(content = "Co jest sercem naszego robota?", ansA = "Arduino", ansB = "ESP32", ansC = "Raspberry Pi", ansD = "STM32", correct = "B"),
                    Question(content = "Ile nóg ma pająk?", ansA = "6", ansB = "8", ansC = "10", ansD = "12", correct = "B"),
                    Question(content = "Najwyższy szczyt świata to...", ansA = "K2", ansB = "Rysy", ansC = "Mount Everest", ansD = "Lhotse", correct = "C"),
                    Question(content = "W którym roku była bitwa pod Grunwaldem?", ansA = "1385", ansB = "1410", ansC = "1492", ansD = "1569", correct = "B"),
                    Question(content = "Na jaką ocenę nadaje się ten budzik?", ansA = "3", ansB = "5", ansC = "4", ansD = "2", correct = "B")
                    // ... możesz dopisać resztę analogicznie
                )
                pytania.forEach { alarmDao.insertQuestion(it) }
            }
        }
    }

    // --- LOGIKA NAJBLIŻSZEGO ALARMU (Dashboard) ---
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