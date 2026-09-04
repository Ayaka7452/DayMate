@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ayaka7452.daymate.feature.vault

import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
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
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.ReorderableItem
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.core.security.VaultCrypto
import com.ayaka7452.daymate.core.security.VaultSession
import com.ayaka7452.daymate.core.util.CountdownCalculator
import com.ayaka7452.daymate.data.db.VaultEventEntity
import com.ayaka7452.daymate.data.db.VaultFolderEntity
import com.ayaka7452.daymate.feature.common.FolderDialog
import com.ayaka7452.daymate.feature.common.PickFolderDialog
import com.ayaka7452.daymate.feature.common.ReorderActions
import com.ayaka7452.daymate.feature.common.SortModes
import com.ayaka7452.daymate.feature.common.eventDaysUntil
import com.ayaka7452.daymate.feature.common.moveItem
import com.ayaka7452.daymate.feature.common.sortEventsForDisplay
import com.ayaka7452.daymate.feature.common.targetIndexForAction
import android.widget.Toast
import com.ayaka7452.daymate.feature.home.AddSheet
import com.ayaka7452.daymate.feature.home.SelectionDot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun VaultScreen(
    container: AppContainer,
    onExit: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val passwordSet by container.settingsRepository.vaultPasswordSet
        .collectAsState(initial = false)
    // 提升到 VaultScreen 级别的 scope：设密时 passwordSet 翻转会先把 setup 子组合移除，
    // 若 setup 用自己的 rememberCoroutineScope 跑 onUnlocked()，协程会被取消，
    // 导致 unlocked 永远置不上、用户设完密码还要再输一遍。用稳定 scope 避免此问题。
    val scope = rememberCoroutineScope()
    var unlocked by remember { mutableStateOf(false) }

    // 退出 Vault 界面时不主动清空密钥：保留会话内解锁态，
    // 以便从主页「移入 Vault」等操作能正确用密钥加密。仅重置密码时清空（见下方）。
    val handleExit: () -> Unit = onExit

    when {
        unlocked -> VaultListScreen(
            container,
            onExit = handleExit,
            onReset = { unlocked = false },
            onNavigate = onNavigate
        )
        !passwordSet -> VaultSetupScreen(
            container,
            scope = scope,
            onUnlocked = { unlocked = true },
            onExit = handleExit
        )
        else -> VaultUnlockScreen(
            container,
            onUnlocked = { unlocked = true },
            onExit = handleExit
        )
    }
}

@Composable
private fun VaultSetupScreen(
    container: AppContainer,
    scope: CoroutineScope,
    onUnlocked: () -> Unit,
    onExit: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var enableBiometric by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    VaultScaffold(title = "设置 Vault 密码", onExit = onExit, showMenu = false) {
        Text(
            "首次进入，请设置密码（至少 6 位）。请牢记，此密码无法找回。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("确认密码") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                when {
                    password.length < 6 -> error = "密码至少 6 位"
                    password != confirm -> error = "两次输入的密码不一致"
                    else -> {
                        scope.launch {
                            val salt = VaultCrypto.newSalt()
                            val hash = VaultCrypto.hash(password, salt)
                            container.settingsRepository.setVaultPassword(hash, salt)
                            container.settingsRepository.setVaultBiometric(enableBiometric)
                            VaultSession.unlock(VaultCrypto.key(password, salt))
                            onUnlocked()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("完成设置")
        }
    }
}

@Composable
private fun VaultUnlockScreen(
    container: AppContainer,
    onUnlocked: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val hash by container.settingsRepository.vaultPasswordHash.collectAsState(initial = null)
    val salt by container.settingsRepository.vaultSalt.collectAsState(initial = null)
    val biometricEnabled by container.settingsRepository.vaultBiometricEnabled
        .collectAsState(initial = false)

    val biometricAvailable = remember(activity) {
        activity != null &&
            activity.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) &&
            BiometricManager.from(activity)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticateWithBiometric() {
        val act = activity ?: return
        val executor = ContextCompat.getMainExecutor(act)
        val prompt = BiometricPrompt(
            act,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("验证身份")
            .setSubtitle("解锁 Vault")
            .setNegativeButtonText("取消")
            .build()
        prompt.authenticate(info)
    }

    VaultScaffold(title = "Vault", onExit = onExit, showMenu = false) {
        Text("输入密码解锁", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                error = null
            },
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val h = hash ?: return@Button
                val s = salt ?: return@Button
                if (password.isBlank()) {
                    error = "请输入密码"
                    return@Button
                }
                val ok = try {
                    VaultCrypto.hash(password, s) == h
                } catch (e: Exception) {
                    false
                }
                if (ok) {
                    VaultSession.unlock(VaultCrypto.key(password, s))
                    onUnlocked()
                } else {
                    error = "密码错误"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Lock, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("解锁")
        }
        if (biometricAvailable && biometricEnabled) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { authenticateWithBiometric() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("使用指纹")
            }
        }
    }
}

@Composable
private fun VaultListScreen(
    container: AppContainer,
    onExit: () -> Unit,
    onReset: () -> Unit = {},
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val eventsFlow = remember { container.vaultRepository.observeRoot() }
    val events by eventsFlow.collectAsState(initial = emptyList())
    val foldersFlow = remember { container.vaultFolderRepository.observeAll() }
    val folders by foldersFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var isDragging by remember { mutableStateOf(false) }
    val folderList = remember { mutableStateListOf<VaultFolderEntity>() }
    val eventList = remember { mutableStateListOf<VaultEventEntity>() }
    val listState = rememberLazyListState()
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

    // 排序模式：manual 才允许手动调整顺序
    val defaultSort by container.settingsRepository.defaultSort
        .collectAsState(initial = SortModes.REMAINING_ASC)
    val manualSort = defaultSort == SortModes.MANUAL

    // 事件显示列表：manual 保持手动顺序，其余按剩余天数排序
    val displayEvents = remember(eventList.toList(), defaultSort) {
        sortEventsForDisplay(eventList.toList(), defaultSort) { eventDaysUntil(it.targetDateEpochDay) }
    }

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
                container.vaultFolderRepository.update(f.copy(sortIndex = index))
            }
        }
    }
    fun persistEventOrder() {
        scope.launch {
            eventList.forEachIndexed { index, e ->
                container.vaultRepository.update(e.copy(sortIndex = index))
            }
        }
    }
    fun moveVaultEvent(event: VaultEventEntity, action: String) {
        if (!manualSort) {
            Toast.makeText(
                context,
                "当前排序模式不支持手动调整，请在「设置 → 默认排序」中切换为手动排序",
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
    fun moveVaultFolder(folder: VaultFolderEntity, action: String) {
        if (!manualSort) {
            Toast.makeText(
                context,
                "当前排序模式不支持手动调整，请在「设置 → 默认排序」中切换为手动排序",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val index = folderList.indexOfFirst { it.id == folder.id }
        if (index >= 0) {
            folderList.moveItem(index, targetIndexForAction(index, folderList.size, action))
            persistFolderOrder()
        }
    }

    var showAddSheet by remember { mutableStateOf(false) }
    var showEventDialog by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<VaultEventEntity?>(null) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var folderTarget by remember { mutableStateOf<VaultFolderEntity?>(null) }
    var pendingMoveAfterCreate by remember { mutableStateOf(false) }

    var selectionMode by remember { mutableStateOf(false) }
    val selectedEventIds = remember { mutableStateListOf<Long>() }
    val selectedFolderIds = remember { mutableStateListOf<Long>() }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val totalSelected = selectedEventIds.size + selectedFolderIds.size

    fun toggleEvent(id: Long) {
        if (id in selectedEventIds) selectedEventIds.remove(id) else selectedEventIds.add(id)
    }

    fun toggleFolder(id: Long) {
        if (id in selectedFolderIds) selectedFolderIds.remove(id) else selectedFolderIds.add(id)
    }

    fun enterSelection() {
        selectedEventIds.clear(); selectedFolderIds.clear(); selectionMode = true
    }

    fun exitSelection() {
        selectedEventIds.clear(); selectedFolderIds.clear(); selectionMode = false
    }

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    VaultScaffold(
        title = "🔒 Vault",
        onExit = onExit,
        selectionMode = selectionMode,
        totalSelected = totalSelected,
        onExitSelection = { exitSelection() },
        hasEventsSelected = selectedEventIds.isNotEmpty(),
        onMove = { showMoveDialog = true },
        onDelete = { showDeleteConfirm = true },
        menuItems = {
            DropdownMenuItem(
                text = { Text("批量管理") },
                onClick = { enterSelection() }
            )
            DropdownMenuItem(
                text = { Text("重置 Vault 密码") },
                onClick = { showResetConfirm = true }
            )
        },
        fab = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "新建")
            }
        }
    ) {
        if (events.isEmpty() && folders.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🔒", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Vault 是空的，点击 + 添加",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(folderList, key = { "f${it.id}" }) { folder ->
                    ReorderableItem(reorderableState, key = "f${folder.id}") {
                        val handleModifier = if (!selectionMode && manualSort) {
                            Modifier.draggableHandle(
                                onDragStarted = { isDragging = true },
                                onDragStopped = {
                                    isDragging = false
                                    persistFolderOrder()
                                }
                            )
                        } else null
                        VaultFolderRow(
                            folder = folder,
                            selectionMode = selectionMode,
                            selected = folder.id in selectedFolderIds,
                            onClick = {
                                if (selectionMode) toggleFolder(folder.id)
                                else onNavigate("vault_folder/${folder.id}")
                            },
                            onLongClick = {
                                if (!selectionMode) {
                                    folderTarget = folder
                                    showFolderDialog = true
                                }
                            },
                            dragHandle = handleModifier,
                            onReorder = { action -> moveVaultFolder(folder, action) }
                        )
                    }
                    ListItemDivider()
                }
                items(displayEvents, key = { "e${it.id}" }) { event ->
                    ReorderableItem(reorderableState, key = "e${event.id}") {
                        val handleModifier = if (selectionMode && manualSort) {
                            Modifier.draggableHandle(
                                onDragStarted = { isDragging = true },
                                onDragStopped = {
                                    isDragging = false
                                    persistEventOrder()
                                }
                            )
                        } else null
                        VaultEventRow(
                            event = event,
                            selectionMode = selectionMode,
                            selected = event.id in selectedEventIds,
                            onClick = {
                                if (selectionMode) toggleEvent(event.id)
                                else {
                                    editingEvent = event
                                    showEventDialog = true
                                }
                            },
                            onMoveToMain = {
                                scope.launch { container.vaultBridge.moveVaultEventToMain(event.id) }
                            },
                            onReorder = { action -> moveVaultEvent(event, action) },
                            dragHandle = handleModifier
                        )
                    }
                    ListItemDivider()
                }
            }
        }
    }

    if (showAddSheet) {
        AddSheet(
            onDismiss = { showAddSheet = false },
            onCreateEvent = {
                showAddSheet = false
                editingEvent = null
                showEventDialog = true
            },
            onCreateFolder = {
                showAddSheet = false
                folderTarget = null
                pendingMoveAfterCreate = false
                showFolderDialog = true
            }
        )
    }

    if (showEventDialog) {
        VaultEventDialog(
            container = container,
            existing = editingEvent,
            onDismiss = { showEventDialog = false }
        )
    }

    if (showFolderDialog) {
        FolderDialog(
            initialName = folderTarget?.name ?: "",
            initialIcon = folderTarget?.icon ?: "📁",
            title = if (folderTarget == null) "新建文件夹" else "编辑文件夹",
            confirmLabel = if (folderTarget == null) "创建" else "保存",
            onDismiss = {
                showFolderDialog = false
                pendingMoveAfterCreate = false
            },
            onSave = { name, icon ->
                scope.launch {
                    if (folderTarget == null) {
                        val newId = container.vaultFolderRepository.add(
                            VaultFolderEntity(name = name, icon = icon)
                        )
                        if (pendingMoveAfterCreate) {
                            container.vaultRepository.moveToFolder(selectedEventIds.toList(), newId)
                            pendingMoveAfterCreate = false
                            exitSelection()
                        }
                    } else {
                        folderTarget?.let {
                            container.vaultFolderRepository.update(it.copy(name = name, icon = icon))
                        }
                    }
                }
                showFolderDialog = false
            },
            onDelete = if (folderTarget != null) {
                {
                    scope.launch { folderTarget?.let { container.vaultFolderRepository.delete(it) } }
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
                scope.launch { container.vaultRepository.moveToFolder(selectedEventIds.toList(), folderId) }
                showMoveDialog = false
                exitSelection()
            },
            onCreateNew = {
                showMoveDialog = false
                folderTarget = null
                pendingMoveAfterCreate = true
                showFolderDialog = true
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除 $totalSelected 项？") },
            text = { Text("此操作不可撤销。删除文件夹时，其中的事件会自动移出到 Vault 根目录。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (selectedFolderIds.isNotEmpty()) {
                            container.vaultRepository.unparentByFolders(selectedFolderIds.toList())
                            container.vaultFolderRepository.deleteByIds(selectedFolderIds.toList())
                        }
                        if (selectedEventIds.isNotEmpty())
                            container.vaultRepository.deleteByIds(selectedEventIds.toList())
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

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("重置 Vault 密码？") },
            text = {
                Text(
                    "此操作会清空 Vault 内的全部数据（事件与文件夹），且无法找回。" +
                        "重置完成后你需要重新设置 Vault 密码。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        container.vaultRepository.clearAll()
                        container.vaultFolderRepository.clearAll()
                        container.settingsRepository.clearVaultPassword()
                        VaultSession.lock()
                        onReset()
                    }
                    showResetConfirm = false
                }) { Text("清空并重置") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun VaultSelectionBar(
    totalSelected: Int,
    hasEventsSelected: Boolean,
    onExit: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onExit) { Text("完成") }
            Spacer(Modifier.width(8.dp))
            Text("已选 $totalSelected 项", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            if (hasEventsSelected) {
                TextButton(onClick = onMove) { Text("移入文件夹") }
            }
            TextButton(onClick = onDelete, enabled = totalSelected > 0) { Text("删除") }
        }
    }
}

@Composable
fun VaultFolderScreen(
    container: AppContainer,
    folderId: Long,
    onBack: () -> Unit
) {
    var folder by remember { mutableStateOf<VaultFolderEntity?>(null) }
    LaunchedEffect(folderId) { folder = container.vaultFolderRepository.getById(folderId) }

    val eventsFlow = remember(folderId) { container.vaultRepository.observeByFolder(folderId) }
    val events by eventsFlow.collectAsState(initial = emptyList())
    val allFoldersFlow = remember { container.vaultFolderRepository.observeAll() }
    val allFolders by allFoldersFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val folderContext = LocalContext.current

    // 排序模式：manual 才允许手动调整顺序
    val defaultSort by container.settingsRepository.defaultSort
        .collectAsState(initial = SortModes.REMAINING_ASC)
    val manualSort = defaultSort == SortModes.MANUAL

    // 拖拽排序用的可变镜像列表（拖拽中不同步，避免跳动）
    var isDraggingEvents by remember { mutableStateOf(false) }
    val eventList = remember { mutableStateListOf<VaultEventEntity>() }
    LaunchedEffect(events) {
        if (!isDraggingEvents) {
            eventList.clear()
            eventList.addAll(events)
        }
    }

    // 事件显示列表：manual 保持手动顺序，其余按剩余天数排序
    val displayEvents = remember(eventList.toList(), defaultSort) {
        sortEventsForDisplay(eventList.toList(), defaultSort) { eventDaysUntil(it.targetDateEpochDay) }
    }

    val folderListState = rememberLazyListState()
    val folderReorderableState = rememberReorderableLazyListState(folderListState) { from, to ->
        val fk = from.key.toString()
        val tk = to.key.toString()
        if (fk.startsWith("e") && tk.startsWith("e")) {
            val fi = eventList.indexOfFirst { "e${it.id}" == fk }
            val ti = eventList.indexOfFirst { "e${it.id}" == tk }
            if (fi >= 0 && ti >= 0) eventList.moveItem(fi, ti)
        }
    }

    fun persistVaultFolderEventOrder() {
        scope.launch {
            eventList.forEachIndexed { index, e ->
                container.vaultRepository.update(e.copy(sortIndex = index))
            }
        }
    }

    fun moveVaultFolderEvent(event: VaultEventEntity, action: String) {
        if (!manualSort) {
            Toast.makeText(
                folderContext,
                "当前排序模式不支持手动调整，请在「设置 → 默认排序」中切换为手动排序",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val index = eventList.indexOfFirst { it.id == event.id }
        if (index >= 0) {
            eventList.moveItem(index, targetIndexForAction(index, eventList.size, action))
            persistVaultFolderEventOrder()
        }
    }

    var showEventDialog by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<VaultEventEntity?>(null) }

    var showFolderDialog by remember { mutableStateOf(false) }
    var pendingMoveAfterCreate by remember { mutableStateOf(false) }

    var selectionMode by remember { mutableStateOf(false) }
    val selectedEventIds = remember { mutableStateListOf<Long>() }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFolderDeleteConfirm by remember { mutableStateOf(false) }

    val totalSelected = selectedEventIds.size

    fun toggleEvent(id: Long) {
        if (id in selectedEventIds) selectedEventIds.remove(id) else selectedEventIds.add(id)
    }

    fun enterSelection() {
        selectedEventIds.clear(); selectionMode = true
    }

    fun exitSelection() {
        selectedEventIds.clear(); selectionMode = false
    }

    VaultScaffold(
        title = folder?.name ?: "文件夹",
        onExit = onBack,
        selectionMode = selectionMode,
        totalSelected = totalSelected,
        onExitSelection = { exitSelection() },
        hasEventsSelected = selectedEventIds.isNotEmpty(),
        onMove = { showMoveDialog = true },
        onDelete = { showDeleteConfirm = true },
        menuItems = {
            DropdownMenuItem(
                text = { Text("批量管理") },
                onClick = { enterSelection() }
            )
            DropdownMenuItem(
                text = { Text("重命名") },
                onClick = { showFolderDialog = true }
            )
            DropdownMenuItem(
                text = { Text("删除文件夹") },
                onClick = { showFolderDeleteConfirm = true }
            )
        },
        fab = {
            FloatingActionButton(onClick = {
                editingEvent = null
                showEventDialog = true
            }) { Icon(Icons.Default.Add, contentDescription = "新建事件") }
        }
    ) {
        if (events.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
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
            LazyColumn(state = folderListState, modifier = Modifier.fillMaxSize()) {
                items(displayEvents, key = { "e${it.id}" }) { event ->
                    ReorderableItem(folderReorderableState, key = "e${event.id}") {
                        val handleModifier = if (selectionMode && manualSort) {
                            Modifier.draggableHandle(
                                onDragStarted = { isDraggingEvents = true },
                                onDragStopped = {
                                    isDraggingEvents = false
                                    persistVaultFolderEventOrder()
                                }
                            )
                        } else null
                        VaultEventRow(
                            event = event,
                            selectionMode = selectionMode,
                            selected = event.id in selectedEventIds,
                            onClick = {
                                if (selectionMode) toggleEvent(event.id)
                                else {
                                    editingEvent = event
                                    showEventDialog = true
                                }
                            },
                            onMoveToMain = {
                                scope.launch { container.vaultBridge.moveVaultEventToMain(event.id) }
                            },
                            onReorder = { action -> moveVaultFolderEvent(event, action) },
                            dragHandle = handleModifier
                        )
                    }
                    ListItemDivider()
                }
            }
        }
    }

    if (showEventDialog) {
        VaultEventDialog(
            container = container,
            existing = editingEvent,
            folderId = folderId,
            onDismiss = { showEventDialog = false }
        )
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
                    folder?.let { container.vaultFolderRepository.update(it.copy(name = name, icon = icon)) }
                    folder = container.vaultFolderRepository.getById(folderId)
                }
                showFolderDialog = false
            },
            onDelete = {
                scope.launch {
                    folder?.let { container.vaultFolderRepository.delete(it) }
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
                scope.launch { container.vaultRepository.moveToFolder(selectedEventIds.toList(), targetFolderId) }
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
                            container.vaultRepository.deleteByIds(selectedEventIds.toList())
                    }
                    showDeleteConfirm = false
                    exitSelection()
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }

    if (showFolderDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showFolderDeleteConfirm = false },
            title = { Text("删除文件夹？") },
            text = {
                Text("文件夹「${folder?.name ?: ""}」内的事件会移出到 Vault 根目录，仅文件夹本身被删除。此操作不可撤销。")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        folder?.let {
                            container.vaultRepository.unparentByFolders(listOf(it.id))
                            container.vaultFolderRepository.delete(it)
                        }
                    }
                    showFolderDeleteConfirm = false
                    onBack()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun VaultEventDialog(
    container: AppContainer,
    existing: VaultEventEntity?,
    folderId: Long? = null,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var epochDay by remember {
        mutableStateOf(existing?.targetDateEpochDay ?: LocalDate.now().plusDays(7).toEpochDay())
    }
    var refDaysText by remember { mutableStateOf(existing?.refDays?.toString() ?: "") }
    var displayUnit by remember {
        mutableStateOf(existing?.displayUnit ?: CountdownCalculator.UNIT_DAY)
    }
    var showDatePicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 对照值的单位跟随「倒计时显示单位」：按月显示时对照值即「月数」，其余同理。
    val refUnitLabel = when (displayUnit) {
        CountdownCalculator.UNIT_MONTH -> "月数"
        CountdownCalculator.UNIT_YEAR -> "年数"
        else -> "天数"
    }
    val datePickerState = rememberDatePickerState()
    LaunchedEffect(epochDay) {
        datePickerState.selectedDateMillis = LocalDate.ofEpochDay(epochDay)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val refValue = refDaysText.toIntOrNull()?.takeIf { it > 0 }
                    if (existing == null) {
                        container.vaultRepository.add(
                            VaultEventEntity(
                                title = title.ifBlank { "未命名" },
                                targetDateEpochDay = epochDay,
                                refDays = refValue,
                                displayUnit = displayUnit.takeIf { it != CountdownCalculator.UNIT_DAY },
                                folderId = folderId
                            )
                        )
                    } else {
                        container.vaultRepository.update(
                            existing.copy(
                                title = title.ifBlank { "未命名" },
                                targetDateEpochDay = epochDay,
                                refDays = refValue,
                                displayUnit = displayUnit.takeIf { it != CountdownCalculator.UNIT_DAY }
                            )
                        )
                    }
                }
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text(if (existing == null) "新建 Vault 事件" else "编辑 Vault 事件") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { showDatePicker = true }) {
                    Text(
                        "目标日期：${
                            LocalDate.ofEpochDay(epochDay)
                                .format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
                        }",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Spacer(Modifier.height(12.dp))

                Text("倒计时显示单位", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = displayUnit == CountdownCalculator.UNIT_DAY,
                        onClick = { displayUnit = CountdownCalculator.UNIT_DAY },
                        label = { Text("天数") }
                    )
                    FilterChip(
                        selected = displayUnit == CountdownCalculator.UNIT_MONTH,
                        onClick = { displayUnit = CountdownCalculator.UNIT_MONTH },
                        label = { Text("月数") }
                    )
                    FilterChip(
                        selected = displayUnit == CountdownCalculator.UNIT_YEAR,
                        onClick = { displayUnit = CountdownCalculator.UNIT_YEAR },
                        label = { Text("年数") }
                    )
                }
                Text(
                    "随时可更改；按月/按年不足一个完整单位时自动改用更小的单位显示",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = refDaysText,
                    onValueChange = { refDaysText = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text("对照${refUnitLabel}（可选）") },
                    placeholder = { Text("例如：8") },
                    supportingText = { Text("目标日期已过去时，显示为「已过 X/N $refUnitLabel」，如 2/8；切换显示单位后请按新单位填写") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        epochDay = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
        ) { DatePicker(state = datePickerState) }
    }
}

@Composable
private fun VaultEventRow(
    event: VaultEventEntity,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onMoveToMain: (() -> Unit)? = null,
    onReorder: ((String) -> Unit)? = null,
    dragHandle: Modifier? = null
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
        if (dragHandle != null) {
            IconButton(modifier = dragHandle, onClick = {}) {
                Text(
                    "⠿",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        if (!selectionMode && onMoveToMain != null) {
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
                    }
                    DropdownMenuItem(
                        text = { Text("移出到主空间") },
                        onClick = {
                            menuExpanded = false
                            onMoveToMain()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultFolderRow(
    folder: VaultFolderEntity,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    dragHandle: Modifier? = null,
    onReorder: ((String) -> Unit)? = null
) {
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
        Text(text = folder.icon ?: "📁", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(10.dp))
        Text(
            text = folder.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (!selectionMode && onReorder != null) {
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
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
                }
            }
        }
        if (dragHandle != null) {
            IconButton(modifier = dragHandle, onClick = {}) {
                Text(
                    "⠿",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Spacer(Modifier.width(4.dp))
        }
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
private fun ListItemDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultScaffold(
    title: String,
    onExit: () -> Unit,
    selectionMode: Boolean = false,
    totalSelected: Int = 0,
    onExitSelection: (() -> Unit)? = null,
    hasEventsSelected: Boolean = false,
    onMove: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    fab: @Composable (() -> Unit)? = null,
    menuItems: @Composable ColumnScope.() -> Unit = {},
    showMenu: Boolean = true,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("已选 $totalSelected 项") },
                    navigationIcon = {
                        IconButton(onClick = { onExitSelection?.invoke() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "完成")
                        }
                    },
                    actions = {
                        if (hasEventsSelected && onMove != null) {
                            TextButton(onClick = onMove) { Text("移入文件夹") }
                        }
                        if (onDelete != null) {
                            TextButton(
                                onClick = onDelete,
                                enabled = totalSelected > 0
                            ) { Text("删除") }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onExit) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出 Vault")
                        }
                    },
                    actions = {
                        if (showMenu) {
                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "菜单")
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) { menuItems() }
                            }
                        }
                        TextButton(onClick = onExit) { Text("退出") }
                    }
                )
            }
        },
        floatingActionButton = { fab?.invoke() },
        bottomBar = {
            if (selectionMode) {
                VaultSelectionBar(
                    totalSelected = totalSelected,
                    hasEventsSelected = hasEventsSelected,
                    onExit = { onExitSelection?.invoke() },
                    onMove = { onMove?.invoke() },
                    onDelete = { onDelete?.invoke() }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            content()
        }
    }
}
