package com.ayaka7452.daymate.data.repo

import com.ayaka7452.daymate.core.security.VaultCrypto
import com.ayaka7452.daymate.core.security.VaultSession
import com.ayaka7452.daymate.data.db.VaultEventDao
import com.ayaka7452.daymate.data.db.VaultEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Vault 事件仓库。对外暴露**明文**实体；写入前用 [VaultSession] 中的密钥加密敏感字段，
 * 读出后解密。DAO 层（即 SQLite）只存密文，主数据空间不受影响。
 */
class VaultRepository(
    private val dao: VaultEventDao,
    private val onChanged: () -> Unit = {}
) {

    private fun decrypt(e: VaultEventEntity): VaultEventEntity {
        val k = VaultSession.key ?: return e
        return e.copy(
            title = VaultCrypto.decrypt(e.title, k) ?: e.title,
            note = VaultCrypto.decrypt(e.note, k) ?: e.note
        )
    }

    private fun encrypt(e: VaultEventEntity): VaultEventEntity {
        val k = VaultSession.key ?: return e
        return e.copy(
            title = VaultCrypto.encrypt(e.title, k) ?: e.title,
            note = VaultCrypto.encrypt(e.note, k) ?: e.note
        )
    }

    fun observeAll(): Flow<List<VaultEventEntity>> = dao.observeAll().map { it.map(::decrypt) }

    fun observeRoot(): Flow<List<VaultEventEntity>> = dao.observeRoot().map { it.map(::decrypt) }

    fun observeByFolder(folderId: Long): Flow<List<VaultEventEntity>> =
        dao.observeByFolder(folderId).map { it.map(::decrypt) }

    suspend fun getById(id: Long): VaultEventEntity? = dao.getById(id)?.let(::decrypt)

    suspend fun add(event: VaultEventEntity): Long = dao.insert(encrypt(event)).also { onChanged() }

    suspend fun addAll(events: List<VaultEventEntity>) {
        for (e in events) dao.insert(encrypt(e))
        onChanged()
    }

    suspend fun update(event: VaultEventEntity) = dao.update(encrypt(event)).also { onChanged() }

    suspend fun delete(event: VaultEventEntity) = dao.delete(event).also { onChanged() }

    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids).also { onChanged() }

    /** 清空整个 Vault 事件表（重置密码时调用）。 */
    suspend fun clearAll() = dao.clearAll().also { onChanged() }

    suspend fun moveToFolder(ids: List<Long>, folderId: Long?) = dao.moveToFolder(ids, folderId).also { onChanged() }
}
