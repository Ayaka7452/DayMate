package com.ayaka7452.daymate.core.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.FileProvider
import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.i18n.Tr
import com.ayaka7452.daymate.core.log.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 下载新版本 APK，并在通知栏显示进度；下载完成后直接拉起系统安装界面。
 *
 * 用前台服务而不是普通后台协程：安装包 14MB，国内网络下要几十秒到几分钟，
 * 期间用户很可能切走；普通后台任务会被系统回收，前台服务才能把进度稳定留在通知栏里。
 * （Android 14 起前台服务必须在 manifest 里声明 `foregroundServiceType`。）
 *
 * 落盘位置是应用私有外部目录 `Android/data/<pkg>/files/update/`——不需要任何存储权限，
 * 通过 FileProvider 把 URI 授给系统安装器。
 */
class UpdateDownloadService : Service() {

    companion object {
        private const val CHANNEL_ID = "update_download"
        private const val NOTIF_ID = 9001
        private const val EXTRA_URL = "url"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_VERSION = "version"

        /** 启动下载。重复调用（用户连点）由 START_NOT_STICKY + 单实例服务天然合并。 */
        fun start(context: Context, info: UpdateInfo) {
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                putExtra(EXTRA_URL, info.apkUrl)
                putExtra(EXTRA_NAME, info.apkName)
                putExtra(EXTRA_VERSION, info.version)
            }
            runCatching { context.startForegroundService(intent) }
        }

        /** 下载完成的 APK 存放目录（外部私有目录不可用时退回内部 files/update）。 */
        fun downloadDir(context: Context): File {
            val ext = context.getExternalFilesDir("update")
            return ext ?: File(context.filesDir, "update")
        }

        /** 是否已允许「安装未知应用」（Android 8+）。未允许时系统会静默拒绝安装。 */
        fun canInstall(context: Context): Boolean = runCatching {
            context.packageManager.canRequestPackageInstalls()
        }.getOrDefault(true)

        /** 跳系统「安装未知应用」授权页。 */
        fun openInstallPermissionSettings(context: Context) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(android.net.Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        /** 构造 APK 安装意图（FileProvider 授权 + 新任务栈）。 */
        fun installIntent(context: Context, apk: File): Intent {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", apk
            )
            return Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        /** 创建更新通知渠道（幂等，重复创建无副作用）。 */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    Tr.s(R.string.update_channel_name),
                    NotificationManager.IMPORTANCE_LOW      // 进度更新不该响铃
                ).apply {
                    description = Tr.s(R.string.update_channel_name)
                    setShowBadge(false)
                }
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)      // 下载大文件，读超时放宽
            .callTimeout(0, TimeUnit.SECONDS)       // 不限总时长（国内网络慢）
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        val name = intent?.getStringExtra(EXTRA_NAME) ?: "DayMate-update.apk"
        val version = intent?.getStringExtra(EXTRA_VERSION).orEmpty()
        if (url.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        ensureChannel(this)
        AppLogger.log(this, "Update", "开始下载新版本 v$version")
        // 必须在 5 秒内进入前台，否则系统抛 ANR/崩溃；先挂一条 0% 的通知上去
        startForegroundCompat(buildProgressNotification(0, 0L, 0L, version))

        scope.launch { download(url, name, version) }
        // 不重启：进程被杀后留着半截文件没有意义，下次点更新会重新下载
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ===== 下载 =====

    private fun download(url: String, name: String, version: String) {
        val dir = downloadDir(this).apply { mkdirs() }
        val part = File(dir, "$name.part")
        val target = File(dir, name)
        try {
            val req = Request.Builder().url(url)
                .header("User-Agent", "DayMate-Android")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    fail(version)
                    return
                }
                val body = resp.body ?: run { fail(version); return }
                val total = body.contentLength().takeIf { it > 0 } ?: 0L
                var written = 0L
                var lastPercent = -1
                var lastNotifyAt = 0L
                body.byteStream().use { input ->
                    FileOutputStream(part).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                            written += read
                            // 通知刷新节流：进度至少涨 1% 且距上次 ≥ 300ms，避免刷爆通知栏
                            val percent = if (total > 0) ((written * 100) / total).toInt() else 0
                            val now = System.currentTimeMillis()
                            if (percent != lastPercent && (now - lastNotifyAt >= 300 || percent == 100)) {
                                lastPercent = percent
                                lastNotifyAt = now
                                notify(buildProgressNotification(percent, written, total, version))
                            }
                        }
                        out.flush()
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                // rename 失败（个别存储实现）：退回复制
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            AppLogger.log(this, "Update", "下载完成 v$version（${target.length()} 字节）")
            done(target, version)
        } catch (t: Throwable) {
            Log.w("DayMateUpdate", "download failed", t)
            AppLogger.logError(this, "Update", "下载失败 v$version", t)
            runCatching { part.delete() }
            fail(version)
        }
    }

    private fun done(apk: File, version: String) {
        val allowed = canInstall(this)
        AppLogger.log(this, "Update", if (allowed) "已授权安装，拉起安装界面 v$version" else "未授权「安装未知应用」，等待用户授权 v$version")
        val tap = if (allowed) {
            PendingIntent.getActivity(
                this, 0, installIntent(this, apk),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            // 没允许「安装未知应用」：点击先去授权，授权后回来再点一次通知即可安装
            PendingIntent.getActivity(
                this, 1,
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(android.net.Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val text = if (allowed) {
            Tr.s(R.string.update_download_done)
        } else {
            Tr.s(R.string.update_install_permission)
        }
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(Tr.s(R.string.update_notif_title, version))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notify(notif)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()

        // 已授权就直接拉起安装界面，省掉用户点通知这一步（用户点「更新」时的预期就是这个）
        if (allowed) {
            runCatching { startActivity(installIntent(this, apk)) }
        }
    }

    private fun fail(version: String) {
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(Tr.s(R.string.update_notif_title, version))
            .setContentText(Tr.s(R.string.update_download_failed))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        notify(notif)
        stopForegroundCompat()
        stopSelf()
    }

    // ===== 通知 =====

    private fun buildProgressNotification(
        percent: Int,
        written: Long,
        total: Long,
        version: String
    ): Notification {
        val open = PendingIntent.getActivity(
            this, 2,
            Intent().setClassName(this, "$packageName.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (total > 0) {
            Tr.s(R.string.update_download_percent, percent)
        } else {
            Tr.s(R.string.update_downloading)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(Tr.s(R.string.update_notif_title, version))
            .setContentText(text)
            .setProgress(100, percent.coerceIn(0, 100), total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun notify(notification: Notification) {
        // 未授予通知权限时静默失败即可（前台服务本身不受影响）
        runCatching { NotificationManagerCompat.from(this).notify(NOTIF_ID, notification) }
    }

    private fun startForegroundCompat(notification: Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this, NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        }
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        runCatching { NotificationManagerCompat.from(this).cancel(NOTIF_ID) }
    }
}
