package com.ayaka7452.daymate.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ayaka7452.daymate.ComposeActivity
import com.ayaka7452.daymate.DayMateApp
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.i18n.Tr
import com.ayaka7452.daymate.core.log.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 多事件列表的槽位硬上限，与渲染层 MAX_LIST_SLOTS 保持一致。 */
private const val MULTI_LIMIT = 4

/**
 * 小组件部署/重新配置页：添加小组件到桌面时弹出。
 * 只有一份事件多选清单：不勾选显示最近的一个倒数日；勾一个以单卡片显示；
 * 勾多个成列表，顺序即勾选顺序（序号仅在本页已勾选行尾显示）。勾选上限按该组件当前尺寸算。
 */
class WidgetConfigActivity : ComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        AppLogger.log(
            this, "Config",
            "onCreate widgetId=$appWidgetId, device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, " +
                "api=${android.os.Build.VERSION.SDK_INT}, launcher=" + runCatching {
                    packageManager.resolveActivity(
                        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
                    )?.activityInfo?.packageName ?: "unknown"
                }.getOrDefault("unknown")
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            AppLogger.log(this, "Config", "onCreate 收到无效 widgetId，直接结束")
            finish()
            return
        }
        // 默认取消结果：用户直接返回时，系统不会把小组件放到桌面
        setResult(RESULT_CANCELED, resultIntent(appWidgetId))

        val container = (application as? DayMateApp)?.container
        if (container == null) {
            AppLogger.logError(this, "Config", "onCreate Application 容器尚未初始化，直接结束")
            finish()
            return
        }
        AppLogger.log(this, "Config", "配置页就绪，等待用户选择")
        setDayMateContent {
            WidgetConfigScreen(
                container = container,
                appWidgetId = appWidgetId,
                onConfirm = { id ->
                    AppLogger.log(this, "Config", "用户确认，widgetId=$id，回传 RESULT_OK")
                    setResult(RESULT_OK, resultIntent(id))
                    CoroutineScope(Dispatchers.IO).launch {
                        runCatching {
                            val ctx = this@WidgetConfigActivity
                            WidgetRenderer.renderOne(
                                ctx,
                                AppWidgetManager.getInstance(ctx),
                                id,
                                styleForWidgetId(id)
                            )
                            AppLogger.log(this@WidgetConfigActivity, "Config", "确认后首次渲染完成 widgetId=$id")
                        }.onFailure {
                            AppLogger.logError(this@WidgetConfigActivity, "Config", "确认后首次渲染异常 widgetId=$id", it)
                        }
                    }
                    finish()
                },
                onCancel = {
                    AppLogger.log(this, "Config", "用户取消，回传 RESULT_CANCELED")
                    setResult(RESULT_CANCELED, resultIntent(appWidgetId))
                    finish()
                }
            )
        }
    }

    private fun styleForWidgetId(appWidgetId: Int): WidgetRenderer.Style {
        val manager = AppWidgetManager.getInstance(this)
        val provider = manager.getAppWidgetInfo(appWidgetId)?.provider?.className ?: return WidgetRenderer.Style.WIDE
        return when {
            provider.endsWith("CountdownWidgetSmallProvider") -> WidgetRenderer.Style.SMALL
            provider.endsWith("CountdownWidgetSquareProvider") -> WidgetRenderer.Style.SQUARE
            else -> WidgetRenderer.Style.WIDE
        }
    }

    private fun resultIntent(appWidgetId: Int): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigScreen(
    container: AppContainer,
    appWidgetId: Int,
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit
) {
    // 用 remember 固定 Flow 实例，避免每次重组新建 Flow 导致观察者反复重建
    val events by remember { container.eventRepository.observeAll() }
        .collectAsState(initial = emptyList())
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // 勾选顺序即列表显示顺序
    var checkedOrder by remember { mutableStateOf(WidgetPrefs.selectedOrderFor(ctx, appWidgetId)) }
    var opacity by remember { mutableStateOf(WidgetPrefs.opacityFor(ctx, appWidgetId).toFloat()) }
    // 勾选上限按该组件当前尺寸算（与渲染层同口径），槽位硬上限 4
    val limit = remember { minOf(MULTI_LIMIT, WidgetRenderer.listSlotCount(ctx, appWidgetId)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Tr.s(R.string.widget_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Tr.s(R.string.common_back))
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = {
                    // 配置只写入该组件实例，与其他小组件互不影响；空列表 = 自动显示最近的一个
                    WidgetPrefs.setSelectedOrderFor(ctx, appWidgetId, checkedOrder)
                    WidgetPrefs.setOpacityFor(ctx, appWidgetId, opacity.toInt())
                    onConfirm(appWidgetId)
                }) {
                    Text(Tr.s(R.string.common_done))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                Tr.s(R.string.widget_config_pick),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                Tr.s(R.string.widget_config_pick_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 已勾选的排前面，按勾选顺序带序号——顺序即 widget 列表的显示顺序
            val ordered = events.sortedBy { ev -> checkedOrder.indexOf(ev.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(ordered, key = { it.id }) { ev ->
                    val id = ev.id
                    val checked = checkedOrder.contains(id)
                    // 勾满上限后未勾选项置灰，需取消已选的才能继续勾
                    val enabled = checked || checkedOrder.size < limit
                    val seq = checkedOrder.indexOf(id)
                    CheckRow(
                        title = ev.title,
                        subtitle = LocalDate.ofEpochDay(ev.targetDateEpochDay)
                            .format(DateTimeFormatter.ofPattern("yyyy/M/d")),
                        checked = checked,
                        enabled = enabled,
                        // 序号只在本页展示：已勾选的行尾显示勾选顺序，即 widget 列表的显示顺序
                        trailing = if (seq >= 0) {
                            {
                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                    Text(
                                        "${seq + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        } else null,
                        onCheck = { want ->
                            checkedOrder = when {
                                want -> checkedOrder + id
                                else -> checkedOrder - id
                            }
                        }
                    )
                }
            }
            Text(
                Tr.s(R.string.widget_config_multi_limit, limit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            if (checkedOrder.isNotEmpty()) {
                TextButton(onClick = { checkedOrder = emptyList() }) {
                    Text(Tr.s(R.string.widget_config_clear))
                }
            }
            Spacer(Modifier.padding(vertical = 4.dp))

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(Tr.s(R.string.widget_config_opacity), style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = opacity / 100f,
                    onValueChange = { opacity = (it * 100f).coerceIn(5f, 100f) },
                    valueRange = 0.05f..1f,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${opacity.toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                Tr.s(R.string.widget_config_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.padding(vertical = 16.dp))
        }
    }
}

@Composable
private fun CheckRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheck: (Boolean) -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheck(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onCheck(it) }, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outline
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}
