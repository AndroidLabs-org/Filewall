package org.androidlabs.filewall.logger

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import org.androidlabs.filewall.R
import org.androidlabs.filewall.SFApplication
import org.androidlabs.filewall.ui.BackNavigable
import org.androidlabs.filewall.ui.SecurityFragmentActivity

class LogsActivity : SecurityFragmentActivity() {
    private val logWrapper = MessageOnlyLogFilter()
    private val sfApplication: SFApplication
        get() = application as SFApplication

    override fun onResume() {
        super.onResume()
        sfApplication.checkAndRunAuth()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.removeLogNode(logWrapper)
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        Log.addLogNode(logWrapper)

        setContent {
            BackNavigable(
                titleResId = R.string.logs,
                onBackPressedDispatcher = onBackPressedDispatcher,
                content = { innerPadding, isScreenHidden ->
                    LogsContent(
                        modifier = Modifier
                            .padding(innerPadding)
                        , next = logWrapper
                    )
                },
                rightView = {
                    TextButton(onClick = ::shareLogs) {
                        Text(text = stringResource(id = R.string.share_logs))
                    }
                }
            )
        }
    }

    private fun shareLogs() {
        val logger = FileLogger(this)
        val fileUri = FileProvider.getUriForFile(
            this,
            "${packageName}.file-provider",
            logger.logFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(
            Intent.createChooser(shareIntent, getString(R.string.share_logs))
        )
    }
}

@Composable
fun LogsContent(
    modifier: Modifier,
    next: MessageOnlyLogFilter,
) {
    val localContext = LocalContext.current
    val fragmentManager = remember { (localContext as FragmentActivity).supportFragmentManager }

    AndroidView(
        factory = { context ->
            FrameLayout(context).apply {
                id = View.generateViewId()

                val logFragment = LogFragment()
                logFragment.logNode = next

                fragmentManager.beginTransaction()
                    .replace(this.id, logFragment)
                    .commit()
            }
        },
        modifier = modifier.fillMaxSize()
    )
}