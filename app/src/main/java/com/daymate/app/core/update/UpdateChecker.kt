package com.ayaka7452.daymate.core.update

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 一次「发现新版本」的结果。 */
data class UpdateInfo(
    val version: String,   // 纯版本号，如 "1.9.7"（已去掉 tag 的 v 前缀）
    val notes: String,     // Release 说明原文（Markdown，展示时按纯文本处理）
    val apkUrl: String,    // 资产下载直链
    val apkName: String,   // 资产文件名，同时用作落盘文件名
    val apkSize: Long      // 字节数，用于算下载进度；0 = 未知
)

/**
 * 检查 GitHub Release 是否有新版本。
 *
 * 仓库公开，匿名请求 `releases/latest` 即可（实测 200），**不需要 token**，
 * 因此 App 里可以只带一个 User-Agent 直接问。
 *
 * 国内直连 `api.github.com` 偶发超时，超时设得比较短、失败一律静默返回 null——
 * 「每次启动都弹网络错误」比不检查更烦人；设置页的「立即检查」会显式提示失败原因。
 */
object UpdateChecker {

    private const val API =
        "https://api.github.com/repos/Ayaka7452/DayMate/releases/latest"

    /** 资产名形如 `DayMate-v1.9.7-signed.apk`。只认这一种，避免误下到别的附件。 */
    private val ASSET_RE = Regex("""^DayMate-v(.+)-signed\.apk$""")

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** 本机已安装的版本号（如 "1.9.6"）；取不到时返回 "0"（＝任何版本都算更新）。 */
    fun currentVersion(context: Context): String = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    }.getOrDefault("0")

    /**
     * 查询最新 Release。
     *
     * - `Result.success(null)`：请求成功但**没有可更新的东西**——没有合规资产、或最新版是 draft/pre-release；
     * - `Result.failure`：网络不通 / 限速 / 响应无法解析。设置页靠这个区分「已是最新」与「检查失败」。
     */
    suspend fun fetchLatest(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "DayMate-Android")   // GitHub API 不带 UA 会 403
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body?.string() ?: throw IOException("empty body")
                parse(JSONObject(body))
            }
        }.onFailure { Log.w("DayMateUpdate", "check failed", it) }
    }

    /** 解析 Release JSON；draft/pre-release 与「没有合规资产」都返回 null。 */
    fun parse(root: JSONObject): UpdateInfo? {
        if (root.optBoolean("draft", false) || root.optBoolean("prerelease", false)) return null
        val version = root.optString("tag_name").trim().removePrefix("v")
        if (version.isBlank()) return null
        val assets = root.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (ASSET_RE.matchEntire(name) == null) continue
            val url = asset.optString("browser_download_url")
            if (url.isBlank()) continue
            return UpdateInfo(
                version = version,
                notes = root.optString("body").trim(),
                apkUrl = url,
                apkName = name,
                apkSize = asset.optLong("size", 0L)
            )
        }
        return null
    }

    /**
     * 远端版本是否比本地新。
     *
     * 逐段按**数字**比较（`1.9.10 > 1.9.9`，字符串比会得出相反结论），段数不足按 0 补齐；
     * 任一侧解析不出纯数字段就返回 false——宁可漏报一次，也不要因为解析歧义误报更新。
     */
    fun isNewer(remote: String, local: String): Boolean {
        val r = segments(remote) ?: return false
        val l = segments(local) ?: return false
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun segments(v: String): List<Int>? {
        val core = v.trim().removePrefix("v").substringBefore('-').substringBefore('+')
        if (core.isBlank()) return null
        val parts = core.split('.')
        val out = ArrayList<Int>(parts.size)
        for (p in parts) out.add(p.trim().toIntOrNull() ?: return null)
        return out
    }
}
