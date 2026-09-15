package com.ayaka7452.daymate.feature.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ayaka7452.daymate.DayMateApp
import com.ayaka7452.daymate.core.StorageConfig
import com.ayaka7452.daymate.core.cloud.CloudBackup
import com.ayaka7452.daymate.core.cloud.WebDavConfig
import com.ayaka7452.daymate.core.cloud.WebDavEntry
import com.ayaka7452.daymate.core.cloud.WebDavException
import com.ayaka7452.daymate.core.cloud.WebDavStore
import com.ayaka7452.daymate.data.repo.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * WebDAV 云端备份配置页。
 *
 * 流程：填写服务器地址 / 用户名 / 密码 → 「测试连接」验证凭据与协议 →
 * 「浏览远程目录」在服务器上逐级挑选一个目录作为备份落点（**可选中根目录**）→ 保存。
 *
 * 保存成功且备份位置仍是「仅本地」时，自动切到「本地 + 云端」——否则用户配好了 WebDAV
 * 却因为备份位置没切而始终不生效（主页顶栏的云图标也不会出现）。
 * 未选定远程目录时配置不算完整，此时保存会明确提示「云端备份不会启用」。
 *
 * 密码不会明文落盘：由 [WebDavStore] 经 AndroidKeyStore 包裹后存储。
 * 支持 http（局域网 NAS）与 https，自签名证书需显式开启对应开关。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavSetupScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as DayMateApp
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(WebDavStore.url(ctx)) }
    var username by remember { mutableStateOf(WebDavStore.username(ctx)) }
    var password by remember { mutableStateOf(WebDavStore.password(ctx)) }
    var directory by remember { mutableStateOf(WebDavStore.directory(ctx)) }
    var selfSigned by remember { mutableStateOf(WebDavStore.allowSelfSigned(ctx)) }
    // 是否已在「远程目录浏览」里选定过目录（选中根目录也算）——决定配置是否算完整
    var directoryPicked by remember { mutableStateOf(WebDavStore.isDirectoryPicked(ctx)) }

    var status by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    // 目录浏览状态
    var browsing by remember { mutableStateOf(false) }
    var browsePath by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<WebDavEntry>>(emptyList()) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    fun configFor(path: String) = WebDavConfig(
        url = url.trim(),
        username = username.trim(),
        password = password,
        directory = path,
        allowSelfSigned = selfSigned
    )

    fun report(message: String, error: Boolean = false) {
        status = message
        isError = error
    }

    /** 在 IO 线程执行 [block]，把 WebDavException 转成可读提示。 */
    fun runRemote(label: String, block: () -> Unit, onDone: () -> Unit = {}) {
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                onDone()
            } catch (e: WebDavException) {
                report("$label 失败：${e.message}", error = true)
            } catch (e: Throwable) {
                report("$label 失败：${e.message ?: e.javaClass.simpleName}", error = true)
            } finally {
                busy = false
            }
        }
    }

    fun loadEntries(path: String) {
        runRemote("读取目录", { entries = CloudBackup.list(configFor(path), path) }) {
            browsePath = path
            report("已读取远程目录：/${path}")
        }
    }

    /**
     * 保存成功后的收尾：若备份位置仍是「仅本地」，自动切到「本地 + 云端」。
     * 否则用户配好了 WebDAV 却因为备份位置没切，云备份其实一直没生效（主页云图标也不出现）。
     */
    fun afterSaved(prefix: String) {
        scope.launch {
            val repo = app.container.settingsRepository
            val current = repo.backupTarget.first()
            if (current == SettingsRepository.BACKUP_TARGET_LOCAL) {
                repo.setBackupTarget(SettingsRepository.BACKUP_TARGET_BOTH)
                val localReady = StorageConfig.isBackupConfigured(ctx)
                report(
                    prefix + "，并已启用云端自动备份（本地 + 云端）。" +
                        if (localReady) "数据改动后会自动上传。"
                        else "本地备份文件夹尚未选择，目前只上传云端；需要本地副本可返回上一页选择。"
                )
            } else {
                report(prefix + "，云端自动备份已处于启用状态。")
            }
        }
    }

    fun save() {
        if (url.isBlank()) {
            report("请先填写服务器地址", error = true)
            return
        }
        val saved = WebDavStore.save(ctx, configFor(directory), directoryPicked)
        if (!saved) {
            report("保存失败：系统密钥库不可用，密码未能安全存储", error = true)
            return
        }
        // 没选定远程目录时配置不算完整，云端备份不会启用——必须说清楚，
        // 否则用户看到「已保存」会以为大功告成，实际主页永远不出现云图标。
        if (!directoryPicked) {
            report(
                "已保存地址与凭据，但还没选定远程目录，云端备份不会启用。" +
                    "请点下方「浏览并选择远程目录」选定存放位置（可选中根目录）。",
                error = true
            )
            return
        }
        afterSaved("已保存 WebDAV 配置")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (browsing) "选择远程目录" else "WebDAV 云端备份") },
                navigationIcon = {
                    IconButton(onClick = { if (browsing) browsing = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (browsing) {
                BrowseSection(
                    path = browsePath,
                    entries = entries,
                    busy = busy,
                    onEnter = { loadEntries(it) },
                    onSelectCurrent = {
                        directory = browsePath
                        directoryPicked = true
                        val saved =
                            WebDavStore.save(ctx, configFor(browsePath), directoryPicked = true)
                        browsing = false
                        if (saved) {
                            afterSaved("已选择远程目录：/${browsePath}")
                        } else {
                            report("目录已选择，但密码未能安全存储（系统密钥库不可用）", error = true)
                        }
                    },
                    onNewFolder = {
                        newFolderName = ""
                        showNewFolderDialog = true
                    }
                )
            } else {
                Text(
                    "把备份同时存一份到你的 WebDAV 服务器（坚果云、Nextcloud、群晖、Alist 等）。" +
                        "服务器地址填 WebDAV 根地址，程序会自动在其下逐级创建并写入 daymate.db。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("服务器地址") },
                    placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码 / 应用密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("允许自签名证书", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "仅在你自建的 NAS 使用自签名 https 证书时开启",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(checked = selfSigned, onCheckedChange = { selfSigned = it })
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            runRemote("测试连接", { CloudBackup.testConnection(configFor(directory)) }) {
                                report("连接成功，凭据可用。记得点「保存」让配置生效。")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !busy
                    ) { Text("测试连接") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { save() },
                        modifier = Modifier.weight(1f),
                        enabled = !busy
                    ) { Text("保存") }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        // 先验证再浏览：目录浏览本身也要用同一套凭据
                        runRemote("读取目录", { entries = CloudBackup.list(configFor(""), "") }) {
                            browsePath = ""
                            browsing = true
                            report("已读取远程目录：/")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("浏览并选择远程目录")
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    if (directoryPicked)
                        "当前远程目录：${if (directory.isBlank()) "（根目录）" else "/$directory"}"
                    else
                        "尚未选定远程目录，云端备份不会启用。请点上方「浏览并选择远程目录」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (directoryPicked) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "备份文件：${if (directory.isBlank()) "/" else "/$directory/"}${WebDavStore.REMOTE_DB}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (status != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    status!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
            if (busy) {
                Spacer(Modifier.height(8.dp))
                Text("处理中…", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (showNewFolderDialog) {
            AlertDialog(
                onDismissRequest = { showNewFolderDialog = false },
                title = { Text("新建远程文件夹") },
                text = {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("文件夹名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val name = newFolderName.trim().trim('/')
                        showNewFolderDialog = false
                        if (name.isEmpty()) return@TextButton
                        val target = if (browsePath.isBlank()) name else "$browsePath/$name"
                        runRemote("新建文件夹", { CloudBackup.ensureDirectory(configFor(browsePath), target) }) {
                            loadEntries(browsePath)
                        }
                    }) { Text("创建") }
                },
                dismissButton = {
                    TextButton(onClick = { showNewFolderDialog = false }) { Text("取消") }
                }
            )
        }
    }
}

/** 远程目录浏览区：列出当前目录下的子项，可进入子目录、选中当前目录或新建文件夹。 */
@Composable
private fun BrowseSection(
    path: String,
    entries: List<WebDavEntry>,
    busy: Boolean,
    onEnter: (String) -> Unit,
    onSelectCurrent: () -> Unit,
    onNewFolder: () -> Unit
) {
    Text(
        "当前路径：/${path}",
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "选中后，备份会写到该目录下的 ${WebDavStore.REMOTE_DB}。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline
    )
    Spacer(Modifier.height(12.dp))

    Button(
        onClick = { onSelectCurrent() },
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy
    ) { Text("选择当前目录") }

    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = { onNewFolder() },
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy
    ) {
        Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("在此新建文件夹")
    }

    if (path.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { onEnter(path.substringBeforeLast('/', "")) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy
        ) { Text("返回上一级") }
    }

    Spacer(Modifier.height(8.dp))
    HorizontalDivider()

    val dirs = entries.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
    val files = entries.filterNot { it.isDirectory }.sortedBy { it.name.lowercase() }

    if (dirs.isEmpty() && files.isEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text(
            "该目录为空。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        return
    }

    for (d in dirs) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !busy) { onEnter(d.path) }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(d.name, style = MaterialTheme.typography.bodyLarge)
        }
    }
    for (f in files) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    f.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                if (f.size > 0) {
                    Text(
                        "${f.size / 1024} KB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
