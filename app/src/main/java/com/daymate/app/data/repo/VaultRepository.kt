package com.daymate.app.data.repo

import com.daymate.app.data.db.VaultEventDao
import com.daymate.app.data.db.VaultEventEntity
import kotlinx.coroutines.flow.Flow

class VaultRepository(private val dao: VaultEventDao) {

    fun observeAll(): Flow<List<VaultEventEntity>> = dao.observeAll()

    suspend fun add(event: VaultEventEntity): Long = dao.insert(event)

    suspend fun update(event: VaultEventEntity) = dao.update(event)

    suspend fun delete(event: VaultEventEntity) = dao.delete(event)
}
