package com.daymate.app.feature.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("DayMate", fontFamily = FontFamily.Cursive)
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
                                onClick = { menuExpanded = false }
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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "新建")
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
                    FolderRow(folder)
                }
                items(events, key = { it.id }) { event ->
                    EventRow(event)
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
                scope.launch {
                    container.folderRepository.add(FolderEntity(name = "新文件夹"))
                }
            }
        )
    }
}

@Composable
fun EventRow(event: EventEntity) {
    val days = CountdownCalculator.daysUntil(event.targetDateEpochDay)
    val isFuture = days >= 0
    val text = if (isFuture) "还有 $days 天" else "已过 ${-days} 天"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(event.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(10.dp))
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
fun FolderRow(folder: FolderEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = folder.icon ?: "📁",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = folder.name,
            style = MaterialTheme.typography.bodyLarge
        )
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
