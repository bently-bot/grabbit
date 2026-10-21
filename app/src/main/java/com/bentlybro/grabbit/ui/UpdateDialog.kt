package com.bentlybro.grabbit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bentlybro.grabbit.BuildConfig
import com.bentlybro.grabbit.update.UpdateUi

/**
 * The app-update dialog. Only the "interesting" states show a dialog; Checking / UpToDate /
 * Failed from the Settings button are shown inline there instead.
 */
@Composable
fun UpdateDialog(
    state: UpdateUi,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onGrantPermission: () -> Unit,
    onSkip: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateUi.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Grabbit ${state.update.versionName} is out") },
            text = {
                Column {
                    Text(
                        "You have ${BuildConfig.VERSION_NAME}. Download size ${"%.0f".format(state.update.apk.size / 1048576.0)} MB.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                        Text(state.update.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDownload) { Text("Update") } },
            dismissButton = {
                Column {
                    TextButton(onClick = onDismiss) { Text("Later") }
                    TextButton(onClick = { onSkip(state.update.versionName) }) { Text("Skip this version") }
                }
            },
        )
        is UpdateUi.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading ${state.update.versionName}") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${(state.fraction * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {},
        )
        is UpdateUi.NeedsInstallPermission -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("One more step") },
            text = { Text("Android needs you to allow Grabbit to install updates. Turn on \"Allow from this source\", then come back.") },
            confirmButton = { TextButton(onClick = onGrantPermission) { Text("Open settings") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        is UpdateUi.ReadyToInstall -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Ready to install") },
            text = { Text("Grabbit ${state.update.versionName} is downloaded and verified (SHA-256 and signature).") },
            confirmButton = { TextButton(onClick = onInstall) { Text("Install") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } },
        )
        else -> Unit
    }
}
