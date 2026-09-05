package com.ayaka7452.daymate.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Query("SELECT * FROM events WHERE isDeleted = 0 ORDER BY isPinned DESC, sortIndex ASC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE folderId IS NULL AND isDeleted = 0 ORDER BY isPinned DESC, sortIndex ASC")
    fun observeRoot(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE folderId = :folderId AND isDeleted = 0 ORDER BY isPinned DESC, sortIndex ASC")
    fun observeByFolder(folderId: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun observeBin(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: Long): EventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("UPDATE events SET isDeleted = 1, deletedAt = :ts WHERE id IN (:ids)")
    suspend fun softDeleteByIds(ids: List<Long>, ts: Long)

    @Query("UPDATE events SET isDeleted = 0, deletedAt = 0 WHERE id IN (:ids)")
    suspend fun restoreByIds(ids: List<Long>)

    @Query("UPDATE events SET folderId = NULL WHERE folderId IN (:folderIds) AND isDeleted = 0")
    suspend fun unparentByFolders(folderIds: List<Long>)

    @Query("UPDATE events SET isDeleted = 0, deletedAt = 0 WHERE folderId IN (:folderIds)")
    suspend fun restoreByFolders(folderIds: List<Long>)

    @Query("DELETE FROM events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE events SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun moveToFolder(ids: List<Long>, folderId: Long?)

    @Query("DELETE FROM events WHERE folderId IN (:folderIds)")
    suspend fun hardDeleteEventsByFolders(folderIds: List<Long>)

    @Query("SELECT MAX(sortIndex) FROM events")
    suspend fun maxSortIndex(): Int?

    @Query("SELECT COUNT(*) FROM events WHERE isDeleted = 0")
    suspend fun countAll(): Int

    /** 循环事件里目标日期已过的（供自动锚定下一周期）。 */
    @Query("SELECT * FROM events WHERE isDeleted = 0 AND repeatRule IS NOT NULL AND targetDateEpochDay < :todayEpochDay")
    suspend fun getRepeatingPast(todayEpochDay: Long): List<EventEntity>

    /** 跟随节日的事件里目标日期已过的（供自动锚定到节日下一次日期）。 */
    @Query("SELECT * FROM events WHERE isDeleted = 0 AND linkedFestival IS NOT NULL AND targetDateEpochDay < :todayEpochDay")
    suspend fun getFestivalLinkedPast(todayEpochDay: Long): List<EventEntity>
}

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders WHERE isDeleted = 0 ORDER BY isPinned DESC, sortIndex ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun observeBin(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: Long): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("UPDATE folders SET isDeleted = 1, deletedAt = :ts WHERE id IN (:ids)")
    suspend fun softDeleteByIds(ids: List<Long>, ts: Long)

    @Query("UPDATE folders SET isDeleted = 0, deletedAt = 0 WHERE id IN (:ids)")
    suspend fun restoreByIds(ids: List<Long>)

    @Query("DELETE FROM folders WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM folders WHERE isDeleted = 0")
    suspend fun countAll(): Int
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

    @Query("SELECT * FROM vault_events")
    suspend fun getAll(): List<VaultEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: VaultEventEntity): Long

    @Update
    suspend fun update(event: VaultEventEntity)

    @Delete
    suspend fun delete(event: VaultEventEntity)

    @Query("DELETE FROM vault_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM vault_events")
    suspend fun clearAll()

    @Query("UPDATE vault_events SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun moveToFolder(ids: List<Long>, folderId: Long?)

    @Query("UPDATE vault_events SET folderId = NULL WHERE folderId IN (:folderIds)")
    suspend fun unparentByFolders(folderIds: List<Long>)

    @Query("SELECT COUNT(*) FROM vault_events")
    suspend fun countAll(): Int
}

@Dao
interface VaultFolderDao {

    @Query("SELECT * FROM vault_folders ORDER BY isPinned DESC, sortIndex ASC")
    fun observeAll(): Flow<List<VaultFolderEntity>>

    @Query("SELECT * FROM vault_folders WHERE id = :id")
    suspend fun getById(id: Long): VaultFolderEntity?

    @Query("SELECT * FROM vault_folders")
    suspend fun getAll(): List<VaultFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: VaultFolderEntity): Long

    @Update
    suspend fun update(folder: VaultFolderEntity)

    @Delete
    suspend fun delete(folder: VaultFolderEntity)

    @Query("DELETE FROM vault_folders WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM vault_folders")
    suspend fun clearAll()
}
