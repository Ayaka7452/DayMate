package com.ayaka7452.daymate.core

import com.ayaka7452.daymate.core.util.CycleCalculator
import com.ayaka7452.daymate.data.db.EventEntity
import com.ayaka7452.daymate.data.repo.CycleRepository
import com.ayaka7452.daymate.data.repo.EventRepository
import com.ayaka7452.daymate.data.repo.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * 周期管家 ⇄ 主表事件桥接：
 *  - 开关开启：创建/更新 specialType="cycle" 的快捷事件（普通事件一样可移动/放文件夹/置顶）
 *  - 日期自动跟随预测：始终递推到未来的一次预测经期首日（逾期未登记时继续按周期外推）
 *  - 开关关闭：把关联事件移入回收站（软删），登记数据保留
 *
 * 点击行为分流在入口处：EventRow 点击时 specialType == "cycle" 直接打开周期管家页。
 */
class CycleEventBridge(
    private val eventRepository: EventRepository,
    private val cycleRepository: CycleRepository,
    private val settingsRepository: SettingsRepository
) {

    /** 基于最近一次登记与生效周期设置（自动均值优先，回落手动值），递推出今天及之后的下一次预测首日。 */
    suspend fun nextPredictedStart(): Long? {
        val logs = cycleRepository.getAll()
        // 特殊情况记录（带备注的单日出血标记）不参与预测锚定
        val realLogs = logs.filter { it.note == null }
        if (realLogs.isEmpty()) return null
        val lastStart = realLogs.maxOf { it.startDateEpochDay }
        val cycleDays = cycleRepository.effectiveCycleDays(
            realLogs,
            settingsRepository.cycleDays.first(),
            settingsRepository.cycleCycleAuto.first()
        )
        var next = CycleCalculator.nextStartAfter(lastStart, cycleDays)
        val today = java.time.LocalDate.now().toEpochDay()
        // 逾期未登记时按周期外推，保证快捷事件始终指向未来的一次预测
        while (next < today) next += cycleDays
        return next
    }

    /**
     * 同步快捷事件：开启则创建/更新（含日期滚动），关闭则软删。
     * 返回同步后的事件 id（关闭或无法计算时返回 -1）。
     */
    suspend fun syncEvent(): Long {
        val enabled = settingsRepository.cycleEventEnabled.first()
        val linkedId = settingsRepository.cycleEventId.first()
        if (!enabled) {
            if (linkedId > 0) {
                eventRepository.getById(linkedId)?.takeIf { !it.isDeleted }?.let { e ->
                    eventRepository.softDeleteByIds(listOf(e.id), System.currentTimeMillis())
                }
            }
            if (linkedId != -1L) settingsRepository.setCycleEventId(-1L)
            return -1L
        }
        val nextStart = nextPredictedStart() ?: return -1L
        val title = settingsRepository.cycleEventTitle.first().ifBlank { "周期管家" }
        val existing = linkedId.takeIf { it > 0 }?.let { eventRepository.getById(it) }
        if (existing != null && existing.isDeleted) {
            // 用户手动把快捷事件删进了回收站 → 尊重意图，自动关闭开关，不再重建
            settingsRepository.setCycleEventEnabled(false)
            settingsRepository.setCycleEventId(-1L)
            return -1L
        }
        return if (existing != null) {
            eventRepository.update(
                existing.copy(
                    title = title,
                    targetDateEpochDay = nextStart,
                    repeatRule = null,
                    linkedFestival = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
            existing.id
        } else {
            val id = eventRepository.add(
                EventEntity(
                    title = title,
                    targetDateEpochDay = nextStart,
                    specialType = SPECIAL_TYPE_CYCLE
                )
            )
            settingsRepository.setCycleEventId(id)
            id
        }
    }

    companion object {
        const val SPECIAL_TYPE_CYCLE = "cycle"
    }
}
