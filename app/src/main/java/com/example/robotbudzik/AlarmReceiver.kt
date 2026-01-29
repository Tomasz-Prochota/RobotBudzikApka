package com.example.robotbudzik

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("RobotAlarm", "System wywołał alarm!")
        val alarmIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("from_alarm", true)
        }

        try {
            context.startActivity(alarmIntent)
        } catch (e: Exception) {
            Log.e("RobotAlarm", "Błąd przy starcie Activity: ${e.message}")
        }
    }
}