package com.example.robotbudzik

import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

object RobotConnector {
    private val client = OkHttpClient()
    private val gson = Gson()

    // TWOJE IP Z TAILSCALE (zmień na właściwe!)
    private const val BASE_URL = "http://100.x.y.z:3000"

    // 1. Pobieranie pytań z serwera
    suspend fun downloadQuestionsFromServer(): List<Question> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$BASE_URL/api/pytania").build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string()
                // Zakładamy, że serwer zwraca listę obiektów Question w JSON
                return@withContext gson.fromJson(body, Array<Question>::class.java).toList()
            }
        } catch (e: Exception) {
            Log.e("RobotConnector", "Błąd pobierania pytań: ${e.message}")
            emptyList()
        }
    }

    // 2. Wysyłanie statystyk na serwer
    fun uploadStatsToServer(stat: Statistic) {
        val json = gson.toJson(stat)
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url("$BASE_URL/api/statystyki").post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("RobotConnector", "Błąd wysyłania statystyk: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                Log.d("RobotConnector", "Statystyki wysłane: ${response.code}")
            }
        })
    }

    // 3. Sprawdzanie statusu (Bot Connected)
    suspend fun isRobotReachable(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$BASE_URL/api/status").build()
        return@withContext try {
            client.newCall(request).execute().isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // 4. Przesyłanie danych WiFi do robota
    fun sendWifiCredentials(ssid: String, pass: String, onResult: (Boolean) -> Unit) {
        val json = mapOf("ssid" to ssid, "password" to pass)
        val body = gson.toJson(json).toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url("$BASE_URL/api/konfiguruj-wifi").post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { onResult(false) }
            override fun onResponse(call: Call, response: Response) { onResult(response.isSuccessful) }
        })
    }

    suspend fun fetchBatteryLevel(): Int = withContext(Dispatchers.IO) {
        // Zakładamy, że serwer ma endpoint /api/bateria, który czyta z tabeli robot_status
        val request = Request.Builder().url("$BASE_URL/api/bateria").build()
        return@withContext try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext 50
                val body = response.body?.string() ?: ""
                // Serwer pewnie zwróci coś w stylu: {"battery": "85"}
                val map = gson.fromJson(body, Map::class.java)
                val value = map["battery"] as? String ?: "50"
                value.toInt()
            }
        } catch (e: Exception) {
            Log.e("RobotConnector", "Błąd pobierania baterii: ${e.message}")
            50 // W razie błędu pokaż 50%
        }
    }
}