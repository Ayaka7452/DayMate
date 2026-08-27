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

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: Long): EventEntity?

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

    @Query("DELETE FROM folders WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface VaultEventDao {

    @Query("SELECT * FROM vault_events ORDER BY isPinned DESC, sortIndex ASC")
    fun observeAll(): Flow<List<VaultEventEntity>>

    @Query("SELECT * FROM vault_events WHERE folderId IS NULL ORDER BY isPinned DESC, sortIndex ASC")
    fun observeRoot(): Flow<List<VaultEventEntity>>

    @Query("SELECT * FROM vault_events WHERE folderId = :folderId ORDER BY isPinned DESC, sortIndex ASC")
    fun observeByFolder(folderId: Long): Flow<List<VaultEventEntity>>

    @Query("SELECT * FROM vault_events WHERE id = :id")
    suspend fun getById(id: Long): VaultEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: VaultEventEntity): Long

    @Update
    suspend fun update(event: VaultEventEntity)

    @Delete
    suspend fun delete(event: VaultEventEntity)

    @Query("DELETE FROM vault_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE vault_events SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun moveToFolder(ids: List<Long>, folderId: Long?)
}

@Dao
interface VaultFolderDao {

    @Query("SELECT * FROM vault_folders ORDER BY isPinned DESC, sortIndex ASC")
    fun observeAll(): Flow<List<VaultFolderEntity>>

    @Query("SELECT * FROM vault_folders WHERE id = :id")
    suspend fun getById(id: Long): VaultFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: VaultFolderEntity): Long

    @Update
    suspend fun update(folder: VaultFolderEntity)

    @Delete
    suspend fun delete(folder: VaultFolderEntity)

    @Query("DELETE FROM vault_folders WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
