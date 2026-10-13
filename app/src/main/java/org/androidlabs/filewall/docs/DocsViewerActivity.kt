package org.androidlabs.filewall.docs

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.remember
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.viewinterop.AndroidView
import org.androidlabs.filewall.R
import org.androidlabs.filewall.SFApplication
import org.androidlabs.filewall.ui.theme.FilewallTheme
import java.io.IOException

class DocsViewerActivity : ComponentActivity() {

    private val sfApplication: SFApplication
        get() = application as SFApplication

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra("name") ?: return
        val filename = intent.getStringExtra("filename") ?: return

        setContent {
            FilewallTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(name) },
                            navigationIcon = {
                                IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = getString(R.string.back)
                                    )
                                }
                            },
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    WebViewComposable(filename, Modifier.padding(innerPadding))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sfApplication.checkAndRunAuth()
    }
}

@Composable
fun WebViewComposable(filename: String, modifier: Modifier = Modifier) {
    // Inside a WebView prefers-color-scheme reports the WebView's own dark state rather
    // than the system theme, so the stylesheet is told which palette to use explicitly.
    val isDark = isSystemInDarkTheme()
    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
    val context = LocalContext.current
    // Read here rather than from the context inside the click handler: a resource read
    // through LocalContext is not configuration-aware and can return a stale value.
    val noAppForLink = stringResource(R.string.no_app_for_link)

    val html = remember(filename, isDark) {
        // Only the app starts this activity, with a name of a bundled document. Should that
        // ever not hold, show nothing rather than let the missing asset take the app down.
        val source = try {
            context.assets.open("$filename.html").bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            ""
        }
        val theme = if (isDark) "dark" else "light"
        if (source.contains("<html", ignoreCase = true)) {
            source.replaceFirst(Regex("<html", RegexOption.IGNORE_CASE),
                "<html data-theme=\"$theme\"")
        } else {
            "<html data-theme=\"$theme\">$source</html>"
        }
    }

    AndroidView(factory = {
        WebView(it).apply {
            setBackgroundColor(backgroundColor)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    request?.url?.let { url ->
                        // Nothing on the device may be able to open this. An unhandled
                        // ActivityNotFoundException here took the document with it: the
                        // user was dropped back to the Legal list and asked to
                        // authenticate again, with no hint that a link had failed.
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, url))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, noAppForLink, Toast.LENGTH_LONG).show()
                        }
                        return true
                    }
                    return false
                }
            }
            // base URL keeps doc.css and any relative asset resolvable
            loadDataWithBaseURL(
                "file:///android_asset/", html, "text/html", "utf-8", null
            )
        }
    }, update = { it.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null) },
        modifier = modifier.fillMaxSize())
}
