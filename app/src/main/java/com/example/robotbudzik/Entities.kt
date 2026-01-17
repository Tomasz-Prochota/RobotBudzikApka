package com.example.robotbudzik

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val isActive: Boolean = true,
    val days: String = ""
)

@Entity(tableName = "questions")
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @SerializedName("tresc_pytania") val content: String,
    @SerializedName("odp_a") val ansA: String,
    @SerializedName("odp_b") val ansB: String,
    @SerializedName("odp_c") val ansC: String,
    @SerializedName("odp_d") val ansD: String,
    @SerializedName("poprawna") val correct: String
)

@Entity(tableName = "stats")
data class Statistic(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @SerializedName("data") val date: String,
    @SerializedName("sekundy") val seconds: Int,
    @SerializedName("proby") val attempts: Int
)