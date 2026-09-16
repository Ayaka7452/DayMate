package com.ayaka7452.daymate.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import com.ayaka7452.daymate.R
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * emoji 目录：全应用共用的表情符号表。
 *
 * [common] 是默认展示的一组（即各功能原先各自维护的那几个），[categories] 是按类别整理的
 * 全量集合，展开后才展示。两者都经过 distinct()，避免重复项在两处同时出现。
 *
 * 说明：为了在低版本 Android（minSdk 26）上也能正常显示，这里刻意不收 Emoji 14.0 以后
 * 新增的符号（旧系统会渲染成方框）。
 */
object EmojiCatalog {

    /**
     * 文件夹图标推荐（默认展示）。
     *
     * 侧重「分类 / 生活领域」，两两成对便于一眼分辨文件夹用途；**不含节日专属符号**
     * （那是[festivalPresets] 的职责），也不把 📁/📂 之外的同义文件夹图标堆在一起。
     */
    val folderPresets: List<String> = listOf(
        "📁", "📂", "🗂️", "🗃️", "💼", "🎓",
        "🏠", "✈️", "🚗", "🎮", "📚", "🎵",
        "📷", "💰", "🍳", "💪", "🎨", "🌱"
    )

    /**
     * 节日卡片角标推荐（默认展示）。
     *
     * 侧重「节日 / 节令」，首位是 🎉，**不会出现文件夹图标**；
     * 覆盖春节(🧧🏮🐉)、生日(🎂)、圣诞(🎄)、万圣(🎃)、跨年(🎆)、中秋(🌕)、七夕(🎋)
     * 以及四季节令(🌸🍁❄️☀️)。
     */
    val festivalPresets: List<String> = listOf(
        "🎉", "🎊", "🎁", "🧧", "🏮", "🐉",
        "🎂", "🎄", "🎃", "🎆", "🌕", "🎋",
        "🍀", "🌸", "🍁", "❄️", "☀️", "⭐"
    )

    /** 全量，按类别分组（展开「更多」后展示）。 */
    val categories: List<Pair<Int, List<String>>> = listOf(
        R.string.emoji_cat_smileys to listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃", "😉", "😊",
            "😇", "🥰", "😍", "🤩", "😘", "😗", "😚", "😙", "😋", "😛", "😜", "🤪",
            "😝", "🤗", "🤭", "🤔", "🤐", "😐", "😑", "😶", "😏", "😒", "🙄", "😬",
            "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🥳", "🥺", "😢", "😭",
            "😤", "😠", "😡", "🤬", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓",
            "🤯", "😵", "🤠", "🥸", "😎", "🤓", "🧐", "😕", "😟", "🙁", "😮", "😯",
            "😲", "🤫", "🤥", "🧒", "👦", "👧", "👨", "👩", "👴", "👵", "👶", "🧓",
            "🙋", "🤦", "🤷", "💁", "🙆", "🙅", "💪", "👏", "🙌", "🤝", "👍", "👎",
            "👌", "✌️", "🤞", "🤟", "🤘", "👋", "🖐️", "✋", "👊", "✊", "🧠", "👀",
            "👁️", "👅", "👄", "🦷", "👣"
        ),
        R.string.emoji_cat_animals to listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮",
            "🐷", "🐸", "🐵", "🐔", "🐧", "🐦", "🐤", "🦆", "🦅", "🦉", "🦇", "🐺",
            "🐗", "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞", "🐢", "🐍", "🦎", "🦂",
            "🦀", "🦐", "🦑", "🐙", "🐠", "🐟", "🐬", "🐳", "🐋", "🦈", "🐊", "🐅",
            "🐆", "🦓", "🦍", "🐘", "🦏", "🐪", "🐫", "🦒", "🐃", "🐄", "🐎", "🐖",
            "🐑", "🐐", "🦌", "🐕", "🐩", "🐈", "🐓", "🦃", "🦚", "🦜", "🐇", "🐁",
            "🐀", "🐿️", "🦔",
            "🌵", "🎄", "🌲", "🌳", "🌴", "🌱", "🌿", "☘️", "🌷", "🌹", "🥀", "🌺",
            "🌼", "🌻", "🌞", "🌝", "🌛", "🌜", "🌚", "🌕", "🌖", "🌗", "🌘", "🌑",
            "🌒", "🌓", "🌔", "🌎", "🌍", "🌏", "🪐", "💫", "🌟", "⚡", "☄️", "💥",
            "🌪️", "🌈", "🌤️", "⛅", "🌥️", "☁️", "🌦️", "🌧️", "⛈️", "🌩️", "🌨️", "❄️",
            "☃️", "⛄", "🌬️", "💨", "💧", "💦", "☔", "☂️", "🌊"
        ),
        R.string.emoji_cat_food to listOf(
            "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🍈", "🍒", "🍑",
            "🥭", "🍍", "🥥", "🥝", "🍅", "🥑", "🥦", "🥬", "🥒", "🌶️", "🌽", "🥕",
            "🧄", "🧅", "🥔", "🍠", "🥐", "🥯", "🍞", "🥖", "🥨", "🧀", "🥚", "🍳",
            "🧈", "🥞", "🧇", "🥓", "🥩", "🍗", "🍖", "🌭", "🍔", "🍟", "🍕", "🥪",
            "🥙", "🧆", "🌮", "🌯", "🥗", "🥘", "🍝", "🍜", "🍲", "🍛", "🍣", "🍱",
            "🥟", "🍤", "🍙", "🍚", "🍘", "🍥", "🥠", "🍢", "🍡", "🍧", "🍨", "🍦",
            "🥧", "🧁", "🎂", "🍰", "🍮", "🍭", "🍬", "🍫", "🍿", "🍩", "🍪", "🌰",
            "🥜", "🍯", "🥛", "🍼", "🍵", "🧃", "🥤", "🍶", "🍺", "🍻", "🥂", "🍷",
            "🥃", "🍸", "🍹", "🧉", "🍾"
        ),
        R.string.emoji_cat_activity to listOf(
            "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱", "🏓", "🏸",
            "🥅", "🏒", "🏑", "🥍", "🏏", "🥊", "🥋", "🎽", "🛹", "🛼", "🛷", "⛸️",
            "🥌", "🎿", "⛷️", "🏂", "🏋️", "🤸", "🤺", "🤾", "🏌️", "🏇", "🧘", "🏄",
            "🏊", "🤽", "🚣", "🧗", "🚵", "🚴", "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️",
            "🏵️", "🎗️", "🎫", "🎟️", "🎪", "🤹", "🎭", "🩰", "🎨", "🎬", "🎤", "🎧",
            "🎼", "🎹", "🥁", "🎷", "🎺", "🎸", "🪕", "🎻", "🎲", "♟️", "🎳", "🎰",
            "🧩",
            "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚐", "🚚", "🚛", "🚜",
            "🦯", "🦽", "🦼", "🛴", "🚲", "🛵", "🏍️", "🛺", "🚨", "🚔", "🚍", "🚘",
            "🚖", "🚡", "🚠", "🚟", "🚃", "🚋", "🚞", "🚝", "🚄", "🚅", "🚈", "🚂",
            "🚆", "🚇", "🚊", "🚉", "🛫", "🛬", "🛩️", "💺", "🛰️", "🚀", "🛸", "🚁",
            "🛶", "⛵", "🚤", "🛥️", "🛳️", "⛴️", "🚢", "⚓", "⛽", "🚧", "🚦", "🚥",
            "🗺️", "🗿", "🗽", "🗼", "🏰", "🏯", "🏟️", "🎡", "🎢", "🎠", "⛲", "⛱️",
            "🏝️", "🏜️", "🌋", "⛰️", "🏔️", "🗻", "🏕️", "⛺", "🏡", "🏘️", "🏚️", "🏗️",
            "🏭", "🏢", "🏬", "🏣", "🏤", "🏥", "🏦", "🏨", "🏪", "🏫", "🏩", "💒",
            "🏛️", "⛪", "🕌", "🕍", "🛕", "🕋", "⛩️", "🛤️", "🛣️", "🗾", "🎑", "🏞️",
            "🌅", "🌄", "🌠", "🎇", "🎆", "🌇", "🌆", "🏙️", "🌃", "🌌", "🌉", "🌁"
        ),
        R.string.emoji_cat_objects to listOf(
            "⌚", "📱", "💻", "⌨️", "🖥️", "🖨️", "🖱️", "🖲️", "🕹️", "🗜️", "💽", "💾",
            "💿", "📀", "📼", "📷", "📸", "📹", "🎥", "📽️", "🎞️", "📞", "☎️", "📟",
            "📠", "📺", "📻", "🎙️", "🎚️", "🎛️", "🧭", "⏱️", "⏲️", "⏰", "🕰️", "⌛",
            "⏳", "📡", "🔋", "🔌", "💡", "🔦", "🕯️", "🪔", "🧯", "🛢️", "💸", "💵",
            "💴", "💶", "💷", "💰", "💳", "💎", "⚖️", "🧰", "🔧", "🔨", "⚒️", "🛠️",
            "⛏️", "🔩", "⚙️", "🧱", "⛓️", "🧲", "🔫", "💣", "🧨", "🪓", "🔪", "🗡️",
            "⚔️", "🛡️", "🚬", "⚰️", "⚱️", "🏺", "🔮", "📿", "🧿", "💈", "⚗️", "🔭",
            "🔬", "🕳️", "🩹", "🩺", "💊", "💉", "🩸", "🧬", "🦠", "🧫", "🧪", "🌡️",
            "🧹", "🧺", "🧻", "🚽", "🚰", "🚿", "🛁", "🛀", "🧼", "🪒", "🧽", "🧴",
            "🛎️", "🔑", "🗝️", "🚪", "🪑", "🛋️", "🛏️", "🛌", "🧸", "🖼️", "🛍️", "🛒",
            "🎈", "🎏", "🎀", "🎊", "🎎", "🏮", "🎐", "🧧", "✉️", "📩", "📨", "📧",
            "💌", "📥", "📤", "📦", "🏷️", "📪", "📫", "📬", "📭", "📮", "📯", "📜",
            "📃", "📄", "📑", "🧾", "📊", "📈", "📉", "🗒️", "🗓️", "📆", "📅", "🗑️",
            "📇", "🗃️", "🗳️", "🗄️", "📋", "🗂️", "🗞️", "📰", "📓", "📔", "📒", "📕",
            "📗", "📘", "📙", "📖", "🔖", "🧷", "🔗", "📎", "🖇️", "📐", "📏", "🧮",
            "📌", "📍", "✂️", "🖊️", "🖋️", "✒️", "🖌️", "🖍️", "📝", "✏️", "🔍", "🔎",
            "🔏", "🔐", "🔒", "🔓"
        ),
        R.string.emoji_cat_symbols to listOf(
            "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞",
            "💓", "💗", "💖", "💘", "💝", "💟", "☮️", "✝️", "☪️", "🕉️", "☸️", "✡️",
            "🔯", "🕎", "☯️", "☦️", "🛐", "⛎", "♈", "♉", "♊", "♋", "♌", "♍",
            "♎", "♏", "♐", "♑", "♒", "♓", "🆔", "⚛️", "🉑", "☢️", "☣️", "📴",
            "📳", "🈶", "🈚", "🈸", "🈺", "🈷️", "✴️", "🆚", "💮", "🉐", "㊙️", "㊗️",
            "🈴", "🈵", "🈹", "🈲", "🅰️", "🅱️", "🆎", "🆑", "🅾️", "🆘", "❌", "⭕",
            "🛑", "⛔", "📛", "🚫", "💯", "💢", "♨️", "🚷", "🚯", "🚳", "🚱", "🔞",
            "📵", "🚭", "❗", "❕", "❓", "❔", "‼️", "⁉️", "🔅", "🔆", "〽️", "⚠️",
            "🚸", "🔱", "⚜️", "🔰", "♻️", "✅", "🈯", "💹", "❇️", "✳️", "❎", "🌐",
            "💠", "Ⓜ️", "🌀", "💤", "🏧", "🚾", "♿", "🅿️", "🈳", "🈂️", "🛂", "🛃",
            "🛄", "🛅", "🚹", "🚺", "🚼", "🚻", "🚮", "🎦", "📶", "🈁", "🔣", "🔤",
            "🔡", "🔠", "🆖", "🆗", "🆙", "🆒", "🆕", "🆓", "0️⃣", "1️⃣", "2️⃣", "3️⃣",
            "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟", "🔢", "#️⃣", "*️⃣", "⏏️", "▶️",
            "⏸️", "⏯️", "⏹️", "⏺️", "⏭️", "⏮️", "⏩", "⏪", "⏫", "⏬", "◀️", "🔼",
            "🔽", "➡️", "⬅️", "⬆️", "⬇️", "↗️", "↘️", "↙️", "↖️", "↕️", "↔️", "↪️",
            "↩️", "⤴️", "⤵️", "🔀", "🔁", "🔂", "🔄", "🔃", "🎵", "🎶", "➕", "➖",
            "➗", "✖️", "♾️", "💲", "💱", "™️", "©️", "®️", "〰️", "➰", "➿", "🔚",
            "🔙", "🔛", "🔝", "🔜", "✔️", "☑️", "🔘", "🔴", "🟠", "🟡", "🟢", "🔵",
            "🟣", "⚫", "⚪", "🟤", "🔺", "🔻", "🔸", "🔹", "🔶", "🔷", "🔳", "🔲",
            "▪️", "▫️", "◾", "◽", "◼️", "◻️", "🟥", "🟧", "🟨", "🟩", "🟦", "🟪",
            "⬛", "⬜", "🟫", "🔈", "🔇", "🔉", "🔊", "🔔", "🔕", "📣", "📢", "💬",
            "💭", "🗯️", "♠️", "♣️", "♥️", "♦️", "🃏", "🎴", "🀄", "🕐"
        )
    )

    /** 全部 emoji（两套推荐 + 分类），去重。 */
    val all: List<String> =
        (folderPresets + festivalPresets + categories.flatMap { it.second }).distinct()
}

/**
 * 统一 emoji 选择器（文件夹图标/封面、节日卡片角标等共用）。
 *
 * 默认只展示 [presets]：按使用场景传入 [EmojiCatalog.folderPresets]（文件夹）或
 * [EmojiCatalog.festivalPresets]（节日角标）——两套推荐各有侧重，不共用同一份。
 * 点「更多」展开按类别分组的全量集合（区域限高、可滚动），再点「收起」返回。
 * 若当前选中项不在推荐集合里，会被临时补到最前面，保证始终可见可点。
 */
@Composable
fun EmojiPicker(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    presets: List<String> = EmojiCatalog.folderPresets,
    columns: Int = 6,
    expandedMaxHeight: Dp = 260.dp
) {
    var expanded by remember { mutableStateOf(false) }

    // 当前选中项不在推荐集合时补进默认视图，避免用户看不到自己选过的表情
    val defaultShown = remember(selected, presets) {
        if (selected.isBlank() || presets.contains(selected)) presets
        else listOf(selected) + presets
    }

    Column(modifier.fillMaxWidth()) {
        if (!expanded) {
            defaultShown.chunked(columns).forEach { row ->
                EmojiGridRow(row, selected, onSelect, columns)
            }
            TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.emoji_more))
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = expandedMaxHeight)
                    .verticalScroll(rememberScrollState())
            ) {
                for ((titleRes, list) in EmojiCatalog.categories) {
                    Text(
                        stringResource(titleRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    list.chunked(columns).forEach { row ->
                        EmojiGridRow(row, selected, onSelect, columns)
                    }
                }
            }
            TextButton(onClick = { expanded = false }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.common_collapse))
            }
        }
    }
}

/** 一行 emoji 单元格：格子在整行宽度内等分，末行补空占位以保持列对齐。 */
@Composable
private fun EmojiGridRow(
    items: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    columns: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 末行不足 columns 个时补空占位，避免剩余的格子被拉宽
        val cells = items + List((columns - items.size).coerceAtLeast(0)) { "" }
        for (em in cells) {
            if (em.isEmpty()) {
                Box(Modifier.weight(1f).aspectRatio(1f))
                continue
            }
            val isSelected = em == selected
            val shape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(shape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    )
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = shape
                    )
                    .clickable { onSelect(em) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    em,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}
