package com.example.robotbudzik

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("robot_settings", Context.MODE_PRIVATE)

    fun saveVolume(volume: Float) { prefs.edit().putFloat("volume", volume).apply() }
    fun getVolume(): Float = prefs.getFloat("volume", 50f)

    fun saveDarkTheme(isDark: Boolean) { prefs.edit().putBoolean("is_dark", isDark).apply() }
    fun isDarkTheme(): Boolean = prefs.getBoolean("is_dark", true)

    fun saveRobotSpeed(speed: Float) { prefs.edit().putFloat("robot_speed", speed).apply() }
    fun getRobotSpeed(): Float = prefs.getFloat("robot_speed", 5f)

    fun saveSelectedSong(songName: String) {
        prefs.edit().putString("selected_song", songName).apply()
    }

    fun getSelectedSong(): String = prefs.getString("selected_song", "Brak wybranej") ?: "Brak wybranej"
    }