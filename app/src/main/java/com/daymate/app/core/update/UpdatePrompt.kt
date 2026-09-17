package com.ayaka7452.daymate.core.update

import android.content.Context
import com.ayaka7452.daymate.data.repo.SettingsRepository
import kotlinx.coroutines.flow.first

/** 「立即检查」的结果：设置页要靠它区分「已是最新」和「检查失败」。 */
sealed interface UpdateCheckResult {
    /** 当前已是最新（或最新版没有可下发的资产）。 */
    data object Latest : UpdateCheckResult

    /** 发现可用更新。 */
    data class Available(val info: UpdateInfo) : UpdateCheckResult

    /** 网络不通 / 被限速 / 响应无法解析。 */
    data object Failed : UpdateCheckResult
}

/**
 * 启动时的检查更新流程。
 *
 * 三条节流规则，缺一不可：
 *  1. 设置里关掉了就完全不发请求；
 *  2. **24 小时内只查一次**（GitHub 匿名 API 每小时 60 次，且国内直连不算稳，
 *     没必要每次冷启动都去问）；
 *  3. 用户点过「稍后」的**那一个版本**不再弹——只针对该版本，下个版本照常提醒。
 *
 * 检查失败一律静默：启动时弹「网络错误」比不检查更烦人。
 * 用户想主动确认时走设置页的「立即检查」，那里会给出明确结果。
 */
object UpdatePrompt {

    private const val THROTTLE_MS = 24L * 60 * 60 * 1000

    /** 冷启动调用；返回非空表示应当弹更新提示。 */
    suspend fun checkOnLaunch(settings: SettingsRepository, context: Context): UpdateInfo? {
        if (!settings.updateCheckEnabled.first()) return null
        val now = System.currentTimeMillis()
        if (now - settings.updateLastCheckAt() < THROTTLE_MS) return null
        // 先记时间戳：即使这次失败，也要等到明天再自动重试，避免每启动一次就打一次网络
        settings.setUpdateLastCheckAt(now)
        val info = UpdateChecker.fetchLatest().getOrNull() ?: return null
        if (!UpdateChecker.isNewer(info.version, UpdateChecker.currentVersion(context))) return null
        if (info.version == settings.updateSkippedVersion()) return null
        return info
    }

    /** 设置页「立即检查」：不受节流与「稍后」影响，明确三态返回。 */
    suspend fun checkNow(context: Context): UpdateCheckResult {
        val result = UpdateChecker.fetchLatest()
        val info = result.getOrNull()
            ?: return if (result.isFailure) UpdateCheckResult.Failed else UpdateCheckResult.Latest
        return if (UpdateChecker.isNewer(info.version, UpdateChecker.currentVersion(context))) {
            UpdateCheckResult.Available(info)
        } else {
            UpdateCheckResult.Latest
        }
    }

    /**
     * Release 说明的轻量清洗：只去掉 Markdown 标题的 `#`，其余原样保留。
     * 不引入 Markdown 渲染——说明是本项目的自述文字，纯文本足够，也避免多一层依赖。
     */
    fun cleanNotes(notes: String): String = notes
        .lines()
        .joinToString("\n") { it.trimStart().removePrefix("### ").removePrefix("## ").removePrefix("# ") }
        .trim()
}
