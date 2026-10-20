package com.bentlybro.grabbit.ui.share

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.bentlybro.grabbit.AppGraph
import com.bentlybro.grabbit.download.Notifications
import com.bentlybro.grabbit.ui.main.MainActivity
import com.bentlybro.grabbit.ui.theme.GrabbitTheme
import kotlinx.coroutines.launch

/**
 * The share target. A translucent, recents-excluded activity that floats a bottom sheet over
 * whatever app the link came from, so one tap queues the download and drops the user right
 * back where they were. With "Download instantly on share" it shows no UI at all.
 */
class ShareActivity : ComponentActivity() {

    private val vm: ShareViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { finishQuietly() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppGraph.init(this)

        val url = intent.toSharedText().url()
        if (url == null) {
            Toast.makeText(this, "No link found in what you shared", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                if (AppGraph.settings.current().instantOnShare) {
                    AppGraph.scheduler.enqueue(url, AppGraph.settings.current().defaultFormat)
                    Toast.makeText(this@ShareActivity, "🐇 Grabbing it in the background", Toast.LENGTH_SHORT).show()
                    afterEnqueue()
                } else {
                    showSheet(url)
                }
            }
        } else {
            showSheet(url)
        }
    }

    private fun showSheet(url: String) {
        vm.start(url)
        setContent {
            GrabbitTheme {
                ShareSheet(
                    vm = vm,
                    onDownload = {
                        vm.download()
                        Toast.makeText(this, "🐇 Added to Grabbit", Toast.LENGTH_SHORT).show()
                        afterEnqueue()
                    },
                    onOpenApp = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra(MainActivity.EXTRA_URL, url)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                        finishQuietly()
                    },
                    onDismiss = ::finishQuietly,
                )
            }
        }
    }

    /** Ask for notifications once (so progress is visible), then get out of the way. */
    private fun afterEnqueue() {
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            finishQuietly()
        }
    }

    private fun finishQuietly() {
        finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
