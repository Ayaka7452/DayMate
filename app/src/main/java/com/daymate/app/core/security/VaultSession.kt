package com.ayaka7452.daymate.core.security

import javax.crypto.SecretKey

/**
 * Vault 解锁后的内存密钥会话。
 *
 * Vault 字段采用「用户密码派生的 AES 密钥」做应用层加密，但密码本身不持久保存在内存
 * （只存由它派生的 [SecretKey]）。解锁或设置密码成功后写入，退出 Vault 或重置密码时清空。
 * 注意：进程被杀后密钥消失，下次需重新解锁。
 */
object VaultSession {
    private var _key: SecretKey? = null

    /** 当前解锁密钥；未解锁为 null。 */
    val key: SecretKey? get() = _key

    val unlocked: Boolean get() = _key != null

    fun unlock(key: SecretKey) {
        _key = key
    }

    fun lock() {
        _key = null
    }
}
