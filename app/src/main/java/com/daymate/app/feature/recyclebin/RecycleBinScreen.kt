package com.ayaka7452.daymate.feature.recyclebin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.ayaka7452.daymate.feature.common.matchesQuery
import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.i18n.Tr
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

    // 回收站搜索：过滤已删除的事件（标题/备注）与文件夹（名称）
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    BackHandler(enabled = searchActive) {
        searchActive = false
        searchQuery = ""
    }
    val query = searchQuery.trim()
    val shownFolders = remember(binFolders, query) {
        if (query.isEmpty()) binFolders
        else {
            val ql = query.lowercase()
            binFolders.filter { it.name.lowercase().contains(ql) }
        }
    }
    val shownEvents = remember(binEvents, query) {
        if (query.isEmpty()) binEvents
        else binEvents.filter { matchesQuery(it.title, it.note, query) }
    }

    val hasItems = binEvents.isNotEmpty() || binFolders.isNotEmpty()

    /**
     * 恢复文件夹：只把「随该文件夹一起进回收站」的事件一并复活。
     *
     * 为什么不能直接调 `restoreByFolders(folderIds)`：那条 SQL 是
     * `UPDATE events SET isDeleted=0, deletedAt=0 WHERE folderId IN (...)`，**不带 isDeleted 过滤**。
     * 而删除文件夹走的是 `unparentByFolders`（`UPDATE events SET folderId=NULL WHERE ... AND isDeleted=0`），
     * 它只把**当时还活着**的事件解除归属、留在主空间，已软删的事件保持 folderId 不变、继续躺在回收站。
     * 于是只剩「先前被单独删掉、且恰好还在这个文件夹里」的事件匹配上 `folderId IN (...)`，
     * 恢复文件夹会把它们静默复活——等于丢弃用户的删除意图。
     *
     * 判据用文件夹自己的 deletedAt：删除文件夹那一刻，其内仍存活的事件 deletedAt 会**晚于或等于**
     * 先前被单独删除的那些（后者写入的是更早的时刻）。这里在内存里按同一口径挑出子事件 id，
     * 再走 [EventRepository.restoreByIds] 精确恢复，不动 DAO/仓储。
     */
    fun restoreFolder(folderId: Long, folderDeletedAt: Long) {
        scope.launch {
            val childIds = binEvents
                .filter { it.folderId == folderId && it.deletedAt >= folderDeletedAt }
                .map { it.id }
            if (childIds.isNotEmpty()) {
                container.eventRepository.restoreByIds(childIds)
            }
            container.folderRepository.restoreByIds(listOf(folderId))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Tr.s(R.string.bin_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Tr.s(R.string.common_back))
                    }
                },
                actions = {
                    if (hasItems) {
                        IconButton(onClick = {
                            searchActive = !searchActive
                            if (!searchActive) searchQuery = ""
                        }) {
                            Icon(Icons.Default.Search, contentDescription = Tr.s(R.string.common_search))
                        }
                        TextButton(onClick = { clearConfirm = true }) { Text(Tr.s(R.string.bin_clear)) }
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
                // 空状态只留文字提示：emoji 属于 UI 装饰，各机型渲染差异大，不再体现
                Text(
                    Tr.s(R.string.bin_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (searchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(Tr.s(R.string.bin_search_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                if (searchActive && query.isNotEmpty() &&
                    shownFolders.isEmpty() && shownEvents.isEmpty()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            Tr.s(R.string.bin_not_found),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(shownFolders, key = { "f${it.id}" }) { folder ->
                            BinRow(
                                title = "${folder.icon ?: "📁"}  ${folder.name}",
                                subtitle = Tr.s(R.string.common_folder),
                                onRestore = { restoreFolder(folder.id, folder.deletedAt) },
                                onDelete = { confirmTarget = BinTarget(folder.id, "folder") }
                            )
                        }
                        items(shownEvents, key = { "e${it.id}" }) { event ->
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
        }
    }

    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            title = { Text(Tr.s(R.string.bin_clear_confirm_title)) },
            text = { Text(Tr.s(R.string.bin_clear_confirm_desc, binEvents.size + binFolders.size)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        // 先删回收站里的事件（只删 isDeleted=1，删完下面按文件夹硬删时已不会重复命中它们），
                        // 再按文件夹整批硬删子事件 + 删文件夹行：各一次批量调用，
                        // 此前逐个文件夹循环会每次都触发一次刷新发射（N+1）。
                        val eventIds = binEvents.map { it.id }
                        if (eventIds.isNotEmpty()) {
                            container.eventRepository.deleteByIds(eventIds)
                        }
                        val folderIds = binFolders.map { it.id }
                        if (folderIds.isNotEmpty()) {
                            container.eventRepository.hardDeleteEventsByFolders(folderIds)
                            container.folderRepository.deleteByIds(folderIds)
                        }
                    }
                    clearConfirm = false
                }) { Text(Tr.s(R.string.common_clear)) }
            },
            dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text(Tr.s(R.string.common_cancel)) } }
        )
    }

    if (confirmTarget != null) {
        val target = confirmTarget!!
        val name = if (target.type == "folder") Tr.s(R.string.common_folder) else Tr.s(R.string.common_event)
        AlertDialog(
            onDismissRequest = { confirmTarget = null },
            title = { Text(Tr.s(R.string.bin_purge_title)) },
            text = { Text(Tr.s(R.string.bin_purge_desc, name)) },
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
                }) { Text(Tr.s(R.string.bin_purge)) }
            },
            dismissButton = { TextButton(onClick = { confirmTarget = null }) { Text(Tr.s(R.string.common_cancel)) } }
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
        TextButton(onClick = onRestore) { Text(Tr.s(R.string.common_restore)) }
        TextButton(onClick = onDelete) { Text(Tr.s(R.string.bin_purge)) }
    }
}
