package com.example.data.local

import androidx.room.*
import com.example.data.model.FocusSession
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getSessionsSince(sinceTimestamp: Long): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions WHERE weekIdentifier = :weekId ORDER BY timestamp ASC")
    fun getSessionsByWeek(weekId: String): Flow<List<FocusSession>>

    @Query("SELECT DISTINCT weekIdentifier FROM focus_sessions WHERE weekIdentifier != '' ORDER BY weekIdentifier DESC")
    fun getAvailableWeeks(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<FocusSession>)

    @Query("SELECT SUM(durationMinutes) FROM focus_sessions")
    suspend fun getTotalFocusMinutes(): Int?

    @Query("DELETE FROM focus_sessions")
    suspend fun deleteAllSessions()

    @Query("SELECT * FROM focus_sessions")
    suspend fun getAllSessionsSync(): List<FocusSession>
}
