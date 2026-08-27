@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.daymate.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.daymate.app.Routes
import com.daymate.app.core.AppContainer
import com.daymate.app.core.util.CountdownCalculator
import com.daymate.app.data.db.EventEntity
import com.daymate.app.data.db.FolderEntity
import com.daymate.app.feature.common.FolderDialog
import com.daymate.app.feature.common.PickFolderDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onNavigate: (String) -> Unit
) {
    val events by container.eventRepository.observeRoot()
        .collectAsState(initial = emptyList())
    val folders by container.folderRepository.observeAll()
        .collectAsState(initial = emptyList())

    var showAddSheet by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var folderDialogTarget by remember { mutableStateOf<FolderEntity?>(null) }
    var pendingMoveAfterCreate by remember { mutableStateOf(false) }

    var selectionMode by remember { mutableStateOf(false) }
    val selectedEventIds = remember { mutableStateListOf<Long>() }
    val selectedFolderIds = remember { mutableStateListOf<Long>() }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

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
                            onClick = { showDeleteConfirm = true },
                            enabled = totalSelected > 0
                        ) { Text("删除") }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("DayMate", fontFamily = FontFamily.Cursive) },
                    actions = {
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
        if (events.isEmpty() && folders.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(folders, key = { it.id }) { folder ->
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
                        }
                    )
                }
                items(events, key = { it.id }) { event ->
                    EventRow(
                        event = event,
                        selectionMode = selectionMode,
                        selected = event.id in selectedEventIds,
                        onClick = {
                            if (selectionMode) toggleEvent(event.id)
                            else onNavigate("event_form?eventId=${event.id}")
                        }
                    )
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
                }
                showFolderDialog = false
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
                }
                showMoveDialog = false
                exitSelection()
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
            title = { Text("删除 $totalSelected 项？") },
            text = { Text("此操作不可撤销。删除文件夹时，其中的事件会自动移出到根目录。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (selectedEventIds.isNotEmpty())
                            container.eventRepository.deleteByIds(selectedEventIds.toList())
                        if (selectedFolderIds.isNotEmpty())
                            container.folderRepository.deleteByIds(selectedFolderIds.toList())
                    }
                    showDeleteConfirm = false
                    exitSelection()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
fun EventRow(
    event: EventEntity,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val days = CountdownCalculator.daysUntil(event.targetDateEpochDay)
    val isFuture = days >= 0
    val text = if (isFuture) "还有 $days 天" else "已过 ${-days} 天"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
        Text(
            text = event.title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFuture) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun FolderRow(
    folder: FolderEntity,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (selectionMode) null else onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
        if (!selectionMode) {
            Text(
                text = "›",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
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
