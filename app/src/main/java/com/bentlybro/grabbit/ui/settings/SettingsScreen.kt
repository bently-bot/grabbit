package com.bentlybro.grabbit.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bentlybro.grabbit.BuildConfig
import com.bentlybro.grabbit.data.EngineChannel
import com.bentlybro.grabbit.ui.components.FormatChips
import com.bentlybro.grabbit.update.UpdateUi
import android.text.format.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val engineVersion by vm.engineVersion.collectAsStateWithLifecycle()
    val engineStatus by vm.engineStatus.collectAsStateWithLifecycle()
    val appUpdate by vm.appUpdate.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Default format", "Preselected on the main screen and in the share sheet.") {
                FormatChips(s.defaultFormat, vm::setFormat)
            }
            ToggleSection(
                "Download instantly on share",
                "Skip the sheet: sharing a link to Grabbit starts the download straight away.",
                s.instantOnShare, vm::setInstant,
            )
            ToggleSection(
                "Offer clipboard links",
                "When you open Grabbit with a link copied, offer to use it.",
                s.offerClipboard, vm::setClipboard,
            )
            Section(
                "Download engine (yt-dlp)",
                "Sites change all the time, YouTube especially. Grabbit updates the engine on first launch, " +
                    "daily, and automatically when a download is refused (HTTP 403).",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Version", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(engineVersion ?: "Checking…", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (s.lastEngineCheck > 0) "Checked ${ago(s.lastEngineCheck)}" else "Not checked yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = vm::updateEngine, enabled = !engineStatus.running) {
                        if (engineStatus.running) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (engineStatus.running) "Updating…" else "Update engine now")
                    }
                }
                engineStatus.lastMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                Spacer(Modifier.height(12.dp))
                Text("Channel", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EngineChannel.entries.forEach { c ->
                        FilterChip(
                            selected = s.engineChannel == c,
                            onClick = { vm.setChannel(c) },
                            label = { Text(c.label) },
                        )
                    }
                }
                Text(
                    "Nightly gets site fixes first and is what yt-dlp suggests when YouTube breaks. Stable changes less often.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Update automatically", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = s.autoUpdateEngine, onCheckedChange = vm::setAutoEngine)
                }
            }
            Section("App updates", if (vm.updatesEnabled) "From github.com/${vm.updateRepo} releases." else "Switched off in this build.") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Installed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.titleMedium)
                    }
                    val checking = appUpdate is UpdateUi.Checking || appUpdate is UpdateUi.Downloading
                    Button(onClick = vm::checkForAppUpdate, enabled = vm.updatesEnabled && !checking) {
                        if (checking) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Check for updates")
                    }
                }
                val line = when (val u = appUpdate) {
                    is UpdateUi.UpToDate -> "You're on the latest version (${u.version})."
                    is UpdateUi.Failed -> u.message
                    else -> null
                }
                line?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Section("About", null) {
                Text("Grabbit ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Powered by yt-dlp, youtubedl-android and FFmpeg. Respect each site's terms and creators' copyright: " +
                        "only download what you're allowed to.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String?, content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ToggleSection(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
        onClick = { onChange(!checked) },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

private fun ago(ms: Long): String =
    DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().lowercase()
