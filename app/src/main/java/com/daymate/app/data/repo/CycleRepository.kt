package com.ayaka7452.daymate.data.repo

import com.ayaka7452.daymate.core.util.CycleCalculator
import com.ayaka7452.daymate.data.db.CycleLogDao
import com.ayaka7452.daymate.data.db.CycleLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * 周期管家：经期登记记录的增删改查。
 * 记录本身即敏感数据——表放在主库 events 同库，导出备份会一并带走（与用户预期一致）；
 * 页面入口的隐私由「周期管家密码开关 + FLAG_SECURE」负责。
 */
class CycleRepository(
    private val dao: CycleLogDao,
    private val onChanged: () -> Unit
) {

    fun observeAll(): Flow<List<CycleLogEntity>> = dao.observeAll()

    suspend fun getById(id: Long): CycleLogEntity? = dao.getById(id)

    suspend fun getAll(): List<CycleLogEntity> = dao.getAll()

    suspend fun add(log: CycleLogEntity): Long =
        dao.insert(log).also { onChanged() }

    suspend fun update(log: CycleLogEntity) =
        dao.update(log.copy(updatedAt = System.currentTimeMillis())).also { onChanged() }

    suspend fun delete(log: CycleLogEntity) =
        dao.delete(log).also { onChanged() }

    /**
     * 近 3 次实测周期均值（天）；不足 2 次记录返回 null。
     * 特殊情况记录（带备注的单日出血标记）不参与推算，只做日历标记。
     */
    fun averageCycleDays(logs: List<CycleLogEntity>): Int? =
        CycleCalculator.averageCycleDays(
            logs.filter { it.note == null }.map { it.startDateEpochDay }.sortedDescending()
        )

    /** 近 3 次记录的经期持续天数均值（天）；不足 2 条记录视为数据不足，返回 null。特殊记录不参与。 */
    fun averagePeriodDays(logs: List<CycleLogEntity>): Int? =
        CycleCalculator.averagePeriodDays(logs.filter { it.note == null }.map { it.periodDays })

    /**
     * 生效周期天数：自动开启且能算出均值（≥2 条有效记录）时用近 3 次均值，
     * 否则回落到手动设置值（数据不足时的种子）。
     */
    fun effectiveCycleDays(logs: List<CycleLogEntity>, manual: Int, auto: Boolean): Int =
        if (auto) averageCycleDays(logs) ?: manual else manual

    /** 生效经期天数：自动开启且能算出均值（≥2 条记录）时用近 3 次均值，否则用手动设置值。 */
    fun effectivePeriodDays(logs: List<CycleLogEntity>, manual: Int, auto: Boolean): Int =
        if (auto) averagePeriodDays(logs) ?: manual else manual
}
