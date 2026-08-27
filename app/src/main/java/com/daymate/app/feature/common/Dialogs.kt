package com.daymate.app.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

private val FOLDER_ICONS = listOf(
    "📁", "📂", "⭐", "❤️", "🎯", "🎁",
    "📚", "💼", "🏠", "✈️", "🎓", "🍎"
)

/**
 * 通用文件夹创建/编辑对话框。
 * - 新建：initialName/initialIcon 留默认，confirmLabel="创建"，onDelete=null
 * - 编辑：传入现有 name/icon，confirmLabel="保存"，onDelete 提供删除
 */
@Composable
fun FolderDialog(
    initialName: String = "",
    initialIcon: String = "📁",
    title: String,
    confirmLabel: String = "保存",
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), icon) }
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("删除") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("图标", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FOLDER_ICONS) { em ->
                        val isSel = em == icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSel) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    if (isSel) 2.dp else 0.dp,
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                                .clickable { icon = em },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(em, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    )
}

/**
 * 选择目标文件夹的对话框。
 * @param folders 可选项 (id, 展示文本)
 * @param showRoot 是否提供“根目录（移出文件夹）”选项
 * @param onPick 选择结果；folderId=null 代表根目录
 * @param onCreateNew 提供则显示“新建文件夹并移入”
 */
@Composable
fun PickFolderDialog(
    folders: List<Pair<Long, String>>,
    showRoot: Boolean = true,
    onDismiss: () -> Unit,
    onPick: (folderId: Long?) -> Unit,
    onCreateNew: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text("移动到") },
        text = {
            LazyColumn {
                if (showRoot) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(null) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) { Text("📂  根目录（移出文件夹）") }
                    }
                }
                items(folders) { (id, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(id) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) { Text(label) }
                }
                if (onCreateNew != null) {
                    item {
                        TextButton(onClick = onCreateNew) { Text("+ 新建文件夹并移入") }
                    }
                }
            }
        }
    )
}
