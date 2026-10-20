package com.bentlybro.grabbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bentlybro.grabbit.core.Phase
import com.bentlybro.grabbit.core.ProgressParser
import com.bentlybro.grabbit.data.QueueItem
import com.bentlybro.grabbit.data.Status
import com.bentlybro.grabbit.ui.icons.GrabbitIcons
import com.bentlybro.grabbit.ui.theme.GrabbitColors

/** Human status line for a queue item; pure so it is covered by unit tests. */
fun statusText(item: QueueItem): String = when (item.status) {
    Status.QUEUED -> "Waiting…"
    Status.RUNNING -> when (item.phase) {
        Phase.STARTING -> "Starting…"
        Phase.DOWNLOADING -> buildString {
            append("%.0f%%".format(item.progress))
            ProgressParser.formatDuration(item.etaSec)?.let { append(" · $it left") }
        }
        Phase.PROCESSING -> if (item.format.isAudio) "Converting to MP3…" else "Merging…"
        Phase.SAVING -> "Saving…"
    }
    Status.DONE -> "Saved · ${item.outputName ?: item.format.label}"
    Status.FAILED -> item.error ?: "Failed"
    Status.CANCELLED -> "Cancelled"
}

@Composable
fun QueueRow(
    item: QueueItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = item.status == Status.DONE, onClick = onOpen),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(width = 88.dp, height = 50.dp).clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.thumbnail != null) {
                        AsyncImage(item.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(
                            if (item.format.isAudio) GrabbitIcons.Music else GrabbitIcons.Link, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.status == Status.DONE) {
                            Icon(Icons.Filled.CheckCircle, null, tint = GrabbitColors.Mint, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            "${if (item.format.isAudio) "MP3" else item.format.label} · ${statusText(item)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = when (item.status) {
                                Status.FAILED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                when (item.status) {
                    Status.QUEUED, Status.RUNNING -> IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, "Cancel")
                    }
                    Status.DONE -> IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "Share") }
                    Status.FAILED, Status.CANCELLED -> {
                        IconButton(onClick = onRetry) { Icon(GrabbitIcons.Retry, "Retry") }
                        IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, "Remove") }
                    }
                }
            }
            if (item.status == Status.RUNNING || item.status == Status.QUEUED) {
                Spacer(Modifier.height(8.dp))
                val determinate = item.status == Status.RUNNING && item.phase == Phase.DOWNLOADING && item.progress > 0f
                if (determinate) {
                    LinearProgressIndicator(
                        progress = { item.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small),
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small),
                        strokeCap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}
