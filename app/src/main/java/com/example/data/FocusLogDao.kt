package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusLogDao {
    @Query("SELECT * FROM focus_logs ORDER BY timestamp DESC")
    fun getAllFocusLogs(): Flow<List<FocusLog>>

    @Query("SELECT * FROM focus_logs WHERE date BETWEEN :startAndEnd AND :startAndEnd")
    fun getLogsForDate(startAndEnd: String): Flow<List<FocusLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFocusLog(log: FocusLog)

    @Delete
    suspend fun deleteFocusLog(log: FocusLog)

    @Query("DELETE FROM focus_logs")
    suspend fun clearAllLogs()
}
