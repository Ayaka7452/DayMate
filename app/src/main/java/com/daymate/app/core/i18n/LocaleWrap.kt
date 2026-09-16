package com.ayaka7452.daymate.core.i18n

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import com.ayaka7452.daymate.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * App 界面语言。
 *
 * [tag] 为 BCP-47 语言标签，与 `res/values-b+*` 资源目录一一对应：
 *  - `zh-Hans` → `values-b+zh+Hans`（简体）
 *  - `zh-Hant` → `values-b+zh+Hant`（繁体）
 *  - `yue-Hant` → `values-b+yue+Hant`（粤语，口语字）
 *  - `ja` / `ko` → `values-ja` / `values-ko`
 *  - `en` → **base `values/`**（英文是兜底语言，任何不支持的语言都落在这里）
 */
enum class AppLanguage(val tag: String, val nativeName: String) {
    AUTO("auto", ""),
    ZH_HANS("zh-Hans", "中文（简体）"),
    ZH_HANT("zh-Hant", "中文（繁體）"),
    YUE("yue-Hant", "粵語"),
    EN("en", "English"),
    JA("ja", "日本語"),
    KO("ko", "한국어");

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: AUTO
    }
}

/**
 * 语言设置的落地存储。
 *
 * 刻意**不走 DataStore**：Activity#attachBaseContext 是同步调用，必须在任何协程/IO 完成前
 * 就知道该用哪个 Locale，DataStore 的异步特性会逼出一个「先用默认语言渲染一帧再重建」的
 * 闪烁窗口。SharedPreferences 同步可读，正好匹配这个使用场景；设置页的选项列表通过
 * [language] 这个 Flow 订阅变更。
 */
class LocaleStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 当前语言设置；变更时发射（设置页用）。 */
    val language: Flow<AppLanguage> = callbackFlow {
        trySend(current())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY) trySend(current())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun current(): AppLanguage = AppLanguage.fromTag(prefs.getString(KEY, null))

    fun set(language: AppLanguage) {
        prefs.edit().putString(KEY, language.tag).apply()
    }

    companion object {
        private const val FILE = "daymate_locale"
        private const val KEY = "app_language"

        /**
         * 供 `attachBaseContext` / 桌面小组件直接读取——不依赖 DI 容器，
         * 因为 Application 容器可能尚未构造完成。
         */
        fun readRaw(context: Context): AppLanguage = AppLanguage.fromTag(
            context.applicationContext
                .getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getString(KEY, null)
        )
    }
}

/** 语言 → Locale 解析与 Context 包装。 */
object LocaleWrap {

    /**
     * 「自动检测」下支持的系统语言。命中则用对应语言；**未命中一律英文**——
     * 这是产品明确要求：遇到不支持的语言默认切为 English，所以英文必须是 base 资源。
     *
     * 香港/澳门系统语言归到粤语（当地日常书写即粤语口语字），台湾/其它繁体归繁体。
     */
    private val HANT_PREFIXES = listOf("zh-hant", "zh-tw")
    private val YUE_PREFIXES = listOf("yue", "zh-hk", "zh-mo")

    fun resolveLocale(chosen: AppLanguage, system: Locale): Locale = when (chosen) {
        AppLanguage.ZH_HANS -> Locale.forLanguageTag("zh-Hans")
        AppLanguage.ZH_HANT -> Locale.forLanguageTag("zh-Hant")
        AppLanguage.YUE -> Locale.forLanguageTag("yue-Hant")
        AppLanguage.EN -> Locale.ENGLISH
        AppLanguage.JA -> Locale.JAPANESE
        AppLanguage.KO -> Locale.KOREAN
        AppLanguage.AUTO -> {
            val tag = system.toLanguageTag().lowercase(Locale.ROOT)
            when {
                YUE_PREFIXES.any { tag.startsWith(it) } -> Locale.forLanguageTag("yue-Hant")
                HANT_PREFIXES.any { tag.startsWith(it) } -> Locale.forLanguageTag("zh-Hant")
                tag.startsWith("zh") -> Locale.forLanguageTag("zh-Hans")
                tag.startsWith("ja") -> Locale.JAPANESE
                tag.startsWith("ko") -> Locale.KOREAN
                else -> Locale.ENGLISH
            }
        }
    }

    /** 系统语言（此处的 base context 尚未被包装，取到的是真实的系统语言）。 */
    private fun systemLocale(base: Context): Locale =
        base.resources.configuration.locales.takeIf { it.size() > 0 }?.get(0)
            ?: Locale.getDefault()

    private fun wrapWith(base: Context, target: Locale): Context {
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(target)
        cfg.setLocales(android.os.LocaleList(target))
        cfg.setLayoutDirection(target)
        return base.createConfigurationContext(cfg)
    }

    /**
     * 按当前语言设置包装 Context —— 在 `Activity#attachBaseContext` 里调用，
     * 这样 `stringResource` / `getString` 读到的都是目标语言的资源。
     */
    fun wrap(base: Context): Context =
        wrapWith(base, resolveLocale(LocaleStore.readRaw(base), systemLocale(base)))

    /**
     * 给**非 Activity** 的界面构建入口用（桌面小组件 RemoteViews、通知、Service）。
     * 这些地方拿到的是 Application/Receiver context，不经过 attachBaseContext，
     * 不包一次就会出现「App 内是英文、桌面小组件还是中文」的割裂。
     */
    fun localized(context: Context): Context {
        val target = resolveLocale(LocaleStore.readRaw(context), systemLocale(context))
        val current = context.resources.configuration.locales.takeIf { it.size() > 0 }?.get(0)
        return if (current?.language == target.language && current.country == target.country &&
            current.variant == target.variant
        ) {
            context
        } else {
            wrapWith(context, target)
        }
    }

    /**
     * 当前**生效**的界面 Locale（把「自动检测」也解析掉）。
     * 设置页判断「语言是否与节日源匹配」、以及按语言取日期格式，都走这里。
     */
    fun effective(context: Context): Locale =
        resolveLocale(LocaleStore.readRaw(context), systemLocale(context))

    /** 当前界面语言；供 `java.time` 的 `getDisplayName(TextStyle, Locale)` 用。 */
    fun locale(): Locale = Locale.forLanguageTag(Tr.s(R.string.locale_tag))

    /**
     * 按当前语言取 [DateTimeFormatter]。**pattern 本身也走资源**——
     * 中文「yyyy年M月d日」、英文「MMM d, yyyy」、韩文「yyyy년 M월 d일」差的不是 Locale 而是模板字面量，
     * 只改 Locale 的话英文界面里会原样渲染出「月」「日」两个汉字。
     */
    fun dateFormatter(patternRes: Int): DateTimeFormatter =
        DateTimeFormatter.ofPattern(Tr.s(patternRes), locale())
}

/**
 * 全局字符串读取入口（`Tr.s(R.string.xxx)` / `Tr.s(R.string.xxx, arg)`）。
 *
 * 为什么不直接用 Compose 的 `stringResource`：本项目有大量文案出现在
 * **onClick / LaunchedEffect / 普通函数 / 数据层** 等非 Composable 上下文里
 * （Dialog 的 status 文案、Toast、备份结果、数据维护报告、WebDAV 错误消息……），
 * `stringResource` 在这些位置根本编译不过。而切换语言后我们直接重启任务栈、
 * 全部界面重新构建，本来也不需要依赖 Composable 的响应式重组。
 *
 * 于是统一从 Application context 取一个「按当前语言包装过」的 Context 来读资源，
 * 任何位置都能用同一套写法。
 */
object Tr {
    @Volatile
    private var base: Context? = null

    @Volatile
    private var cached: Context? = null

    @Volatile
    private var cachedTag: String? = null

    /** 必须在任何取词之前调用（见 DayMateApp.onCreate 首行）。 */
    fun init(context: Context) {
        base = context.applicationContext
    }

    private const val PREFS_FILE = "daymate_locale"
    private const val PREFS_KEY = "app_language"

    private fun localized(): Context? {
        val b = base ?: return null
        // SharedPreferences 读的是内存快照，开销可忽略；只在语言变化时重建包装 Context。
        val tag = b.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getString(PREFS_KEY, null)
        if (cached == null || cachedTag != tag) {
            cached = LocaleWrap.localized(b)
            cachedTag = tag
        }
        return cached
    }

    operator fun get(resId: Int, vararg args: Any?): String {
        val ctx = localized() ?: return ""
        return if (args.isEmpty()) ctx.getString(resId) else ctx.getString(resId, *args)
    }

    /** 语义化别名，调用处读起来更明确：`Tr.s(R.string.home_title)`。 */
    fun s(resId: Int, vararg args: Any?): String = get(resId, *args)
}
