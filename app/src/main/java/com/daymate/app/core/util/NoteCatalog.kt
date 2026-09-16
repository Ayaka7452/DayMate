package com.ayaka7452.daymate.core.util

/**
 * 周期管家「日常记录」的可选项目录。
 *
 * 选取依据（2026-09 调研 Clue / Flo / Period Tracker & Cycle Diary / 美柚 / WomanLog，
 * 以及一篇横向评测 13 款经期 App 的学术综述）：
 *  - 身体症状与情绪是各 App **覆盖率最高**的两类（综述中身体症状占全部记录项约 48%，
 *    头痛/痛经/痤疮/腰背痛/乳房胀痛是出现频次最高的条目）；
 *  - 出血与分泌物是周期的直接旁证（点滴出血、褐色分泌物是各 App 的标配）；
 *  - 性生活是国内外 App 的一致项（美柚「爱爱」、Clue「Sex life」、WomanLog）；
 *  - 自定义标签是行业标配（Clue custom tags、美柚「我的 → 设置 → 记录选项」新增专属标签）。
 *
 * 本目录**只取高频项**，刻意不铺开成 Clue 那样的 200 项清单——DayMate 的定位是轻量倒数日，
 * 记录功能要能在两次点击内完成。用户需要更细的条目时走「自定义」自由填写。
 *
 * 注意：本目录只服务「记录」本身，**不参与任何周期/排卵推算**（见 CycleNoteEntity 的说明）。
 */
object NoteCatalog {

    /**
     * 日常记录大类。[key] 落库，[label] 仅用于展示。
     * 顺序即界面排列顺序，刻意按「使用频率」排：身体症状与情绪是各 App 里记的最多的两类，
     * 出血/分泌物次之，性生活最私密放最后（多滚一屏也好过一进来就撞见）。
     */
    enum class Category(val key: String, val label: String) {
        SYMPTOM("SYMPTOM", "身体症状"),
        MOOD("MOOD", "情绪与状态"),
        BLEED("BLEED", "出血与分泌物"),
        SEX("SEX", "性生活"),
        CUSTOM("CUSTOM", "自定义");

        companion object {
            /** 落库 key → 枚举；未知 key 归入自定义，保证旧数据/异常数据永远读得出来。 */
            fun of(key: String?): Category =
                entries.firstOrNull { it.key == key } ?: CUSTOM
        }
    }

    /** 一个预置项。[key] 落库，[label] 为展示文案。 */
    data class Preset(val key: String, val label: String)

    /** 各大类下的预置项；自定义类无预置项（由用户输入文本）。 */
    val presets: Map<Category, List<Preset>> = mapOf(
        Category.SEX to listOf(
            Preset("sex_intercourse", "有性生活"),
            Preset("sex_drive_up", "性欲提升"),
            Preset("sex_drive_down", "性欲减退")
        ),
        Category.BLEED to listOf(
            Preset("bleed_spotting", "点滴出血"),
            Preset("bleed_brown", "褐色分泌物"),
            Preset("bleed_clots", "血块"),
            Preset("discharge_egg", "白带拉丝"),
            Preset("discharge_more", "白带增多")
        ),
        Category.SYMPTOM to listOf(
            Preset("sym_cramp", "痛经"),
            Preset("sym_backache", "腰酸"),
            Preset("sym_headache", "头痛"),
            Preset("sym_breast", "乳房胀痛"),
            Preset("sym_bloating", "腹胀"),
            Preset("sym_fatigue", "乏力"),
            Preset("sym_constipation", "便秘"),
            Preset("sym_acne", "痤疮")
        ),
        Category.MOOD to listOf(
            Preset("mood_happy", "开心"),
            Preset("mood_calm", "平静"),
            Preset("mood_low", "低落"),
            Preset("mood_irritable", "烦躁"),
            Preset("mood_anxious", "焦虑"),
            Preset("mood_stress", "压力大")
        ),
        Category.CUSTOM to emptyList()
    )

    /** 除自定义外、可在界面上选择的大类（自定义单独用一个输入框表达，不混在分段里）。 */
    val selectable: List<Category> = Category.entries.filter { it != Category.CUSTOM }

    /** 大类展示名；未知 key 返回兜底文案而非抛错。 */
    fun labelOf(categoryKey: String?): String = Category.of(categoryKey).label

    /** 自定义记录的落库大类 key。 */
    val CUSTOM_KEY: String = Category.CUSTOM.key

    /** 自定义文本长度上限（与备注一致，避免撑爆界面）。 */
    const val MAX_LABEL_LENGTH = 20

    /** 补充说明长度上限。 */
    const val MAX_NOTE_LENGTH = 50
}
