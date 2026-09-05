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
     * 自动锚定：把目标日期已过的事件滚动到下一次日期。返回滚动的事件数。
     *  - repeatRule（WEEKLY/MONTHLY/YEARLY）→ 按周期同位日期滚动；
     *  - linkedFestival（跟随节日）→ 按节假日数据源中该节日的下一次日期滚动（优先于 repeatRule）。
     * 在应用启动、跨天午夜刷新、节假日数据下载成功后调用；重复调用安全。
     * festivalRepo 传入 null 或缓存无数据时，节日跟随事件保持原日期不动。
     */
    suspend fun rollForwardRepeating(
        festivalRepo: com.ayaka7452.daymate.data.festival.FestivalRepository? = null
    ): Int {
        val today = java.time.LocalDate.now()
        val todayEpochDay = today.toEpochDay()
        var count = 0
        // 1) 周期循环（linkedFestival 存在时跳过——节日跟随优先）
        for (e in dao.getRepeatingPast(todayEpochDay)) {
            if (!e.linkedFestival.isNullOrBlank()) continue
            val next = com.ayaka7452.daymate.core.util.CountdownCalculator
                .nextOccurrence(e.targetDateEpochDay, e.repeatRule) ?: continue
            dao.update(
                e.copy(targetDateEpochDay = next.toEpochDay(), updatedAt = System.currentTimeMillis())
            )
            count++
        }
        // 2) 跟随节日
        if (festivalRepo != null) {
            for (e in dao.getFestivalLinkedPast(todayEpochDay)) {
                val name = e.linkedFestival ?: continue
                val next = festivalRepo.nextOccurrenceOf(name, today) ?: continue
                if (next.toEpochDay() != e.targetDateEpochDay) {
                    dao.update(
                        e.copy(
                            targetDateEpochDay = next.toEpochDay(),
                            repeatRule = null,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    count++
                }
            }
        }
        if (count > 0) {
            onChanged()
            refreshSignal.tryEmit(Unit)
        }
        return count
    }

    /**
     * 校正「预估日期」：快选节日时若数据源尚未发布新年份（如农历春节 2027），
     * 会以「上次日期 + 1 年」预估。下载到新数据后调用本方法，把目标日期与任何真实
     * 节日日期都对不上的事件（即预估产物）修正为数据中该节日的下一次日期。
     * 用户手动设到真实节日日期上的事件不会被改动。返回校正的事件数。
     */
    suspend fun reanchorFestivalEstimates(
        festivalRepo: com.ayaka7452.daymate.data.festival.FestivalRepository
    ): Int {
        val today = java.time.LocalDate.now()
        var count = 0
        for (e in dao.getFestivalLinkedAll()) {
            val name = e.linkedFestival ?: continue
            val next = festivalRepo.nextOccurrenceOf(name, today) ?: continue
            if (next.toEpochDay() == e.targetDateEpochDay) continue
            val matchesRealOccurrence = festivalRepo.occurrencesOf(name)
                .any { it.toEpochDay() == e.targetDateEpochDay }
            if (!matchesRealOccurrence) {
                dao.update(
                    e.copy(
                        targetDateEpochDay = next.toEpochDay(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                count++
            }
        }
        if (count > 0) {
            onChanged()
            refreshSignal.tryEmit(Unit)
        }
        return count
    }
}
