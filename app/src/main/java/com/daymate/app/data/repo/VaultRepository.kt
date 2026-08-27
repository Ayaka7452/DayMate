package com.daymate.app.data.repo

import com.daymate.app.data.db.VaultEventDao
import com.daymate.app.data.db.VaultEventEntity
import kotlinx.coroutines.flow.Flow

class VaultRepository(private val dao: VaultEventDao) {

    fun observeAll(): Flow<List<VaultEventEntity>> = dao.observeAll()

    fun observeRoot(): Flow<List<VaultEventEntity>> = dao.observeRoot()

    fun observeByFolder(folderId: Long): Flow<List<VaultEventEntity>> = dao.observeByFolder(folderId)

    suspend fun getById(id: Long): VaultEventEntity? = dao.getById(id)

    suspend fun add(event: VaultEventEntity): Long = dao.insert(event)

    suspend fun update(event: VaultEventEntity) = dao.update(event)

    suspend fun delete(event: VaultEventEntity) = dao.delete(event)

    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids)

    suspend fun moveToFolder(ids: List<Long>, folderId: Long?) = dao.moveToFolder(ids, folderId)
}
