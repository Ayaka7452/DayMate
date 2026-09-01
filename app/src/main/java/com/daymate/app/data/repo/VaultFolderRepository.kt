package com.ayaka7452.daymate.data.repo

import com.ayaka7452.daymate.core.security.VaultCrypto
import com.ayaka7452.daymate.core.security.VaultSession
import com.ayaka7452.daymate.data.db.VaultFolderDao
import com.ayaka7452.daymate.data.db.VaultFolderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * Vault 文件夹仓库。与 [VaultRepository] 同理：对外明文，DAO 层存密文（仅 name 加密）。
 */
class VaultFolderRepository(
    private val dao: VaultFolderDao,
    private val onChanged: () -> Unit = {}
) {

    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun <T> Flow<List<T>>.reEmitOnChange(): Flow<List<T>> =
        merge(this, refreshSignal.map { this@reEmitOnChange.first() })

    private fun decrypt(f: VaultFolderEntity): VaultFolderEntity {
        val k = VaultSession.key ?: return f
        return f.copy(name = VaultCrypto.decrypt(f.name, k) ?: f.name)
    }

    private fun encrypt(f: VaultFolderEntity): VaultFolderEntity {
        val k = VaultSession.key ?: return f
        return f.copy(name = VaultCrypto.encrypt(f.name, k) ?: f.name)
    }

    fun observeAll(): Flow<List<VaultFolderEntity>> =
        dao.observeAll().reEmitOnChange().map { it.map(::decrypt) }

    suspend fun getById(id: Long): VaultFolderEntity? = dao.getById(id)?.let(::decrypt)

    suspend fun add(folder: VaultFolderEntity): Long =
        dao.insert(encrypt(folder)).also { onChanged(); refreshSignal.tryEmit(Unit) }

    suspend fun addAll(folders: List<VaultFolderEntity>) {
        for (f in folders) dao.insert(encrypt(f))
        onChanged()
        refreshSignal.tryEmit(Unit)
    }

    suspend fun update(folder: VaultFolderEntity) =
        dao.update(encrypt(folder)).also { onChanged(); refreshSignal.tryEmit(Unit) }

    suspend fun delete(folder: VaultFolderEntity) =
        dao.delete(folder).also { onChanged(); refreshSignal.tryEmit(Unit) }

    suspend fun deleteByIds(ids: List<Long>) =
        dao.deleteByIds(ids).also { onChanged(); refreshSignal.tryEmit(Unit) }

    /** 清空整个 Vault 文件夹表（重置密码时调用）。 */
    suspend fun clearAll() =
        dao.clearAll().also { onChanged(); refreshSignal.tryEmit(Unit) }
}
