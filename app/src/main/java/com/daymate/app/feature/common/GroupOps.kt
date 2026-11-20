package com.ayaka7452.daymate.feature.common

import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.data.db.FolderKinds
import kotlinx.coroutines.flow.first

/**
 * 把事件组成员移出事件组（v1.27.1）。
 *
 * 移出后逐组检查存活成员数，剩余不足 2 个时组自动解散（无提示，用户拍板）：
 * 剩余成员回归组所在层级（组的 parentId，null = 主页根层），组本体软删。
 * 批量移出涉及多个组时逐组处理。
 *
 * @param targetFolderId 成员去处：null = 根层、普通文件夹 id；不可为事件组 id
 *   （成员换组不支持，组的增减只走合并与解散）。
 */
suspend fun moveGroupMembersOut(
    container: AppContainer,
    eventIds: List<Long>,
    targetFolderId: Long?
) {
    // 移动前先记录涉及的组（成员当前 folderId = 组 id）。
    // 只认 kind=mini 的目标：直属事件从普通文件夹移出绝不触发文件夹解散检查
    val before = container.eventRepository.observeAll().first()
    val affectedGroups = before
        .filter { it.id in eventIds }
        .mapNotNull { it.folderId }
        .filter { gid -> container.folderRepository.getById(gid)?.kind == FolderKinds.MINI }
        .toSet()
    container.eventRepository.moveToFolder(eventIds, targetFolderId)
    // 解散检查：observeAll 已滤软删，剩余 <2 即解散
    val after = container.eventRepository.observeAll().first()
    for (gid in affectedGroups) {
        val remain = after.filter { it.folderId == gid }
        if (remain.size < 2) {
            val parent = container.folderRepository.getById(gid)?.parentId
            if (remain.isNotEmpty()) {
                container.eventRepository.moveToFolder(remain.map { it.id }, parent)
            }
            container.folderRepository.softDeleteByIds(listOf(gid), System.currentTimeMillis())
        }
    }
}
