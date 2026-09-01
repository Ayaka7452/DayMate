package com.ayaka7452.daymate.data.repo

import com.ayaka7452.daymate.core.security.VaultCrypto
import com.ayaka7452.daymate.core.security.VaultSession
import com.ayaka7452.daymate.data.db.VaultEventDao
import com.ayaka7452.daymate.data.db.VaultEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * Vault 事件仓库。对外暴露**明文**实体；写入前用 [VaultSession] 中的密钥加密敏感字段，
 * 读出后解密。DAO 层（即 SQLite）只存密文，主数据空间不受影响。
 */
class VaultRepository(
    private val dao: VaultEventDao,
    private val onChanged: () -> Unit = {}
) {

    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun <T> Flow<List<T>>.reEmitOnChange(): Flow<List<T>> =
        merge(this, refreshSignal.map { this@reEmitOnChange.first() })

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

    fun observeAll(): Flow<List<VaultEventEntity>> =
        dao.observeAll().reEmitOnChange().map { it.map(::decrypt) }

    fun observeRoot(): Flow<List<VaultEventEntity>> =
        dao.observeRoot().reEmitOnChange().map { it.map(::decrypt) }

    fun observeByFolder(folderId: Long): Flow<List<VaultEventEntity>> =
        dao.observeByFolder(folderId).reEmitOnChange().map { it.map(::decrypt) }

    suspend fun getById(id: Long): VaultEventEntity? = dao.getById(id)?.let(::decrypt)

    suspend fun add(event: VaultEventEntity): Long =
        dao.insert(encrypt(event)).also { onChanged(); refreshSignal.tryEmit(Unit) }

    suspend fun addAll(events: List<VaultEventEntity>) {
        for (e in events) dao.insert(encrypt(e))
        onChanged()
        refreshSignal.tryEmit(Unit)
    }

    suspend fun update(event: VaultEventEntity) =
        dao.update(encrypt(event)).also { onChanged(); refreshSignal.tryEmit(Unit) }

    suspend fun delete(event: VaultEventEntity) =
        dao.delete(event).also { onChanged(); refreshSignal.tryEmit(Unit) }

    suspend fun deleteByIds(ids: List<Long>) =
        dao.deleteByIds(ids).also { onChanged(); refreshSignal.tryEmit(Unit) }

    /** 清空整个 Vault 事件表（重置密码时调用）。 */
    suspend fun clearAll() =
        dao.clearAll().also { onChanged(); refreshSignal.tryEmit(Unit) }

    suspend fun countAll(): Int = dao.countAll()

    suspend fun moveToFolder(ids: List<Long>, folderId: Long?) =
        dao.moveToFolder(ids, folderId).also { onChanged(); refreshSignal.tryEmit(Unit) }
}
