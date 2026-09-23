package com.ayaka7452.daymate.feature.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ayaka7452.daymate.Routes
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.data.db.EventEntity
import com.ayaka7452.daymate.data.db.FolderEntity
import com.ayaka7452.daymate.feature.common.FolderDialog
import com.ayaka7452.daymate.feature.common.PickFolderDialog
import com.ayaka7452.daymate.feature.common.matchesQuery
import com.ayaka7452.daymate.feature.common.SortModes
import com.ayaka7452.daymate.feature.common.eventDaysUntil
import com.ayaka7452.daymate.feature.common.moveItem
import com.ayaka7452.daymate.feature.common.sortEventsForDisplay
import com.ayaka7452.daymate.feature.common.targetIndexForAction
import com.ayaka7452.daymate.feature.home.EventRow
import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.i18n.Tr
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

    // 单事件「...」菜单 → 移动到文件夹（可选根目录移出）
    var singleMoveEventId by remember { mutableStateOf<Long?>(null) }

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

    // 今日节日/调休横幅（数据未下载时为 null，不显示；主页倒数卡片负责引导下载）。
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
    val eventSnapshot = eventList.toList()
    val displayEvents = remember(eventSnapshot, defaultSort) {
        sortEventsForDisplay(eventSnapshot, defaultSort) { eventDaysUntil(it.targetDateEpochDay) }
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
                Tr.s(R.string.folder_sort_hint),
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
                    title = { Text(Tr.s(R.string.folder_selected_n, totalSelected)) },
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Tr.s(R.string.common_done))
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            events.forEach { if (it.id !in selectedEventIds) selectedEventIds.add(it.id) }
                        }) { Text(Tr.s(R.string.common_select_all)) }
                        if (selectedEventIds.isNotEmpty()) {
                            TextButton(onClick = { showMoveDialog = true }) { Text(Tr.s(R.string.folder_move_in)) }
                        }
                        TextButton(
                            onClick = { if (vaultSet) vaultConfirmBatch = true else vaultNeedSetup = true },
                            enabled = selectedEventIds.isNotEmpty()
                        ) { Text(Tr.s(R.string.folder_move_vault)) }
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = totalSelected > 0
                        ) { Text(Tr.s(R.string.folder_move_bin)) }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(folder?.name ?: Tr.s(R.string.common_folder)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Tr.s(R.string.common_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            searchActive = !searchActive
                            if (!searchActive) searchQuery = ""
                        }) {
                            Icon(Icons.Default.Search, contentDescription = Tr.s(R.string.common_search))
                        }
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = Tr.s(R.string.folder_menu))
                            }
                            DropdownMenu(
                                modifier = Modifier.widthIn(min = 200.dp),
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(Tr.s(R.string.folder_batch)) },
                                    onClick = { menuExpanded = false; enterSelection() }
                                )
                                DropdownMenuItem(
                                    text = { Text(Tr.s(R.string.common_rename)) },
                                    onClick = { menuExpanded = false; showFolderDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text(Tr.s(R.string.folder_move_bin)) },
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
                    Icon(Icons.Default.Add, contentDescription = Tr.s(R.string.folder_new_event))
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize()) {
            if (searchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(Tr.s(R.string.folder_search_hint)) },
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
                    // 空状态只留文字提示：emoji 属于 UI 装饰，各机型渲染差异大，不再体现
                    Text(
                        Tr.s(R.string.folder_empty),
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
                        Tr.s(R.string.folder_not_found),
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
                                else if (event.specialType == "cycle") onNavigate("cycle")
                                else onNavigate("event_detail?eventId=${event.id}")
                            },
                            onMoveToVault = {
                                if (vaultSet) {
                                    scope.launch { container.vaultBridge.moveEventToVault(event.id) }
                                } else {
                                    vaultNeedSetup = true
                                }
                            },
                            onMoveToFolder = { singleMoveEventId = event.id },
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
            title = Tr.s(R.string.folder_edit_title),
            confirmLabel = Tr.s(R.string.common_save),
            onDismiss = {
                showFolderDialog = false
                pendingMoveAfterCreate = false
            },
            onSave = { name, icon ->
                scope.launch {
                    folder?.let { container.folderRepository.update(it.copy(name = name, icon = icon)) }
                    folder = container.folderRepository.getById(folderId)
                    // 写库完成后再关闭弹窗，否则列表可能来不及刷新（与主页一致）
                    showFolderDialog = false
                }
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

    if (singleMoveEventId != null) {
        PickFolderDialog(
            title = Tr.s(R.string.common_move_to),
            folders = allFolders
                .filter { it.id != folderId }
                .map { it.id to "${it.icon ?: "📁"}  ${it.name}" },
            // 根目录选项保留：可把事件移出当前文件夹
            showRoot = true,
            onDismiss = { singleMoveEventId = null },
            onPick = { targetFolderId ->
                scope.launch {
                    singleMoveEventId?.let {
                        container.eventRepository.moveToFolder(listOf(it), targetFolderId)
                    }
                    singleMoveEventId = null
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(Tr.s(R.string.folder_move_bin_title)) },
            text = { Text(Tr.s(R.string.folder_move_bin_desc, totalSelected)) },
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
                }) { Text(Tr.s(R.string.folder_move_bin)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(Tr.s(R.string.common_cancel)) }
            }
        )
    }

    if (showFolderDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showFolderDeleteConfirm = false },
            title = { Text(Tr.s(R.string.folder_move_bin_title)) },
            text = {
                Text(Tr.s(R.string.folder_delete_desc, folder?.name ?: ""))
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
                }) { Text(Tr.s(R.string.folder_move_bin)) }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDeleteConfirm = false }) { Text(Tr.s(R.string.common_cancel)) }
            }
        )
    }

    if (vaultNeedSetup) {
        AlertDialog(
            onDismissRequest = { vaultNeedSetup = false },
            title = { Text(Tr.s(R.string.folder_vault_not_setup)) },
            text = { Text(Tr.s(R.string.folder_vault_not_setup_desc)) },
            confirmButton = {
                TextButton(onClick = { vaultNeedSetup = false; onNavigate(Routes.VAULT) }) { Text(Tr.s(R.string.folder_go_setup)) }
            },
            dismissButton = {
                TextButton(onClick = { vaultNeedSetup = false }) { Text(Tr.s(R.string.common_cancel)) }
            }
        )
    }

    if (vaultConfirmBatch) {
        AlertDialog(
            onDismissRequest = { vaultConfirmBatch = false },
            title = { Text(Tr.s(R.string.folder_vault_confirm_title)) },
            text = { Text(Tr.s(R.string.folder_vault_confirm_desc, selectedEventIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        selectedEventIds.toList().forEach { container.vaultBridge.moveEventToVault(it) }
                    }
                    vaultConfirmBatch = false
                    exitSelection()
                }) { Text(Tr.s(R.string.folder_move_in_short)) }
            },
            dismissButton = {
                TextButton(onClick = { vaultConfirmBatch = false }) { Text(Tr.s(R.string.common_cancel)) }
            }
        )
    }
}
