package com.bentlybro.grabbit.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bentlybro.grabbit.core.DownloadFormat
import com.bentlybro.grabbit.core.ProgressParser
import com.bentlybro.grabbit.core.UrlExtractor
import com.bentlybro.grabbit.engine.MediaInfo
import com.bentlybro.grabbit.ui.icons.GrabbitIcons
import com.bentlybro.grabbit.ui.theme.GrabbitColors

/** Link preview lifecycle shared by the main screen and the share sheet. */
sealed interface PreviewState {
    data object Idle : PreviewState
    data class Loading(val url: String) : PreviewState
    data class Loaded(val info: MediaInfo) : PreviewState
    data class Error(val url: String, val message: String) : PreviewState
}

@Composable
fun PreviewCard(state: PreviewState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        AnimatedContent(targetState = state, label = "preview") { s ->
            when (s) {
                PreviewState.Idle -> Spacer(Modifier.height(0.dp))
                is PreviewState.Loading -> LoadingPreview(s.url)
                is PreviewState.Loaded -> LoadedPreview(s.info)
                is PreviewState.Error -> ErrorPreview(s, onRetry)
            }
        }
    }
}

@Composable
private fun LoadingPreview(url: String) {
    Column(Modifier.padding(16.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(36.dp)) }
        Spacer(Modifier.height(12.dp))
        Text("Fetching info…", style = MaterialTheme.typography.titleMedium)
        Text(
            UrlExtractor.host(url) ?: url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LoadedPreview(info: MediaInfo) {
    Column(Modifier.padding(12.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (info.thumbnail != null) {
                AsyncImage(
                    model = info.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.65f))
                )
            )
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                info.extractor?.let { Badge(it) }
                Spacer(Modifier.weight(1f))
                ProgressParser.formatDuration(info.durationSec)?.let { Badge(it) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            info.title ?: info.url,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        info.uploader?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ErrorPreview(s: PreviewState.Error, onRetry: () -> Unit) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Couldn't read that link", style = MaterialTheme.typography.titleMedium)
            Text(
                s.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
fun Badge(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
fun FormatChips(selected: DownloadFormat, onSelect: (DownloadFormat) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DownloadFormat.entries.forEach { f ->
            val isSel = f == selected
            FilterChip(
                selected = isSel,
                onClick = { onSelect(f) },
                label = { Text(if (f.isAudio) "Audio · MP3" else f.label) },
                leadingIcon = when {
                    isSel -> { { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) } }
                    f.isAudio -> { { Icon(GrabbitIcons.Music, null, Modifier.size(18.dp)) } }
                    else -> null
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

/** Big gradient call-to-action used by both screens. */
@Composable
fun GrabButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) {
        Box(
            Modifier.fillMaxSize().background(
                if (enabled) GrabbitColors.accentGradient
                else Brush.linearGradient(listOf(GrabbitColors.SurfaceHighest, GrabbitColors.SurfaceHighest)),
                shape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    GrabbitIcons.Download, null,
                    tint = if (enabled) Color(0xFF1A0E05) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) Color(0xFF1A0E05) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
