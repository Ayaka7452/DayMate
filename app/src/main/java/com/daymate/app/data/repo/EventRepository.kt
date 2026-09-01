package com.ayaka7452.daymate.data.repo

import com.ayaka7452.daymate.data.db.EventDao
import com.ayaka7452.daymate.data.db.EventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge

class EventRepository(
    private val dao: EventDao,
    private val onChanged: () -> Unit = {}
) {

    /**
     * 刷新信号：每次写入（含软删除/恢复/移动/增删改）后发射，使观察 Flow 强制重新查询一次。
     * 用以兜底 Room 在原地（不重建界面）写库时、失效通知可能未及时到达 Collector 的边界情况，
     * 确保「移入回收站」等原地操作后，已在屏幕上的列表能可靠刷新。
     */
    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 把 Room 流与刷新信号合并：Room 流负责初始加载，刷新信号负责每次写库后强制重新查询。 */
    private fun <T> Flow<List<T>>.reEmitOnChange(): Flow<List<T>> =
        merge(this, refreshSignal.map { this@reEmitOnChange.first() })

    fun observeAll(): Flow<List<EventEntity>> = dao.observeAll().reEmitOnChange()

    fun observeRoot(): Flow<List<EventEntity>> = dao.observeRoot().reEmitOnChange()

    fun observeByFolder(folderId: Long): Flow<List<EventEntity>> =
        dao.observeByFolder(folderId).reEmitOnChange()

    fun observeBin(): Flow<List<EventEntity>> = dao.observeBin().reEmitOnChange()

    suspend fun getById(id: Long): EventEntity? = dao.getById(id)

    suspend fun add(event: EventEntity): Long =
        dao.insert(event).also { onChanged(); refreshSignal.tryEmit(Unit) }

    suspend fun update(event: EventEntity) =
        dao.update(event).also { onChanged(); refreshSignal.tryEmit(Unit) }

    suspend fun delete(event: EventEntity) =
        dao.delete(event).also { onChanged(); refreshSignal.tryEmit(Unit) }

    suspend fun deleteByIds(ids: List<Long>) =
        dao.deleteByIds(ids).also { onChanged(); refreshSignal.tryEmit(Unit) }

    suspend fun countAll(): Int = dao.countAll()

    suspend fun softDeleteByIds(ids: List<Long>, ts: Long) =
        dao.softDeleteByIds(ids, ts).also { onChanged(); refreshSignal.tryEmit(Unit) }

    suspend fun restoreByIds(ids: List<Long>) =
        dao.restoreByIds(ids).also { onChanged(); refreshSignal.tryEmit(Unit) }

    suspend fun softDeleteByFolders(folderIds: List<Long>, ts: Long) {
        dao.softDeleteByFolders(folderIds, ts)
        onChanged()
        refreshSignal.tryEmit(Unit)
    }

    suspend fun restoreByFolders(folderIds: List<Long>) =
        dao.restoreByFolders(folderIds).also { onChanged(); refreshSignal.tryEmit(Unit) }

    suspend fun moveToFolder(ids: List<Long>, folderId: Long?) =
        dao.moveToFolder(ids, folderId).also { onChanged(); refreshSignal.tryEmit(Unit) }

    suspend fun hardDeleteEventsByFolders(folderIds: List<Long>) {
        dao.hardDeleteEventsByFolders(folderIds)
        onChanged()
        refreshSignal.tryEmit(Unit)
    }

    suspend fun nextSortIndex(): Int = (dao.maxSortIndex() ?: -1) + 1
}
