package com.ayaka7452.daymate.data.repo

import com.ayaka7452.daymate.data.db.EventDao
import com.ayaka7452.daymate.data.db.EventEntity
import kotlinx.coroutines.flow.Flow

class EventRepository(private val dao: EventDao) {

    fun observeAll(): Flow<List<EventEntity>> = dao.observeAll()

    fun observeRoot(): Flow<List<EventEntity>> = dao.observeRoot()

    fun observeByFolder(folderId: Long): Flow<List<EventEntity>> = dao.observeByFolder(folderId)

    fun observeBin(): Flow<List<EventEntity>> = dao.observeBin()

    suspend fun getById(id: Long): EventEntity? = dao.getById(id)

    suspend fun add(event: EventEntity): Long = dao.insert(event)

    suspend fun update(event: EventEntity) = dao.update(event)

    suspend fun delete(event: EventEntity) = dao.delete(event)

    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids)

    suspend fun softDeleteByIds(ids: List<Long>, ts: Long) = dao.softDeleteByIds(ids, ts)

    suspend fun restoreByIds(ids: List<Long>) = dao.restoreByIds(ids)

    suspend fun softDeleteByFolders(folderIds: List<Long>, ts: Long) =
        dao.softDeleteByFolders(folderIds, ts)

    suspend fun restoreByFolders(folderIds: List<Long>) = dao.restoreByFolders(folderIds)

    suspend fun moveToFolder(ids: List<Long>, folderId: Long?) = dao.moveToFolder(ids, folderId)

    suspend fun hardDeleteEventsByFolders(folderIds: List<Long>) =
        dao.hardDeleteEventsByFolders(folderIds)

    suspend fun nextSortIndex(): Int = (dao.maxSortIndex() ?: -1) + 1
}
