@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ayaka7452.daymate.feature.vault

import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.i18n.Tr
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.ReorderableItem
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.core.security.BiometricKeyStore
import com.ayaka7452.daymate.core.security.VaultCrypto
import com.ayaka7452.daymate.core.security.VaultSession
import com.ayaka7452.daymate.core.util.CountdownCalculator
import com.ayaka7452.daymate.data.db.VaultEventEntity
import com.ayaka7452.daymate.data.db.VaultFolderEntity
import com.ayaka7452.daymate.feature.common.FolderDialog
import com.ayaka7452.daymate.feature.common.PickFolderDialog
import com.ayaka7452.daymate.feature.common.ReorderMenuItems
import com.ayaka7452.daymate.feature.common.SortModes
import com.ayaka7452.daymate.feature.common.eventDaysUntil
import com.ayaka7452.daymate.feature.common.highlightedText
import com.ayaka7452.daymate.feature.common.matchesQuery
import com.ayaka7452.daymate.feature.common.moveItem
import com.ayaka7452.daymate.feature.common.noteHitOnly
import com.ayaka7452.daymate.feature.common.sortEventsForDisplay
import com.ayaka7452.daymate.feature.common.targetIndexForAction
import android.widget.Toast
import com.ayaka7452.daymate.feature.home.AddSheet
import com.ayaka7452.daymate.feature.home.SelectionDot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.crypto.spec.SecretKeySpec
import java.time.format.DateTimeFormatter

@Composable
fun VaultScreen(
    container: AppContainer,
    onExit: () -> Unit,
    onNavigate: (String) -> Unit
) {
    // 密码状态：首帧同步读一次偏好（DataStore 已被主题读取预热），避免首帧按「未设密」
    // 闪现设密页、下一帧才切到解锁门；后续仍跟随设置变化
    val initialPasswordSet = remember {
        runCatching {
            runBlocking { container.settingsRepository.vaultPasswordSet.first() }
        }.getOrDefault(false)
    }
    val passwordSet by container.settingsRepository.vaultPasswordSet
        .collectAsState(initial = initialPasswordSet)
    // 提升到 VaultScreen 级别的 scope：设密时 passwordSet 翻转会先把 setup 子组合移除，
    // 若 setup 用自己的 rememberCoroutineScope 跑 onUnlocked()，协程会被取消，
    // 导致 unlocked 永远置不上、用户设完密码还要再输一遍。用稳定 scope 避免此问题。
    val scope = rememberCoroutineScope()
    var unlocked by remember { mutableStateOf(false) }

    // 退出 Vault 界面时不主动清空密钥：保留会话内解锁态，
    // 以便从主页「移入 Vault」等操作能正确用密钥加密。仅重置密码时清空（见下方）。

    // 解锁门 ⇄ 内容淡入淡出：解锁/设密完成不生硬跳变
    val gateState = when {
        unlocked -> "list"
        !passwordSet -> "setup"
        else -> "unlock"
    }

    // 防截屏/最近任务缩略图遮挡：内容页跟随「设置 → 隐私」的开关（默认阻止），
    // 但解锁页与设密页无论开关如何都始终阻止，避免密码被截屏/录屏。
    val allowScreenshot by container.settingsRepository.allowScreenshotVault
        .collectAsState(initial = false)
    val screenshotActivity = LocalContext.current as? FragmentActivity
    DisposableEffect(allowScreenshot, gateState) {
        if (!allowScreenshot || gateState != "list") {
            screenshotActivity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            screenshotActivity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            screenshotActivity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    Crossfade(targetState = gateState, label = "vault_gate") { state ->
        when (state) {
            "list" -> VaultListScreen(
                container,
                onExit = onExit,
                onReset = { unlocked = false },
                onNavigate = onNavigate
            )
            "setup" -> VaultSetupScreen(
                container,
                scope = scope,
                onUnlocked = { unlocked = true },
                onExit = onExit
            )
            else -> VaultUnlockScreen(
                container,
                onUnlocked = { unlocked = true },
                onExit = onExit
            )
        }
    }
}

@Composable
private fun VaultSetupScreen(
    container: AppContainer,
    scope: CoroutineScope,
    onUnlocked: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var enableBiometric by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    VaultScaffold(title = stringResource(R.string.vault_setup_title), onExit = onExit, showMenu = false) {
        Text(
            stringResource(R.string.vault_setup_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.vault_password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text(stringResource(R.string.vault_confirm_password)) },
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
                    password.length < 6 -> error = context.getString(R.string.vault_password_min)
                    password != confirm -> error = context.getString(R.string.vault_password_mismatch)
                    else -> {
                        scope.launch {
                            val salt = VaultCrypto.newSalt()
                            // PBKDF2（10 万次迭代）+ KeyStore 操作都不应占用主线程；
                            // deriveAll 一次派生同时得到验证 hash 与加密密钥（不再跑两遍）
                            val (hash, key) = withContext(Dispatchers.Default) {
                                VaultCrypto.deriveAll(password, salt)
                            }
                            container.settingsRepository.setVaultPassword(hash, salt)
                            VaultSession.unlock(key)
                            if (enableBiometric) {
                                // 托管会话密钥，否则指纹解锁后拿不到密钥，Vault 内容会显示成密文
                                val wrapped = withContext(Dispatchers.IO) {
                                    BiometricKeyStore.wrap(context, key.encoded)
                                }
                                // 托管失败（设备 Keystore 不可用）就关掉指纹，避免解锁后拿不到密钥
                                if (!wrapped) enableBiometric = false
                            } else {
                                withContext(Dispatchers.IO) { BiometricKeyStore.clear(context) }
                            }
                            container.settingsRepository.setVaultBiometric(enableBiometric)
                            onUnlocked()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.vault_complete_setup))
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
    // 指纹解锁还要求托管密钥**当前能解封**：Keystore 密钥硬件绑定，刷机/换机恢复后必丢，
    // 只查 hasWrappedKey 会被恢复回来的「死档」骗过（按钮在、扫完指纹才报过期，且永远无法自愈）
    val biometricReady = biometricAvailable && biometricEnabled &&
        remember(biometricEnabled) { BiometricKeyStore.canUnwrap(context) }

    fun authenticateWithBiometric() {
        val act = activity ?: return
        val executor = ContextCompat.getMainExecutor(act)
        val prompt = BiometricPrompt(
            act,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    // 指纹验证只证明「是本人」，密钥需从 Keystore 托管的包裹值里解封
                    val raw = BiometricKeyStore.unwrap(context)
                    if (raw != null) {
                        VaultSession.unlock(SecretKeySpec(raw, "AES"))
                        onUnlocked()
                    } else {
                        error = context.getString(R.string.vault_fingerprint_invalid)
                    }
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.vault_verify_identity))
            .setSubtitle(context.getString(R.string.vault_unlock_vault))
            .setNegativeButtonText(context.getString(R.string.common_cancel))
            .build()
        prompt.authenticate(info)
    }

    VaultScaffold(title = "Vault", onExit = onExit, showMenu = false) {
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                error = null
            },
            // 与周期管家解锁页保持一致：框内只放动作指引 placeholder（点击此处来输入密码），
            // 说明「解锁的是哪个功能」放下方 supportingText——框内长句会被读成「已填内容」。
            // 出错时同一位置换成错误文案并染红。
            placeholder = { Text(stringResource(R.string.common_password_placeholder)) },
            supportingText = { Text(error ?: stringResource(R.string.vault_enter_password_unlock)) },
            isError = error != null,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val h = hash ?: return@Button
                val s = salt ?: return@Button
                if (password.isBlank()) {
                    error = context.getString(R.string.vault_enter_password_prompt)
                    return@Button
                }
                val input = password
                scope.launch {
                    // PBKDF2（10 万次迭代）较重，放后台线程；一次派生同时得到 hash 与密钥
                    val (computed, key) = withContext(Dispatchers.Default) {
                        VaultCrypto.deriveAll(input, s)
                    }
                    if (computed != h) {
                        error = context.getString(R.string.vault_password_wrong)
                        return@launch
                    }
                    VaultSession.unlock(key)
                    // 重新托管会话密钥：既兼容旧版本未托管的情况，也修复刷机/换机恢复后
                    // Keystore 密钥丢失导致的「指纹过期」——旧条件 !hasWrappedKey 会被
                    // 恢复回来的死档挡住，密码解锁永远不会重建托管，指纹从此修不好。
                    // wrap 每次生成新 IV 覆盖旧记录，开销可忽略。
                    if (biometricEnabled) {
                        withContext(Dispatchers.IO) { BiometricKeyStore.wrap(context, key.encoded) }
                    }
                    onUnlocked()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Lock, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.vault_unlock))
        }
        if (biometricReady) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { authenticateWithBiometric() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.vault_use_fingerprint))
            }
        } else if (biometricAvailable && biometricEnabled) {
            // 开了指纹但托管密钥解不开（刷机/恢复后 Keystore 密钥丢失）：与其扫完才报
            // 「过期」，不如直接说明原因和恢复办法——用密码解锁一次即自动重新托管
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.vault_biometric_stale_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
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
                context.getString(R.string.vault_switch_manual_sort),
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
                context.getString(R.string.vault_switch_manual_sort),
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

    // 搜索状态：仅在已解锁的 Vault 页内可用；字段加密，故取全量解密后内存过滤
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val allVaultEventsFlow = remember { container.vaultRepository.observeAll() }
    val allVaultEvents by allVaultEventsFlow.collectAsState(initial = emptyList())
    val vaultFolderNameById = remember(folders) { folders.associate { it.id to it.name } }
    val vaultSearchResults = remember(allVaultEvents, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else allVaultEvents
            .filter { matchesQuery(it.title, it.note, searchQuery) }
            .sortedBy { eventDaysUntil(it.targetDateEpochDay) }
    }
    BackHandler(enabled = searchActive) {
        searchActive = false
        searchQuery = ""
    }

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

    // 截图限制已提升到 VaultScreen 顶层（同时覆盖解锁页与设密页），此处不再重复设置。

    VaultScaffold(
        title = "Vault",
        onExit = onExit,
        selectionMode = selectionMode,
        totalSelected = totalSelected,
        onExitSelection = { exitSelection() },
        hasEventsSelected = selectedEventIds.isNotEmpty(),
        onMove = { showMoveDialog = true },
        onDelete = { showDeleteConfirm = true },
        menuItems = {
            DropdownMenuItem(
                modifier = Modifier.heightIn(min = 64.dp),
                text = { Text(stringResource(R.string.vault_batch_manage)) },
                onClick = { enterSelection() }
            )
            DropdownMenuItem(
                modifier = Modifier.heightIn(min = 64.dp),
                text = { Text(stringResource(R.string.vault_reset_password)) },
                onClick = { showResetConfirm = true }
            )
        },
        extraActions = {
            IconButton(onClick = { searchActive = !searchActive; if (!searchActive) searchQuery = "" }) {
                Icon(
                    if (searchActive) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Search,
                    contentDescription = stringResource(R.string.common_search)
                )
            }
        },
        fab = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.common_new))
            }
        }
    ) {
        if (searchActive) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.vault_search_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
        when {
            searchActive && searchQuery.isBlank() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.vault_search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            searchActive && searchQuery.isNotBlank() -> {
                if (vaultSearchResults.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.vault_search_no_result),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(vaultSearchResults, key = { "e${it.id}" }) { event ->
                            VaultEventRow(
                                event = event,
                                onClick = {
                                    editingEvent = event
                                    showEventDialog = true
                                },
                                onMoveToMain = {
                                    scope.launch { container.vaultBridge.moveVaultEventToMain(event.id) }
                                },
                                searchQuery = searchQuery,
                                folderBadge = event.folderId?.let { vaultFolderNameById[it] },
                                noteHit = noteHitOnly(event.title, event.note, searchQuery)
                            )
                            ListItemDivider()
                        }
                    }
                }
            }
            events.isEmpty() && folders.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("🔒", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.vault_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            else -> {
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
                            // 非手动排序时重排项无意义（moveVaultFolder 内部也会直接返回），不展示菜单入口
                            onReorder = if (manualSort) ({ action -> moveVaultFolder(folder, action) }) else null
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
                            onReorder = if (manualSort) ({ action -> moveVaultEvent(event, action) }) else null,
                            dragHandle = handleModifier
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
            title = if (folderTarget == null) stringResource(R.string.vault_new_folder) else stringResource(R.string.vault_edit_folder),
            confirmLabel = if (folderTarget == null) stringResource(R.string.common_create) else stringResource(R.string.common_save),
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
            title = { Text(stringResource(R.string.vault_delete_n_items, totalSelected)) },
            text = { Text(stringResource(R.string.vault_delete_confirm_text)) },
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
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.vault_reset_password_confirm)) },
            text = {
                Text(
                    stringResource(R.string.vault_reset_warning)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        container.vaultRepository.clearAll()
                        container.vaultFolderRepository.clearAll()
                        container.settingsRepository.clearVaultPassword()
                        VaultSession.lock()
                        // 旧的会话密钥托管值已失效（新密码派生不同密钥），一并清除
                        withContext(Dispatchers.IO) { BiometricKeyStore.clear(context) }
                        onReset()
                    }
                    showResetConfirm = false
                }) { Text(stringResource(R.string.vault_clear_and_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
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
            TextButton(onClick = onExit) { Text(stringResource(R.string.common_done)) }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.vault_selected_n, totalSelected), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            if (hasEventsSelected) {
                TextButton(onClick = onMove) { Text(stringResource(R.string.vault_move_to_folder)) }
            }
            TextButton(onClick = onDelete, enabled = totalSelected > 0) { Text(stringResource(R.string.common_delete)) }
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

    // 防截屏/最近任务缩略图遮挡：跟随「设置 → 隐私 → 保险箱允许截图」（默认阻止）。
    val allowScreenshot by container.settingsRepository.allowScreenshotVault
        .collectAsState(initial = false)
    val folderActivity = folderContext as? FragmentActivity
    DisposableEffect(allowScreenshot) {
        if (!allowScreenshot) {
            folderActivity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            folderActivity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { folderActivity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

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
                folderContext.getString(R.string.vault_switch_manual_sort),
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

    // folderTarget=null 表示「新建文件夹」（从移入文件夹的创建入口进入），非 null 表示重命名当前文件夹
    var showFolderDialog by remember { mutableStateOf(false) }
    var folderTarget by remember { mutableStateOf<VaultFolderEntity?>(null) }
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
        title = folder?.name ?: stringResource(R.string.common_folder),
        onExit = onBack,
        selectionMode = selectionMode,
        totalSelected = totalSelected,
        onExitSelection = { exitSelection() },
        hasEventsSelected = selectedEventIds.isNotEmpty(),
        onMove = { showMoveDialog = true },
        onDelete = { showDeleteConfirm = true },
        menuItems = {
            DropdownMenuItem(
                modifier = Modifier.heightIn(min = 64.dp),
                text = { Text(stringResource(R.string.vault_batch_manage)) },
                onClick = { enterSelection() }
            )
            DropdownMenuItem(
                modifier = Modifier.heightIn(min = 64.dp),
                text = { Text(stringResource(R.string.common_rename)) },
                onClick = {
                    folderTarget = folder
                    showFolderDialog = true
                }
            )
            DropdownMenuItem(
                modifier = Modifier.heightIn(min = 64.dp),
                text = { Text(stringResource(R.string.vault_delete_folder)) },
                onClick = { showFolderDeleteConfirm = true }
            )
        },
        fab = {
            FloatingActionButton(onClick = {
                editingEvent = null
                showEventDialog = true
            }) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.vault_new_event)) }
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
                    stringResource(R.string.vault_folder_empty),
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
                            onReorder = if (manualSort) ({ action -> moveVaultFolderEvent(event, action) }) else null,
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
            initialName = folderTarget?.name ?: "",
            initialIcon = folderTarget?.icon ?: "📁",
            title = if (folderTarget == null) stringResource(R.string.vault_new_folder) else stringResource(R.string.vault_edit_folder),
            confirmLabel = if (folderTarget == null) stringResource(R.string.common_create) else stringResource(R.string.common_save),
            onDismiss = {
                showFolderDialog = false
                pendingMoveAfterCreate = false
            },
            onSave = { name, icon ->
                scope.launch {
                    if (folderTarget == null) {
                        // 从「移入文件夹 → 新建文件夹」进来：先建好目录，再把选中的事件移进去
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
                            folder = container.vaultFolderRepository.getById(folderId)
                        }
                    }
                }
                showFolderDialog = false
            },
            onDelete = if (folderTarget != null) {
                {
                    scope.launch {
                        folderTarget?.let { container.vaultFolderRepository.delete(it) }
                        onBack()
                    }
                }
            } else null
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
                folderTarget = null
                pendingMoveAfterCreate = true
                showFolderDialog = true
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.vault_delete_n_items, totalSelected)) },
            text = { Text(stringResource(R.string.vault_delete_no_undo)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (selectedEventIds.isNotEmpty())
                            container.vaultRepository.deleteByIds(selectedEventIds.toList())
                    }
                    showDeleteConfirm = false
                    exitSelection()
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    if (showFolderDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showFolderDeleteConfirm = false },
            title = { Text(stringResource(R.string.vault_delete_folder_confirm)) },
            text = {
                Text(stringResource(R.string.vault_delete_folder_text, folder?.name ?: ""))
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
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
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
    var noteText by remember { mutableStateOf(existing?.note ?: "") }
    var epochDay by remember {
        mutableStateOf(existing?.targetDateEpochDay ?: LocalDate.now().plusDays(7).toEpochDay())
    }
    var repeatRule by remember { mutableStateOf(existing?.repeatRule) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var refDaysText by remember { mutableStateOf(existing?.refDays?.toString() ?: "") }
    var displayUnit by remember {
        mutableStateOf(existing?.displayUnit ?: CountdownCalculator.UNIT_DAY)
    }
    var showDatePicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 对照值的单位跟随「倒计时显示单位」：按月显示时对照值即「月数」，其余同理。
    val refUnitLabelRes = when (displayUnit) {
        CountdownCalculator.UNIT_MONTH -> R.string.vault_unit_months
        CountdownCalculator.UNIT_YEAR -> R.string.vault_unit_years
        else -> R.string.vault_unit_days
    }
    val refUnitLabel = stringResource(refUnitLabelRes)
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
                    val noteValue = noteText.takeIf { it.isNotBlank() }
                    if (existing == null) {
                        container.vaultRepository.add(
                            VaultEventEntity(
                                title = title.ifBlank { Tr.s(R.string.common_unnamed) },
                                targetDateEpochDay = epochDay,
                                refDays = refValue,
                                displayUnit = displayUnit.takeIf { it != CountdownCalculator.UNIT_DAY },
                                note = noteValue,
                                repeatRule = repeatRule,
                                folderId = folderId
                            )
                        )
                    } else {
                        container.vaultRepository.update(
                            existing.copy(
                                title = title.ifBlank { Tr.s(R.string.common_unnamed) },
                                targetDateEpochDay = epochDay,
                                refDays = refValue,
                                displayUnit = displayUnit.takeIf { it != CountdownCalculator.UNIT_DAY },
                                note = noteValue,
                                repeatRule = repeatRule
                            )
                        )
                    }
                }
                onDismiss()
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        title = { Text(if (existing == null) stringResource(R.string.vault_new_vault_event) else stringResource(R.string.vault_edit_vault_event)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.common_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.vault_note_label)) },
                    placeholder = { Text(stringResource(R.string.vault_note_placeholder)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(
                            stringResource(
                                R.string.vault_target_date,
                                LocalDate.ofEpochDay(epochDay)
                                    .format(DateTimeFormatter.ofPattern(Tr.s(R.string.date_pattern_ymd)))
                            ),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    TextButton(onClick = { showResetConfirm = true }) {
                        Text(stringResource(R.string.vault_reset_to_today))
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text(stringResource(R.string.vault_countdown_unit_label), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = displayUnit == CountdownCalculator.UNIT_DAY,
                        onClick = { displayUnit = CountdownCalculator.UNIT_DAY },
                        label = { Text(stringResource(R.string.vault_unit_days)) }
                    )
                    FilterChip(
                        selected = displayUnit == CountdownCalculator.UNIT_MONTH,
                        onClick = { displayUnit = CountdownCalculator.UNIT_MONTH },
                        label = { Text(stringResource(R.string.vault_unit_months)) }
                    )
                    FilterChip(
                        selected = displayUnit == CountdownCalculator.UNIT_YEAR,
                        onClick = { displayUnit = CountdownCalculator.UNIT_YEAR },
                        label = { Text(stringResource(R.string.vault_unit_years)) }
                    )
                }
                Text(
                    stringResource(R.string.vault_unit_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(12.dp))

                Text(stringResource(R.string.vault_repeat_label), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = repeatRule == null,
                        onClick = { repeatRule = null },
                        label = { Text(stringResource(R.string.vault_repeat_none)) }
                    )
                    FilterChip(
                        selected = repeatRule == CountdownCalculator.REPEAT_WEEKLY,
                        onClick = { repeatRule = CountdownCalculator.REPEAT_WEEKLY },
                        label = { Text(stringResource(R.string.vault_repeat_weekly)) }
                    )
                    FilterChip(
                        selected = repeatRule == CountdownCalculator.REPEAT_MONTHLY,
                        onClick = { repeatRule = CountdownCalculator.REPEAT_MONTHLY },
                        label = { Text(stringResource(R.string.vault_repeat_monthly)) }
                    )
                    FilterChip(
                        selected = repeatRule == CountdownCalculator.REPEAT_YEARLY,
                        onClick = { repeatRule = CountdownCalculator.REPEAT_YEARLY },
                        label = { Text(stringResource(R.string.vault_repeat_yearly)) }
                    )
                }
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = refDaysText,
                    onValueChange = { refDaysText = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text(stringResource(R.string.vault_ref_label, refUnitLabel)) },
                    placeholder = { Text(stringResource(R.string.vault_ref_placeholder)) },
                    supportingText = { Text(stringResource(R.string.vault_ref_support, refUnitLabel)) },
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
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.common_cancel)) } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.vault_reset_target_date)) },
            text = {
                Text(
                    stringResource(
                        R.string.vault_reset_date_confirm,
                        LocalDate.ofEpochDay(epochDay)
                            .format(DateTimeFormatter.ofPattern(Tr.s(R.string.date_pattern_ymd)))
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val today = LocalDate.now().toEpochDay()
                    epochDay = today
                    // 编辑模式下与主页表单一致：立即写库，仅改日期
                    existing?.let { e ->
                        scope.launch {
                            container.vaultRepository.update(
                                e.copy(targetDateEpochDay = today)
                            )
                        }
                    }
                    showResetConfirm = false
                }) { Text(stringResource(R.string.common_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
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
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (searchQuery.isBlank()) AnnotatedString(event.title)
                else highlightedText(event.title, searchQuery),
                style = MaterialTheme.typography.bodyLarge
            )
            if (searchQuery.isNotBlank() && !event.note.isNullOrBlank()) {
                Text(
                    text = highlightedText(event.note, searchQuery),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        if (noteHit) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.vault_note_hit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        if (!selectionMode && onMoveToMain != null) {
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
                        modifier = Modifier.heightIn(min = 64.dp),
                        text = { Text(stringResource(R.string.vault_move_out_main)) },
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
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_more))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    ReorderMenuItems(onReorder) { menuExpanded = false }
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
    extraActions: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.vault_selected_n, totalSelected)) },
                    navigationIcon = {
                        IconButton(onClick = { onExitSelection?.invoke() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_done))
                        }
                    },
                    actions = {
                        if (hasEventsSelected && onMove != null) {
                            TextButton(onClick = onMove) { Text(stringResource(R.string.vault_move_to_folder)) }
                        }
                        if (onDelete != null) {
                                TextButton(
                                    onClick = onDelete,
                                    enabled = totalSelected > 0
                                ) { Text(stringResource(R.string.common_delete)) }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onExit) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.vault_exit_vault))
                        }
                    },
                    actions = {
                        extraActions()
                        if (showMenu) {
                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.vault_menu))
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) { menuItems() }
                            }
                        }
                        TextButton(onClick = onExit) { Text(stringResource(R.string.vault_exit)) }
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
