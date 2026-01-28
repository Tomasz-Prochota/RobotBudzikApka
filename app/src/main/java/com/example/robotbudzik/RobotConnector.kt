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
            val msgWithNewline = if (message.endsWith("\n")) message else message + "\n"
            outStream?.write(msgWithNewline.toByteArray())
            outStream?.flush()

            // SZCZEGÓŁOWY LOG DLA CIEBIE
            Log.d("RobotConnector", ">>> BLUETOOTH SEND: $msgWithNewline")
        } catch (e: IOException) {
            Log.e("RobotConnector", "!!! BŁĄD WYSYŁANIA: $message")
        }
    }

    fun requestSongList() {
        sendToRobot("GET_SONGS")
    }

    fun setMusic(action: String, songName: String) {
        sendToRobot("MUSIC:$action|$songName")
    }

    fun sendAlarmData(question: Question, speed: Int, volume: Int, song: String) {
        // Format: ALARM_START|speed|volume|song.wav|ABCD|Pytanie|A|B|C|D|poprawna
        val msg = "ALARM_START|$speed|$volume|$song|ABCD|${question.content}|${question.ansA}|${question.ansB}|${question.ansC}|${question.ansD}|${question.correct.lowercase()}"
        sendToRobot(msg)
    }

    fun sendMathData(expression: String, result: Int, speed: Int, volume: Int, song: String) {
        // Format: ALARM_START|speed|volume|song.wav|INPUT|wyrażenie|wynik
        val msg = "ALARM_START|$speed|$volume|$song|INPUT|$expression|$result"
        sendToRobot(msg)
    }

    fun sendSpeed(speed: Int) {
        sendToRobot("SET_SPEED:$speed")
    }

    fun sendVolume(volume: Int) {
        sendToRobot("SET_VOLUME:$volume")
    }

    fun sendAlarmTimeToRobot(hour: Int, minute: Int) {
        val msg = String.format(Locale.getDefault(), "SET_ALARM:%02d:%02d", hour, minute)
        sendToRobot(msg)
    }

    fun sendManualAlarmStart() {
        sendToRobot("ALARM_START")
    }

    fun sendAlarmStop(finalResult: String) {
        sendToRobot("ALARM_STOP|$finalResult")
    }

    fun sendAlarmResume() {
        sendToRobot("ALARM_RESUME")
    }


    fun sendCurrentTimeToRobot() {
        val now = Calendar.getInstance()
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
        onSnoozeReceived: () -> Unit,
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
                            onSnoozeReceived()
                        }
                        else if (incoming.startsWith("WIFI_LIST:")) {
                            val list = incoming.substringAfter("WIFI_LIST:").split(",").filter { it.isNotEmpty() }
                            onWifiListReceived(list)
                        }
                        else if (incoming.startsWith("SONG_LIST:")) {
                            val list = incoming.substringAfter("SONG_LIST:")
                                .split(",")
                                .filter { it.lowercase().endsWith(".wav") }
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
        sendToRobot("WIFI_SCAN_REQ")
    }

    fun uploadStatsToServer(stat: Statistic) {
        Log.d(TAG, "Statystyka gotowa do wysłania na serwer Tailscale")
    }
}