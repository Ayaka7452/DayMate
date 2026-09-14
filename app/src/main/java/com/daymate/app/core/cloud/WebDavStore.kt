package com.ayaka7452.daymate.core.cloud

import android.content.Context
import com.ayaka7452.daymate.core.security.KeystoreWrap

/** WebDAV 连接参数（密码为明文，仅在内存中短暂存在）。 */
data class WebDavConfig(
    val url: String,
    val username: String,
    val password: String,
    /** 相对于 [url] 的远程目录，例如 `DayMate/backup`；为空表示直接用根目录。 */
    val directory: String,
    /** 允许 https 使用自签名 / 不受信任证书（自建 NAS 常见），默认关闭。 */
    val allowSelfSigned: Boolean = false
)

/**
 * WebDAV 云端备份的配置存取。
 *
 * 服务器地址 / 用户名 / 远程目录 / 自签名开关用 SharedPreferences 明文保存；
 * **密码由 [KeystoreWrap] 经 AndroidKeyStore 包裹后存储**，避免备份口令以明文落盘。
 */
object WebDavStore {
    private const val PREFS = "daymate_cloud"
    private const val KEY_URL = "webdav_url"
    private const val KEY_USER = "webdav_user"
    private const val KEY_PASS = "webdav_pass"
    private const val KEY_DIR = "webdav_dir"
    private const val KEY_SELF_SIGNED = "webdav_self_signed"
    private const val ALIAS = "daymate_webdav_pass_wrap"

    /** 云端备份文件名。与本地 SAF 备份同名，便于两种渠道互相导入。 */
    const val REMOTE_DB = "daymate.db"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun url(ctx: Context): String = prefs(ctx).getString(KEY_URL, "").orEmpty()

    fun username(ctx: Context): String = prefs(ctx).getString(KEY_USER, "").orEmpty()

    fun directory(ctx: Context): String = prefs(ctx).getString(KEY_DIR, "").orEmpty()

    fun allowSelfSigned(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SELF_SIGNED, false)

    /** 取回明文密码；未保存或 Keystore 解不开时返回空串。 */
    fun password(ctx: Context): String {
        val stored = prefs(ctx).getString(KEY_PASS, null) ?: return ""
        val raw = KeystoreWrap.unwrap(ALIAS, stored) ?: return ""
        return runCatching { String(raw, Charsets.UTF_8) }.getOrDefault("")
    }

    /** 是否已配置完整（地址、远程目录、可解出的密码齐备）。 */
    fun isConfigured(ctx: Context): Boolean =
        url(ctx).isNotBlank() && directory(ctx).isNotBlank() && password(ctx).isNotEmpty()

    /** 取完整配置；未配置完整返回 null。 */
    fun config(ctx: Context): WebDavConfig? {
        if (!isConfigured(ctx)) return null
        return WebDavConfig(
            url = url(ctx),
            username = username(ctx),
            password = password(ctx),
            directory = directory(ctx),
            allowSelfSigned = allowSelfSigned(ctx)
        )
    }

    /**
     * 保存配置。
     * @return 密码是否成功托管——返回 false 表示 Keystore 不可用，密码没有落盘，
     * 此时配置不算生效（[isConfigured] 会为 false），调用方应提示用户。
     */
    fun save(ctx: Context, cfg: WebDavConfig): Boolean {
        val wrapped = KeystoreWrap.wrap(ALIAS, cfg.password.toByteArray(Charsets.UTF_8))
        prefs(ctx).edit().apply {
            putString(KEY_URL, cfg.url.trim())
            putString(KEY_USER, cfg.username.trim())
            putString(KEY_DIR, cfg.directory.trim().trim('/'))
            putBoolean(KEY_SELF_SIGNED, cfg.allowSelfSigned)
            if (wrapped != null) putString(KEY_PASS, wrapped)
            apply()
        }
        return wrapped != null
    }

    /** 清除全部 WebDAV 配置与托管密码。 */
    fun clear(ctx: Context) {
        KeystoreWrap.deleteKey(ALIAS)
        prefs(ctx).edit().clear().apply()
    }
}
