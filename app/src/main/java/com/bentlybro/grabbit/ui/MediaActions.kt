package com.bentlybro.grabbit.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import com.bentlybro.grabbit.data.QueueItem

/** Open / share a finished download through other apps. */
object MediaActions {

    fun open(context: Context, item: QueueItem) {
        val uri = item.outputUri?.toUri() ?: return
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mimeType ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(context: Context, item: QueueItem) {
        val uri = item.outputUri?.toUri() ?: return
        val send = Intent(Intent.ACTION_SEND)
            .setType(item.mimeType ?: "*/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, item.displayTitle))
    }
}
