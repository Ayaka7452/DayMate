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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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

/**
 * 小组件部署/重新配置页：添加小组件到桌面时弹出，选择该小组件显示的事件。
 * 选择「自动」则跟随最近倒数日；选择具体事件则固定显示该事件。
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
    var selected by remember { mutableStateOf(WidgetPrefs.eventForWidget(ctx, appWidgetId)) }
    var opacity by remember { mutableStateOf(WidgetPrefs.opacityFor(ctx, appWidgetId).toFloat()) }

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
                    // 两项配置都只写入该组件实例，与其他小组件互不影响
                    WidgetPrefs.setEventForWidget(ctx, appWidgetId, selected)
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
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                item {
                    OptionRow(
                        title = Tr.s(R.string.widget_config_auto),
                        subtitle = Tr.s(R.string.widget_config_auto_desc),
                        selected = selected == 0L,
                        onClick = { selected = 0L }
                    )
                }
                items(events, key = { it.id }) { ev ->
                    OptionRow(
                        title = ev.title,
                        subtitle = LocalDate.ofEpochDay(ev.targetDateEpochDay)
                            .format(DateTimeFormatter.ofPattern("yyyy/M/d")),
                        selected = selected == ev.id,
                        onClick = { selected = ev.id }
                    )
                }
            }
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
private fun OptionRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
