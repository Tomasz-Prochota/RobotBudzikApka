package com.example.robotbudzik

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute ASC")
    fun getAllAlarms(): Flow<List<Alarm>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: Alarm): Long

    @Delete
    suspend fun deleteAlarm(alarm: Alarm)

    @Update
    suspend fun updateAlarm(alarm: Alarm)

    @Query("SELECT * FROM questions ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomQuestion(): Question?

    @Insert
    suspend fun insertQuestion(question: Question)

    // Statystyki
    @Query("SELECT * FROM stats ORDER BY id DESC LIMIT 20")
    fun getRecentStats(): kotlinx.coroutines.flow.Flow<List<Statistic>>

    @Insert
    suspend fun insertStat(stat: Statistic)
}