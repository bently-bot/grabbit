package com.bentlybro.grabbit.ui.main

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bentlybro.grabbit.AppGraph
import com.bentlybro.grabbit.download.Notifications
import com.bentlybro.grabbit.ui.UpdateDialog
import com.bentlybro.grabbit.update.UpdateUi
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.bentlybro.grabbit.ui.settings.SettingsScreen
import com.bentlybro.grabbit.ui.settings.SettingsViewModel
import com.bentlybro.grabbit.ui.theme.GrabbitTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "com.bentlybro.grabbit.URL"
    }

    private val vm: MainViewModel by viewModels()
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppGraph.init(this)
        if (savedInstanceState == null) handleIntent(intent)
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(this) && savedInstanceState == null) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (savedInstanceState == null) lifecycleScope.launch { AppGraph.appUpdater.autoCheck() }

        setContent {
            GrabbitTheme {
                val updater = AppGraph.appUpdater
                val update by updater.ui.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()
                UpdateDialog(
                    state = update,
                    onDownload = { (update as? UpdateUi.Available)?.let { scope.launch { updater.download(it.update) } } },
                    onInstall = {
                        (update as? UpdateUi.ReadyToInstall)?.let {
                            runCatching { startActivity(updater.installIntent(it.apk)) }
                            updater.dismiss()
                        }
                    },
                    onGrantPermission = { runCatching { startActivity(updater.installPermissionIntent()) } },
                    onSkip = { v -> scope.launch { updater.skip(v) } },
                    onDismiss = updater::dismiss,
                )
                var showSettings by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = showSettings) { showSettings = false }
                AnimatedContent(targetState = showSettings, label = "screen") { settings ->
                    if (settings) {
                        SettingsScreen(viewModel<SettingsViewModel>(), onBack = { showSettings = false })
                    } else {
                        MainScreen(vm, onPaste = ::pasteFromClipboard, onOpenSettings = { showSettings = true })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from "Install unknown apps"? Move straight on to the install prompt.
        AppGraph.appUpdater.recheckPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_URL)?.let(vm::useText)
    }

    // Android 10+ only lets the focused app read the clipboard, so check on focus gain.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) vm.onClipboard(clipboardText())
    }

    private fun pasteFromClipboard() {
        clipboardText()?.let(vm::useText)
    }

    private fun clipboardText(): String? = runCatching {
        val cm = getSystemService(ClipboardManager::class.java)
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) null else clip.getItemAt(0).coerceToText(this)?.toString()
    }.getOrNull()
}
