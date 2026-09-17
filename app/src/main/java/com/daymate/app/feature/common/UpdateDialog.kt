package com.ayaka7452.daymate.feature.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.update.UpdateInfo
import com.ayaka7452.daymate.core.update.UpdatePrompt
import java.util.Locale

/**
 * 「发现新版本」弹窗。
 *
 * 只做展示，不碰任何副作用：下载与权限申请都由调用方决定，
 * 这样设置页里将来要复用同一个弹窗也不会带出一串隐式行为。
 */
@Composable
fun UpdateAvailableDialog(
    info: UpdateInfo,
    currentVersion: String,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.update_dialog_title, info.version)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.update_dialog_message, currentVersion, info.version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                val notes = remember(info.notes) { UpdatePrompt.cleanNotes(info.notes) }
                if (notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.update_dialog_notes),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
                val size = formatApkSize(info.apkSize)
                if (size.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.update_dialog_size, size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdate) { Text(stringResource(R.string.update_now)) }
        },
        dismissButton = {
            TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) }
        }
    )
}

/** 安装包体积（只用于弹窗里的一句提示，未知时返回空串、整行不显示）。 */
private fun formatApkSize(bytes: Long): String =
    if (bytes <= 0) "" else String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
