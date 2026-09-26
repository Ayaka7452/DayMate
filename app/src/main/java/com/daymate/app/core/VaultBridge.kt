package com.ayaka7452.daymate.core

import com.ayaka7452.daymate.core.log.AppLogger
import com.ayaka7452.daymate.data.db.EventEntity
import com.ayaka7452.daymate.data.db.VaultEventEntity
import com.ayaka7452.daymate.data.repo.EventRepository
import com.ayaka7452.daymate.data.repo.VaultRepository

/**
 * 主空间事件 ↔ Vault 事件的搬运。两张表同在主库 daymate.db，但语义隔离
 * （Vault 表的标题/备注为密文），故以「目标表新建 + 源表删除」实现。
 * folderId 落地为 null（进入对方根目录），其余字段原样保留。
 */
class VaultBridge(
    private val eventRepository: EventRepository,
    private val vaultRepository: VaultRepository
) {
    suspend fun moveEventToVault(eventId: Long): Boolean {
        val e = eventRepository.getById(eventId) ?: run {
            AppLogger.log("Vault", "移入 Vault 失败：事件不存在 id=$eventId")
            return false
        }
        vaultRepository.add(
            VaultEventEntity(
                title = e.title,
                targetDateEpochDay = e.targetDateEpochDay,
                note = e.note,
                color = e.color,
                refDays = e.refDays,
                displayUnit = e.displayUnit,
                repeatRule = e.repeatRule,
                linkedFestival = e.linkedFestival,
                folderId = null,
                sortIndex = 0,
                isPinned = false,
                createdAt = e.createdAt,
                updatedAt = System.currentTimeMillis()
            )
        )
        eventRepository.delete(e)
        AppLogger.log("Vault", "事件移入 Vault id=$eventId")
        return true
    }

    suspend fun moveVaultEventToMain(vaultEventId: Long): Boolean {
        val v = vaultRepository.getById(vaultEventId) ?: run {
            AppLogger.log("Vault", "移出 Vault 失败：事件不存在 id=$vaultEventId")
            return false
        }
        eventRepository.add(
            EventEntity(
                title = v.title,
                targetDateEpochDay = v.targetDateEpochDay,
                note = v.note,
                color = v.color,
                refDays = v.refDays,
                displayUnit = v.displayUnit,
                repeatRule = v.repeatRule,
                linkedFestival = v.linkedFestival,
                folderId = null,
                sortIndex = 0,
                isPinned = false,
                createdAt = v.createdAt,
                updatedAt = System.currentTimeMillis()
            )
        )
        vaultRepository.delete(v)
        AppLogger.log("Vault", "事件移出 Vault id=$vaultEventId")
        return true
    }
}
