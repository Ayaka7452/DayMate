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

/** 单个节假日条目：name 节日名（调休上班日沿用所属节日名，仅 isOffDay=false 区分），
 *  isOffDay true=放假 / false=调休上班。
 *  isEstimate 仅用于表单快选的「预估日期」（缓存里没有该节日未来日期时按上次日期+1年推算，
 *  农历节日可能不准），下载到新一年数据后会自动校正，不会持久化到缓存。 */
data class FestivalDay(
    val name: String,
    val date: LocalDate,
    val isOffDay: Boolean,
    val isEstimate: Boolean = false
)

/**
 * 一次在线更新的结果：
 *  - [okYears] 成功解析并写入缓存的年份；
 *  - [notPublishedYears] 数据源里该年份还没内容（如次年放假安排尚未发布）——**不是失败**；
 *  - [failedYears] 网络异常、格式无法识别等真正的失败。
 */
data class FestivalUpdateResult(
    val okYears: List<Int>,
    val notPublishedYears: List<Int>,
    val failedYears: List<Int>
) {
    val success: Boolean get() = okYears.isNotEmpty()

    fun summaryText(): String {
        val parts = buildList {
            if (okYears.isNotEmpty()) add("已更新 ${okYears.joinToString("、")} 年")
            if (notPublishedYears.isNotEmpty()) add("${notPublishedYears.joinToString("、")} 年放假安排尚未发布")
            if (failedYears.isNotEmpty()) add("${failedYears.joinToString("、")} 年下载失败")
        }
        return when {
            parts.isEmpty() -> "没有可下载的年份"
            // 全部都是「还没发布」：不报失败，明确告诉用户数据源还没出
            okYears.isEmpty() && failedYears.isEmpty() -> "所选的年份放假安排尚未发布"
            else -> parts.joinToString("；")
        }
    }
}

/**
 * 中国法定节假日数据仓库：**不内置离线数据**，完全依赖「在线下载 + 本地文件缓存」。
 *
 * 默认数据源为 holiday-cn（NateScarlet，跟随国务院通知发布，jsDelivr CDN 国内可达）；
 * 用户可在设置中换成任意 URL——解析器按结构自动识别，不绑定具体厂商，已覆盖：
 *  1. holiday-cn：{"year":2026,"days":[{"name":"元旦","date":"2026-01-01","isOffDay":true}]}
 *  2. timor.tech 老格式：{"holiday":{"01-01":{"holiday":true,"name":"元旦","date":"2026-01-01"}}}
 *  3. 日期为键：{"2026-01-01":{"name":"元旦","isOffDay":true}}（jiejiariapi 等）
 *  4. 节假日/工作日双 Map：{"holidays":{"2026-01-01":"New Year's Day,元旦,1"},"workdays":{…}}（chinese-days）
 *  5. 包裹在 data / data.list 下的日条目数组（apihubs 等）
 *  6. 以年份为键：{"2026":[…]}
 *  7. 顶层就是日条目数组：[{…}]
 *
 * 节日名会统一成规范写法（chinese-days 的「清明」→「清明节」），因为「跟随节日」事件
 * 按名字锚定，不统一的话换数据源会让既有事件失效。
 *
 * URL 含 `{year}` 占位符时按年逐个下载；不含占位符（如全量 JSON）则下载一次、按年份切分。
 * 下载成功后按「归一化格式」（与 holiday-cn 相同的精简结构）写入 filesDir/festival_cache/{year}.json；
 * 所有查询只读缓存——缓存为空时查询返回 null/空列表，由调用方提示用户去设置下载。
 */
class FestivalRepository(private val appContext: Context) {

    companion object {
        /** 默认源：holiday-cn 开源数据（跟随国务院通知发布，jsDelivr CDN 国内可达）。 */
        const val SOURCE_HOLIDAY_CN = "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/{year}.json"
        const val DEFAULT_SOURCE = SOURCE_HOLIDAY_CN

        /** URL 中的年份占位符；缺省表示「整份文件源」。 */
        const val YEAR_PLACEHOLDER = "{year}"

        /** 可勾选的年份偏移范围：去年（−1）～ 三年后（+3）。过去年份无实际用途，不再提供。 */
        private const val OFFSET_MIN = -1
        private const val OFFSET_MAX = 3

        /** 默认覆盖：去年 + 今年。次年放假安排一般当年 11 月才公布，故默认不勾明年。 */
        private val DEFAULT_OFFSETS = listOf(-1, 0)

        /** 自动补下当年数据的最小重试间隔：数据源长期不可用时不要每次冷启动都联网。 */
        private const val AUTO_TRY_INTERVAL_MS = 24L * 60 * 60 * 1000

        private const val PREFS = "festival_prefs"
        private const val KEY_SOURCE = "source_url"
        private const val KEY_YEARS = "cache_years"
        private const val KEY_AUTO_UPDATE = "auto_update_current"
        private const val KEY_LAST_AUTO_TRY = "last_auto_try"
        private const val CACHE_DIR = "festival_cache"

        /** 把年份列表渲染成「2025–2027 年」（连续）或「2025、2027 年」（不连续）。 */
        fun yearsText(years: List<Int>): String {
            if (years.isEmpty()) return "无"
            val s = years.sorted()
            return if (s.last() - s.first() == s.size - 1) "${s.first()}–${s.last()} 年"
            else s.joinToString("、") + " 年"
        }
    }

    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cacheDir = File(appContext.filesDir, CACHE_DIR)

    init {
        // 老版本可能选过已下线的 timor.tech（现返回 403）——自动切回默认源，
        // 否则升级后下载会静默失败，而用户看不出是数据源挂了。
        val stored = prefs.getString(KEY_SOURCE, null)?.trim()
        if (stored != null && stored.contains("timor.tech", ignoreCase = true)) {
            prefs.edit().putString(KEY_SOURCE, DEFAULT_SOURCE).apply()
        }
        // 清掉早已用不到的旧年份缓存（查询一律带 `>= 今天` 过滤，过去年份永远是死数据）
        pruneOldCache()
    }

    // ---------- 数据源管理（设置页可编辑） ----------

    fun sourceUrl(): String = prefs.getString(KEY_SOURCE, DEFAULT_SOURCE) ?: DEFAULT_SOURCE

    fun setSourceUrl(url: String) {
        prefs.edit().putString(KEY_SOURCE, url.trim()).apply()
    }

    fun sourceLabel(): String =
        if (sourceUrl() == SOURCE_HOLIDAY_CN) "holiday-cn（默认）" else "自定义源"

    // ---------- 缓存年份选择（存偏移，随年份自动滑动） ----------

    /**
     * 设置页可勾选的年份（去年 ～ 三年后）。
     *
     * 内部一律用「相对今年的偏移」表达，绝不存绝对年份：存绝对年份会让选择集在写入那一刻
     * 冻结，几年后窗口已经滑走而选择集没动，结果是**当前年份反而没有数据**。
     */
    fun selectableYears(today: LocalDate = LocalDate.now()): List<Int> =
        (OFFSET_MIN..OFFSET_MAX).map { today.year + it }

    /** 用户勾选的年份偏移；从未设置过则为默认范围。今年（偏移 0）恒被包含。 */
    fun selectedOffsets(): List<Int> {
        val raw = prefs.getString(KEY_YEARS, null) ?: return DEFAULT_OFFSETS
        val parsed = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        // 兼容旧版存的绝对年份（如 "2025,2026,2027"）：按相对今天的偏移换算，越界丢弃
        val thisYear = LocalDate.now().year
        val offsets = if (parsed.any { it >= 2000 }) parsed.map { it - thisYear } else parsed
        val kept = (offsets.filter { it in OFFSET_MIN..OFFSET_MAX } + 0).distinct().sorted()
        return kept.ifEmpty { DEFAULT_OFFSETS }
    }

    /** 用户勾选的缓存年份（绝对年份，随年份自动滑动）。 */
    fun selectedYears(today: LocalDate = LocalDate.now()): List<Int> =
        selectedOffsets().map { today.year + it }

    /** 写入勾选结果（传入偏移）。今年（0）始终保留——否则当前年份会没有数据。 */
    fun setSelectedOffsets(offsets: Collection<Int>) {
        val cleaned = (offsets.filter { it in OFFSET_MIN..OFFSET_MAX } + 0).distinct().sorted()
        prefs.edit().putString(KEY_YEARS, cleaned.joinToString(",")).apply()
    }

    // ---------- 自动补下当年数据（默认关闭，用户可在设置中打开） ----------

    fun autoUpdateCurrent(): Boolean = prefs.getBoolean(KEY_AUTO_UPDATE, false)

    fun setAutoUpdateCurrent(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
    }

    /**
     * 是否需要自动补下当年数据：开关已打开 + 当年无缓存 + 距上次尝试超过 24 小时。
     * 当年数据缺失时小组件与「下一个节日」会取不到值，跨年后尤其明显。
     */
    fun shouldAutoUpdateCurrent(today: LocalDate = LocalDate.now()): Boolean =
        autoUpdateCurrent() && !isCached(today.year) &&
            System.currentTimeMillis() - prefs.getLong(KEY_LAST_AUTO_TRY, 0L) > AUTO_TRY_INTERVAL_MS

    /** 记录一次自动尝试，无论成功与否（避免失败时每次冷启动都重试联网）。 */
    fun markAutoTried() {
        prefs.edit().putLong(KEY_LAST_AUTO_TRY, System.currentTimeMillis()).apply()
    }

    /**
     * 清掉早于「去年」的缓存文件：可勾选的最小年份就是去年，更早的年份在任何查询里
     * 都会被 `>= 今天` 过滤掉，留着只会让 [cachedYears] / [allDays] 反复解析白耗。
     */
    private fun pruneOldCache(today: LocalDate = LocalDate.now()) {
        val minYear = today.year + OFFSET_MIN
        runCatching {
            cacheDir.listFiles()?.forEach { f ->
                val y = f.nameWithoutExtension.toIntOrNull() ?: return@forEach
                if (y < minYear) runCatching { f.delete() }
            }
        }
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

    /** 某年份是否已有可用缓存（设置页逐年份显示状态用）。 */
    fun isCached(year: Int): Boolean = loadYear(year).isNotEmpty()

    fun dataStatusText(): String {
        val years = cachedYears()
        return if (years.isEmpty()) "未下载" else "已缓存 ${yearsText(years)}"
    }

    private fun loadYear(year: Int): List<FestivalDay> {
        val f = cacheFile(year)
        if (!f.exists()) return emptyList()
        return runCatching { parseAny(f.readText())[year] ?: emptyList() }.getOrDefault(emptyList())
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

    /**
     * 表单「跟随节日」快选：跨年列出所有已知节日（按名称去重），各取下一次日期——
     * 不以「今年」为界：今年已过的节日（如元旦、春节）也选得到，锚定到数据中的下一次。
     * 缓存里没有未来日期的节日（官方尚未发布新年份数据），以「最近一次日期 + 1 年」
     * 预估并标记 isEstimate=true（农历节日可能偏差），下载新数据后自动校正。
     */
    fun pickerFestivals(from: LocalDate, limit: Int = 20): List<FestivalDay> {
        val days = allDays()
        val names = days.filter { it.isOffDay }.map { it.name }.distinct()
        val list = mutableListOf<FestivalDay>()
        for (name in names) {
            val next = days.firstOrNull { it.name == name && it.date >= from && it.isOffDay }
            if (next != null) {
                list.add(next)
            } else {
                val last = days.last { it.name == name && it.isOffDay }
                list.add(last.copy(date = last.date.plusYears(1), isEstimate = true))
            }
        }
        return list.sortedWith(compareBy({ it.isEstimate }, { it.date })).take(limit)
    }

    /** 指定节日在缓存中的全部日期（升序）。供下载后校正「预估日期」时判断某日期是否真实存在。 */
    fun occurrencesOf(name: String): List<LocalDate> =
        allDays().filter { it.name == name }.map { it.date }

    // ---------- 在线下载（App 自行拉取并解析） ----------

    /**
     * 下载 [years] 各年数据并写缓存（默认取用户勾选的年份）。
     *
     * 单年失败不影响其他年份。数据源返回「空内容」或 HTTP 404（如国务院尚未公布次年放假安排）
     * 归入 [FestivalUpdateResult.notPublishedYears]，与真正的网络失败区分开——避免把
     * 「还没发布」误报成「下载失败」。
     */
    suspend fun updateFromNetwork(years: List<Int> = selectedYears()): FestivalUpdateResult =
        withContext(Dispatchers.IO) {
            val ok = mutableListOf<Int>()
            val pending = mutableListOf<Int>()
            val fail = mutableListOf<Int>()
            val url = sourceUrl()
            if (!url.contains(YEAR_PLACEHOLDER)) {
                // 整份文件源（如 chinese-days 全量 JSON）：只下一次，按年份切分
                val all = runCatching { parseAny(download(url)) }.getOrNull()
                if (all.isNullOrEmpty()) {
                    fail.addAll(years)
                } else {
                    for (y in years) commit(y, all[y].orEmpty(), ok, pending)
                }
            } else {
                for (y in years) {
                    try {
                        val days = parseAny(download(url.replace(YEAR_PLACEHOLDER, y.toString())))[y].orEmpty()
                        commit(y, days, ok, pending)
                    } catch (_: DataNotPublished) {
                        pending.add(y)
                    } catch (_: Exception) {
                        fail.add(y)
                    }
                }
            }
            // 下载完顺手清一次：年份滑走后旧缓存就没用了
            pruneOldCache()
            FestivalUpdateResult(ok.sorted(), pending.sorted(), fail.sorted())
        }

    private fun commit(
        year: Int,
        days: List<FestivalDay>,
        ok: MutableList<Int>,
        pending: MutableList<Int>
    ) {
        if (days.isEmpty()) {
            pending.add(year)
        } else {
            cacheFile(year).writeText(normalize(year, days))
            ok.add(year)
        }
    }

    /** 该年份的文件还不存在/还没内容——「尚未发布」，不是失败。 */
    private class DataNotPublished : RuntimeException()

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.requestMethod = "GET"
        try {
            val code = conn.responseCode
            if (code == 404) throw DataNotPublished()
            if (code !in 200..299) throw RuntimeException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ---------- 缓存读写（归一化格式） ----------

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

    // ---------- 解析：按结构自动识别，不绑定具体厂商 ----------

    /** 解析任意主流节假日数据源返回体，返回「年份 → 条目」；无法识别返回空 Map。 */
    fun parseAny(text: String): Map<Int, List<FestivalDay>> = runCatching {
        val body = text.trimStart()
        if (body.startsWith("[")) {
            // 顶层就是日条目数组（少数 API 如此）
            return@runCatching readDayArray(JSONArray(body), null)
                .groupBy { it.date.year }
                .mapValues { (_, v) -> v.sortedBy { it.date } }
        }
        val root = JSONObject(body)
        val buckets = linkedMapOf<Int, MutableList<FestivalDay>>()
        fun put(year: Int, day: FestivalDay) {
            buckets.getOrPut(year) { mutableListOf() }.add(day)
        }

        // 形态 1/5：日条目数组（顶层 days / list / data，或 data.list / data.days）
        val array = dayArrayOf(root)
        if (array != null) {
            val explicitYear = root.optInt("year", 0).takeIf { it > 0 }
            for (d in readDayArray(array, explicitYear)) put(explicitYear ?: d.date.year, d)
            return@runCatching sortBuckets(buckets)
        }

        // 形态 2/3/4：对象形态（值可能是对象，也可能是 "英文,中文,等级" 这类字符串）
        val maps = mutableListOf<Pair<JSONObject, Boolean>>()
        root.optJSONObject("holiday")?.let { maps.add(it to true) }    // timor.tech
        root.optJSONObject("holidays")?.let { maps.add(it to true) }   // chinese-days
        root.optJSONObject("workdays")?.let { maps.add(it to false) }  // chinese-days
        if (maps.isEmpty() && root.keys().asSequence().any { parseDate(it) != null }) {
            maps.add(root to true)                                     // 日期为键（jiejiariapi）
        }
        val fallbackYear = root.optInt("year", 0).takeIf { it > 0 }
        for ((obj, offDefault) in maps) {
            for (d in readDateKeyedMap(obj, offDefault, fallbackYear)) put(d.date.year, d)
        }

        // 形态 6：以年份为键
        if (buckets.isEmpty()) {
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val year = key.toIntOrNull() ?: continue
                val arr = root.optJSONArray(key)
                    ?: root.optJSONObject(key)?.let { dayArrayOf(it) }
                if (arr != null) for (d in readDayArray(arr, year)) put(year, d)
            }
        }
        sortBuckets(buckets)
    }.getOrDefault(emptyMap())

    private fun sortBuckets(buckets: Map<Int, MutableList<FestivalDay>>): Map<Int, List<FestivalDay>> =
        buckets.mapValues { (_, v) -> v.sortedBy { it.date } }

    private fun dayArrayOf(root: JSONObject): JSONArray? =
        root.optJSONArray("days")
            ?: root.optJSONArray("list")
            ?: root.optJSONArray("data")
            ?: root.optJSONObject("data")?.let { dayArrayOf(it) }

    /** 读「日条目数组」。无名字的「非放假」条目直接丢弃——它们只是普通工作日（如 apihubs 的全年流水）。 */
    private fun readDayArray(arr: JSONArray, fallbackYear: Int?): List<FestivalDay> {
        val out = mutableListOf<FestivalDay>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val date = parseDate(o.opt("date")?.toString())
                ?: parseDate(o.opt("day")?.toString())
                ?: fallbackYear?.let { y -> monthDayOf(o)?.let { md -> md.atYear(y) } }
                ?: continue
            val off = offDayOf(o, true)
            val name = nameOf(o)
            if (!off && name == null) continue
            out.add(FestivalDay(name ?: "节假日", date, off))
        }
        return out
    }

    /** 读「以日期（或 MM-dd）为键」的对象；值可以是条目对象，也可以是 "英文,中文,等级" 这类字符串。 */
    private fun readDateKeyedMap(
        obj: JSONObject,
        offDefault: Boolean,
        fallbackYear: Int?
    ): List<FestivalDay> {
        val out = mutableListOf<FestivalDay>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            when (val v = obj.opt(key)) {
                is JSONObject -> {
                    val date = parseDate(v.opt("date")?.toString())
                        ?: parseDate(key)
                        ?: fallbackYear?.let { y -> monthDayOf(v)?.let { md -> md.atYear(y) } }
                        ?: continue
                    val off = offDayOf(v, offDefault)
                    val name = nameOf(v)
                    if (!off && name == null) continue
                    out.add(FestivalDay(name ?: "节假日", date, off))
                }
                // chinese-days："New Year's Day,元旦,1" → 取中文名（值也可能是纯中文名）
                is String -> {
                    val date = parseDate(key) ?: continue
                    val parts = v.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val name = (parts.getOrNull(1) ?: parts.firstOrNull())?.let { canonicalName(it) }
                    if (!offDefault && name == null) continue
                    out.add(FestivalDay(name ?: "节假日", date, offDefault))
                }
                else -> Unit
            }
        }
        return out
    }

    /** 条目名：优先取显式名字（统一成规范名）；取不到返回 null（由调用方决定是否丢弃）。 */
    private fun nameOf(o: JSONObject): String? {
        for (k in listOf("name", "name_cn", "holidayName", "festival", "title", "cn")) {
            val s = o.optString(k, "").trim()
            if (s.isNotEmpty() && s != "null") return canonicalName(s)
        }
        return null
    }

    /**
     * 节日名归一化。各数据源对同一节日的写法略有差异（chinese-days 作「清明」、
     * holiday-cn / jiejiariapi 作「清明节」），而「跟随节日」事件是按**名字**锚定的
     * （见 EventRepository.nextOccurrenceOf），不统一的话换数据源会让既有事件
     * 因名字对不上而停止滚动。读取时也走这里，所以**旧缓存无需重下**。
     */
    private fun canonicalName(raw: String): String = when (val s = raw.trim()) {
        "清明", "清明节" -> "清明节"
        "中秋", "中秋节" -> "中秋节"
        "端午", "端午节" -> "端午节"
        "五一", "五一劳动节", "劳动节" -> "劳动节"
        "十一", "国庆", "国庆节" -> "国庆节"
        else -> s
    }

    /**
     * 是否放假。按字段语义逐个尝试；数字码只在 0/1/2 这类明确语义下才采信
     * （apihubs 用 1=是 / 2=否，其余数字码含义不明则跳过，避免把普通工作日误判成假期）。
     */
    private fun offDayOf(o: JSONObject, fallback: Boolean): Boolean {
        for (k in listOf("isOffDay", "isHoliday", "offDay", "holiday_recess")) {
            asBool(o.opt(k))?.let { return it }
        }
        asBool(o.opt("holiday"))?.let { return it }
        asBool(o.opt("isWorkday"))?.let { return !it }
        asBool(o.opt("workday"))?.let { return !it }
        return fallback
    }

    private fun asBool(v: Any?): Boolean? = when (v) {
        is Boolean -> v
        is Number -> when (v.toInt()) {
            1 -> true
            0, 2 -> false
            else -> null
        }
        is String -> when (v.trim().lowercase()) {
            "true", "1", "y", "yes", "是" -> true
            "false", "0", "2", "n", "no", "否" -> false
            else -> null
        }
        else -> null
    }

    /** MM-dd 形式的键/字段 → MonthDay。 */
    private fun monthDayOf(o: JSONObject): java.time.MonthDay? =
        runCatching {
            val m = o.optInt("month", 0)
            val d = o.optInt("day", 0)
            if (m in 1..12 && d in 1..31) java.time.MonthDay.of(m, d) else null
        }.getOrNull()

    /** 兼容 "2026-01-01" / "2026/1/1" / "20260101" / 数字 20260101。 */
    private fun parseDate(raw: String?): LocalDate? {
        val s = raw?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
        return runCatching {
            if (Regex("^\\d{8}$").matches(s)) {
                LocalDate.of(s.substring(0, 4).toInt(), s.substring(4, 6).toInt(), s.substring(6, 8).toInt())
            } else {
                val m = Regex("^(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})$").find(s) ?: return null
                LocalDate.of(
                    m.groupValues[1].toInt(),
                    m.groupValues[2].toInt(),
                    m.groupValues[3].toInt()
                )
            }
        }.getOrNull()
    }
}
