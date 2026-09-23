@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ayaka7452.daymate.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayaka7452.daymate.Routes
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.core.CloudBackupState
import com.ayaka7452.daymate.core.util.CountdownCalculator
import com.ayaka7452.daymate.data.db.EventEntity
import com.ayaka7452.daymate.data.db.FolderEntity
import com.ayaka7452.daymate.data.repo.SettingsRepository
import com.ayaka7452.daymate.feature.common.FolderDialog
import com.ayaka7452.daymate.feature.common.PickFolderDialog
import com.ayaka7452.daymate.feature.common.ReorderMenuItems
import com.ayaka7452.daymate.feature.common.SortModes
import com.ayaka7452.daymate.feature.common.UpdateHost
import com.ayaka7452.daymate.feature.common.eventDaysUntil
import com.ayaka7452.daymate.feature.common.highlightedText
import com.ayaka7452.daymate.feature.common.matchesQuery
import com.ayaka7452.daymate.feature.common.moveItem
import com.ayaka7452.daymate.feature.common.noteHitOnly
import com.ayaka7452.daymate.feature.common.sortEventsForDisplay
import com.ayaka7452.daymate.feature.common.targetIndexForAction
import com.ayaka7452.daymate.feature.home.SelectionDot
import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.i18n.Tr
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.launch
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    // 主页顶部卡片模式：festival（默认）/ event / off
    val homeTopCard by container.settingsRepository.homeTopCard
        .collectAsState(initial = "festival")
    // 节日卡片右侧角标 emoji（默认 ☀️）
    val homeBadgeEmoji by container.settingsRepository.homeBadgeEmoji
        .collectAsState(initial = "☀️")
    // 周期管家入口按钮：默认关，需在「周期管家 → 设置 → 隐私与快捷事件」里开启
    val cycleEntryEnabled by container.settingsRepository.cycleEntryEnabled
        .collectAsState(initial = false)

    // 云备份指示器：备份位置含云端（both/cloud）且 WebDAV 配置完整时常驻显示
    val cloudEnabled by container.autoBackup.cloudEnabled.collectAsState(initial = false)
    val cloudState by container.autoBackup.cloudState.collectAsState(initial = CloudBackupState.Idle)
    var showCloudSheet by remember { mutableStateOf(false) }

    var showAddSheet by remember { mutableStateOf(false) }
    // FAB 展开态：开关开启后，长按 + 号展开（月亮从加号上方升起），单击 + 号仍是直接新建事件。
    // 让用户先看清右下角还有哪些入口，避免直接弹面板把人推到「事件 / 文件夹」二选一里。
    // 开关关闭时这个状态永远是 false，点 + 号一步新建，与改动前完全一致。
    var fabExpanded by remember { mutableStateOf(false) }
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

    // 单事件「...」菜单 → 移动到文件夹
    var singleMoveEventId by remember { mutableStateOf<Long?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 检查更新：冷启动按节流规则查一次（24h 内不重复查、点过「稍后」的版本不再弹）。
    // 整条链路（弹窗 → 通知权限 → 下载服务）都收在 UpdateHost 里，这里只挂一行。
    UpdateHost(container)

    // 节假日数据（在线下载 + 本地缓存）：主页顶部横幅 + 下一节日倒数卡片
    val festivalRepo = remember { container.festivalRepository }
    var festivalHasData by remember { mutableStateOf(false) }
    var todayFestival by remember { mutableStateOf<com.ayaka7452.daymate.data.festival.FestivalDay?>(null) }
    // 明日补班预告：仅当今天不是节日、明天是调休上班日时非空（横幅换绿底「班」+「明日」句式）
    var tomorrowMakeup by remember { mutableStateOf<com.ayaka7452.daymate.data.festival.FestivalDay?>(null) }
    var nextFestival by remember { mutableStateOf<com.ayaka7452.daymate.data.festival.FestivalDay?>(null) }
    // 以「节日数据版本号」为 key 重读，而不是 Unit：换数据源或下载完成后数据变了，
    // 卡片必须跟着变——早先只在首次组合时读一次，用户得重启 App 才看得到新国家的节日。
    val festivalVersion by festivalRepo.version.collectAsState()
    // 明日补班预告开关（默认开）：设置里可关，关掉后横幅/角标不再预告
    val makeupHint by container.settingsRepository.makeupHintEnabled.collectAsState(initial = true)
    LaunchedEffect(festivalVersion, makeupHint) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val today = java.time.LocalDate.now()
            Triple(festivalRepo.hasData(), festivalRepo.todayInfo(today), festivalRepo.nextOffDay(today))
        }?.let { (has, todayF, nextF) ->
            festivalHasData = has
            todayFestival = todayF
            nextFestival = nextF
        }
        tomorrowMakeup = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val today = java.time.LocalDate.now()
            // 今日本身是节日（无论休/班）就不预告明天，避免横幅叠报；开关关闭同样不预告
            if (makeupHint && festivalRepo.todayInfo(today) == null) {
                festivalRepo.todayInfo(today.plusDays(1))?.takeIf { !it.isOffDay }
            } else null
        }
    }

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
    val eventSnapshot = eventList.toList()
    val displayEvents = remember(eventSnapshot, defaultSort) {
        sortEventsForDisplay(eventSnapshot, defaultSort) { eventDaysUntil(it.targetDateEpochDay) }
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

    // 注意 initial 必须为 null：DataStore 异步读盘，若用列表当初始值，设置了网格视图的
    // 冷启动会先渲染一帧列表再闪切成网格。null 期间内容区渲染空白帧（见下方 when 分支）。
    val homeViewMode by container.settingsRepository.homeViewMode
        .collectAsState(initial = null)

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
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
            context.getString(R.string.home_manual_sort_toast),
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

    // FAB 展开态下按返回键先收起，而不是直接退出 App
    BackHandler(enabled = fabExpanded && !searchActive && !selectionMode) { fabExpanded = false }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.home_selected_n, totalSelected)) },
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_done)
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            events.forEach { if (it.id !in selectedEventIds) selectedEventIds.add(it.id) }
                            folders.forEach { if (it.id !in selectedFolderIds) selectedFolderIds.add(it.id) }
                        }) { Text(stringResource(R.string.common_select_all)) }
                        if (selectedEventIds.isNotEmpty()) {
                            TextButton(onClick = { showMoveDialog = true }) { Text(stringResource(R.string.home_move_into_folder)) }
                        }
                        TextButton(
                            onClick = { if (vaultSet) vaultConfirmBatch = true else vaultNeedSetup = true },
                            enabled = totalSelected > 0
                        ) { Text(stringResource(R.string.home_move_to_vault)) }
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = totalSelected > 0
                        ) { Text(stringResource(R.string.home_move_to_trash)) }
                    }
                )
            } else if (searchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
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
                                contentDescription = stringResource(R.string.home_exit_search)
                            )
                        }
                    },
                    actions = {
                        if (searchQuery.isNotBlank()) {
                            TextButton(onClick = { searchQuery = "" }) { Text(stringResource(R.string.common_clear)) }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("DayMate", fontFamily = FontFamily.Cursive) },
                    actions = {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.common_search))
                        }
                        // 云备份指示器：仅在 WebDAV 已配置且备份位置含云端时出现，点击查看详情/手动备份
                        if (cloudEnabled) {
                            IconButton(onClick = { showCloudSheet = true }) {
                                CloudBackupIndicator(state = cloudState)
                            }
                        }
                        var menuExpanded by remember { mutableStateOf(false) }
                        var viewSubmenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.home_menu))
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    modifier = Modifier.heightIn(min = 56.dp),
                                    text = { Text(stringResource(R.string.home_batch_manage)) },
                                    onClick = { menuExpanded = false; enterSelection() }
                                )
                                DropdownMenuItem(
                                    modifier = Modifier.heightIn(min = 56.dp),
                                    text = { Text(stringResource(R.string.home_view_mode)) },
                                    trailingIcon = {
                                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        viewSubmenuExpanded = true
                                    }
                                )
                                DropdownMenuItem(
                                    modifier = Modifier.heightIn(min = 56.dp),
                                    text = { Text(stringResource(R.string.home_cycle_tracker)) },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigate(Routes.CYCLE)
                                    }
                                )
                                DropdownMenuItem(
                                    modifier = Modifier.heightIn(min = 56.dp),
                                    text = { Text(stringResource(R.string.home_vault)) },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigate(Routes.VAULT)
                                    }
                                )
                                DropdownMenuItem(
                                    modifier = Modifier.heightIn(min = 56.dp),
                                    text = { Text(stringResource(R.string.common_settings)) },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigate(Routes.SETTINGS)
                                    }
                                )
                                DropdownMenuItem(
                                    modifier = Modifier.heightIn(min = 56.dp),
                                    text = { Text(stringResource(R.string.home_about)) },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigate(Routes.ABOUT)
                                    }
                                )
                                DropdownMenuItem(
                                    modifier = Modifier.heightIn(min = 56.dp),
                                    text = { Text(stringResource(R.string.home_trash)) },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigate(Routes.RECYCLE_BIN)
                                    }
                                )
                            }
                            // 视图模式子菜单：主菜单点「视图模式」后关主菜单、开本菜单，
                            // 两个菜单锚在同一个 MoreVert 按钮上，视觉上就是二级子菜单
                            DropdownMenu(
                                expanded = viewSubmenuExpanded,
                                onDismissRequest = { viewSubmenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    modifier = Modifier.heightIn(min = 56.dp),
                                    text = { Text(stringResource(R.string.home_view_list)) },
                                    trailingIcon = {
                                        if (homeViewMode == SettingsRepository.VIEW_MODE_LIST) {
                                            Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp))
                                        }
                                    },
                                    onClick = {
                                        viewSubmenuExpanded = false
                                        scope.launch { container.settingsRepository.setHomeViewMode(SettingsRepository.VIEW_MODE_LIST) }
                                    }
                                )
                                DropdownMenuItem(
                                    modifier = Modifier.heightIn(min = 56.dp),
                                    text = { Text(stringResource(R.string.home_view_medium)) },
                                    trailingIcon = {
                                        if (homeViewMode == SettingsRepository.VIEW_MODE_MEDIUM) {
                                            Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp))
                                        }
                                    },
                                    onClick = {
                                        viewSubmenuExpanded = false
                                        scope.launch { container.settingsRepository.setHomeViewMode(SettingsRepository.VIEW_MODE_MEDIUM) }
                                    }
                                )
                                DropdownMenuItem(
                                    modifier = Modifier.heightIn(min = 56.dp),
                                    text = { Text(stringResource(R.string.home_view_large)) },
                                    trailingIcon = {
                                        if (homeViewMode == SettingsRepository.VIEW_MODE_LARGE) {
                                            Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp))
                                        }
                                    },
                                    onClick = {
                                        viewSubmenuExpanded = false
                                        scope.launch { container.settingsRepository.setHomeViewMode(SettingsRepository.VIEW_MODE_LARGE) }
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
                // 两枚按钮右对齐成一列：小按钮（周期管家）在上，新建加号在下。
                // 用 Alignment.End 而不是居中——让 40dp 小按钮的右边缘与 56dp 加号的右边缘对齐，
                // 两枚按钮落在同一条竖线上，视觉上才是一组；居中会让小按钮相对加号偏左半个差值。
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 周期管家入口：只在展开态出现（开关关着则永不出现），所以平时右下角仍只有一枚加号。
                    // 从下往上撑开——它长在加号上方，若从上方展开会像从屏幕外掉进来。
                    AnimatedVisibility(
                        visible = cycleEntryEnabled && fabExpanded,
                        enter = fadeIn(tween(140)) + expandVertically(
                            expandFrom = Alignment.Bottom,
                            animationSpec = tween(200)
                        ),
                        exit = fadeOut(tween(100)) + shrinkVertically(
                            shrinkTowards = Alignment.Bottom,
                            animationSpec = tween(160)
                        )
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                fabExpanded = false
                                onNavigate(Routes.CYCLE)
                            },
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(Icons.Filled.Nightlight, contentDescription = stringResource(R.string.home_cycle_tracker))
                        }
                    }
                    // 加号：单击直接新建事件，长按展开（再长按收起）周期管家入口。
                    // 长按不挂在 FloatingActionButton 上——它内部自带 clickable 且只暴露 onClick，
                    // 长按手势拿不到，外面再叠一层也会被它先吃掉。所以盖一层透明手势层接管点击，FAB 只负责画。
                    // 万一遮挡失效，FAB 的 onClick 是空实现，最坏只是「点了没反应」，不会误触发别的动作。
                    Box {
                        FloatingActionButton(onClick = {}) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.common_new))
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                // 裁成 FAB 的圆角，否则按下时水波纹会从四角溢出方形边界
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    onClick = {
                                        fabExpanded = false
                                        showAddSheet = true
                                    },
                                    onLongClick = {
                                        // 开关关着时没有入口可展开，长按保持无动作
                                        if (cycleEntryEnabled) fabExpanded = !fabExpanded
                                    }
                                )
                        )
                    }
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
                            stringResource(R.string.home_no_results),
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
                                onClick = {
                                    if (event.specialType == "cycle") onNavigate("cycle")
                                    else onNavigate("event_detail?eventId=${event.id}")
                                },
                                onMoveToVault = {
                                    if (vaultSet) vaultConfirmEventId = event.id else vaultNeedSetup = true
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
            homeViewMode == null -> {
                // DataStore 视图偏好尚未读出：先渲染空白一帧，避免冷启动先用列表
                // 渲染、读到网格设置后再闪切（列表模式无感、网格模式必闪的根因）
                Spacer(Modifier.fillMaxSize())
            }
            else -> Column(Modifier.fillMaxSize()) {
                // 顶部横幅与倒数卡片：两种视图共用一份、不参与切换动画；宽度统一为列表口径（水平 8dp），
                // 整个顶部区域一次性让开顶栏（Scaffold 顶内边距），下方内容区不再重复计算
                val hasHeader = (todayFestival ?: tomorrowMakeup) != null || homeTopCard != "off"
                if (hasHeader) {
                    Column(Modifier.padding(top = padding.calculateTopPadding())) {
                        // 今日节日/调休横幅；今日本身不是节日但明天要补班时，横幅换成「明日」预告（绿底班角标）
                        (todayFestival ?: tomorrowMakeup)?.let { tf ->
                            com.ayaka7452.daymate.feature.common.FestivalTodayBanner(
                                day = tf,
                                tomorrow = todayFestival == null,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        // 主页顶部卡片：模式由设置控制（festival=下一节日[默认] / event=最近倒数日 / off=隐藏）
                        when (homeTopCard) {
                            "festival" -> com.ayaka7452.daymate.feature.common.FestivalCountdownCard(
                                hasData = festivalHasData,
                                festival = nextFestival,
                                onClick = {
                                    if (!festivalHasData) {
                                        onNavigate(Routes.SETTINGS)
                                    } else if (nextFestival != null) {
                                        val f = nextFestival!!
                                        onNavigate(
                                            "event_form?festivalName=" +
                                                android.net.Uri.encode(f.name) +
                                                "&festivalEpochDay=" + f.date.toEpochDay()
                                        )
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                badgeEmoji = homeBadgeEmoji
                            )
                            "event" -> {
                                // 最近倒数日：优先取剩余天数最少的未过期事件；全部已过期则取最近过期的
                                val today = java.time.LocalDate.now().toEpochDay()
                                val nearest = allEvents
                                    .filter { it.targetDateEpochDay >= today }
                                    .minByOrNull { it.targetDateEpochDay }
                                val pastNearest = allEvents
                                    .filter { it.targetDateEpochDay < today }
                                    .maxByOrNull { it.targetDateEpochDay }
                                val show = nearest ?: pastNearest
                                val isPast = nearest == null
                                if (show != null) {
                                    val dateStr = java.time.LocalDate.ofEpochDay(show.targetDateEpochDay)
                                        .format(java.time.format.DateTimeFormatter.ofPattern(Tr.s(R.string.date_pattern_md)))
                                    com.ayaka7452.daymate.feature.common.EventCountdownCard(
                                        title = show.title,
                                        dateStr = dateStr,
                                        days = kotlin.math.abs(show.targetDateEpochDay - today).toInt(),
                                        past = isPast,
                                        onClick = {
                                            if (show.specialType == "cycle") onNavigate("cycle")
                                            else onNavigate("event_detail?eventId=${show.id}")
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Crossfade(
                    targetState = homeViewMode,
                    label = "home_view"
                ) { mode ->
                    when (mode) {
                    SettingsRepository.VIEW_MODE_LIST -> {
                    LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = if (hasHeader) 0.dp else padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding()
                    ),
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
                                else if (event.specialType == "cycle") onNavigate("cycle")
                                else onNavigate("event_detail?eventId=${event.id}")
                            },
                            onMoveToVault = {
                                if (vaultSet) vaultConfirmEventId = event.id else vaultNeedSetup = true
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
                    ListItemDivider()
                }
                }
            }
                else -> {
                val isLarge = mode == SettingsRepository.VIEW_MODE_LARGE
                val columns = if (isLarge) GridCells.Fixed(2) else GridCells.Fixed(3)
                LazyVerticalGrid(
                    columns = columns,
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = (if (hasHeader) 0.dp else padding.calculateTopPadding()) + 4.dp,
                        bottom = padding.calculateBottomPadding() + 80.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(folderList, key = { "f${it.id}" }) { folder ->
                        val count = allEvents.count { it.folderId == folder.id }
                        FolderGridItem(
                            folder = folder,
                            itemCount = count,
                            isLarge = isLarge,
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
                            }
                        )
                    }
                    items(displayEvents, key = { "e${it.id}" }) { event ->
                        EventGridItem(
                            event = event,
                            isLarge = isLarge,
                            selectionMode = selectionMode,
                            selected = event.id in selectedEventIds,
                            onClick = {
                                if (selectionMode) toggleEvent(event.id)
                                else if (event.specialType == "cycle") onNavigate("cycle")
                                else onNavigate("event_detail?eventId=${event.id}")
                            },
                            onMoveToVault = {
                                if (vaultSet) vaultConfirmEventId = event.id else vaultNeedSetup = true
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
                            folderBadge = event.folderId?.let { folderNameById[it] }
                        )
                    }
                }
                }
                }
            }
            }
        }

        // 展开态遮罩：点空白处收起。压在列表之上、顶栏之下——顶栏的搜索/菜单仍可正常点，
        // 不把整页都吃掉。用 indication = null，避免整屏泛出水波纹。
        if (fabExpanded && cycleEntryEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { fabExpanded = false }
            )
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

    if (showCloudSheet) {
        CloudBackupSheet(
            state = cloudState,
            onDismiss = { showCloudSheet = false },
            // 复用自动备份的完整流程（含空数据护栏），只是跳过 1.5s 防抖立即执行
            onBackupNow = { container.autoBackup.flush() }
        )
    }

    if (showFolderDialog) {
        FolderDialog(
            initialName = folderDialogTarget?.name ?: "",
            initialIcon = folderDialogTarget?.icon ?: "📁",
            title = if (folderDialogTarget == null) stringResource(R.string.home_new_folder) else stringResource(R.string.home_edit_folder),
            confirmLabel = if (folderDialogTarget == null) stringResource(R.string.common_create) else stringResource(R.string.common_save),
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
            // 主页本身就是根目录，「根目录」选项无意义，不显示
            showRoot = false,
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

    if (singleMoveEventId != null) {
        PickFolderDialog(
            title = stringResource(R.string.common_move_to),
            folders = folders.map { it.id to "${it.icon ?: "📁"}  ${it.name}" },
            // 主页的事件都在根目录，这里只提供文件夹目标
            showRoot = false,
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
            title = { Text(stringResource(R.string.home_move_to_trash_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.home_delete_confirm_desc, totalSelected))
                    if (selectedFolderIds.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.home_folder_delete_note),
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
                }) { Text(stringResource(R.string.home_move_to_trash)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showFolderDeleteConfirm && folderToDelete != null) {
        AlertDialog(
            onDismissRequest = { showFolderDeleteConfirm = false; folderToDelete = null },
            title = { Text(stringResource(R.string.home_move_to_trash_title)) },
            text = {
                Text(stringResource(R.string.home_folder_delete_confirm_desc, folderToDelete!!.name))
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
                }) { Text(stringResource(R.string.home_move_to_trash)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFolderDeleteConfirm = false
                    folderToDelete = null
                }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (vaultNeedSetup) {
        AlertDialog(
            onDismissRequest = { vaultNeedSetup = false },
            title = { Text(stringResource(R.string.home_vault_not_setup)) },
            text = { Text(stringResource(R.string.home_vault_setup_hint)) },
            confirmButton = {
                TextButton(onClick = { vaultNeedSetup = false; onNavigate(Routes.VAULT) }) { Text(stringResource(R.string.home_go_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { vaultNeedSetup = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (vaultConfirmEventId != null) {
        AlertDialog(
            onDismissRequest = { vaultConfirmEventId = null },
            title = { Text(stringResource(R.string.home_move_to_vault_title)) },
            text = { Text(stringResource(R.string.home_move_to_vault_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vaultConfirmEventId?.let { container.vaultBridge.moveEventToVault(it) } }
                    vaultConfirmEventId = null
                }) { Text(stringResource(R.string.home_move_in)) }
            },
            dismissButton = {
                TextButton(onClick = { vaultConfirmEventId = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (vaultConfirmBatch) {
        AlertDialog(
            onDismissRequest = { vaultConfirmBatch = false },
            title = { Text(stringResource(R.string.home_move_to_vault_title)) },
            text = { Text(stringResource(R.string.home_batch_vault_desc, selectedEventIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        selectedEventIds.toList().forEach { container.vaultBridge.moveEventToVault(it) }
                    }
                    vaultConfirmBatch = false
                    exitSelection()
                }) { Text(stringResource(R.string.home_move_in)) }
            },
            dismissButton = {
                TextButton(onClick = { vaultConfirmBatch = false }) { Text(stringResource(R.string.common_cancel)) }
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
    onMoveToFolder: (() -> Unit)? = null,
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
                text = stringResource(R.string.home_note_hit),
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
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_more))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (onReorder != null) {
                        ReorderMenuItems(onReorder) { menuExpanded = false }
                    }
                    if (onMoveToFolder != null) {
                        DropdownMenuItem(
                            modifier = Modifier.heightIn(min = 56.dp),
                            text = { Text(stringResource(R.string.home_move_to_folder_ellipsis)) },
                            onClick = {
                                menuExpanded = false
                                onMoveToFolder.invoke()
                            }
                        )
                    }
                    DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 56.dp),
                        text = { Text(stringResource(R.string.home_move_to_vault)) },
                        onClick = {
                            menuExpanded = false
                            onMoveToVault?.invoke()
                        }
                    )
                    DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 56.dp),
                        text = { Text(stringResource(R.string.home_move_to_trash)) },
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
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_more))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (onReorder != null) {
                        ReorderMenuItems(onReorder) { menuExpanded = false }
                    }
                    DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 56.dp),
                        text = { Text(stringResource(R.string.home_move_to_trash)) },
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
            Text(stringResource(R.string.common_new), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SheetAction(
                    emoji = "📅",
                    label = stringResource(R.string.common_event),
                    onClick = onCreateEvent
                )
                SheetAction(
                    emoji = "📁",
                    label = stringResource(R.string.common_folder),
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
            stringResource(R.string.home_empty_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

// ===== 云备份指示器 =====

/**
 * 顶栏云备份图标（方案 A：云为恒定基底，右下角挂结果角标）。
 *  - Idle    中性云
 *  - Syncing 主题色旋转箭头
 *  - Success 绿勾角标（停留 2s 后由 AutoBackupManager 回到 Idle）
 *  - Failure 红叉角标（常驻，直到下次成功或手动备份）
 */
@Composable
private fun CloudBackupIndicator(state: CloudBackupState, modifier: Modifier = Modifier) {
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier = modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (state is CloudBackupState.Syncing) {
            val transition = rememberInfiniteTransition(label = "cloud-sync")
            val angle by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
                label = "cloud-sync-angle"
            )
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = stringResource(R.string.home_cloud_backing_up),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(angle)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = stringResource(R.string.home_cloud_backup),
                tint = neutral,
                modifier = Modifier.size(22.dp)
            )
            val badge = when (state) {
                is CloudBackupState.Success -> Color(0xFF1D9E75) to Icons.Default.Check
                is CloudBackupState.Failure -> MaterialTheme.colorScheme.error to Icons.Default.Close
                else -> null
            }
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(13.dp)
                        // 先铺一圈 surface 色作分隔，避免角标与云糊在一起
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(1.dp)
                        .background(badge.first, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = badge.second,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(8.dp)
                    )
                }
            }
        }
    }
}

/** 云备份详情弹窗：当前状态、上次执行时间、失败原因与「立即备份」。 */
@Composable
private fun CloudBackupSheet(
    state: CloudBackupState,
    onDismiss: () -> Unit,
    onBackupNow: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(stringResource(R.string.home_cloud_backup), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.home_cloud_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(16.dp))

            val (statusText, statusColor) = when (state) {
                is CloudBackupState.Idle -> stringResource(R.string.home_cloud_idle) to MaterialTheme.colorScheme.onSurface
                is CloudBackupState.Syncing -> stringResource(R.string.home_cloud_syncing) to MaterialTheme.colorScheme.primary
                is CloudBackupState.Success ->
                    stringResource(R.string.home_cloud_success, fmt.format(Date(state.at))) to Color(0xFF1D9E75)
                is CloudBackupState.Failure ->
                    stringResource(R.string.home_cloud_failure, fmt.format(Date(state.at))) to MaterialTheme.colorScheme.error
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CloudBackupIndicator(state = state)
                Spacer(Modifier.width(12.dp))
                Text(statusText, style = MaterialTheme.typography.bodyLarge, color = statusColor)
            }

            if (state is CloudBackupState.Failure) {
                Spacer(Modifier.height(8.dp))
                Text(
                    state.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = onBackupNow,
                enabled = state !is CloudBackupState.Syncing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.home_backup_now))
            }
        }
    }
}

// ===== 网格卡片 =====

/**
 * 文件夹网格项：自适应支持中图标（3 列居中）与大图标（2 列卡片）。
 * 沿用文件夹自带 emoji，不额外找外置图标。
 */
@Composable
fun FolderGridItem(
    folder: FolderEntity,
    itemCount: Int,
    isLarge: Boolean,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onMoveToRecycleBin: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (isLarge) 14.dp else 12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(
                0.5.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                RoundedCornerShape(if (isLarge) 14.dp else 12.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (selectionMode) {
                        onClick()
                    } else if (onMoveToRecycleBin != null) {
                        menuExpanded = true
                    } else {
                        onLongClick()
                    }
                }
            )
            .padding(if (isLarge) 12.dp else 10.dp)
    ) {
        if (isLarge) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = folder.icon ?: "📁",
                        fontSize = 32.sp,
                        lineHeight = 32.sp
                    )
                    if (selectionMode) {
                        SelectionDot(selected = selected)
                    } else if (itemCount > 0) {
                        Text(
                            text = Tr.s(R.string.settings_unit_items, itemCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (selectionMode) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                        SelectionDot(selected = selected)
                    }
                }
                Text(
                    text = folder.icon ?: "📁",
                    fontSize = 32.sp,
                    lineHeight = 32.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (itemCount > 0) {
                    Text(
                        text = Tr.s(R.string.settings_unit_items, itemCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                } else {
                    Spacer(Modifier.height(14.dp))
                }
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                modifier = Modifier.heightIn(min = 56.dp),
                text = { Text(stringResource(R.string.home_edit_folder)) },
                onClick = {
                    menuExpanded = false
                    onLongClick()
                }
            )
            DropdownMenuItem(
                modifier = Modifier.heightIn(min = 56.dp),
                text = { Text(stringResource(R.string.home_move_to_trash)) },
                onClick = {
                    menuExpanded = false
                    onMoveToRecycleBin?.invoke()
                }
            )
        }
    }
}

/**
 * 事件网格项：自适应支持中图标（3 列经典居中，天数徽章）与大图标（2 列大字号天数卡片）。
 */
@Composable
fun EventGridItem(
    event: EventEntity,
    isLarge: Boolean,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onMoveToVault: (() -> Unit)? = null,
    onMoveToFolder: (() -> Unit)? = null,
    onMoveToRecycleBin: (() -> Unit)? = null,
    folderBadge: String? = null
) {
    val days = CountdownCalculator.daysUntil(event.targetDateEpochDay)
    val isFuture = days >= 0
    val text = CountdownCalculator.formatCountdown(
        event.targetDateEpochDay,
        event.displayUnit,
        event.refDays
    )
    val eventColor = event.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val statusColor = if (isFuture) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary

    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (isLarge) 14.dp else 12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(
                0.5.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                RoundedCornerShape(if (isLarge) 14.dp else 12.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (selectionMode) onClick()
                    else if (onMoveToVault != null) menuExpanded = true
                }
            )
            .padding(if (isLarge) 12.dp else 10.dp)
    ) {
        if (isLarge) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(eventColor)
                    )
                    if (selectionMode) {
                        SelectionDot(selected = selected)
                    } else if (folderBadge != null) {
                        Text(
                            text = folderBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (selectionMode) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                        SelectionDot(selected = selected)
                    }
                }
                val absDays = kotlin.math.abs(days)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(eventColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (absDays > 999) "999+" else absDays.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = eventColor
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            if (onMoveToFolder != null) {
                DropdownMenuItem(
                    modifier = Modifier.heightIn(min = 56.dp),
                    text = { Text(stringResource(R.string.home_move_to_folder_ellipsis)) },
                    onClick = {
                        menuExpanded = false
                        onMoveToFolder()
                    }
                )
            }
            DropdownMenuItem(
                modifier = Modifier.heightIn(min = 56.dp),
                text = { Text(stringResource(R.string.home_move_to_vault)) },
                onClick = {
                    menuExpanded = false
                    onMoveToVault?.invoke()
                }
            )
            DropdownMenuItem(
                modifier = Modifier.heightIn(min = 56.dp),
                text = { Text(stringResource(R.string.home_move_to_trash)) },
                onClick = {
                    menuExpanded = false
                    onMoveToRecycleBin?.invoke()
                }
            )
        }
    }
}
