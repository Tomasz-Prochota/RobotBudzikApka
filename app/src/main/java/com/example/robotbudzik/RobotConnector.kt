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

    // --- TE NAZWY MUSZĄ SIĘ ZGADZAĆ Z VIEWMODEL ---

    fun setMusic(action: String, songName: String) {
        sendToRobot("MUSIC:$action|$songName")
    }

    fun sendAlarmData(question: Question, speed: Int) {
        val msg = "ALARM_START|$speed|${question.content}|${question.ansA}|${question.ansB}|${question.ansC}|${question.ansD}|${question.correct}"
        sendToRobot(msg)
    }

    fun listenForData(onBatteryReceived: (Int) -> Unit) {
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
                    }
                } catch (e: IOException) { break }
            }
        }.start()
    }

    fun sendWifiToRobot(ssid: String, pass: String) {
        sendToRobot("WIFI:$ssid|$pass")
    }

    // Zapasowa funkcja dla serwera (opcjonalnie)
    fun uploadStatsToServer(stat: Statistic) {
        Log.d(TAG, "Statystyka gotowa do wysłania na serwer Tailscale")
    }
}