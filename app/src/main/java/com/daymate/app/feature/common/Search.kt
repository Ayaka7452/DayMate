package com.ayaka7452.daymate.feature.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * 搜索匹配：标题或备注包含关键词（不区分大小写）。
 * 空白关键词恒为 false，由调用方决定空关键词时的展示（通常回退到普通列表）。
 */
fun matchesQuery(title: String?, note: String?, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return false
    val ql = q.lowercase()
    return title?.lowercase()?.contains(ql) == true ||
        note?.lowercase()?.contains(ql) == true
}

/** 仅备注命中而标题未命中（用于在结果行显示「命中备注」提示）。 */
fun noteHitOnly(title: String?, note: String?, query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return false
    val titleHit = title?.lowercase()?.contains(q) == true
    val noteHit = note?.lowercase()?.contains(q) == true
    return !titleHit && noteHit
}

/**
 * 生成高亮关键词的 AnnotatedString（标题/备注通用）。
 * 关键词为空时原样返回；命中片段用 primary 色 15% 透明度作背景。
 */
@Composable
fun highlightedText(text: String, query: String): AnnotatedString {
    val q = query.trim()
    if (q.isEmpty()) return AnnotatedString(text)
    val highlightStyle = SpanStyle(
        background = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    )
    return buildAnnotatedString {
        var index = 0
        val lowerText = text.lowercase()
        val lowerQ = q.lowercase()
        while (true) {
            val hit = lowerText.indexOf(lowerQ, index)
            if (hit < 0) {
                append(text.substring(index))
                break
            }
            append(text.substring(index, hit))
            withStyle(highlightStyle) { append(text.substring(hit, hit + q.length)) }
            index = hit + q.length
        }
    }
}
