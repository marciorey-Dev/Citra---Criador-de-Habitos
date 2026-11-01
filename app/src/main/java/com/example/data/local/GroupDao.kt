package com.example.data.local

import androidx.room.*
import com.example.data.model.CitraGroup
import com.example.data.model.GroupMember
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups WHERE status = 'ACTIVE' ORDER BY createdAt DESC")
    fun getAllGroups(): Flow<List<CitraGroup>>

    @Query("SELECT * FROM groups WHERE status = 'INVITED' ORDER BY createdAt DESC")
    fun getPendingGroupInvites(): Flow<List<CitraGroup>>

    @Query("SELECT * FROM groups WHERE id = :id LIMIT 1")
    suspend fun getGroupById(id: Long): CitraGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: CitraGroup): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<CitraGroup>)

    @Update
    suspend fun updateGroup(group: CitraGroup)

    @Delete
    suspend fun deleteGroup(group: CitraGroup)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun deleteGroupById(id: Long)

    // Group Members
    @Query("SELECT * FROM group_members WHERE groupId = :groupId ORDER BY isAdmin DESC, name ASC")
    fun getMembersForGroup(groupId: Long): Flow<List<GroupMember>>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    suspend fun getMembersForGroupSync(groupId: Long): List<GroupMember>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: GroupMember): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<GroupMember>)

    @Delete
    suspend fun deleteMember(member: GroupMember)

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND (friendId = :friendId OR id = :memberId)")
    suspend fun removeMember(groupId: Long, friendId: Long, memberId: Long)

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun deleteAllMembersForGroup(groupId: Long)

    @Query("SELECT COUNT(*) FROM groups")
    suspend fun countAll(): Int
}
