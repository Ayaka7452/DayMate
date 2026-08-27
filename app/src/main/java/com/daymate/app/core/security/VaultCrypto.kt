package com.daymate.app.core.security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Vault 密码哈希工具（Alpha 版）。
 * 使用 PBKDF2WithHmacSHA256，盐随机生成，哈希结果存 DataStore。
 */
object VaultCrypto {
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 256

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun hash(password: String, saltHex: String): String {
        val salt = saltHex.hexToBytes()
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded.toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
