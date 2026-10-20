package com.bentlybro.grabbit.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bentlybro.grabbit.core.UrlExtractor
import com.bentlybro.grabbit.data.QueueItem
import com.bentlybro.grabbit.data.Status
import com.bentlybro.grabbit.engine.EngineState
import com.bentlybro.grabbit.ui.MediaActions
import com.bentlybro.grabbit.ui.components.FormatChips
import com.bentlybro.grabbit.ui.components.GrabButton
import com.bentlybro.grabbit.ui.components.PreviewCard
import com.bentlybro.grabbit.ui.components.PreviewState
import com.bentlybro.grabbit.ui.components.QueueRow
import com.bentlybro.grabbit.ui.icons.GrabbitIcons
import com.bentlybro.grabbit.ui.theme.GrabbitColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel, onPaste: () -> Unit, onOpenSettings: () -> Unit) {
    val home by vm.home.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val engine by vm.engineState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasUrl = UrlExtractor.extract(home.input) != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Wordmark() },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Paste a link from YouTube, TikTok, Instagram, X, Reddit… or just hit Share in those apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                OutlinedTextField(
                    value = home.input,
                    onValueChange = vm::onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://…") },
                    leadingIcon = { Icon(GrabbitIcons.Link, null) },
                    trailingIcon = {
                        if (home.input.isNotEmpty()) {
                            IconButton(onClick = vm::clearInput) { Icon(Icons.Filled.Clear, "Clear") }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (hasUrl) vm.download() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onPaste) {
                        Icon(GrabbitIcons.Paste, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Paste")
                    }
                    if (engine is EngineState.Initializing) {
                        EngineChip("Warming up yt-dlp…")
                    } else if (engine is EngineState.Failed) {
                        EngineChip("Engine error: ${(engine as EngineState.Failed).message}")
                    }
                }
            }
            home.clipboardOffer?.let { clip ->
                item(key = "clip") { ClipboardOffer(clip, onUse = { vm.useText(clip) }, onDismiss = vm::dismissClipboard) }
            }
            if (home.preview != PreviewState.Idle) {
                item(key = "preview") { PreviewCard(home.preview, onRetry = vm::retryPreview) }
            }
            item {
                AnimatedVisibility(visible = hasUrl) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FormatChips(home.format, vm::selectFormat)
                        GrabButton(
                            text = if (home.preview is PreviewState.Loading) "Download (fetching info…)" else "Download",
                            enabled = hasUrl,
                            onClick = vm::download,
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Downloads", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (queue.any { it.status == Status.DONE || it.status == Status.CANCELLED }) {
                        TextButton(onClick = vm::clearFinished) { Text("Clear finished") }
                    }
                }
            }
            if (queue.isEmpty()) {
                item { EmptyQueue() }
            }
            items(queue, key = QueueItem::id) { item ->
                QueueRow(
                    item = item,
                    onOpen = { MediaActions.open(context, item) },
                    onShare = { MediaActions.share(context, item) },
                    onCancel = { vm.cancel(item.id) },
                    onRetry = { vm.retry(item.id) },
                    onRemove = { vm.remove(item.id) },
                )
            }
        }
    }
}

@Composable
fun Wordmark() {
    Text(
        buildAnnotatedString {
            append("🐇 ")
            withStyle(SpanStyle(brush = GrabbitColors.accentGradient)) { append("Grabbit") }
        },
        style = MaterialTheme.typography.headlineMedium,
    )
}

@Composable
private fun EngineChip(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ClipboardOffer(url: String, onUse: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(GrabbitIcons.Paste, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Link on your clipboard", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(url, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
            }
            TextButton(onClick = onUse) { Text("Use") }
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Clear, "Dismiss", Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun EmptyQueue() {
    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🥕", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text("Nothing grabbed yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Downloads land in Movies/Grabbit and Music/Grabbit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
