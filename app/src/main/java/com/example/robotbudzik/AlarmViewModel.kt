package com.example.robotbudzik

import android.app.Application
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val alarmDao = db.alarmDao()
    private val settings = SettingsManager(application)

    private val daysMap = mapOf(
        "Nd" to Calendar.SUNDAY, "Pn" to Calendar.MONDAY, "Wt" to Calendar.TUESDAY,
        "Śr" to Calendar.WEDNESDAY, "Cz" to Calendar.THURSDAY, "Pt" to Calendar.FRIDAY, "So" to Calendar.SATURDAY
    )

    val allAlarms: Flow<List<Alarm>> = alarmDao.getAllAlarms()
    val allStats = alarmDao.getRecentStats()

    // Stan motywu i głośności
    var isDarkMode = mutableStateOf(settings.isDarkTheme())
    var volume = mutableStateOf(settings.getVolume())
    var robotSpeed = mutableStateOf(settings.getRobotSpeed())
    var batteryLevel = mutableIntStateOf(75)

    var nextAlarmInfo = mutableStateOf("Brak aktywnych budzików")
    var nextAlarmCountdown = mutableStateOf("")

    fun updateSpeed(newSpeed: Float) {
        robotSpeed.value = newSpeed
        settings.saveRobotSpeed(newSpeed)
    }


    fun toggleTheme() {
        isDarkMode.value = !isDarkMode.value
        settings.saveDarkTheme(isDarkMode.value)
    }

    fun updateVolume(newVol: Float) {
        volume.value = newVol
        settings.saveVolume(newVol)
    }

    fun addAlarm(hour: Int, minute: Int, days: String, onComplete: (Alarm) -> Unit) {
        viewModelScope.launch {
            val alarm = Alarm(hour = hour, minute = minute, days = days)
            val newId = alarmDao.insertAlarm(alarm).toInt()
            onComplete(alarm.copy(id = newId))
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch { alarmDao.deleteAlarm(alarm) }
    }

    fun toggleAlarm(alarm: Alarm, isActive: Boolean) {
        viewModelScope.launch {
            alarmDao.updateAlarm(alarm.copy(isActive = isActive))
        }
    }

    // Status połączenia
    var isRobotConnected = mutableStateOf(false)

    // Lista dostępnych sieci (symulowana)
    val availableWifi = listOf("Dom_Router_1", "Moja_Siec_2G", "Robot_Hotspot", "TP-Link_Guest")

    init {
        // Pętla sprawdzająca status połączenia co 10 sekund
        viewModelScope.launch {
            while (true) {
                isRobotConnected.value = RobotConnector.isRobotReachable()
                batteryLevel.intValue = RobotConnector.fetchBatteryLevel()
                delay(10000)
            }
        }
    }

    fun connectRobotToWifi(ssid: String, pass: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = RobotConnector.sendWifiCredentials(ssid, pass)
            onResult(success)
        }
    }

    // Pobieranie losowego pytania (suspend, bo baza danych)
    suspend fun getRandomQuestion(): Question? {
        return alarmDao.getRandomQuestion()
    }

    fun seedDatabase() {
        viewModelScope.launch {
            // Dodajemy tylko jeśli baza jest pusta (uproszczone)
            alarmDao.insertQuestion(Question(content = "2 + 2 * 2 = ?", ansA = "8", ansB = "6", ansC = "4", ansD = "10", correct = "B"))
            alarmDao.insertQuestion(Question(content = "Ile kół ma samochód?", ansA = "2", ansB = "3", ansC = "4", ansD = "5", correct = "C"))
        }
    }

    fun updateAlarm(alarm: Alarm) {
        viewModelScope.launch {
            alarmDao.updateAlarm(alarm)
        }
    }

    var selectedSong = mutableStateOf(settings.getSelectedSong())

    fun updateSong(name: String) {
        selectedSong.value = name
        settings.saveSelectedSong(name)
        android.util.Log.d("RobotComm", "Sygnał dla robota: Zmień piosenkę na: $name")
    }

    init {
        viewModelScope.launch {
            while (true) {
                batteryLevel.intValue = RobotConnector.fetchBatteryLevel()
                delay(30000) // 30 sekund
            }
        }
    }

    fun controlRobot(direction: String) {
        RobotConnector.sendDirection(direction)
    }

    fun refreshQuestions() {
        viewModelScope.launch {
            val newQuestions = RobotConnector.downloadQuestionsFromServer()
            if (newQuestions.isNotEmpty()) {
                // Możesz tu dodać usuwanie starych pytań z bazy lokalnej
                // i dodawanie tych z serwera
                newQuestions.forEach { question ->
                    alarmDao.insertQuestion(question)
                }
            }
        }
    }

    // 3. Podpięcie muzyki
    fun toggleRobotMusic() {
        isRobotMusicPlaying.value = !isRobotMusicPlaying.value
        val action = if (isRobotMusicPlaying.value) "PLAY" else "STOP"
        RobotConnector.setMusic(action, selectedSong.value)
    }

    // 4. Podpięcie startu/stopu alarmu
    fun sendStartToRobot() {
        RobotConnector.setAlarmStatus("START", robotSpeed.value.toInt())
    }

    fun sendStopToRobot() {
        RobotConnector.setAlarmStatus("STOP", 0)
    }

    // 5. Rozbudowa zapisu statystyk o serwer
    fun saveStat(seconds: Int, attempts: Int) {
        viewModelScope.launch {
            val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            val newStat = Statistic(date = date, seconds = seconds, attempts = attempts)
            alarmDao.insertStat(newStat)
            RobotConnector.uploadStatsToServer(newStat) // WYŚLIJ NA SERWER
        }
    }

    var isRobotMusicPlaying = mutableStateOf(false)

    fun updateNextAlarmDisplay(alarms: List<Alarm>) {
        val activeAlarms = alarms.filter { it.isActive }
        if (activeAlarms.isEmpty()) {
            nextAlarmInfo.value = "Brak aktywnych budzików"
            nextAlarmCountdown.value = ""
            return
        }

        val now = Calendar.getInstance()
        var minTime: Long = Long.MAX_VALUE
        var bestAlarm: Alarm? = null
        var bestCalendar: Calendar? = null

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
                    bestAlarm = alarm
                    bestCalendar = cal
                }
            } else {
                // Szukamy najbliższego wybranego dnia tygodnia
                var earliestForThisAlarm = Long.MAX_VALUE
                var earliestCalForThisAlarm: Calendar? = null

                for (dayStr in selectedDays) {
                    val targetDay = daysMap[dayStr] ?: continue
                    val tempCal = cal.clone() as Calendar

                    // Ustawiamy na wybrany dzień tygodnia
                    while (tempCal.get(Calendar.DAY_OF_WEEK) != targetDay || tempCal.before(now)) {
                        tempCal.add(Calendar.DATE, 1)
                    }

                    if (tempCal.timeInMillis < earliestForThisAlarm) {
                        earliestForThisAlarm = tempCal.timeInMillis
                        earliestCalForThisAlarm = tempCal
                    }
                }

                if (earliestForThisAlarm < minTime) {
                    minTime = earliestForThisAlarm
                    bestAlarm = alarm
                    bestCalendar = earliestCalForThisAlarm
                }
            }
        }

        bestCalendar?.let {
            val dayNames = listOf("", "Nd", "Pn", "Wt", "Śr", "Cz", "Pt", "So")
            val dayName = dayNames[it.get(Calendar.DAY_OF_WEEK)]
            nextAlarmInfo.value = String.format("Najbliższy alarm: %s, %02d:%02d", dayName, it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE))

            // Obliczanie odliczania
            val diff = minTime - now.timeInMillis
            val d = diff / (24 * 60 * 60 * 1000)
            val h = (diff / (60 * 60 * 1000)) % 24
            val m = (diff / (60 * 1000)) % 60
            val s = (diff / 1000) % 60
            nextAlarmCountdown.value = String.format("za %dd, %02dh, %02dm, %02ds", d, h, m, s)
        }
    }
}