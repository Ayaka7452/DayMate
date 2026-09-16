package com.ayaka7452.daymate.data.repo

import com.ayaka7452.daymate.data.db.CycleNoteDao
import com.ayaka7452.daymate.data.db.CycleNoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * 周期管家：日常记录（症状 / 情绪 / 性生活 / 自定义）的增删查。
 *
 * 与 [CycleRepository] 分开：本仓库的数据**不参与周期与排卵推算**，
 * 因此这里刻意不提供任何 effectiveXxx / averageXxx 之类的推算入口，
 * 从类型层面杜绝「顺手把日常记录喂进周期均值」这类错误。
 *
 * 数据同样是敏感信息——与经期记录同库，导出备份会一并带走（与用户预期一致）。
 */
class CycleNoteRepository(
    private val dao: CycleNoteDao,
    private val onChanged: () -> Unit
) {

    fun observeAll(): Flow<List<CycleNoteEntity>> = dao.observeAll()

    suspend fun getAll(): List<CycleNoteEntity> = dao.getAll()

    suspend fun getByDay(day: Long): List<CycleNoteEntity> = dao.getByDay(day)

    /** 批量新增（一次弹窗里可勾选多个预置项）。空列表直接返回，不触发备份与小组件刷新。 */
    suspend fun addAll(notes: List<CycleNoteEntity>) {
        if (notes.isEmpty()) return
        dao.insertAll(notes)
        onChanged()
    }

    suspend fun update(note: CycleNoteEntity) =
        dao.update(note).also { onChanged() }

    suspend fun delete(note: CycleNoteEntity) =
        dao.delete(note).also { onChanged() }

    /** 按 id 批量删除（删除弹窗勾选后执行）。空列表直接返回。 */
    suspend fun deleteByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        dao.deleteByIds(ids)
        onChanged()
    }
}
