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
import com.ayaka7452.daymate.data.db.FolderKinds
import com.ayaka7452.daymate.feature.common.FolderDialog
import com.ayaka7452.daymate.feature.common.PickFolderDialog
import com.ayaka7452.daymate.feature.common.matchesQuery
import com.ayaka7452.daymate.feature.common.SortModes
import com.ayaka7452.daymate.feature.common.eventDaysUntil
import com.ayaka7452.daymate.feature.common.moveItem
import com.ayaka7452.daymate.feature.common.sortEventsForDisplay
import com.ayaka7452.daymate.feature.home.EventRow
import com.ayaka7452.daymate.feature.home.MiniFolderMergeDialog
import com.ayaka7452.daymate.feature.home.MiniFolderRow
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

    // ===== 事件组（v1.26.9）：存放在本文件夹下的事件组（kind=mini 且 parentId=本文件夹）=====
    // 成员事件的 folderId 指向组 id，通过组的 parentId 间接归属本文件夹。
    val minis = allFolders.filter { it.kind == FolderKinds.MINI && it.parentId == folderId }
    val allEventsFlow = remember { container.eventRepository.observeAll() }
    val allEvents by allEventsFlow.collectAsState(initial = emptyList())
    val memberLists = remember(minis, allEvents) {
        minis.associate { mini ->
            mini.id to allEvents
                .filter { it.folderId == mini.id }
                .sortedBy { it.sortIndex }
        }
    }
    val expandedMiniIds = remember { mutableStateListOf<Long>() }
    // 组重命名 / 解散确认弹窗状态
    var miniRenameTarget by remember { mutableStateOf<FolderEntity?>(null) }
    var showMiniRenameDialog by remember { mutableStateOf(false) }
    var miniToDelete by remember { mutableStateOf<FolderEntity?>(null) }

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
    // 组名也参与搜索过滤
    val shownMinis = remember(minis, query) {
        if (query.isEmpty()) minis
        else minis.filter { it.name.lowercase().contains(query.lowercase()) }
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

    // ===== 事件组合并（用户拍板：组建在主页根层，成员事件移出本文件夹；扁平合并不嵌套）=====
    var pendingMergeIds by remember { mutableStateOf(emptyList<Long>()) }
    var showMergeDialog by remember { mutableStateOf(false) }

    // 从当前多选态发起合并：本页事件都在同一文件夹，只认普通事件（排除周期等特殊类型）
    fun startMergeFromSelection() {
        val ids = events
            .filter { it.id in selectedEventIds && it.specialType == null }
            .map { it.id }
        if (ids.size < 2) {
            Toast.makeText(context, context.getString(R.string.merge_need_two), Toast.LENGTH_SHORT).show()
            return
        }
        pendingMergeIds = ids
        showMergeDialog = true
    }

    // 从单事件「更多→合并」发起：预选该事件进入多选态，提示继续勾选
    fun startMergeFromEvent(eventId: Long) {
        selectedEventIds.clear()
        selectedEventIds.add(eventId)
        selectionMode = true
        Toast.makeText(context, context.getString(R.string.merge_pick_hint), Toast.LENGTH_SHORT).show()
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
                        // 合并：与主页批量顶栏同构（高频动作放顶栏），选中至少两个可合并事件后出现
                        if (selectedEventIds.size >= 2) {
                            TextButton(onClick = { startMergeFromSelection() }) {
                                Text(Tr.s(R.string.merge_action))
                            }
                        }
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = totalSelected > 0
                        ) { Text(Tr.s(R.string.folder_move_bin)) }
                        // 移入文件夹、移入 Vault 收进 ⋮，避免小屏溢出
                        var overflowExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { overflowExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = Tr.s(R.string.common_more))
                            }
                            DropdownMenu(
                                expanded = overflowExpanded,
                                onDismissRequest = { overflowExpanded = false }
                            ) {
                                if (selectedEventIds.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(Tr.s(R.string.folder_move_in)) },
                                        onClick = { overflowExpanded = false; showMoveDialog = true }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(Tr.s(R.string.folder_move_vault)) },
                                    onClick = {
                                        overflowExpanded = false
                                        if (vaultSet) vaultConfirmBatch = true else vaultNeedSetup = true
                                    }
                                )
                            }
                        }
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
            if (events.isEmpty() && minis.isEmpty()) {
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
            } else if (searchActive && query.isNotEmpty() && shownEvents.isEmpty() && shownMinis.isEmpty()) {
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
                // 事件组区（v1.26.9）：本文件夹下的事件组固定排在事件列表前。
                // 组不参与拖拽/多选（与主页网格同理，避免手势与选择语义复杂化），多选模式下点击无效。
                items(shownMinis, key = { "m${it.id}" }) { mini ->
                    MiniFolderRow(
                        folder = mini,
                        expanded = mini.id in expandedMiniIds,
                        itemCount = memberLists[mini.id]?.size ?: 0,
                        onClick = {
                            if (!selectionMode) {
                                if (mini.id in expandedMiniIds) expandedMiniIds.remove(mini.id)
                                else expandedMiniIds.add(mini.id)
                            }
                        },
                        onRename = {
                            miniRenameTarget = mini
                            showMiniRenameDialog = true
                        },
                        onDissolve = { miniToDelete = mini },
                        onMoveToRecycleBin = { miniToDelete = mini }
                    )
                    // 展开的组成员行：缩进 + 左缘浅绿竖线串联（与主页一致），不参与多选与拖拽
                    if (mini.id in expandedMiniIds) {
                        memberLists[mini.id]?.forEach { member ->
                            EventRow(
                                event = member,
                                festivalRepo = container.festivalRepository,
                                indent = true,
                                connector = true,
                                onClick = {
                                    if (member.specialType == "cycle") onNavigate("cycle")
                                    else onNavigate("event_detail?eventId=${member.id}")
                                },
                                onMoveToVault = {
                                    if (vaultSet) {
                                        scope.launch { container.vaultBridge.moveEventToVault(member.id) }
                                    } else {
                                        vaultNeedSetup = true
                                    }
                                },
                                onMoveToRecycleBin = {
                                    scope.launch {
                                        container.eventRepository.softDeleteByIds(
                                            listOf(member.id),
                                            System.currentTimeMillis()
                                        )
                                    }
                                }
                            )
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    )
                }
                items(shownEvents, key = { "e${it.id}" }) { event ->
                    ReorderableItem(reorderableState, key = "e${event.id}") {
                        val canDrag = manualSort && !selectionMode
                        val dragModifier = if (canDrag) {
                            Modifier.longPressDraggableHandle(
                                onDragStarted = { isDragging = true },
                                onDragStopped = {
                                    isDragging = false
                                    persistEventOrder()
                                }
                            )
                        } else null
                        EventRow(
                            event = event,
                            festivalRepo = container.festivalRepository,
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
                            // 合并：普通事件可发起（预选后继续勾选），周期等特殊类型不参与
                            onMerge = if (event.specialType == null) {
                                { startMergeFromEvent(event.id) }
                            } else null,
                            onMoveToRecycleBin = {
                                scope.launch {
                                    container.eventRepository.softDeleteByIds(
                                        listOf(event.id),
                                        System.currentTimeMillis()
                                    )
                                }
                            },
                            dragModifier = dragModifier,
                            onBlockedLongPress = if (!canDrag && !selectionMode && event.specialType == null) {
                                { Toast.makeText(context, context.getString(R.string.home_manual_sort_toast), Toast.LENGTH_SHORT).show() }
                            } else null
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
                // 与本页「移到回收站」入口（showFolderDeleteConfirm）走同一套软删：
                // 此前这里调 folderRepository.delete()（@Delete → DELETE FROM folders），
                // 同一个「删除文件夹」概念存在不可撤销与可撤销两套实现。
                scope.launch {
                    folder?.let { f ->
                        // 连带解散本文件夹下的事件组：成员（folderId 指向子组）与直属事件一并解归属
                        val childMinis = allFolders
                            .filter { it.kind == FolderKinds.MINI && it.parentId == f.id }
                            .map { m -> m.id }
                        container.eventRepository.unparentByFolders(listOf(f.id) + childMinis)
                        container.folderRepository.softDeleteByIds(
                            listOf(f.id) + childMinis,
                            System.currentTimeMillis()
                        )
                    }
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
                        folder?.let { f ->
                            // 连带解散本文件夹下的事件组（同 FolderDialog.onDelete）
                            val childMinis = allFolders
                                .filter { it.kind == FolderKinds.MINI && it.parentId == f.id }
                                .map { m -> m.id }
                            container.eventRepository.unparentByFolders(listOf(f.id) + childMinis)
                            container.folderRepository.softDeleteByIds(
                                listOf(f.id) + childMinis,
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

    // 合并命名弹窗：与主页共用。组建 kind=MINI 的事件组，parentId 指向本文件夹（v1.26.9 起组留在文件夹内），
    // 成员事件 folderId 指向新组，仍归属本文件夹
    if (showMergeDialog && pendingMergeIds.isNotEmpty()) {
        MiniFolderMergeDialog(
            count = pendingMergeIds.size,
            onDismiss = { showMergeDialog = false; pendingMergeIds = emptyList() },
            onConfirm = { name ->
                val finalName = name.trim().ifBlank { Tr.s(R.string.mini_folder_default_name) }
                val ids = pendingMergeIds
                val minSort = events
                    .filter { it.id in ids }
                    .minOfOrNull { it.sortIndex } ?: 0
                scope.launch {
                    val newId = container.folderRepository.add(
                        FolderEntity(
                            name = finalName,
                            kind = FolderKinds.MINI,
                            parentId = folderId,
                            sortIndex = minSort
                        )
                    )
                    container.eventRepository.moveToFolder(ids, newId)
                }
                showMergeDialog = false
                pendingMergeIds = emptyList()
                exitSelection()
            }
        )
    }

    // 事件组重命名弹窗（与主页同款）
    if (showMiniRenameDialog && miniRenameTarget != null) {
        var miniName by remember(miniRenameTarget) { mutableStateOf(miniRenameTarget!!.name) }
        AlertDialog(
            onDismissRequest = { showMiniRenameDialog = false; miniRenameTarget = null },
            title = { Text(Tr.s(R.string.common_rename)) },
            text = {
                OutlinedTextField(
                    value = miniName,
                    onValueChange = { miniName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = miniRenameTarget ?: return@TextButton
                    val finalName = miniName.trim()
                    if (finalName.isNotBlank()) {
                        scope.launch { container.folderRepository.update(target.copy(name = finalName)) }
                    }
                    showMiniRenameDialog = false
                    miniRenameTarget = null
                }) { Text(Tr.s(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showMiniRenameDialog = false; miniRenameTarget = null }) {
                    Text(Tr.s(R.string.common_cancel))
                }
            }
        )
    }

    // 事件组解散/移入回收站确认：成员事件回归本文件夹（组在文件夹内，成员不回主页根层）
    if (miniToDelete != null) {
        val target = miniToDelete!!
        AlertDialog(
            onDismissRequest = { miniToDelete = null },
            title = { Text(Tr.s(R.string.home_move_to_trash_title)) },
            text = { Text(Tr.s(R.string.home_folder_delete_confirm_desc, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    val memberIds = memberLists[target.id]?.map { it.id } ?: emptyList()
                    scope.launch {
                        if (memberIds.isNotEmpty()) {
                            container.eventRepository.moveToFolder(memberIds, folderId)
                        }
                        container.folderRepository.softDeleteByIds(
                            listOf(target.id),
                            System.currentTimeMillis()
                        )
                    }
                    miniToDelete = null
                }) { Text(Tr.s(R.string.home_move_to_trash)) }
            },
            dismissButton = {
                TextButton(onClick = { miniToDelete = null }) { Text(Tr.s(R.string.common_cancel)) }
            }
        )
    }
}
