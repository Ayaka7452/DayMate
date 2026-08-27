package com.daymate.app.core

import com.daymate.app.data.db.EventEntity
import com.daymate.app.data.db.VaultEventEntity
import com.daymate.app.data.repo.EventRepository
import com.daymate.app.data.repo.VaultRepository

/**
 * 主空间事件 ↔ Vault 事件的跨库搬运。
 * 两个 Room 库（daymate.db / vault.db）互不可见，故以「目标库新建 + 源库删除」实现。
 * folderId 落地为 null（进入对方根目录），其余字段原样保留。
 */
class VaultBridge(
    private val eventRepository: EventRepository,
    private val vaultRepository: VaultRepository
) {
    suspend fun moveEventToVault(eventId: Long): Boolean {
        val e = eventRepository.getById(eventId) ?: return false
        vaultRepository.add(
            VaultEventEntity(
                title = e.title,
                targetDateEpochDay = e.targetDateEpochDay,
                repeatYearly = e.repeatYearly,
                note = e.note,
                color = e.color,
                folderId = null,
                sortIndex = 0,
                isPinned = false,
                createdAt = e.createdAt,
                updatedAt = System.currentTimeMillis()
            )
        )
        eventRepository.delete(e)
        return true
    }

    suspend fun moveVaultEventToMain(vaultEventId: Long): Boolean {
        val v = vaultRepository.getById(vaultEventId) ?: return false
        eventRepository.add(
            EventEntity(
                title = v.title,
                targetDateEpochDay = v.targetDateEpochDay,
                repeatYearly = v.repeatYearly,
                note = v.note,
                color = v.color,
                folderId = null,
                sortIndex = 0,
                isPinned = false,
                createdAt = v.createdAt,
                updatedAt = System.currentTimeMillis()
            )
        )
        vaultRepository.delete(v)
        return true
    }
}
