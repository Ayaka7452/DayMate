package com.ayaka7452.daymate.data.festival

import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.i18n.Tr

/**
 * 节日名的跨语言显示。
 *
 * ## 为什么不是「实时翻译」
 *
 * 节假日名字是一个**封闭集合**：主流数据源（holiday-cn、Nager.Date）每年新增的条目屈指可数，
 * 且都是「元旦 / 感恩节 / 秋夕」这类专有名词。实时机器翻译要联网、要 key、要计费，离线时还会
 * 静默退化成原文——为了一个封闭集合付出这些代价不划算，译名还会随服务商版本漂移。
 * 内置一张静态表则完全离线、结果稳定、可人工校对。所以这里**只查表，不联网**。
 *
 * ## 表放在 Kotlin 而不是 strings.xml
 *
 * 这是**数据**不是界面文案：4 个国家/地区 × 几十个节日 × 5 种目标语言 ≈ 200 条，放进
 * `strings.xml` 就要在 6 个文件里各加一遍同名 key（任何一处漏写都会静默回落英文），
 * 而它天然是「源语言名 → 各语言名」的二维结构，用 Map 表达最直接、最容易一眼校对。
 *
 * ## 解析顺序（三级回落）
 *
 *  1. 查表命中 → 用译文；
 *  2. 表里没有 → 用数据源自带的 `localName`（原产国语言名，Nager.Date 提供）；
 *  3. 再没有 → 用原始名。
 *
 * 第 2 级很关键：数据源将来新增的节日（或用户自填的私有数据源）我们没来得及收录，
 * 此时显示「现地语言原名」也比显示「空的译文」或「英文名」有信息量。
 *
 * ## 与「锚定名」的关系
 *
 * 本表的输入是 [FestivalDay.name]（锚定名），**输出只用于展示**：
 * `events.linkedFestival` 落库、`nextOccurrenceOf` 比对用的始终是锚定名，
 * 所以切语言绝不会让跟随节日的事件失锚。
 */
object HolidayNames {

    /** 五种目标语言的紧凑写法，顺序固定为 简 / 繁 / 粤 / 日 / 韩，避免逐条写 map key 写错。 */
    private fun x(hans: String, hant: String, yue: String, ja: String, ko: String): Map<String, String> =
        mapOf(
            "zh-Hans" to hans,
            "zh-Hant" to hant,
            "yue" to yue,
            "ja" to ja,
            "ko" to ko
        )

    /**
     * 中国节日的六语言写法。
     *
     * 必须要 en：[x] 不含英文，而中国源给的是中文名，英文界面下如果查不到 en 就会原样吐出中文。
     * 其余源（美国/日本/韩国）的名字本身就是英文，英文界面下本来就落回原名，无需额外条目。
     */
    private fun cn(
        en: String, hans: String, hant: String, yue: String, ja: String, ko: String
    ): Map<String, String> = x(hans, hant, yue, ja, ko) + ("en" to en)

    /**
     * key = 数据源里的原始节日名（与 `FestivalRepository.canonicalName` 归一化后的结果一致）。
     *
     * 四组条目的 key 形态不同但机制相同：中国源给中文名（`春节`），
     * 美/日/韩源给英文名（`Thanksgiving Day`）——无论哪种，都按同一张表查当前语言。
     */
    private val table: Map<String, Map<String, String>> = buildMap {
        // ---------- 中国 ----------
        // holiday-cn 给的是**简体**中文名，所以五个目标语言都要写：繁体/粤语要转字形
        // （否则繁體界面里会原样显示「春节」「国庆节」），英/日/韩要译名。
        put("元旦", cn("New Year's Day", "元旦", "元旦", "元旦", "元日", "새해"))
        put("春节", cn("Chinese New Year", "春节", "春節", "春節", "春節", "설날"))
        put("清明节", cn("Qingming Festival", "清明节", "清明節", "清明節", "清明節", "청명절"))
        put("劳动节", cn("Labour Day", "劳动节", "勞動節", "勞動節", "労働節", "노동절"))
        put("端午节", cn("Dragon Boat Festival", "端午节", "端午節", "端午節", "端午節", "단오절"))
        put("中秋节", cn("Mid-Autumn Festival", "中秋节", "中秋節", "中秋節", "中秋節", "추석"))
        put("国庆节", cn("National Day", "国庆节", "國慶節", "國慶節", "国慶節", "국경절"))

        // ---------- 美国（Nager.Date US） ----------
        put("New Year's Day", x("元旦", "元旦", "元旦", "元日", "새해"))
        put("Martin Luther King, Jr. Day", x("马丁·路德·金纪念日", "馬丁·路德·金紀念日", "馬丁·路德·金紀念日", "キング牧師記念日", "마틴 루터 킹 데이"))
        put("Presidents Day", x("总统日", "總統日", "總統日", "大統領の日", "대통령의 날"))
        put("Good Friday", x("耶稣受难日", "耶穌受難日", "耶穌受難日", "聖金曜日", "성금요일"))
        put("Memorial Day", x("阵亡将士纪念日", "陣亡將士紀念日", "陣亡將士紀念日", "戦没者追悼の日", "메모리얼 데이"))
        put("Juneteenth National Independence Day", x("六月节", "六月節", "六月節", "ジューンティーンス", "준틴스"))
        put("Independence Day", x("独立日", "獨立日", "獨立日", "独立記念日", "독립기념일"))
        put("Labour Day", x("劳动节", "勞動節", "勞動節", "レイバーデー", "노동절"))
        put("Columbus Day", x("哥伦布日", "哥倫布日", "哥倫布日", "コロンブスデー", "콜럼버스 데이"))
        put("Indigenous Peoples' Day", x("原住民日", "原住民日", "原住民日", "先住民の日", "원주민의 날"))
        put("Veterans Day", x("退伍军人节", "退伍軍人節", "退伍軍人節", "退役軍人の日", "재향군인의 날"))
        put("Thanksgiving Day", x("感恩节", "感恩節", "感恩節", "感謝祭", "추수감사절"))
        put("Christmas Day", x("圣诞节", "聖誕節", "聖誕節", "クリスマス", "크리스마스"))

        // ---------- 日本（Nager.Date JP） ----------
        put("Coming of Age Day", x("成人节", "成人節", "成人節", "成人の日", "성인의 날"))
        put("Foundation Day", x("建国纪念日", "建國紀念日", "建國紀念日", "建国記念の日", "건국기념일"))
        put("The Emperor's Birthday", x("天皇诞生日", "天皇誕生日", "天皇誕生日", "天皇誕生日", "천황탄생일"))
        put("Vernal Equinox Day", x("春分日", "春分日", "春分日", "春分の日", "춘분의 날"))
        put("Autumnal Equinox Day", x("秋分日", "秋分日", "秋分日", "秋分の日", "추분의 날"))
        put("Shōwa Day", x("昭和日", "昭和日", "昭和日", "昭和の日", "쇼와의 날"))
        put("Constitution Memorial Day", x("宪法纪念日", "憲法紀念日", "憲法紀念日", "憲法記念日", "헌법기념일"))
        put("Greenery Day", x("绿之日", "綠之日", "綠之日", "みどりの日", "녹색의 날"))
        put("Children's Day", x("儿童节", "兒童節", "兒童節", "こどもの日", "어린이날"))
        put("Marine Day", x("海之日", "海之日", "海之日", "海の日", "바다의 날"))
        put("Mountain Day", x("山之日", "山之日", "山之日", "山の日", "산의 날"))
        put("Respect for the Aged Day", x("敬老日", "敬老日", "敬老日", "敬老の日", "경로의 날"))
        put("Sports Day", x("体育节", "體育節", "體育節", "スポーツの日", "스포츠의 날"))
        put("Culture Day", x("文化节", "文化節", "文化節", "文化の日", "문화의 날"))
        put("Labour Thanksgiving Day", x("勤劳感谢日", "勤勞感謝日", "勤勞感謝日", "勤労感謝の日", "근로감사의 날"))

        // ---------- 韩国（Nager.Date KR） ----------
        put("Lunar New Year", x("春节", "春節", "農曆新年", "旧正月", "설날"))
        put("Independence Movement Day", x("三一节", "三一節", "三一節", "三一独立運動記念日", "삼일절"))
        put("Buddha's Birthday", x("佛诞节", "佛誕節", "佛誕節", "釈迦誕生日", "부처님 오신 날"))
        put("Local Election Day", x("地方选举日", "地方選舉日", "地方選舉日", "地方選挙の日", "지방선거일"))
        put("Constitution Day", x("制宪节", "制憲節", "制憲節", "制憲節", "제헌절"))
        put("Liberation Day", x("光复节", "光復節", "光復節", "光復節", "광복절"))
        put("Chuseok", x("秋夕", "秋夕", "秋夕", "秋夕", "추석"))
        put("National Foundation Day", x("开天节", "開天節", "開天節", "開天節", "개천절"))
        put("Hangul Day", x("韩文节", "韓文節", "韓文節", "ハングルの日", "한글날"))
    }

    /**
     * 当前界面语言对应的表内语言键。
     *
     * 用 `locale_tag` 资源而不是 `Locale.getDefault()`：前者正是 `LocaleWrap` 用来包装界面的
     * 那个语言，用户把 App 语言设成「粵語」但系统是英文时，两者会不一致。
     * 前缀判断与 `LocaleWrap.resolveLocale` 的 AUTO 分支保持同一套规则。
     */
    private fun langKey(): String {
        val tag = Tr.s(R.string.locale_tag).lowercase()
        return when {
            tag.startsWith("yue") || tag.startsWith("zh-hk") || tag.startsWith("zh-mo") -> "yue"
            tag.startsWith("zh-hant") || tag.startsWith("zh-tw") -> "zh-Hant"
            tag.startsWith("zh") -> "zh-Hans"
            tag.startsWith("ja") -> "ja"
            tag.startsWith("ko") -> "ko"
            else -> "en"
        }
    }

    /**
     * 节日显示名（三级回落：词表 → 原产国名 → 原始名）。
     *
     * [localName] 只在词表未命中时使用，且不做任何语言判断——数据源给出的原产国名，
     * 对「用该国语言的用户」恰好就是最正确的写法（如 ja 界面下的「元日」），
     * 对其它语言的用户也比英文名更有辨识度。
     */
    fun display(name: String, localName: String? = null): String =
        table[name]?.get(langKey())
            ?: localName?.takeIf { it.isNotBlank() }
            ?: name

    /** [FestivalDay] 的显示名。展示一律走这里，**不要**再直接读 `day.name`。 */
    fun display(day: FestivalDay): String = display(day.name, day.localName)

    /**
     * 把落库的锚定名还原成当前语言的显示名。
     *
     * 事件表里存的 `linkedFestival` 永远是锚定名（可能是中文、也可能是英文），
     * 展示时必须过一道这里，否则英文界面里会出现「Following 春节」这种混排。
     */
    fun displayLinked(anchor: String): String = display(anchor)
}
