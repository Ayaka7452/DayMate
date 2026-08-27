package com.daymate.app.data.repo

import com.daymate.app.data.db.EventDao
import com.daymate.app.data.db.EventEntity
import kotlinx.coroutines.flow.Flow

class EventRepository(private val dao: EventDao) {

    fun observeAll(): Flow<List<EventEntity>> = dao.observeAll()

    fun observeRoot(): Flow<List<EventEntity>> = dao.observeRoot()

    fun observeByFolder(folderId: Long): Flow<List<EventEntity>> = dao.observeByFolder(folderId)

    suspend fun add(event: EventEntity): Long = dao.insert(event)

    suspend fun update(event: EventEntity) = dao.update(event)

    suspend fun delete(event: EventEntity) = dao.delete(event)

    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids)

    suspend fun moveToFolder(ids: List<Long>, folderId: Long?) = dao.moveToFolder(ids, folderId)

    suspend fun nextSortIndex(): Int = (dao.maxSortIndex() ?: -1) + 1
}
