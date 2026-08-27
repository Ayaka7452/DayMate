package com.daymate.app.feature.vault

import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.daymate.app.core.AppContainer
import com.daymate.app.core.security.VaultCrypto
import com.daymate.app.core.util.CountdownCalculator
import com.daymate.app.data.db.VaultEventEntity
import com.daymate.app.data.db.VaultFolderEntity
import com.daymate.app.feature.common.FolderDialog
import com.daymate.app.feature.common.PickFolderDialog
import com.daymate.app.feature.home.AddSheet
import com.daymate.app.feature.home.SelectionDot
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
    var unlocked by remember { mutableStateOf(false) }

    when {
        !passwordSet -> VaultSetupScreen(container, onExit)
        !unlocked -> VaultUnlockScreen(container, onUnlocked = { unlocked = true }, onExit = onExit)
        else -> VaultListScreen(container, onExit = onExit, onNavigate = onNavigate)
    }
}

@Composable
private fun VaultSetupScreen(container: AppContainer, onExit: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var enableBiometric by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    VaultScaffold(title = "设置 Vault 密码", onExit = onExit) {
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

    VaultScaffold(title = "Vault", onExit = onExit) {
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
                if (VaultCrypto.hash(password, s) == h) {
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
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val events by container.vaultRepository.observeRoot()
        .collectAsState(initial = emptyList())
    val folders by container.vaultFolderRepository.observeAll()
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

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
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(folders, key = { it.id }) { folder ->
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
                        }
                    )
                }
                items(events, key = { it.id }) { event ->
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
                        if (selectedEventIds.isNotEmpty())
                            container.vaultRepository.deleteByIds(selectedEventIds.toList())
                        if (selectedFolderIds.isNotEmpty())
                            container.vaultFolderRepository.deleteByIds(selectedFolderIds.toList())
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

    if (selectionMode) {
        VaultSelectionBar(
            totalSelected = totalSelected,
            hasEventsSelected = selectedEventIds.isNotEmpty(),
            onExit = { exitSelection() },
            onMove = { showMoveDialog = true },
            onDelete = { showDeleteConfirm = true }
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

    val events by container.vaultRepository.observeByFolder(folderId)
        .collectAsState(initial = emptyList())
    val allFolders by container.vaultFolderRepository.observeAll()
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var showEventDialog by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<VaultEventEntity?>(null) }

    var showFolderDialog by remember { mutableStateOf(false) }
    var pendingMoveAfterCreate by remember { mutableStateOf(false) }

    var selectionMode by remember { mutableStateOf(false) }
    val selectedEventIds = remember { mutableStateListOf<Long>() }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(events, key = { it.id }) { event ->
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
                        }
                    )
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

    if (selectionMode) {
        VaultSelectionBar(
            totalSelected = totalSelected,
            hasEventsSelected = selectedEventIds.isNotEmpty(),
            onExit = { exitSelection() },
            onMove = { showMoveDialog = true },
            onDelete = { showDeleteConfirm = true }
        )
    } else {
        Box {}
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
    var showDatePicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
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
                    if (existing == null) {
                        container.vaultRepository.add(
                            VaultEventEntity(
                                title = title.ifBlank { "未命名" },
                                targetDateEpochDay = epochDay,
                                folderId = folderId
                            )
                        )
                    } else {
                        container.vaultRepository.update(
                            existing.copy(
                                title = title.ifBlank { "未命名" },
                                targetDateEpochDay = epochDay
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
private fun VaultFolderRow(
    folder: VaultFolderEntity,
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
        Text(text = folder.icon ?: "📁", style = MaterialTheme.typography.bodyLarge)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultScaffold(
    title: String,
    onExit: () -> Unit,
    fab: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出 Vault")
                    }
                },
                actions = {
                    TextButton(onClick = onExit) { Text("退出") }
                }
            )
        },
        floatingActionButton = { fab?.invoke() }
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
