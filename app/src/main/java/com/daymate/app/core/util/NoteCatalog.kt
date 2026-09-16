package com.ayaka7452.daymate.core.util

import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.i18n.Tr

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
    enum class Category(val key: String, val labelRes: Int) {
        SYMPTOM("SYMPTOM", R.string.note_cat_symptom),
        MOOD("MOOD", R.string.note_cat_mood),
        BLEED("BLEED", R.string.note_cat_bleed),
        SEX("SEX", R.string.note_cat_sex),
        CUSTOM("CUSTOM", R.string.note_cat_custom);

        companion object {
            /** 落库 key → 枚举；未知 key 归入自定义，保证旧数据/异常数据永远读得出来。 */
            fun of(key: String?): Category =
                entries.firstOrNull { it.key == key } ?: CUSTOM
        }
    }

    /**
     * 一个预置项。[key] 落库，[labelRes] 为展示文案。
     *
     * 刻意**只存资源 id 不存字符串**：语言随时可切，把当前语言的文案固化进静态对象后，
     * 切语言必须重启进程才生效。落库时再 `Tr.s(labelRes)` 取当时的语言文本。
     */
    data class Preset(val key: String, val labelRes: Int)

    /**
     * 各大类下的预置项；自定义类无预置项（由用户输入文本）。
     *
     * 性生活一栏刻意**只记行为、不记性欲**。Clue 确有 `sex drive`（性欲高/低）项，但那建立在
     * 它会参与周期分析的前提上；DayMate 的日常记录**不参与任何推算**（见 CycleNoteEntity），
     * 记性欲就只剩留痕，而且「感受」混进「行为」栏语义不统一。保护与否才是真正有参考价值的
     * 信息（备孕 / 避孕），且它本就是性行为的一个属性。要记性欲的人可走「自定义」自由填写。
     */
    val presets: Map<Category, List<Preset>> = mapOf(
        Category.SEX to listOf(
            Preset("sex_protected", R.string.note_sex_protected),
            Preset("sex_unprotected", R.string.note_sex_unprotected)
        ),
        Category.BLEED to listOf(
            Preset("bleed_spotting", R.string.note_bleed_spotting),
            Preset("bleed_brown", R.string.note_bleed_brown),
            Preset("bleed_clots", R.string.note_bleed_clots),
            Preset("discharge_egg", R.string.note_discharge_egg),
            Preset("discharge_more", R.string.note_discharge_more)
        ),
        Category.SYMPTOM to listOf(
            Preset("sym_cramp", R.string.note_sym_cramp),
            Preset("sym_backache", R.string.note_sym_backache),
            Preset("sym_headache", R.string.note_sym_headache),
            Preset("sym_breast", R.string.note_sym_breast),
            Preset("sym_bloating", R.string.note_sym_bloating),
            Preset("sym_fatigue", R.string.note_sym_fatigue),
            Preset("sym_constipation", R.string.note_sym_constipation),
            Preset("sym_acne", R.string.note_sym_acne)
        ),
        Category.MOOD to listOf(
            Preset("mood_happy", R.string.note_mood_happy),
            Preset("mood_calm", R.string.note_mood_calm),
            Preset("mood_low", R.string.note_mood_low),
            Preset("mood_irritable", R.string.note_mood_irritable),
            Preset("mood_anxious", R.string.note_mood_anxious),
            Preset("mood_stress", R.string.note_mood_stress)
        ),
        Category.CUSTOM to emptyList()
    )

    /**
     * 互斥组：同组预置项不可能同时成立（key → 组名）。界面据此把同组项做成单选。
     *
     * 「有保护 / 无保护」一次只可能成立一个。多选 UI 若不特判，用户能同时勾上两项，
     * 落库就是一条自相矛盾的记录——这种矛盾数据后续既无法解释也无法修正。
     */
    val exclusiveGroups: Map<String, String> = mapOf(
        "sex_protected" to "sex_protection",
        "sex_unprotected" to "sex_protection"
    )

    /** 除自定义外、可在界面上选择的大类（自定义单独用一个输入框表达，不混在分段里）。 */
    val selectable: List<Category> = Category.entries.filter { it != Category.CUSTOM }

    /** 大类展示名；未知 key 返回兜底文案而非抛错。 */
    fun labelOf(categoryKey: String?): String = Tr.s(Category.of(categoryKey).labelRes)

    /** 预置项 key → 展示名资源；未知 key 返回 null。 */
    fun presetResOf(presetKey: String?): Int? =
        presetKey?.let { k -> presets.values.flatten().firstOrNull { it.key == k }?.labelRes }

    /**
     * 展示一条已落库记录的文案。
     *
     * 库里冗余存了写入当时的 `label`（保证老记录永远读得出来），但那是**写入时那种语言**的文本。
     * 预置项一律按 `presetKey` 用当前语言重译；只有自定义项（presetKey 为 null）才回落库文本。
     */
    fun displayLabel(presetKey: String?, storedLabel: String): String =
        presetResOf(presetKey)?.let { Tr.s(it) } ?: storedLabel

    /** 自定义记录的落库大类 key。 */
    val CUSTOM_KEY: String = Category.CUSTOM.key

    /** 自定义文本长度上限（与备注一致，避免撑爆界面）。 */
    const val MAX_LABEL_LENGTH = 20

    /** 补充说明长度上限。 */
    const val MAX_NOTE_LENGTH = 50
}
