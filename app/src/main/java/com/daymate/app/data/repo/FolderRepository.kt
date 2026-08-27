package com.daymate.app.data.repo

import com.daymate.app.data.db.FolderDao
import com.daymate.app.data.db.FolderEntity
import kotlinx.coroutines.flow.Flow

class FolderRepository(private val dao: FolderDao) {

    fun observeAll(): Flow<List<FolderEntity>> = dao.observeAll()

    suspend fun add(folder: FolderEntity): Long = dao.insert(folder)

    suspend fun update(folder: FolderEntity) = dao.update(folder)

    suspend fun delete(folder: FolderEntity) = dao.delete(folder)
}
