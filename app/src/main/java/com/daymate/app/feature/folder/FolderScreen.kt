package com.daymate.app.feature.folder

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.daymate.app.core.AppContainer
import com.daymate.app.data.db.EventEntity
import com.daymate.app.data.db.FolderEntity
import com.daymate.app.feature.common.FolderDialog
import com.daymate.app.feature.common.PickFolderDialog
import com.daymate.app.feature.home.EventRow
import com.daymate.app.feature.home.SelectionDot
import kotlinx.coroutines.launch

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

    val events by container.eventRepository.observeByFolder(folderId)
        .collectAsState(initial = emptyList())
    val allFolders by container.folderRepository.observeAll()
        .collectAsState(initial = emptyList())

    var showFolderDialog by remember { mutableStateOf(false) }
    var pendingMoveAfterCreate by remember { mutableStateOf(false) }

    var selectionMode by remember { mutableStateOf(false) }
    val selectedEventIds = remember { mutableStateListOf<Long>() }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val totalSelected = selectedEventIds.size

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
                            onClick = { showDeleteConfirm = true },
                            enabled = totalSelected > 0
                        ) { Text("删除") }
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
                                    text = { Text("删除文件夹") },
                                    onClick = {
                                        menuExpanded = false
                                        scope.launch {
                                            folder?.let { container.folderRepository.delete(it) }
                                            onBack()
                                        }
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
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(events, key = { it.id }) { event ->
                    EventRow(
                        event = event,
                        selectionMode = selectionMode,
                        selected = event.id in selectedEventIds,
                        onClick = {
                            if (selectionMode) toggleEvent(event.id)
                            else onNavigate("event_form?eventId=${event.id}")
                        },
                        onMoveToVault = {
                            scope.launch { container.vaultBridge.moveEventToVault(event.id) }
                        }
                    )
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
            title = { Text("删除 $totalSelected 项？") },
            text = { Text("此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (selectedEventIds.isNotEmpty())
                            container.eventRepository.deleteByIds(selectedEventIds.toList())
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
