package com.example.data.local

import androidx.room.*
import com.example.data.model.Friend
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {
    @Query("SELECT * FROM friends WHERE status = 'FRIEND' ORDER BY name ASC")
    fun getAllFriends(): Flow<List<Friend>>

    @Query("SELECT * FROM friends WHERE status = 'PENDING_INCOMING' ORDER BY createdAt DESC")
    fun getPendingRequests(): Flow<List<Friend>>

    @Query("SELECT * FROM friends WHERE status = 'SUGGESTION' ORDER BY name ASC")
    fun getSuggestedFriends(): Flow<List<Friend>>

    @Query("SELECT * FROM friends WHERE status = 'BLOCKED' ORDER BY name ASC")
    fun getBlockedFriends(): Flow<List<Friend>>

    @Query("SELECT COUNT(*) FROM friends WHERE status = 'PENDING_INCOMING'")
    fun getPendingRequestsCount(): Flow<Int>

    @Query("SELECT * FROM friends WHERE id = :id LIMIT 1")
    suspend fun getFriendById(id: Long): Friend?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: Friend): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriends(friends: List<Friend>)

    @Update
    suspend fun updateFriend(friend: Friend)

    @Delete
    suspend fun deleteFriend(friend: Friend)

    @Query("UPDATE friends SET status = :status WHERE id = :id")
    suspend fun updateFriendStatus(id: Long, status: String)

    @Query("SELECT COUNT(*) FROM friends")
    suspend fun countAll(): Int
}
