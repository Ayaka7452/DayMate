package com.ayaka7452.daymate.data.repo

import com.ayaka7452.daymate.data.db.VaultFolderDao
import com.ayaka7452.daymate.data.db.VaultFolderEntity
import kotlinx.coroutines.flow.Flow

class VaultFolderRepository(private val dao: VaultFolderDao) {

    fun observeAll(): Flow<List<VaultFolderEntity>> = dao.observeAll()

    suspend fun getById(id: Long): VaultFolderEntity? = dao.getById(id)

    suspend fun add(folder: VaultFolderEntity): Long = dao.insert(folder)

    suspend fun update(folder: VaultFolderEntity) = dao.update(folder)

    suspend fun delete(folder: VaultFolderEntity) = dao.delete(folder)

    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids)
}
