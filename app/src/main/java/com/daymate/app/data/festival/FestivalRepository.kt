package com.ayaka7452.daymate.data.festival

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** 单个节假日条目：name 节日名（调休上班日为「调休上班（XX）」），isOffDay true=放假 / false=调休上班。 */
data class FestivalDay(val name: String, val date: LocalDate, val isOffDay: Boolean)

/** 一次在线更新的结果：okYears 成功缓存的年份，failedYears 失败年份。 */
data class FestivalUpdateResult(val okYears: List<Int>, val failedYears: List<Int>) {
    val success: Boolean get() = okYears.isNotEmpty()
    fun summaryText(): String = when {
        okYears.isEmpty() -> "下载失败，请检查网络或数据源"
        failedYears.isEmpty() -> "已更新 ${okYears.min()}–${okYears.max()} 年"
        else -> "更新了 ${okYears.joinToString("、")}；失败：${failedYears.joinToString("、")}"
    }
}

/**
 * 中国法定节假日数据仓库：**不内置离线数据**，完全依赖「在线下载 + 本地文件缓存」。
 *
 * App 运行时自行下载数据并解析——内置两种已知公开格式的解析器（自动识别）：
 *  1. holiday-cn：{"year":2026,"days":[{"name":"元旦","date":"2026-01-01","isOffDay":true}]}
 *  2. timor.tech：{"code":0,"holiday":{"01-01":{"holiday":true,"name":"元旦","date":"2026-01-01"}}}
 *
 * 数据源 URL 可在设置中更换（含 {year} 占位符），默认为 holiday-cn 的 jsDelivr CDN。
 * 下载成功后按「归一化格式」（与 holiday-cn 相同的精简结构）写入 filesDir/festival_cache/{year}.json；
 * 所有查询只读缓存——缓存为空时查询返回 null/空列表，由调用方提示用户去设置下载。
 */
class FestivalRepository(private val appContext: Context) {

    companion object {
        /** 默认源：holiday-cn 开源数据（GitHub 权威发布，jsDelivr CDN 国内可达）。 */
        const val SOURCE_HOLIDAY_CN = "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/{year}.json"
        /** 备选源：timor.tech 免费节假日 API。 */
        const val SOURCE_TIMOR = "https://timor.tech/api/holiday/year/{year}"
        const val DEFAULT_SOURCE = SOURCE_HOLIDAY_CN

        private const val PREFS = "festival_prefs"
        private const val KEY_SOURCE = "source_url"
        private const val CACHE_DIR = "festival_cache"
    }

    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cacheDir = File(appContext.filesDir, CACHE_DIR)

    // ---------- 数据源管理（设置页可编辑） ----------

    fun sourceUrl(): String = prefs.getString(KEY_SOURCE, DEFAULT_SOURCE) ?: DEFAULT_SOURCE

    fun setSourceUrl(url: String) {
        prefs.edit().putString(KEY_SOURCE, url.trim()).apply()
    }

    fun sourceLabel(): String = when (sourceUrl()) {
        SOURCE_HOLIDAY_CN -> "holiday-cn（默认）"
        SOURCE_TIMOR -> "timor.tech"
        else -> "自定义源"
    }

    private fun cacheFile(year: Int): File = cacheDir.apply { mkdirs() }.let { File(it, "$year.json") }

    // ---------- 缓存状态 ----------

    /** 已缓存且有实际数据的年份（升序）。 */
    fun cachedYears(): List<Int> =
        cacheDir.listFiles()
            ?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            ?.filter { loadYear(it).isNotEmpty() }
            ?.sorted()
            ?: emptyList()

    fun hasData(): Boolean = cachedYears().isNotEmpty()

    fun dataStatusText(): String {
        val years = cachedYears()
        return if (years.isEmpty()) "未下载" else "已缓存 ${years.first()}–${years.last()} 年"
    }

    private fun loadYear(year: Int): List<FestivalDay> {
        val f = cacheFile(year)
        if (!f.exists()) return emptyList()
        return runCatching { parseJson(f.readText())?.second ?: emptyList() }.getOrDefault(emptyList())
    }

    private fun allDays(): List<FestivalDay> =
        cachedYears().flatMap { loadYear(it) }.sortedBy { it.date }

    // ---------- 查询（缓存为空时返回 null/空，调用方负责提示下载） ----------

    /** 今天是否为节假日/调休上班日。 */
    fun todayInfo(today: LocalDate): FestivalDay? =
        loadYear(today.year).firstOrNull { it.date == today }

    /** 下一个放假的节日（>= from）。 */
    fun nextOffDay(from: LocalDate): FestivalDay? =
        allDays().firstOrNull { it.date >= from && it.isOffDay }

    /**
     * 指定节日的下一次日期（date >= after）。
     * 供「跟随节日」事件滚动：目标日期过后锚定到数据源中该节日的下一次日期。
     * 缓存无数据或找不到该节日名时返回 null（保持原目标日期不变）。
     */
    fun nextOccurrenceOf(name: String, after: LocalDate): LocalDate? =
        allDays().firstOrNull { it.name == name && it.date >= after }?.date

    /** 接下来的放假节日（按名称去重、各取最早一次），供表单快选。 */
    fun upcomingOffDays(from: LocalDate, limit: Int = 12): List<FestivalDay> {
        val seen = mutableSetOf<String>()
        return allDays()
            .filter { it.date >= from && it.isOffDay }
            .filter { seen.add(it.name) }
            .take(limit)
    }

    // ---------- 在线下载（App 自行拉取并解析） ----------

    /**
     * 下载 [当前年-1, 当前年, 当前年+1] 三年的数据并写缓存。
     * 单年失败不影响其他年份；全部失败时 success=false。
     */
    suspend fun updateFromNetwork(): FestivalUpdateResult = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val years = listOf(today.year - 1, today.year, today.year + 1)
        val ok = mutableListOf<Int>()
        val fail = mutableListOf<Int>()
        for (y in years) {
            try {
                val text = download(sourceUrl().replace("{year}", y.toString()))
                val (parsedYear, days) = parseJson(text)
                    ?: throw IllegalArgumentException("无法识别的数据格式")
                if (days.isEmpty()) throw IllegalArgumentException("数据为空")
                cacheFile(y).writeText(normalize(parsedYear, days))
                ok.add(y)
            } catch (_: Exception) {
                fail.add(y)
            }
        }
        FestivalUpdateResult(ok, fail)
    }

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.requestMethod = "GET"
        try {
            if (conn.responseCode !in 200..299) throw RuntimeException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ---------- 解析（写缓存用归一化格式，读缓存兼容两种来源的归一化结果） ----------

    private fun normalize(year: Int, days: List<FestivalDay>): String {
        val root = JSONObject()
        root.put("year", year)
        val arr = JSONArray()
        for (d in days) {
            val o = JSONObject()
            o.put("name", d.name)
            o.put("date", d.date.toString())
            o.put("isOffDay", d.isOffDay)
            arr.put(o)
        }
        root.put("days", arr)
        return root.toString()
    }

    /** 自动识别 holiday-cn / timor.tech 两种格式；无法识别返回 null。 */
    fun parseJson(text: String): Pair<Int, List<FestivalDay>>? = runCatching {
        val root = JSONObject(text)
        when {
            root.has("days") -> {
                // holiday-cn 格式
                val arr = root.getJSONArray("days")
                val days = mutableListOf<FestivalDay>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val local = runCatching { LocalDate.parse(o.getString("date")) }.getOrNull() ?: continue
                    days.add(FestivalDay(o.optString("name", "节假日"), local, o.optBoolean("isOffDay", true)))
                }
                if (days.isEmpty()) null else (root.optInt("year", days.first().date.year) to days)
            }
            root.has("holiday") -> {
                // timor.tech 格式：key 为 MM-dd，date 字段为完整日期
                val hol = root.getJSONObject("holiday")
                val days = mutableListOf<FestivalDay>()
                val keys = hol.keys()
                while (keys.hasNext()) {
                    val o = hol.getJSONObject(keys.next())
                    val local = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull() ?: continue
                    val off = o.optBoolean("holiday", true)
                    val name = if (off) {
                        o.optString("name", "节假日").ifBlank { "节假日" }
                    } else {
                        val target = o.optString("target").takeIf { it.isNotBlank() }
                        if (target != null) "调休上班（$target）" else "调休上班"
                    }
                    days.add(FestivalDay(name, local, off))
                }
                if (days.isEmpty()) null else (days.first().date.year to days)
            }
            else -> null
        }
    }.getOrNull()
}
