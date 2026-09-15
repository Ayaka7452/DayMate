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
 *
 * 注意「目录」有两种空值语义，不要只看字符串：
 *  - **从未选定过目录**（[isDirectoryPicked] 为 false）：配置不完整，不能用于备份；
 *  - **选定了根目录**（[isDirectoryPicked] 为 true 且 [directory] 为空）：合法配置，
 *    备份写到 `<baseUrl>/daymate.db`。
 * 早期版本用「目录串非空」判断，导致「选择根目录」这一选项永远无法生效，故引入显式标记。
 */
object WebDavStore {
    private const val PREFS = "daymate_cloud"
    private const val KEY_URL = "webdav_url"
    private const val KEY_USER = "webdav_user"
    private const val KEY_PASS = "webdav_pass"
    private const val KEY_DIR = "webdav_dir"
    private const val KEY_DIR_PICKED = "webdav_dir_picked"
    private const val KEY_SELF_SIGNED = "webdav_self_signed"
    private const val ALIAS = "daymate_webdav_pass_wrap"

    /** 云端备份文件名。与本地 SAF 备份同名，便于两种渠道互相导入。 */
    const val REMOTE_DB = "daymate.db"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun url(ctx: Context): String = prefs(ctx).getString(KEY_URL, "").orEmpty()

    fun username(ctx: Context): String = prefs(ctx).getString(KEY_USER, "").orEmpty()

    fun directory(ctx: Context): String = prefs(ctx).getString(KEY_DIR, "").orEmpty()

    fun allowSelfSigned(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SELF_SIGNED, false)

    /**
     * 是否已选定过远程存放目录（**选中根目录也算**）。
     * 兼容旧版配置：老数据没有标记，但目录串非空即视为已选定。
     */
    fun isDirectoryPicked(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_DIR_PICKED, false) || directory(ctx).isNotBlank()

    /** 取回明文密码；未保存或 Keystore 解不开时返回空串。 */
    fun password(ctx: Context): String {
        val stored = prefs(ctx).getString(KEY_PASS, null) ?: return ""
        val raw = KeystoreWrap.unwrap(ALIAS, stored) ?: return ""
        return runCatching { String(raw, Charsets.UTF_8) }.getOrDefault("")
    }

    /** 是否已配置完整（地址、已选定远程目录、可解出的密码齐备）。 */
    fun isConfigured(ctx: Context): Boolean =
        url(ctx).isNotBlank() && isDirectoryPicked(ctx) && password(ctx).isNotEmpty()

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
     *
     * @param directoryPicked 调用方是否是在「远程目录浏览」中明确选定目录（含选中根目录）。
     *   为 true 时写入「已选定」标记——即使 [WebDavConfig.directory] 为空串（根目录）也算配置完整。
     * @return 密码是否成功托管——返回 false 表示 Keystore 不可用，密码没有落盘，
     *   此时配置不算生效（[isConfigured] 会为 false），调用方应提示用户。
     */
    fun save(ctx: Context, cfg: WebDavConfig, directoryPicked: Boolean = false): Boolean {
        val wrapped = KeystoreWrap.wrap(ALIAS, cfg.password.toByteArray(Charsets.UTF_8))
        val picked = directoryPicked || cfg.directory.trim().isNotBlank()
        prefs(ctx).edit().apply {
            putString(KEY_URL, cfg.url.trim())
            putString(KEY_USER, cfg.username.trim())
            putString(KEY_DIR, cfg.directory.trim().trim('/'))
            putBoolean(KEY_SELF_SIGNED, cfg.allowSelfSigned)
            if (picked) putBoolean(KEY_DIR_PICKED, true)
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
