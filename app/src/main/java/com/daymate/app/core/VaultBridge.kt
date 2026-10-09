package com.ayaka7452.daymate.core

import com.ayaka7452.daymate.core.log.AppLogger
import com.ayaka7452.daymate.core.security.VaultSession
import com.ayaka7452.daymate.data.db.EventEntity
import com.ayaka7452.daymate.data.db.VaultEventEntity
import com.ayaka7452.daymate.data.repo.EventRepository
import com.ayaka7452.daymate.data.repo.VaultRepository

/**
 * 主空间事件 ↔ Vault 事件的搬运。两张表同在主库 daymate.db，但语义隔离
 * （Vault 表的标题/备注为密文），故以「目标表新建 + 源表删除」实现。
 * folderId 落地为 null（进入对方根目录），其余字段原样保留。
 *
 * ⚠️ **两个方向都必须在已解锁时才能搬**（2026-10-09 修）：
 *  - 移入：未解锁时 [VaultRepository.encrypt] 会走 `VaultSession.key ?: return e` 兜底，
 *    把**明文**标题/备注写进 Vault 表，并随每一次备份（含 WebDAV 云端）上传——之后再解锁
 *    看到的仍是明文，隐私彻底失效。
 *  - 移出：未解锁时 `getById` 返回的是**密文**实体，搬进主空间就成了乱码。
 *  故此处统一拦在 Bridge 层，调用方拿 `false` 后应提示用户先解锁。
 */
class VaultBridge(
    private val eventRepository: EventRepository,
    private val vaultRepository: VaultRepository
) {
    suspend fun moveEventToVault(eventId: Long): Boolean {
        if (VaultSession.key == null) {
            AppLogger.log("Vault", "移入 Vault 中止：保险箱未解锁，事件保持原样 id=$eventId")
            return false
        }
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
                endMinuteOfDay = e.endMinuteOfDay,
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
        // 未解锁时 getById 返回的是密文实体，搬进主空间会变成乱码
        if (VaultSession.key == null) {
            AppLogger.log("Vault", "移出 Vault 中止：保险箱未解锁，事件保持原样 id=$vaultEventId")
            return false
        }
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
                endMinuteOfDay = v.endMinuteOfDay,
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
