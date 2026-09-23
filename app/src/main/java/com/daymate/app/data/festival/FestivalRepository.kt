package com.ayaka7452.daymate.data.festival

import android.content.Context
import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.i18n.Tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 内置节日数据源区域。
 *
 * [url] 里的 `{year}` 由下载器替换；[labelRes] 为设置页展示名。
 * 中国走 holiday-cn（跟随国务院通知），其余走 Nager.Date 的公共假日接口（免费、无需 key，
 * 返回顶层数组，正好落在 [FestivalRepository.parseAny] 已支持的「形态 7」上，无需新增解析分支）。
 */
enum class FestivalRegion(val url: String, val labelRes: Int) {
    CN(FestivalRepository.SOURCE_HOLIDAY_CN, R.string.settings_source_region_cn),
    US("https://date.nager.at/api/v3/PublicHolidays/{year}/US", R.string.settings_source_region_us),
    JP("https://date.nager.at/api/v3/PublicHolidays/{year}/JP", R.string.settings_source_region_jp),
    KR("https://date.nager.at/api/v3/PublicHolidays/{year}/KR", R.string.settings_source_region_kr);

    companion object {
        /**
         * 语言 → 建议区域。判断只看 `language`：zh / yue 都归中国（繁体、粤语用户同样过中国节日），
         * 其余未支持的语言回落美国（与「未支持语言显示英文」的兜底口径一致）。
         */
        fun forLanguage(language: String): FestivalRegion = when (language.lowercase()) {
            "ja" -> JP
            "ko" -> KR
            "zh", "yue" -> CN
            else -> US
        }
    }
}

/**
 * 单个节假日条目。
 *
 *  - [name]：**锚定名**。既是落库 `events.linkedFestival` 的比对依据，也是「跟随节日」事件
 *    滚动时的查找键，因此解析阶段就要归一化（见 `canonicalName`），并且**绝不随界面语言变化**——
 *    否则用户切一次语言，所有跟随节日的事件都会因名字对不上而静默停止滚动。
 *  - [localName]：数据源提供的「节日所属国语言」名（Nager.Date 的 `localName`）。
 *    只作展示兜底，界面语言的显示名由 [HolidayNames.resolve] 决定。
 *  - [isOffDay]：true=放假 / false=调休上班（调休日沿用所属节日名，仅靠本字段区分）。
 *  - [isEstimate]：仅用于表单快选的「预估日期」（缓存里没有该节日未来日期时按上次日期+1年推算，
 *    农历节日可能不准），下载到新一年数据后会自动校正，不会持久化到缓存。
 */
data class FestivalDay(
    val name: String,
    val date: LocalDate,
    val isOffDay: Boolean,
    val isEstimate: Boolean = false,
    val localName: String? = null
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
        val sep = Tr.s(R.string.festival_list_sep)
        val parts = buildList {
            if (okYears.isNotEmpty()) add(Tr.s(R.string.festival_summary_updated, okYears.joinToString(sep)))
            if (notPublishedYears.isNotEmpty())
                add(Tr.s(R.string.festival_summary_pending, notPublishedYears.joinToString(sep)))
            if (failedYears.isNotEmpty())
                add(Tr.s(R.string.festival_summary_failed, failedYears.joinToString(sep)))
        }
        return when {
            parts.isEmpty() -> Tr.s(R.string.festival_summary_no_years)
            // 全部都是「还没发布」：不报失败，明确告诉用户数据源还没出
            okYears.isEmpty() && failedYears.isEmpty() -> Tr.s(R.string.festival_summary_all_pending)
            else -> parts.joinToString(Tr.s(R.string.festival_sentence_sep))
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

        /** 自定义数据源的缓存键：用户手填的 URL 不隶属任何内置区域。 */
        private const val KEY_CUSTOM = "CUSTOM"

        /** 把年份列表渲染成「2025–2027 年」（连续）或「2025、2027 年」（不连续）。 */
        fun yearsText(years: List<Int>): String {
            if (years.isEmpty()) return Tr.s(R.string.festival_years_none)
            val s = years.sorted()
            return if (s.last() - s.first() == s.size - 1) {
                Tr.s(R.string.festival_years_range, s.first().toString(), s.last().toString())
            } else {
                Tr.s(R.string.festival_years_list, s.joinToString(Tr.s(R.string.festival_list_sep)))
            }
        }
    }

    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cacheDir = File(appContext.filesDir, CACHE_DIR)

    /**
     * 数据变更信号（自增计数）。源切换、下载完成、缓存清理时 +1。
     *
     * 主页与文件夹页的节日横幅都是在 `LaunchedEffect(Unit)` 里一次性读缓存的——没有这个信号，
     * 换完数据源就只能**重启 App** 才看得到新数据（v1.9.0 的实际表现）。
     * 用自增计数而非布尔量：连续两次变更（切源 + 下载完成）必须各触发一次重读。
     */
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    private fun changed() {
        _version.value += 1
    }

    init {
        // 老版本可能选过已下线的 timor.tech（现返回 403）——自动切回默认源，
        // 否则升级后下载会静默失败，而用户看不出是数据源挂了。
        val stored = prefs.getString(KEY_SOURCE, null)?.trim()
        if (stored != null && stored.contains("timor.tech", ignoreCase = true)) {
            prefs.edit().putString(KEY_SOURCE, DEFAULT_SOURCE).apply()
        }
        // 必须先把老命名迁到带源前缀的新命名，再清理过期年份——否则会把刚迁过来的当年数据误删
        migrateLegacyCache()
        // 清掉早已用不到的旧年份缓存（查询一律带 `>= 今天` 过滤，过去年份永远是死数据）
        pruneOldCache()
    }

    /**
     * 把老命名的 `{year}.json` 迁到 `{源}_{year}.json`。
     *
     * 缓存从 v1.9.1 起**按数据源分文件**（切到日本节日不再覆盖中国节日的缓存，切回来是秒开，
     * 也正是「切换数据源」弹窗里那句「已有缓存会保留」的兑现）。老格式里没有记录当时用的是哪个源，
     * 只能归到**当前源**名下——当前源就是用户此刻正在用的那个，归到别处才是真的丢数据。
     */
    private fun migrateLegacyCache() {
        runCatching {
            val key = cacheKey()
            cacheDir.listFiles()?.forEach { f ->
                val year = f.nameWithoutExtension.toIntOrNull() ?: return@forEach
                val target = File(cacheDir, "${key}_$year.json")
                if (target.exists()) runCatching { f.delete() }
                else runCatching { f.renameTo(target) }
            }
        }
    }

    // ---------- 数据源管理（设置页可编辑） ----------

    fun sourceUrl(): String = prefs.getString(KEY_SOURCE, DEFAULT_SOURCE) ?: DEFAULT_SOURCE

    /**
     * 换数据源。缓存按源分文件，所以这里**不动任何缓存文件**——切走再切回来，原数据仍在。
     * 只不过新源的缓存大概率是空的，调用方（设置页）需要紧接着触发一次下载。
     */
    fun setSourceUrl(url: String) {
        val next = url.trim()
        if (next == sourceUrl()) return
        prefs.edit().putString(KEY_SOURCE, next).apply()
        changed()
    }

    /** 内置区域的展示名；不是内置区域（用户自己填的 URL）时回落「自定义数据源」。 */
    fun sourceLabel(): String {
        val url = sourceUrl()
        val region = FestivalRegion.entries.firstOrNull { it.url == url }
        return if (region != null) Tr.s(region.labelRes) else Tr.s(R.string.settings_source_region_custom)
    }

    /** 当前源对应的内置区域；自定义 URL 返回 null。 */
    fun regionOfCurrentSource(): FestivalRegion? =
        FestivalRegion.entries.firstOrNull { it.url == sourceUrl() }

    /** 切换到某个内置区域的数据源。 */
    fun setRegion(region: FestivalRegion) = setSourceUrl(region.url)

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
                // 两种命名都认：老的 `2026.json`、现在的 `CN_2026.json`（取最后一个下划线之后那段）
                val y = f.nameWithoutExtension.substringAfterLast('_').toIntOrNull() ?: return@forEach
                if (y < minYear) runCatching { f.delete() }
            }
        }
    }

    /** 当前数据源对应的缓存键：内置区域用区域名，自定义 URL 统一用 CUSTOM。 */
    private fun cacheKey(): String = regionOfCurrentSource()?.name ?: KEY_CUSTOM

    /**
     * 某数据源某年的缓存文件。**键必须由调用方在下载开始时捕获并一路传下去**——
     * 下载是异步的，期间用户可能又切了源，此时若在写入时才取 cacheKey()，
     * 别的源的数据就会被写进新源的文件里（切一次源得到两份混合数据）。
     */
    private fun cacheFile(year: Int, key: String = cacheKey()): File =
        cacheDir.apply { mkdirs() }.let { File(it, "${key}_$year.json") }

    // ---------- 缓存状态 ----------

    /** 当前数据源下已缓存且有实际数据的年份（升序）。 */
    fun cachedYears(): List<Int> = cachedYearsOf(cacheKey())

    /** 指定数据源的已缓存年份。设置页只关心当前源，测试与调试需要按源取。 */
    fun cachedYearsOf(key: String): List<Int> =
        cacheDir.listFiles()
            ?.filter { it.name.startsWith("${key}_") }
            ?.mapNotNull { it.nameWithoutExtension.substringAfterLast('_').toIntOrNull() }
            ?.filter { loadYearOf(key, it).isNotEmpty() }
            ?.sorted()
            ?: emptyList()

    fun hasData(): Boolean = cachedYears().isNotEmpty()

    /** 某年份是否已有可用缓存（设置页逐年份显示状态用）。 */
    fun isCached(year: Int): Boolean = loadYear(year).isNotEmpty()

    fun dataStatusText(): String {
        val years = cachedYears()
        return if (years.isEmpty()) Tr.s(R.string.festival_status_none)
        else Tr.s(R.string.festival_status_cached, yearsText(years))
    }

    private fun loadYear(year: Int): List<FestivalDay> = loadYearOf(cacheKey(), year)

    private fun loadYearOf(key: String, year: Int): List<FestivalDay> {
        val f = cacheFile(year, key)
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
     * 当前数据源是否存在「调休上班日」（isOffDay=false）条目。
     * 补班是中国的调休产物（其他国家撞周末只补休、不补班），据此决定：
     *  - 设置页「休息及补班提醒」开关是否显示（非补班数据源整组隐藏）；
     *  - 节日卡片 / 小组件是否可能出现「班」角标与补班预告。
     * 按数据内容判断而非硬编码区域：中国源为 true；美日韩源为 false；
     * 自定义源里如果是含补班数据的中文接口也自动为 true。
     */
    fun hasMakeupData(): Boolean = allDays().any { !it.isOffDay }

    /**
     * [date] 所在的连续假期段长度（含 [date] 当天）。
     * 数据源里列出的放假日优先；数据源没列的周六/周日也按放假日计——
     * 这样美日韩等「只列法定假日、不列普通周末」的数据源，也能正确算出
     * 假日撞周末形成的连休（如周一假期 → 连休 3 天）；而中国调休日
     * （周六/周日补班，isOffDay=false）在数据里显式存在，不会被误计为休。
     * date 本身不是放假日时返回 0。
     */
    fun offDaySpanLength(date: LocalDate): Int {
        if (!isOffDay(date)) return 0
        var len = 1
        var d = date.minusDays(1)
        while (isOffDay(d)) { len++; d = d.minusDays(1) }
        d = date.plusDays(1)
        while (isOffDay(d)) { len++; d = d.plusDays(1) }
        return len
    }

    /** 单日是否为放假日：数据源有该日条目时以 isOffDay 为准（调休补班的周六日不算休），否则按普通周末。 */
    private fun isOffDay(date: LocalDate): Boolean {
        val entry = loadYear(date.year).firstOrNull { it.date == date }
        return entry?.isOffDay ?: (date.dayOfWeek == DayOfWeek.SATURDAY ||
            date.dayOfWeek == DayOfWeek.SUNDAY)
    }

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
     *
     * 「尚未发布」的未来年份若仍残留旧缓存，一律清掉（[dropStaleFutureCache]）——
     * 源说没发布、状态却说已缓存，自相矛盾（v1.11.1 修）。
     */
    suspend fun updateFromNetwork(years: List<Int> = selectedYears()): FestivalUpdateResult =
        withContext(Dispatchers.IO) {
            val ok = mutableListOf<Int>()
            val pending = mutableListOf<Int>()
            val fail = mutableListOf<Int>()
            val removed = mutableListOf<Int>()
            // 下载开始时就把目标源的键钉死：中途用户可能又切了源，写入必须落回发起时那个源
            val key = cacheKey()
            val url = sourceUrl()
            if (!url.contains(YEAR_PLACEHOLDER)) {
                // 整份文件源（如 chinese-days 全量 JSON）：只下一次，按年份切分
                val all = runCatching { parseAny(download(url)) }.getOrNull()
                if (all.isNullOrEmpty()) {
                    fail.addAll(years)
                } else {
                    for (y in years) commit(key, y, all[y].orEmpty(), ok, pending, removed)
                }
            } else {
                for (y in years) {
                    try {
                        val days = parseAny(download(url.replace(YEAR_PLACEHOLDER, y.toString())))[y].orEmpty()
                        commit(key, y, days, ok, pending, removed)
                    } catch (_: DataNotPublished) {
                        pending.add(y)
                        if (dropStaleFutureCache(key, y)) removed.add(y)
                    } catch (_: Exception) {
                        fail.add(y)
                    }
                }
            }
            // 下载完顺手清一次：年份滑走后旧缓存就没用了
            pruneOldCache()
            // removed 也要触发重读：清残留会改变「已缓存」年份列表，设置页得跟着刷新
            if (ok.isNotEmpty() || removed.isNotEmpty()) changed()
            FestivalUpdateResult(ok.sorted(), pending.sorted(), fail.sorted())
        }

    private fun commit(
        key: String,
        year: Int,
        days: List<FestivalDay>,
        ok: MutableList<Int>,
        pending: MutableList<Int>,
        removed: MutableList<Int>
    ) {
        if (days.isEmpty()) {
            pending.add(year)
            if (dropStaleFutureCache(key, year)) removed.add(year)
        } else {
            cacheFile(year, key).writeText(normalize(year, days))
            ok.add(year)
        }
    }

    /**
     * 源明确报告 [year]「尚未发布」（HTTP 404 或空内容）时，缓存里若仍留着该年份的数据，
     * 那份数据只可能来自旧数据源的占位/生成内容——v1.9.1 前缓存不分源，裸命名文件被
     * [migrateLegacyCache] 一律归到当前源名下（如 timor 时代生成的「未来年份占位」被归到 CN）。
     * 国务院未公布前不可能有真实数据：留着会让「已缓存 2027」与下载提示「2027 尚未发布」
     * 自相矛盾，占位日期还会静默驱动「跟随节日」事件——删掉，等真正发布后重下。
     *
     * 只清未来年份：今年/去年返回空更可能是 CDN/网络故障，保留缓存、下次下载自动恢复。
     *
     * @return 是否真的删掉了文件
     */
    private fun dropStaleFutureCache(key: String, year: Int, today: LocalDate = LocalDate.now()): Boolean {
        if (year <= today.year) return false
        val f = cacheFile(year, key)
        return f.exists() && runCatching { f.delete() }.getOrDefault(false)
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
            // 原产国语言名要一并落缓存，否则切回该源时只能显示英文名
            d.localName?.let { o.put("localName", it) }
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

    /**
     * 读「日条目数组」。无名字的「非放假」条目直接丢弃——它们只是普通工作日（如 apihubs 的全年流水）。
     *
     * 两处**只在字段存在时才生效**的收敛（对 holiday-cn 这类源零影响）：
     *  - `types`（Nager.Date）：只留含 `Public` / `Bank` 的条目。US 数据里混着
     *    `School` / `Authorities` / `Observance` 的州级纪念日（Truman Day、Lincoln's Birthday），
     *    它们在全国都不放假，留着只会让「下一个节日」卡片和快选列表冒出一串没人放假的节日。
     *  - 同一天同一节日的重复条目（Columbus Day 被拆成 `Public/global=false` 与 `Bank/global=true`
     *    两条）：去重时**优先保留标记为全国性的那条**。
     */
    private fun readDayArray(arr: JSONArray, fallbackYear: Int?): List<FestivalDay> {
        val raw = mutableListOf<Pair<FestivalDay, Boolean>>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (!isNationwideHoliday(o)) continue
            val date = parseDate(o.opt("date")?.toString())
                ?: parseDate(o.opt("day")?.toString())
                ?: fallbackYear?.let { y -> monthDayOf(o)?.let { md -> md.atYear(y) } }
                ?: continue
            val off = offDayOf(o, true)
            val name = nameOf(o)
            if (!off && name == null) continue
            val global = asBool(o.opt("global")) != false
            raw.add(
                FestivalDay(
                    name ?: Tr.s(R.string.festival_unnamed),
                    date,
                    off,
                    localName = o.optString("localName", "").trim()
                        .takeIf { it.isNotEmpty() && it != "null" }
                ) to global
            )
        }
        // sortedByDescending 是稳定排序：同一天同一节日只保留第一条，全国性的那条自然排在前面
        return raw.sortedByDescending { it.second }.map { it.first }.distinctBy { it.date to it.name }
    }

    /** 是否全国性假日。数据源没有 `types` 字段时一律保留（不拿单一厂商的字段去卡通用解析）。 */
    private fun isNationwideHoliday(o: JSONObject): Boolean {
        val types = o.optJSONArray("types") ?: return true
        for (i in 0 until types.length()) {
            val t = types.optString(i, "")
            if (t.equals("Public", ignoreCase = true) || t.equals("Bank", ignoreCase = true)) return true
        }
        return false
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
                    out.add(FestivalDay(name ?: Tr.s(R.string.festival_unnamed), date, off))
                }
                // chinese-days："New Year's Day,元旦,1" → 取中文名（值也可能是纯中文名）
                is String -> {
                    val date = parseDate(key) ?: continue
                    val parts = v.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val name = (parts.getOrNull(1) ?: parts.firstOrNull())?.let { canonicalName(it) }
                    if (!offDefault && name == null) continue
                    out.add(FestivalDay(name ?: Tr.s(R.string.festival_unnamed), date, offDefault))
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
