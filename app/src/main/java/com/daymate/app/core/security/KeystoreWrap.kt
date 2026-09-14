package com.ayaka7452.daymate.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 用 AndroidKeyStore 中一把**不可导出**的 AES-256 密钥包裹任意字节，返回 Base64 密文
 * （格式：`IV 长度(1B) || IV || 密文+GCM tag`）。调用方自行把密文存进应用私有存储。
 *
 * 由 [BiometricKeyStore]（Vault 会话密钥托管）与
 * [com.ayaka7452.daymate.core.cloud.WebDavStore]（备份密码）共用，
 * 避免同一段 AES/GCM 包裹逻辑复制多份。
 *
 * 安全边界：Keystore 密钥受系统保护且随应用卸载销毁，包裹值离开本机无法解开；
 * 但它是「本地托管」而非额外加密层——整体强度仍取决于调用方原本的凭据（密码/口令）。
 */
internal object KeystoreWrap {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val GCM_TAG_BITS = 128
    private const val IV_LEN_FIELD = 1

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    /**
     * 取已存在的包裹密钥；不存在返回 null。
     * 刻意不在此创建——否则旧密文无法解开（新密钥解不开旧包裹值）。
     */
    private fun existingKey(alias: String): SecretKey? = runCatching {
        (keyStore().getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }.getOrNull()

    /** 取或创建包裹密钥（仅在加密路径上创建）。 */
    private fun ensureKey(alias: String): SecretKey? = existingKey(alias) ?: runCatching {
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }.getOrNull()

    /** 包裹 [raw]；Keystore 不可用或加密失败返回 null（调用方应按「托管失败」处理）。 */
    fun wrap(alias: String, raw: ByteArray): String? {
        val key = ensureKey(alias) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(raw)
            val blob = ByteArray(IV_LEN_FIELD + iv.size + encrypted.size)
            blob[0] = iv.size.toByte()
            System.arraycopy(iv, 0, blob, IV_LEN_FIELD, iv.size)
            System.arraycopy(encrypted, 0, blob, IV_LEN_FIELD + iv.size, encrypted.size)
            Base64.getEncoder().encodeToString(blob)
        } catch (_: Throwable) {
            null
        }
    }

    /** 解封 [stored]（[wrap] 的产物）；未托管、密钥丢失或密文损坏均返回 null。 */
    fun unwrap(alias: String, stored: String): ByteArray? {
        val key = existingKey(alias) ?: return null
        return try {
            val blob = Base64.getDecoder().decode(stored)
            if (blob.size <= IV_LEN_FIELD) return null
            val ivSize = blob[0].toInt()
            if (ivSize <= 0 || ivSize >= blob.size) return null
            val iv = blob.copyOfRange(IV_LEN_FIELD, IV_LEN_FIELD + ivSize)
            val encrypted = blob.copyOfRange(IV_LEN_FIELD + ivSize, blob.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(encrypted)
        } catch (_: Throwable) {
            null
        }
    }

    /** 删除包裹密钥（清除托管内容时调用，使旧密文彻底不可解）。 */
    fun deleteKey(alias: String) {
        runCatching { keyStore().deleteEntry(alias) }
    }
}
