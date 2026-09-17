package com.ayaka7452.daymate.feature.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.core.update.UpdateChecker
import com.ayaka7452.daymate.core.update.UpdateDownloadService
import com.ayaka7452.daymate.core.update.UpdateInfo
import com.ayaka7452.daymate.core.update.UpdatePrompt
import kotlinx.coroutines.launch

/**
 * 拿到一个「点了更新」的执行体：申请通知权限 →（回调里）起下载服务 → 需要时引导开「安装未知应用」。
 *
 * 权限申请必须由 Composable 发起（ActivityResultLauncher 要在组合期注册），
 * 所以抽成这个 hook，主页与设置页共用同一套行为，不会出现「设置页点更新少要一次权限」的岔子。
 */
@Composable
fun rememberUpdateStarter(): (UpdateInfo) -> Unit {
    val context = LocalContext.current

    // 等通知权限回调后再起下载：Android 13+ 没有通知权限就看不到进度，所以先问一次；
    // 用户拒绝也照常下载（只是没有通知栏进度可看）。
    var pending by remember { mutableStateOf<UpdateInfo?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        pending?.let { UpdateDownloadService.start(context, it) }
        pending = null
    }

    return remember(context) {
        { info ->
            val needsNotification =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
            if (needsNotification) {
                pending = info
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                UpdateDownloadService.start(context, info)
            }
            // 未允许「安装未知应用」时顺手打开授权页：下载要跑一会儿，
            // 用户授权完回来正好收到安装提示，省掉「装到一半被系统拦下」那一步。
            if (!UpdateDownloadService.canInstall(context)) {
                UpdateDownloadService.openInstallPermissionSettings(context)
            }
        }
    }
}

/**
 * 启动时的检查更新挂载点（主页挂一行即可）。
 *
 * [LaunchedEffect] 每次冷启动跑一次，是否真的发请求由 [UpdatePrompt.checkOnLaunch] 的节流规则决定：
 * 设置里关掉了不发请求、24 小时内只查一次、点过「稍后」的版本不再弹。
 */
@Composable
fun UpdateHost(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val startUpdate = rememberUpdateStarter()

    var available by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        available = UpdatePrompt.checkOnLaunch(container.settingsRepository, context)
    }

    available?.let { info ->
        UpdateAvailableDialog(
            info = info,
            currentVersion = UpdateChecker.currentVersion(context),
            onUpdate = {
                available = null
                startUpdate(info)
            },
            onLater = {
                // 「稍后」只对**这一个版本**静音——否则点过一次就等于永久关掉提醒
                val skipped = info.version
                available = null
                scope.launch { container.settingsRepository.setUpdateSkippedVersion(skipped) }
            }
        )
    }
}
