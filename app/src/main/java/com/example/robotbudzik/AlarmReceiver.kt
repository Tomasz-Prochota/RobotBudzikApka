package com.example.robotbudzik

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("RobotAlarm", "System wywołał alarm!")

        // To jest ten moment, który wyciąga apkę na wierzch!
        val alarmIntent = Intent(context, MainActivity::class.java).apply {
            // Flagi, które mówią: "Otwórz to okno na samym przodzie, nawet jak apka śpi"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("from_alarm", true) // Przekazujemy flagę do głównego pliku
        }

        try {
            context.startActivity(alarmIntent)
        } catch (e: Exception) {
            Log.e("RobotAlarm", "Błąd przy starcie Activity: ${e.message}")
        }
    }
}