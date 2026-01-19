package com.example.robotbudzik

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

@SuppressLint("MissingPermission")
object RobotConnector {
    private const val TAG = "RobotConnector"
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var bluetoothSocket: BluetoothSocket? = null
    private var outStream: OutputStream? = null
    private var inStream: InputStream? = null

    fun connectToRobot(deviceName: String): Boolean {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        val device = pairedDevices?.find { it.name == deviceName } ?: return false

        return try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
            bluetoothSocket?.connect()
            outStream = bluetoothSocket?.outputStream
            inStream = bluetoothSocket?.inputStream
            true
        } catch (e: IOException) {
            false
        }
    }

    fun sendToRobot(message: String) {
        try {
            outStream?.write((message + "\n").toByteArray())
        } catch (e: IOException) {
            Log.e(TAG, "Błąd wysyłania: $message")
        }
    }

    fun requestSongList() {
        sendToRobot("GET_SONGS") // Prośba do robota: "Powiedz mi co masz na karcie SD"
    }

    // --- TE NAZWY MUSZĄ SIĘ ZGADZAĆ Z VIEWMODEL ---

    fun setMusic(action: String, songName: String) {
        sendToRobot("MUSIC:$action|$songName")
    }

    fun sendAlarmData(question: Question, speed: Int) {
        val msg = "ALARM_START|$speed|${question.content}|${question.ansA}|${question.ansB}|${question.ansC}|${question.ansD}|${question.correct}"
        sendToRobot(msg)
    }

    fun sendAlarmTimeToRobot(hour: Int, minute: Int) {
        val msg = String.format(Locale.getDefault(), "SET_ALARM:%02d:%02d", hour, minute)
        sendToRobot(msg)
    }

    fun sendManualAlarmStart() {
        sendToRobot("ALARM_START")
    }

    fun sendAlarmStop() {
        sendToRobot("ALARM_STOP")
    }


    fun sendCurrentTimeToRobot() {
        val now = Calendar.getInstance()
        // Formatujemy czas na: TIME:GG:MM:SS
        val timeStr = String.format(
            Locale.getDefault(),
            "TIME:%02d:%02d:%02d",
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            now.get(Calendar.SECOND)
        )
        sendToRobot(timeStr)
        Log.d("RobotConnector", "Wysłano synchronizację czasu: $timeStr")
    }

    fun listenForData(
        onBatteryReceived: (Int) -> Unit,
        onWifiListReceived: (List <String>) -> Unit,
        onSnoozeReceived: () -> Unit, // Dodajemy to
        onSongsReceived: (List<String>) -> Unit
    ) {
        Thread {
            val buffer = ByteArray(1024)
            while (bluetoothSocket?.isConnected == true) {
                try {
                    val bytes = inStream?.read(buffer) ?: 0
                    if (bytes > 0) {
                        val incoming = String(buffer, 0, bytes).trim()

                        if (incoming.startsWith("BAT:")) {
                            val level = incoming.substringAfter("BAT:").toIntOrNull() ?: 0
                            onBatteryReceived(level)
                        }
                        else if (incoming == "SNOOZE_PRESSED") {
                            onSnoozeReceived() // Wywołujemy, gdy robot wyśle sygnał
                        }
                        else if (incoming.startsWith("WIFI_LIST:")) {
                            val list = incoming.substringAfter("WIFI_LIST:").split(",").filter { it.isNotEmpty() }
                            onWifiListReceived(list)
                        }
                        else if (incoming.startsWith("SONG_LIST:")) {
                            val list = incoming.substringAfter("SONG_LIST:")
                                .split(",")
                                .filter { it.lowercase().endsWith(".wav") } // ZMIANA na .wav
                            onSongsReceived(list)
                        }
                    }
                } catch (e: IOException) { break }
            }
        }.start()
    }

    fun sendWifiToRobot(ssid: String, pass: String) {
        sendToRobot("WIFI:$ssid|$pass")
    }

    fun requestWifiScan() {
        sendToRobot("WIFI_SCAN_REQ") // Prośba do robota: "Prześlij mi co widzisz"
    }

    // Zapasowa funkcja dla serwera (opcjonalnie)
    fun uploadStatsToServer(stat: Statistic) {
        Log.d(TAG, "Statystyka gotowa do wysłania na serwer Tailscale")
    }
}