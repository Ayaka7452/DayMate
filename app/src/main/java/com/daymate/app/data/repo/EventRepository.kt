package com.ayaka7452.daymate.data.repo

import com.ayaka7452.daymate.data.db.EventDao
import com.ayaka7452.daymate.data.db.EventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    suspend fun unparentByFolders(folderIds: List<Long>) =
        dao.unparentByFolders(folderIds).also { onChanged(); refreshSignal.tryEmit(Unit) }

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

    /**
     * 循环事件自动锚定：把目标日期已过的循环事件（WEEKLY/MONTHLY/YEARLY）滚动到
     * 下一周期的同位日期。返回滚动的事件数；有滚动时通知界面与小组件刷新。
     * 在应用启动、跨天午夜刷新等时机调用；重复调用安全（已滚动的日期不再是「已过」）。
     */
    suspend fun rollForwardRepeating(): Int {
        val todayEpochDay = java.time.LocalDate.now().toEpochDay()
        val stale = dao.getRepeatingPast(todayEpochDay)
        if (stale.isEmpty()) return 0
        var count = 0
        for (e in stale) {
            val next = com.ayaka7452.daymate.core.util.CountdownCalculator
                .nextOccurrence(e.targetDateEpochDay, e.repeatRule) ?: continue
            dao.update(
                e.copy(
                    targetDateEpochDay = next.toEpochDay(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            count++
        }
        if (count > 0) {
            onChanged()
            refreshSignal.tryEmit(Unit)
        }
        return count
    }
}
