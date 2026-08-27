package com.daymate.app.feature.vault

import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.daymate.app.core.AppContainer
import com.daymate.app.core.security.VaultCrypto
import com.daymate.app.core.util.CountdownCalculator
import com.daymate.app.data.db.VaultEventEntity
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun VaultScreen(
    container: AppContainer,
    onExit: () -> Unit
) {
    val passwordSet by container.settingsRepository.vaultPasswordSet
        .collectAsState(initial = false)
    var unlocked by remember { mutableStateOf(false) }

    when {
        !passwordSet -> VaultSetupScreen(container, onExit)
        !unlocked -> VaultUnlockScreen(container, onUnlocked = { unlocked = true }, onExit = onExit)
        else -> VaultListScreen(container, onExit = onExit)
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
private fun VaultListScreen(container: AppContainer, onExit: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val events by container.vaultRepository.observeAll()
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    VaultScaffold(
        title = "🔒 Vault",
        onExit = onExit,
        fab = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "新建")
            }
        }
    ) {
        if (events.isEmpty()) {
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
                items(events, key = { it.id }) { event ->
                    val days = CountdownCalculator.daysUntil(event.targetDateEpochDay)
                    val text = if (days >= 0) "还有 $days 天" else "已过 ${-days} 天"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            event.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (days >= 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新建 Vault 事件") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("标题") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        container.vaultRepository.add(
                            VaultEventEntity(
                                title = newTitle.ifBlank { "未命名" },
                                targetDateEpochDay = LocalDate.now().plusDays(7).toEpochDay()
                            )
                        )
                    }
                    newTitle = ""
                    showAddDialog = false
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            }
        )
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
