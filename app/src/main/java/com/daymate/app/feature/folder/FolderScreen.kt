package com.ayaka7452.daymate.feature.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ayaka7452.daymate.Routes
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.data.db.EventEntity
import com.ayaka7452.daymate.data.db.FolderEntity
import com.ayaka7452.daymate.feature.common.FolderDialog
import com.ayaka7452.daymate.feature.common.PickFolderDialog
import com.ayaka7452.daymate.feature.common.matchesQuery
import com.ayaka7452.daymate.feature.common.ReorderActions
import com.ayaka7452.daymate.feature.common.SortModes
import com.ayaka7452.daymate.feature.common.eventDaysUntil
import com.ayaka7452.daymate.feature.common.moveItem
import com.ayaka7452.daymate.feature.common.sortEventsForDisplay
import com.ayaka7452.daymate.feature.common.targetIndexForAction
import com.ayaka7452.daymate.feature.home.EventRow
import com.ayaka7452.daymate.feature.home.SelectionDot
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.launch
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    container: AppContainer,
    folderId: Long,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    var folder by remember { mutableStateOf<FolderEntity?>(null) }
    LaunchedEffect(folderId) { folder = container.folderRepository.getById(folderId) }

    // 用 remember 固定 Flow 实例，避免每次重组重建 collectAsState 观察者导致返回时漏掉变更
    val eventsFlow = remember(folderId) { container.eventRepository.observeByFolder(folderId) }
    val events by eventsFlow.collectAsState(initial = emptyList())
    val allFoldersFlow = remember { container.folderRepository.observeAll() }
    val allFolders by allFoldersFlow.collectAsState(initial = emptyList())
    val vaultSet by container.settingsRepository.vaultPasswordSet
        .collectAsState(initial = false)

    var showFolderDialog by remember { mutableStateOf(false) }
    var pendingMoveAfterCreate by remember { mutableStateOf(false) }

    var selectionMode by remember { mutableStateOf(false) }
    val selectedEventIds = remember { mutableStateListOf<Long>() }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFolderDeleteConfirm by remember { mutableStateOf(false) }

    var vaultConfirmBatch by remember { mutableStateOf(false) }
    var vaultNeedSetup by remember { mutableStateOf(false) }

    // 页内搜索（标题 + 备注）
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    BackHandler(enabled = searchActive) {
        searchActive = false
        searchQuery = ""
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val totalSelected = selectedEventIds.size

    // 今日节日/调休横幅（数据未下载时为 null，不显示；主页倒数卡片负责引导下载）
    var todayFestival by remember {
        mutableStateOf<com.ayaka7452.daymate.data.festival.FestivalDay?>(null)
    }
    LaunchedEffect(Unit) {
        val t = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            container.festivalRepository.todayInfo(java.time.LocalDate.now())
        }
        todayFestival = t
    }

    // 排序模式：manual 才允许手动调整顺序
    val defaultSort by container.settingsRepository.defaultSort
        .collectAsState(initial = SortModes.REMAINING_ASC)
    val manualSort = defaultSort == SortModes.MANUAL

    // 拖拽排序用的可变镜像列表（拖拽中不同步，避免跳动）
    var isDragging by remember { mutableStateOf(false) }
    val eventList = remember { mutableStateListOf<EventEntity>() }
    LaunchedEffect(events) {
        if (!isDragging) {
            eventList.clear()
            eventList.addAll(events)
        }
    }

    // 事件显示列表：manual 保持手动顺序，其余按剩余天数排序
    val displayEvents = remember(eventList.toList(), defaultSort) {
        sortEventsForDisplay(eventList.toList(), defaultSort) { eventDaysUntil(it.targetDateEpochDay) }
    }

    // 页内搜索过滤（标题 + 备注）
    val query = searchQuery.trim()
    val shownEvents = remember(displayEvents, query) {
        if (query.isEmpty()) displayEvents
        else displayEvents.filter { matchesQuery(it.title, it.note, query) }
    }

    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val fk = from.key.toString()
        val tk = to.key.toString()
        if (fk.startsWith("e") && tk.startsWith("e")) {
            val fi = eventList.indexOfFirst { "e${it.id}" == fk }
            val ti = eventList.indexOfFirst { "e${it.id}" == tk }
            if (fi >= 0 && ti >= 0) eventList.moveItem(fi, ti)
        }
    }

    fun persistEventOrder() {
        scope.launch {
            eventList.forEachIndexed { index, e ->
                container.eventRepository.update(e.copy(sortIndex = index))
            }
        }
    }

    fun moveEvent(event: EventEntity, action: String) {
        if (!manualSort) {
            Toast.makeText(
                context,
                "请先在设置中切换为手动排序",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val index = eventList.indexOfFirst { it.id == event.id }
        if (index >= 0) {
            eventList.moveItem(index, targetIndexForAction(index, eventList.size, action))
            persistEventOrder()
        }
    }

    fun toggleEvent(id: Long) {
        if (id in selectedEventIds) selectedEventIds.remove(id) else selectedEventIds.add(id)
    }

    fun enterSelection() {
        selectedEventIds.clear()
        selectionMode = true
    }

    fun exitSelection() {
        selectedEventIds.clear()
        selectionMode = false
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("已选 $totalSelected 项") },
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "完成")
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            events.forEach { if (it.id !in selectedEventIds) selectedEventIds.add(it.id) }
                        }) { Text("全选") }
                        if (selectedEventIds.isNotEmpty()) {
                            TextButton(onClick = { showMoveDialog = true }) { Text("移入文件夹") }
                        }
                        TextButton(
                            onClick = { if (vaultSet) vaultConfirmBatch = true else vaultNeedSetup = true },
                            enabled = selectedEventIds.isNotEmpty()
                        ) { Text("移入 Vault") }
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = totalSelected > 0
                        ) { Text("移入回收站") }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(folder?.name ?: "文件夹") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            searchActive = !searchActive
                            if (!searchActive) searchQuery = ""
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "菜单")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("批量管理") },
                                    onClick = { menuExpanded = false; enterSelection() }
                                )
                                DropdownMenuItem(
                                    text = { Text("重命名") },
                                    onClick = { menuExpanded = false; showFolderDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("移入回收站") },
                                    onClick = {
                                        menuExpanded = false
                                        showFolderDeleteConfirm = true
                                    }
                                )
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(onClick = { onNavigate("event_form?folderId=$folderId") }) {
                    Icon(Icons.Default.Add, contentDescription = "新建事件")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize()) {
            if (searchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索标题或备注") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            if (events.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("📂", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "这个文件夹还是空的",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else if (searchActive && query.isNotEmpty() && shownEvents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "未找到相关事件",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // 今日节日/调休横幅（数据未下载时不在文件夹页引导，主页卡片负责提示）
                todayFestival?.let { tf ->
                    item(key = "festival_today") {
                        com.ayaka7452.daymate.feature.common.FestivalTodayBanner(
                            day = tf,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                items(shownEvents, key = { "e${it.id}" }) { event ->
                    ReorderableItem(reorderableState, key = "e${event.id}") {
                        val handle = if (selectionMode && manualSort) {
                            Modifier.draggableHandle(
                                onDragStarted = { isDragging = true },
                                onDragStopped = {
                                    isDragging = false
                                    persistEventOrder()
                                }
                            )
                        } else null
                        EventRow(
                            event = event,
                            selectionMode = selectionMode,
                            selected = event.id in selectedEventIds,
                            onClick = {
                                if (selectionMode) toggleEvent(event.id)
                                else onNavigate("event_form?eventId=${event.id}")
                            },
                            onMoveToVault = {
                                if (vaultSet) {
                                    scope.launch { container.vaultBridge.moveEventToVault(event.id) }
                                } else {
                                    vaultNeedSetup = true
                                }
                            },
                            onMoveToRecycleBin = {
                                scope.launch {
                                    container.eventRepository.softDeleteByIds(
                                        listOf(event.id),
                                        System.currentTimeMillis()
                                    )
                                }
                            },
                            onReorder = { action -> moveEvent(event, action) },
                            dragHandle = handle
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    )
                }
            }
            }
        }
    }

    if (showFolderDialog) {
        FolderDialog(
            initialName = folder?.name ?: "",
            initialIcon = folder?.icon ?: "📁",
            title = "编辑文件夹",
            confirmLabel = "保存",
            onDismiss = {
                showFolderDialog = false
                pendingMoveAfterCreate = false
            },
            onSave = { name, icon ->
                scope.launch {
                    folder?.let { container.folderRepository.update(it.copy(name = name, icon = icon)) }
                    folder = container.folderRepository.getById(folderId)
                }
                showFolderDialog = false
            },
            onDelete = {
                scope.launch {
                    folder?.let { container.folderRepository.delete(it) }
                    onBack()
                }
            }
        )
    }

    if (showMoveDialog) {
        PickFolderDialog(
            folders = allFolders
                .filter { it.id != folderId }
                .map { it.id to "${it.icon ?: "📁"}  ${it.name}" },
            onDismiss = { showMoveDialog = false },
            onPick = { targetFolderId ->
                scope.launch {
                    container.eventRepository.moveToFolder(selectedEventIds.toList(), targetFolderId)
                }
                showMoveDialog = false
                exitSelection()
            },
            onCreateNew = {
                showMoveDialog = false
                pendingMoveAfterCreate = true
                showFolderDialog = true
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("移入回收站？") },
            text = { Text("将把选中的 $totalSelected 项移入回收站，可在「回收站」中恢复或彻底删除。") },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selectedEventIds.toList()
                    scope.launch {
                        if (ids.isNotEmpty())
                            container.eventRepository.softDeleteByIds(
                                ids,
                                System.currentTimeMillis()
                            )
                        showDeleteConfirm = false
                        exitSelection()
                    }
                }) { Text("移入回收站") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showFolderDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showFolderDeleteConfirm = false },
            title = { Text("移入回收站？") },
            text = {
                Text("文件夹「${folder?.name ?: ""}」内的文件将移回主空间（不再属于该文件夹），仅文件夹本身会被移入回收站。")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        folder?.let {
                            container.eventRepository.unparentByFolders(listOf(it.id))
                            container.folderRepository.softDeleteByIds(
                                listOf(it.id),
                                System.currentTimeMillis()
                            )
                        }
                    }
                    showFolderDeleteConfirm = false
                    onBack()
                }) { Text("移入回收站") }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    if (vaultNeedSetup) {
        AlertDialog(
            onDismissRequest = { vaultNeedSetup = false },
            title = { Text("Vault 尚未设置") },
            text = { Text("请先进入 Vault 设置密码，之后才能将内容移入。") },
            confirmButton = {
                TextButton(onClick = { vaultNeedSetup = false; onNavigate(Routes.VAULT) }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { vaultNeedSetup = false }) { Text("取消") }
            }
        )
    }

    if (vaultConfirmBatch) {
        AlertDialog(
            onDismissRequest = { vaultConfirmBatch = false },
            title = { Text("移入 Vault？") },
            text = { Text("将把选中的 ${selectedEventIds.size} 个事件移入 Vault。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        selectedEventIds.toList().forEach { container.vaultBridge.moveEventToVault(it) }
                    }
                    vaultConfirmBatch = false
                    exitSelection()
                }) { Text("移入") }
            },
            dismissButton = {
                TextButton(onClick = { vaultConfirmBatch = false }) { Text("取消") }
            }
        )
    }
}
