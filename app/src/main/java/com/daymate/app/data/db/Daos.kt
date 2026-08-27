package com.daymate.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Query("SELECT * FROM events ORDER BY isPinned DESC, sortIndex ASC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE folderId IS NULL ORDER BY isPinned DESC, sortIndex ASC")
    fun observeRoot(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE folderId = :folderId ORDER BY isPinned DESC, sortIndex ASC")
    fun observeByFolder(folderId: Long): Flow<List<EventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("DELETE FROM events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE events SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun moveToFolder(ids: List<Long>, folderId: Long?)

    @Query("SELECT MAX(sortIndex) FROM events")
    suspend fun maxSortIndex(): Int?
}

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY isPinned DESC, sortIndex ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)
}

@Dao
interface VaultEventDao {

    @Query("SELECT * FROM vault_events ORDER BY isPinned DESC, sortIndex ASC")
    fun observeAll(): Flow<List<VaultEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: VaultEventEntity): Long

    @Update
    suspend fun update(event: VaultEventEntity)

    @Delete
    suspend fun delete(event: VaultEventEntity)
}
