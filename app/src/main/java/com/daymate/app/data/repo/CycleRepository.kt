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
     * 传入记录会先按首日降序排列再计算。
     */
    fun averageCycleDays(logs: List<CycleLogEntity>): Int? =
        CycleCalculator.averageCycleDays(logs.map { it.startDateEpochDay }.sortedDescending())
}
