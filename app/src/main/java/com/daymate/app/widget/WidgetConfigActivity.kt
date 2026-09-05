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
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // 默认取消结果：用户直接返回时，系统不会把小组件放到桌面
        setResult(RESULT_CANCELED, resultIntent(appWidgetId))

        val container = (application as? DayMateApp)?.container
        if (container == null) {
            finish()
            return
        }
        setDayMateContent {
            WidgetConfigScreen(
                container = container,
                appWidgetId = appWidgetId,
                onConfirm = { id ->
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
                        }
                    }
                    finish()
                },
                onCancel = {
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
    val events by container.eventRepository.observeAll().collectAsState(initial = emptyList())
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var selected by remember { mutableStateOf(WidgetPrefs.eventForWidget(ctx, appWidgetId)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加小组件") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                    WidgetPrefs.setEventForWidget(ctx, appWidgetId, selected)
                    onConfirm(appWidgetId)
                }) {
                    Text("完成")
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
                "选择这个小组件要显示的事件：",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                item {
                    OptionRow(
                        title = "自动（最近的倒数日）",
                        subtitle = "总是显示最近的一个事件",
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
            Text(
                "提示：也可以稍后在应用「设置 → 桌面小组件」中修改默认事件与卡片透明度。",
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
