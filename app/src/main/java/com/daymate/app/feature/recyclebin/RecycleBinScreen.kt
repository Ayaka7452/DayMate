package com.ayaka7452.daymate.feature.recyclebin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.data.db.EventEntity
import com.ayaka7452.daymate.data.db.FolderEntity
import kotlinx.coroutines.launch

private data class BinTarget(val id: Long, val type: String) // type: "event" | "folder"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val binEventsFlow = remember { container.eventRepository.observeBin() }
    val binEvents by binEventsFlow.collectAsState(initial = emptyList())
    val binFoldersFlow = remember { container.folderRepository.observeBin() }
    val binFolders by binFoldersFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var confirmTarget by remember { mutableStateOf<BinTarget?>(null) }
    var clearConfirm by remember { mutableStateOf(false) }

    val hasItems = binEvents.isNotEmpty() || binFolders.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("回收站") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (hasItems) {
                        TextButton(onClick = { clearConfirm = true }) { Text("清空回收站") }
                    }
                }
            )
        }
    ) { padding ->
        if (!hasItems) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🗑️", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "回收站是空的",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(binFolders, key = { "f${it.id}" }) { folder ->
                    BinRow(
                        title = "${folder.icon ?: "📁"}  ${folder.name}",
                        subtitle = "文件夹",
                        onRestore = {
                            scope.launch {
                                val ts = System.currentTimeMillis()
                                container.eventRepository.restoreByFolders(listOf(folder.id))
                                container.folderRepository.restoreByIds(listOf(folder.id))
                            }
                        },
                        onDelete = { confirmTarget = BinTarget(folder.id, "folder") }
                    )
                }
                items(binEvents, key = { "e${it.id}" }) { event ->
                    BinRow(
                        title = event.title,
                        subtitle = if (event.note.isNullOrBlank()) null else event.note,
                        onRestore = {
                            scope.launch {
                                container.eventRepository.restoreByIds(listOf(event.id))
                            }
                        },
                        onDelete = { confirmTarget = BinTarget(event.id, "event") }
                    )
                }
            }
        }
    }

    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            title = { Text("清空回收站？") },
            text = { Text("将永久删除全部 ${binEvents.size + binFolders.size} 项，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        container.eventRepository.deleteByIds(binEvents.map { it.id })
                        binFolders.forEach {
                            container.eventRepository.hardDeleteEventsByFolders(listOf(it.id))
                            container.folderRepository.deleteByIds(listOf(it.id))
                        }
                    }
                    clearConfirm = false
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("取消") } }
        )
    }

    if (confirmTarget != null) {
        val target = confirmTarget!!
        val name = if (target.type == "folder") "文件夹" else "事件"
        AlertDialog(
            onDismissRequest = { confirmTarget = null },
            title = { Text("彻底删除？") },
            text = { Text("将永久删除该$name，不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (target.type == "folder") {
                            container.eventRepository.hardDeleteEventsByFolders(listOf(target.id))
                            container.folderRepository.deleteByIds(listOf(target.id))
                        } else {
                            container.eventRepository.deleteByIds(listOf(target.id))
                        }
                    }
                    confirmTarget = null
                }) { Text("彻底删除") }
            },
            dismissButton = { TextButton(onClick = { confirmTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun BinRow(
    title: String,
    subtitle: String?,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        TextButton(onClick = onRestore) { Text("恢复") }
        TextButton(onClick = onDelete) { Text("彻底删除") }
    }
}
