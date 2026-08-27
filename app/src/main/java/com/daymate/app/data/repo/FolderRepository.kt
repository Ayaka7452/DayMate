package com.ayaka7452.daymate.data.repo

import com.ayaka7452.daymate.data.db.FolderDao
import com.ayaka7452.daymate.data.db.FolderEntity
import kotlinx.coroutines.flow.Flow

class FolderRepository(private val dao: FolderDao) {

    fun observeAll(): Flow<List<FolderEntity>> = dao.observeAll()

    suspend fun add(folder: FolderEntity): Long = dao.insert(folder)

    suspend fun update(folder: FolderEntity) = dao.update(folder)

    suspend fun delete(folder: FolderEntity) = dao.delete(folder)

    suspend fun getById(id: Long): FolderEntity? = dao.getById(id)

    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids)
}
