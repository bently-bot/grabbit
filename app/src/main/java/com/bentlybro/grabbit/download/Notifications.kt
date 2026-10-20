package com.bentlybro.grabbit.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bentlybro.grabbit.R
import com.bentlybro.grabbit.core.Phase
import com.bentlybro.grabbit.core.ProgressParser
import com.bentlybro.grabbit.ui.main.MainActivity

object Notifications {
    const val CHANNEL_PROGRESS = "downloads"
    const val CHANNEL_RESULTS = "results"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "Download progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows downloads while they run"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULTS, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when a download is saved or fails"
            }
        )
    }

    /** Stable per-item ids; the result uses a different id from the foreground progress one. */
    fun progressId(itemId: String) = (itemId.hashCode() and 0x3fffffff) or 0x1
    fun resultId(itemId: String) = (itemId.hashCode() and 0x3fffffff) and 0x3ffffffe

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun progress(
        context: Context,
        title: String,
        phase: Phase,
        percent: Float,
        etaSec: Long?,
        cancel: PendingIntent?,
    ): Notification {
        val text = when (phase) {
            Phase.STARTING -> "Starting…"
            Phase.DOWNLOADING -> buildString {
                append("%.0f%%".format(percent))
                ProgressParser.formatDuration(etaSec)?.let { append(" · $it left") }
            }
            Phase.PROCESSING -> "Processing…"
            Phase.SAVING -> "Saving…"
        }
        val indeterminate = phase != Phase.DOWNLOADING
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_grabbit)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, percent.toInt(), indeterminate)
            .setContentIntent(openAppIntent(context))
            .apply { if (cancel != null) addAction(0, "Cancel", cancel) }
            .build()
    }

    fun done(context: Context, itemId: String, title: String, uri: Uri, mime: String) {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            context, resultId(itemId), view,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        notify(
            context, resultId(itemId),
            NotificationCompat.Builder(context, CHANNEL_RESULTS)
                .setSmallIcon(R.drawable.ic_stat_grabbit)
                .setContentTitle("Saved")
                .setContentText(title)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
        )
    }

    fun failed(context: Context, itemId: String, title: String, error: String) {
        notify(
            context, resultId(itemId),
            NotificationCompat.Builder(context, CHANNEL_RESULTS)
                .setSmallIcon(R.drawable.ic_stat_grabbit)
                .setContentTitle("Download failed")
                .setContentText(title)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$error"))
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context))
                .build()
        )
    }

    fun canPost(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < 33

    private fun notify(context: Context, id: Int, n: Notification) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}
