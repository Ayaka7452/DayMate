@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ayaka7452.daymate.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ayaka7452.daymate.Routes
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.core.util.CountdownCalculator
import com.ayaka7452.daymate.data.db.EventEntity
import com.ayaka7452.daymate.data.db.FolderEntity
import com.ayaka7452.daymate.feature.common.FolderDialog
import com.ayaka7452.daymate.feature.common.PickFolderDialog
import com.ayaka7452.daymate.feature.common.ReorderActions
import com.ayaka7452.daymate.feature.common.SortModes
import com.ayaka7452.daymate.feature.common.eventDaysUntil
import com.ayaka7452.daymate.feature.common.highlightedText
import com.ayaka7452.daymate.feature.common.matchesQuery
import com.ayaka7452.daymate.feature.common.moveItem
import com.ayaka7452.daymate.feature.common.noteHitOnly
import com.ayaka7452.daymate.feature.common.sortEventsForDisplay
import com.ayaka7452.daymate.feature.common.targetIndexForAction
import com.ayaka7452.daymate.feature.home.SelectionDot
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.launch
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onNavigate: (String) -> Unit
) {
    // 用 remember 固定 Flow 实例，避免每次重组都新建 Flow 导致 collectAsState 底层的
    // LaunchedEffect 反复取消/重建观察者，从而在从其它 Activity 返回时漏掉 Room 的变更通知。
    val eventsFlow = remember { container.eventRepository.observeRoot() }
    val events by eventsFlow.collectAsState(initial = emptyList())
    val foldersFlow = remember { container.folderRepository.observeAll() }
    val folders by foldersFlow.collectAsState(initial = emptyList())
    val vaultSet by container.settingsRepository.vaultPasswordSet
        .collectAsState(initial = false)

    var showAddSheet by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var folderDialogTarget by remember { mutableStateOf<FolderEntity?>(null) }
    var pendingMoveAfterCreate by remember { mutableStateOf(false) }

    // 搜索状态：顶栏放大镜展开为搜索框，输入即搜（内存过滤，含各文件夹内的事件）
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searchActive) {
        if (searchActive) searchFocus.requestFocus()
    }
    fun closeSearch() {
        searchActive = false
        searchQuery = ""
    }

    var selectionMode by remember { mutableStateOf(false) }
    val selectedEventIds = remember { mutableStateListOf<Long>() }
    val selectedFolderIds = remember { mutableStateListOf<Long>() }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFolderDeleteConfirm by remember { mutableStateOf(false) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }

    // Vault 移入相关弹窗状态
    var vaultConfirmEventId by remember { mutableStateOf<Long?>(null) }
    var vaultConfirmBatch by remember { mutableStateOf(false) }
    var vaultNeedSetup by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 排序模式：remaining_asc/remaining_desc/manual（manual 才允许手动调整顺序）
    val defaultSort by container.settingsRepository.defaultSort
        .collectAsState(initial = SortModes.REMAINING_ASC)
    val manualSort = defaultSort == SortModes.MANUAL

    // 拖拽排序用的可变镜像列表：Flow 更新时同步（拖拽中不同步，避免跳动）
    var isDragging by remember { mutableStateOf(false) }
    val folderList = remember { mutableStateListOf<FolderEntity>() }
    val eventList = remember { mutableStateListOf<EventEntity>() }
    LaunchedEffect(folders) {
        if (!isDragging) {
            folderList.clear()
            folderList.addAll(folders)
        }
    }
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

    // 搜索数据源：全部未删除事件（含文件夹内的）；结果按剩余天数升序
    val allEventsFlow = remember { container.eventRepository.observeAll() }
    val allEvents by allEventsFlow.collectAsState(initial = emptyList())
    val folderNameById = remember(folders) { folders.associate { it.id to it.name } }
    val searchResults = remember(allEvents, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else allEvents
            .filter { matchesQuery(it.title, it.note, searchQuery) }
            .sortedBy { eventDaysUntil(it.targetDateEpochDay) }
    }
    // 文件夹名也参与匹配，命中文件夹排在事件结果前面
    val searchFolderResults = remember(folders, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else folders.filter { it.name.lowercase().contains(searchQuery.trim().lowercase()) }
    }

    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val fk = from.key.toString()
        val tk = to.key.toString()
        when {
            fk.startsWith("f") && tk.startsWith("f") -> {
                val fi = folderList.indexOfFirst { "f${it.id}" == fk }
                val ti = folderList.indexOfFirst { "f${it.id}" == tk }
                if (fi >= 0 && ti >= 0) folderList.moveItem(fi, ti)
            }
            fk.startsWith("e") && tk.startsWith("e") -> {
                val fi = eventList.indexOfFirst { "e${it.id}" == fk }
                val ti = eventList.indexOfFirst { "e${it.id}" == tk }
                if (fi >= 0 && ti >= 0) eventList.moveItem(fi, ti)
            }
        }
    }

    fun persistFolderOrder() {
        scope.launch {
            folderList.forEachIndexed { index, f ->
                container.folderRepository.update(f.copy(sortIndex = index))
            }
        }
    }

    fun persistEventOrder() {
        scope.launch {
            eventList.forEachIndexed { index, e ->
                container.eventRepository.update(e.copy(sortIndex = index))
            }
        }
    }

    fun requireManualThen(action: () -> Unit) {
        if (manualSort) action() else Toast.makeText(
            context,
            "请先在设置中切换为手动排序",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun moveEvent(event: EventEntity, action: String) = requireManualThen {
        val index = eventList.indexOfFirst { it.id == event.id }
        if (index >= 0) {
            eventList.moveItem(index, targetIndexForAction(index, eventList.size, action))
            persistEventOrder()
        }
    }

    fun moveFolder(folder: FolderEntity, action: String) = requireManualThen {
        val index = folderList.indexOfFirst { it.id == folder.id }
        if (index >= 0) {
            folderList.moveItem(index, targetIndexForAction(index, folderList.size, action))
            persistFolderOrder()
        }
    }

    val totalSelected = selectedEventIds.size + selectedFolderIds.size

    fun toggleEvent(id: Long) {
        if (id in selectedEventIds) selectedEventIds.remove(id) else selectedEventIds.add(id)
    }

    fun toggleFolder(id: Long) {
        if (id in selectedFolderIds) selectedFolderIds.remove(id) else selectedFolderIds.add(id)
    }

    fun enterSelection() {
        selectedEventIds.clear()
        selectedFolderIds.clear()
        selectionMode = true
    }

    fun exitSelection() {
        selectedEventIds.clear()
        selectedFolderIds.clear()
        selectionMode = false
    }

    BackHandler(enabled = searchActive || selectionMode) {
        if (searchActive) closeSearch() else exitSelection()
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("已选 $totalSelected 项") },
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "完成"
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            events.forEach { if (it.id !in selectedEventIds) selectedEventIds.add(it.id) }
                            folders.forEach { if (it.id !in selectedFolderIds) selectedFolderIds.add(it.id) }
                        }) { Text("全选") }
                        if (selectedEventIds.isNotEmpty()) {
                            TextButton(onClick = { showMoveDialog = true }) { Text("移入文件夹") }
                        }
                        TextButton(
                            onClick = { if (vaultSet) vaultConfirmBatch = true else vaultNeedSetup = true },
                            enabled = totalSelected > 0
                        ) { Text("移入 Vault") }
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = totalSelected > 0
                        ) { Text("移入回收站") }
                    }
                )
            } else if (searchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索事件标题或备注") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocus)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { closeSearch() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "退出搜索"
                            )
                        }
                    },
                    actions = {
                        if (searchQuery.isNotBlank()) {
                            TextButton(onClick = { searchQuery = "" }) { Text("清空") }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("DayMate", fontFamily = FontFamily.Cursive) },
                    actions = {
                        IconButton(onClick = { searchActive = true }) {
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
                                    text = { Text("Vault") },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigate(Routes.VAULT)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("设置") },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigate(Routes.SETTINGS)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("关于") },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigate(Routes.ABOUT)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("回收站") },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigate(Routes.RECYCLE_BIN)
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
                FloatingActionButton(onClick = { showAddSheet = true }) {
                    Icon(Icons.Default.Add, contentDescription = "新建")
                }
            }
        }
    ) { padding ->
        when {
            searchActive && searchQuery.isNotBlank() -> {
                if (searchResults.isEmpty() && searchFolderResults.isEmpty()) {
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
                        items(searchFolderResults, key = { "sf${it.id}" }) { folder ->
                            FolderRow(
                                folder = folder,
                                onClick = { onNavigate("folder/${folder.id}") },
                                onLongClick = {
                                    folderDialogTarget = folder
                                    showFolderDialog = true
                                },
                                onMoveToRecycleBin = {
                                    folderToDelete = folder
                                    showFolderDeleteConfirm = true
                                }
                            )
                            ListItemDivider()
                        }
                        items(searchResults, key = { "e${it.id}" }) { event ->
                            EventRow(
                                event = event,
                                onClick = { onNavigate("event_form?eventId=${event.id}") },
                                onMoveToVault = {
                                    if (vaultSet) vaultConfirmEventId = event.id else vaultNeedSetup = true
                                },
                                onMoveToRecycleBin = {
                                    scope.launch {
                                        container.eventRepository.softDeleteByIds(
                                            listOf(event.id),
                                            System.currentTimeMillis()
                                        )
                                    }
                                },
                                searchQuery = searchQuery,
                                folderBadge = event.folderId?.let { folderNameById[it] },
                                noteHit = noteHitOnly(event.title, event.note, searchQuery)
                            )
                            ListItemDivider()
                        }
                    }
                }
            }
            events.isEmpty() && folders.isEmpty() -> {
                EmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
            else -> {
                LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(folderList, key = { "f${it.id}" }) { folder ->
                    ReorderableItem(reorderableState, key = "f${folder.id}") {
                        val handle = if (selectionMode && manualSort) {
                            Modifier.draggableHandle(
                                onDragStarted = { isDragging = true },
                                onDragStopped = {
                                    isDragging = false
                                    persistFolderOrder()
                                }
                            )
                        } else null
                        FolderRow(
                            folder = folder,
                            selectionMode = selectionMode,
                            selected = folder.id in selectedFolderIds,
                            onClick = {
                                if (selectionMode) toggleFolder(folder.id)
                                else onNavigate("folder/${folder.id}")
                            },
                            onLongClick = {
                                if (!selectionMode) {
                                    folderDialogTarget = folder
                                    showFolderDialog = true
                                }
                            },
                            onMoveToRecycleBin = {
                                folderToDelete = folder
                                showFolderDeleteConfirm = true
                            },
                            onReorder = { action -> moveFolder(folder, action) },
                            dragHandle = handle
                        )
                    }
                    ListItemDivider()
                }
                items(displayEvents, key = { "e${it.id}" }) { event ->
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
                                if (vaultSet) vaultConfirmEventId = event.id else vaultNeedSetup = true
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
                    ListItemDivider()
                }
                }
            }
        }
    }

    if (showAddSheet) {
        AddSheet(
            onDismiss = { showAddSheet = false },
            onCreateEvent = {
                showAddSheet = false
                onNavigate(Routes.EVENT_FORM)
            },
            onCreateFolder = {
                showAddSheet = false
                folderDialogTarget = null
                pendingMoveAfterCreate = false
                showFolderDialog = true
            }
        )
    }

    if (showFolderDialog) {
        FolderDialog(
            initialName = folderDialogTarget?.name ?: "",
            initialIcon = folderDialogTarget?.icon ?: "📁",
            title = if (folderDialogTarget == null) "新建文件夹" else "编辑文件夹",
            confirmLabel = if (folderDialogTarget == null) "创建" else "保存",
            onDismiss = {
                showFolderDialog = false
                pendingMoveAfterCreate = false
            },
            onSave = { name, icon ->
                scope.launch {
                    if (folderDialogTarget == null) {
                        val newId = container.folderRepository.add(
                            FolderEntity(name = name, icon = icon)
                        )
                        if (pendingMoveAfterCreate) {
                            container.eventRepository.moveToFolder(
                                selectedEventIds.toList(),
                                newId
                            )
                            pendingMoveAfterCreate = false
                            exitSelection()
                        }
                    } else {
                        val f = folderDialogTarget!!
                        container.folderRepository.update(f.copy(name = name, icon = icon))
                    }
                    // 写库完成后再关闭弹窗，否则列表不会立即刷新
                    showFolderDialog = false
                }
            },
            onDelete = if (folderDialogTarget != null) {
                {
                    scope.launch { container.folderRepository.delete(folderDialogTarget!!) }
                    showFolderDialog = false
                }
            } else null
        )
    }

    if (showMoveDialog) {
        PickFolderDialog(
            folders = folders.map { it.id to "${it.icon ?: "📁"}  ${it.name}" },
            onDismiss = { showMoveDialog = false },
            onPick = { folderId ->
                scope.launch {
                    container.eventRepository.moveToFolder(selectedEventIds.toList(), folderId)
                    // 写库完成后再关闭弹窗并退出选择，否则列表不会立即刷新
                    showMoveDialog = false
                    exitSelection()
                }
            },
            onCreateNew = {
                showMoveDialog = false
                folderDialogTarget = null
                pendingMoveAfterCreate = true
                showFolderDialog = true
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("移入回收站？") },
            text = {
                Column {
                    Text("将把 $totalSelected 项移入回收站，可在「回收站」中恢复或彻底删除（清空后不可恢复）。")
                    if (selectedFolderIds.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "注意：被删除文件夹内的文件将移回主空间（不再属于该文件夹），仅文件夹本身进入回收站。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val eventIds = selectedEventIds.toList()
                    val folderIds = selectedFolderIds.toList()
                    scope.launch {
                        val ts = System.currentTimeMillis()
                        if (eventIds.isNotEmpty())
                            container.eventRepository.softDeleteByIds(eventIds, ts)
                        if (folderIds.isNotEmpty()) {
                            container.eventRepository.unparentByFolders(folderIds)
                            container.folderRepository.softDeleteByIds(folderIds, ts)
                        }
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

    if (showFolderDeleteConfirm && folderToDelete != null) {
        AlertDialog(
            onDismissRequest = { showFolderDeleteConfirm = false; folderToDelete = null },
            title = { Text("移入回收站？") },
            text = {
                Text("文件夹「${folderToDelete!!.name}」内的文件将移回主空间（不再属于该文件夹），仅文件夹本身会被移入回收站。")
            },
            confirmButton = {
                TextButton(onClick = {
                    val fid = folderToDelete!!.id
                    scope.launch {
                        container.eventRepository.unparentByFolders(listOf(fid))
                        container.folderRepository.softDeleteByIds(
                            listOf(fid),
                            System.currentTimeMillis()
                        )
                    }
                    showFolderDeleteConfirm = false
                    folderToDelete = null
                }) { Text("移入回收站") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFolderDeleteConfirm = false
                    folderToDelete = null
                }) { Text("取消") }
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

    if (vaultConfirmEventId != null) {
        AlertDialog(
            onDismissRequest = { vaultConfirmEventId = null },
            title = { Text("移入 Vault？") },
            text = { Text("该事件将被移入 Vault 空间，之后需输入密码才能查看。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vaultConfirmEventId?.let { container.vaultBridge.moveEventToVault(it) } }
                    vaultConfirmEventId = null
                }) { Text("移入") }
            },
            dismissButton = {
                TextButton(onClick = { vaultConfirmEventId = null }) { Text("取消") }
            }
        )
    }

    if (vaultConfirmBatch) {
        AlertDialog(
            onDismissRequest = { vaultConfirmBatch = false },
            title = { Text("移入 Vault？") },
            text = { Text("将把选中的 ${selectedEventIds.size} 个事件移入 Vault（文件夹暂不支持移入 Vault）。") },
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

@Composable
fun EventRow(
    event: EventEntity,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onMoveToVault: (() -> Unit)? = null,
    onMoveToRecycleBin: (() -> Unit)? = null,
    onReorder: ((String) -> Unit)? = null,
    dragHandle: Modifier? = null,
    searchQuery: String = "",
    folderBadge: String? = null,
    noteHit: Boolean = false
) {
    val days = CountdownCalculator.daysUntil(event.targetDateEpochDay)
    val isFuture = days >= 0
    val text = CountdownCalculator.formatCountdown(
        event.targetDateEpochDay,
        event.displayUnit,
        event.refDays
    )

    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            SelectionDot(selected = selected)
            Spacer(Modifier.width(10.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(event.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (searchQuery.isBlank()) AnnotatedString(event.title)
                else highlightedText(event.title, searchQuery),
                style = MaterialTheme.typography.bodyLarge
            )
            if (!event.note.isNullOrBlank()) {
                Text(
                    text = if (searchQuery.isBlank()) AnnotatedString(event.note)
                    else highlightedText(event.note, searchQuery),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        if (noteHit) {
            Text(
                text = "命中备注",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFuture) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary
        )
        if (folderBadge != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = folderBadge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        if (dragHandle != null) {
            IconButton(modifier = dragHandle, onClick = {}) {
                Text(
                    "⠿",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        if (!selectionMode && onMoveToVault != null) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (onReorder != null) {
                        DropdownMenuItem(
                            text = { Text("上移") },
                            onClick = { menuExpanded = false; onReorder(ReorderActions.UP) }
                        )
                        DropdownMenuItem(
                            text = { Text("下移") },
                            onClick = { menuExpanded = false; onReorder(ReorderActions.DOWN) }
                        )
                        DropdownMenuItem(
                            text = { Text("移到顶部") },
                            onClick = { menuExpanded = false; onReorder(ReorderActions.TOP) }
                        )
                        DropdownMenuItem(
                            text = { Text("移到底部") },
                            onClick = { menuExpanded = false; onReorder(ReorderActions.BOTTOM) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("移入 Vault") },
                        onClick = {
                            menuExpanded = false
                            onMoveToVault?.invoke()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("移入回收站") },
                        onClick = {
                            menuExpanded = false
                            onMoveToRecycleBin?.invoke()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FolderRow(
    folder: FolderEntity,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onMoveToRecycleBin: (() -> Unit)? = null,
    onReorder: ((String) -> Unit)? = null,
    dragHandle: Modifier? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (selectionMode) null else onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            SelectionDot(selected = selected)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = folder.icon ?: "📁",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = folder.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (dragHandle != null) {
            IconButton(modifier = dragHandle, onClick = {}) {
                Text(
                    "⠿",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        if (!selectionMode) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (onReorder != null) {
                        DropdownMenuItem(
                            text = { Text("上移") },
                            onClick = { menuExpanded = false; onReorder(ReorderActions.UP) }
                        )
                        DropdownMenuItem(
                            text = { Text("下移") },
                            onClick = { menuExpanded = false; onReorder(ReorderActions.DOWN) }
                        )
                        DropdownMenuItem(
                            text = { Text("移到顶部") },
                            onClick = { menuExpanded = false; onReorder(ReorderActions.TOP) }
                        )
                        DropdownMenuItem(
                            text = { Text("移到底部") },
                            onClick = { menuExpanded = false; onReorder(ReorderActions.BOTTOM) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("移入回收站") },
                        onClick = {
                            menuExpanded = false
                            onMoveToRecycleBin?.invoke()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ListItemDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    )
}

@Composable
fun SelectionDot(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(
    onDismiss: () -> Unit,
    onCreateEvent: () -> Unit,
    onCreateFolder: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("新建", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SheetAction(
                    emoji = "📅",
                    label = "事件",
                    onClick = onCreateEvent
                )
                SheetAction(
                    emoji = "📁",
                    label = "文件夹",
                    onClick = onCreateFolder
                )
            }
        }
    }
}

@Composable
private fun SheetAction(emoji: String, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📝", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "轻点 + 创建你的第一个倒数日",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
