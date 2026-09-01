package com.ayaka7452.daymate.data.repo

import com.ayaka7452.daymate.data.db.FolderDao
import com.ayaka7452.daymate.data.db.FolderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

class FolderRepository(
    private val dao: FolderDao,
    private val onChanged: () -> Unit = {}
) {

    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun <T> Flow<List<T>>.reEmitOnChange(): Flow<List<T>> =
        merge(this, refreshSignal.map { this@reEmitOnChange.first() })

    fun observeAll(): Flow<List<FolderEntity>> = dao.observeAll().reEmitOnChange()

    fun observeBin(): Flow<List<FolderEntity>> = dao.observeBin().reEmitOnChange()

    suspend fun add(folder: FolderEntity): Long =
        dao.insert(folder).also { onChanged(); refreshSignal.tryEmit(Unit) }

    suspend fun update(folder: FolderEntity) =
        dao.update(folder).also { onChanged(); refreshSignal.tryEmit(Unit) }

    suspend fun delete(folder: FolderEntity) =
        dao.delete(folder).also { onChanged(); refreshSignal.tryEmit(Unit) }

    suspend fun getById(id: Long): FolderEntity? = dao.getById(id)

    suspend fun deleteByIds(ids: List<Long>) =
        dao.deleteByIds(ids).also { onChanged(); refreshSignal.tryEmit(Unit) }

    suspend fun softDeleteByIds(ids: List<Long>, ts: Long) =
        dao.softDeleteByIds(ids, ts).also { onChanged(); refreshSignal.tryEmit(Unit) }

    suspend fun restoreByIds(ids: List<Long>) =
        dao.restoreByIds(ids).also { onChanged(); refreshSignal.tryEmit(Unit) }

    suspend fun countAll(): Int = dao.countAll()
}
