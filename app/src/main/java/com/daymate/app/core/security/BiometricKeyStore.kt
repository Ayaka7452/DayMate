package com.ayaka7452.daymate.core.security

import android.content.Context

/**
 * 指纹解锁用的「Vault 会话密钥托管」。
 *
 * 背景：Vault 的字段加密密钥由用户密码经 PBKDF2 派生，密码本身不落盘（只存 hash + salt），
 * 因此**指纹验证通过也无法自行派生密钥**——若指纹解锁后不写入 [VaultSession]，
 * 读取时 [com.ayaka7452.daymate.data.repo.VaultRepository] 拿不到密钥，
 * 会把密文原样当成明文显示（乱码）。
 *
 * 做法：解锁（设密 / 密码解锁）时把已派生的会话密钥交给 [KeystoreWrap] 用 AndroidKeyStore
 * 中一把不可导出的 AES-256 密钥包裹后存进应用私有 SharedPreferences；指纹验证通过后解封
 * 写回 [VaultSession]。
 *
 * 注意：本类是「指纹解锁后仍能解密」的必要条件，不是额外安全层——Vault 的威胁模型
 * 仍是「密码 + PBKDF2」，与是否使用指纹无关。
 */
object BiometricKeyStore {
    private const val PREF = "daymate_vault_bio"
    private const val PREF_WRAPPED = "wrapped_key"
    private const val ALIAS = "daymate_vault_bio_wrap"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 是否已托管过会话密钥（否则指纹解锁无法解密，应引导用户改用密码）。 */
    fun hasWrappedKey(ctx: Context): Boolean =
        prefs(ctx).getString(PREF_WRAPPED, null) != null

    /** 把会话密钥包裹后持久化；成功返回 true（Keystore 不可用时返回 false）。 */
    fun wrap(ctx: Context, rawKey: ByteArray): Boolean {
        val blob = KeystoreWrap.wrap(ALIAS, rawKey) ?: return false
        prefs(ctx).edit().putString(PREF_WRAPPED, blob).apply()
        return true
    }

    /** 解封会话密钥；未托管或解封失败返回 null（调用方应回落到密码解锁）。 */
    fun unwrap(ctx: Context): ByteArray? {
        val stored = prefs(ctx).getString(PREF_WRAPPED, null) ?: return null
        return KeystoreWrap.unwrap(ALIAS, stored)
    }

    /**
     * 托管密钥当前能否解封。Keystore 密钥硬件绑定、不随应用数据备份/恢复迁移
     * （刷机、换机用第三方备份恢复后必丢），残留的托管记录会变成「死档」：
     * 记录还在、但永远解不开。此时指纹按钮应隐藏并提示「用密码解锁一次恢复」，
     * 而不是等用户扫完指纹才报错。
     */
    fun canUnwrap(ctx: Context): Boolean = unwrap(ctx) != null

    /** 清除托管的密钥（关闭指纹、重置密码时调用）。 */
    fun clear(ctx: Context) {
        KeystoreWrap.deleteKey(ALIAS)
        prefs(ctx).edit().remove(PREF_WRAPPED).apply()
    }
}
