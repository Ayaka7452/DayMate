package com.ayaka7452.daymate.data.repo

import com.ayaka7452.daymate.data.db.FolderDao
import com.ayaka7452.daymate.data.db.FolderEntity
import kotlinx.coroutines.flow.Flow

class FolderRepository(
    private val dao: FolderDao,
    private val onChanged: () -> Unit = {}
) {

    fun observeAll(): Flow<List<FolderEntity>> = dao.observeAll()

    fun observeBin(): Flow<List<FolderEntity>> = dao.observeBin()

    suspend fun add(folder: FolderEntity): Long = dao.insert(folder).also { onChanged() }

    suspend fun update(folder: FolderEntity) = dao.update(folder).also { onChanged() }

    suspend fun delete(folder: FolderEntity) = dao.delete(folder).also { onChanged() }

    suspend fun getById(id: Long): FolderEntity? = dao.getById(id)

    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids).also { onChanged() }

    suspend fun softDeleteByIds(ids: List<Long>, ts: Long) = dao.softDeleteByIds(ids, ts).also { onChanged() }

    suspend fun restoreByIds(ids: List<Long>) = dao.restoreByIds(ids).also { onChanged() }
}
