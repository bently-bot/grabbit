package com.bentlybro.grabbit.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bentlybro.grabbit.ui.components.FormatChips
import com.bentlybro.grabbit.ui.components.GrabButton
import com.bentlybro.grabbit.ui.components.PreviewCard
import com.bentlybro.grabbit.ui.components.PreviewState
import com.bentlybro.grabbit.ui.main.Wordmark
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(vm: ShareViewModel, onDownload: () -> Unit, onOpenApp: () -> Unit, onDismiss: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Wordmark()
                Text(
                    "  " + (state.url ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            PreviewCard(state.preview, onRetry = vm::fetch)
            FormatChips(state.format, vm::select)
            GrabButton(
                text = if (state.preview is PreviewState.Loading) "Download now" else "Download",
                enabled = state.url != null && state.formatLoaded,
                onClick = {
                    scope.launch { sheet.hide() }.invokeOnCompletion { onDownload() }
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onOpenApp) { Text("Open in Grabbit") }
                TextButton(onClick = { scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() } }) { Text("Cancel") }
            }
        }
    }
}
