package com.ayaka7452.daymate.core.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

/**
 * Vault 加密工具（Alpha 版）。
 *
 * 设计：Vault 库本身保持明文 SQLite（不引入整库 SQLCipher），
 * 仅对 Vault 内的敏感字段（标题/备注/文件夹名）用「用户 Vault 密码」派生的
 * AES-256 密钥做**应用层加密**——主数据空间（倒数日事件/文件夹）维持明文。
 *
 * - [hash]   : 派生值的 hex 形式，仅用于「密码是否正确」的验证比对（存 DataStore）。
 * - [key]    : 同一派生值的 32 字节，作为 AES-256 密钥，用于字段加解密。
 * - [encrypt]/[decrypt] : AES/CBC/PKCS5Padding，随机 IV 前缀在密文里。
 *
 * 主数据空间不加密，因此本类不参与主库；只有 Vault 表走这里。
 */
object VaultCrypto {
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 256
    private const val IV_LENGTH = 16

    /** 由密码 + salt 派生 32 字节原始密钥材料。 */
    private fun derive(password: String, saltHex: String): ByteArray {
        val salt = saltHex.hexToBytes()
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    /** 密码验证用派生（hex），与旧实现兼容。 */
    fun hash(password: String, saltHex: String): String = derive(password, saltHex).toHex()

    /** 加密用 AES-256 密钥（与 [hash] 同源派生）。 */
    fun key(password: String, saltHex: String): SecretKey =
        SecretKeySpec(derive(password, saltHex), "AES")

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    /** 加密明文字符串，返回 base64(iv + ciphertext)。null 直接返回 null。 */
    fun encrypt(plain: String?, key: SecretKey): String? {
        if (plain == null) return null
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val enc = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val out = ByteArray(iv.size + enc.size)
        System.arraycopy(iv, 0, out, 0, iv.size)
        System.arraycopy(enc, 0, out, iv.size, enc.size)
        return Base64.getEncoder().encodeToString(out)
    }

    /** 解密；失败（如密钥不匹配）返回 null，调用方应以占位/原文兜底。 */
    fun decrypt(cipherText: String?, key: SecretKey): String? {
        if (cipherText == null) return null
        return runCatching {
            val raw = Base64.getDecoder().decode(cipherText)
            val iv = raw.copyOfRange(0, IV_LENGTH)
            val enc = raw.copyOfRange(IV_LENGTH, raw.size)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
            String(cipher.doFinal(enc), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
