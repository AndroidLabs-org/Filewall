package org.androidlabs.filewall.auth

import android.content.Intent
import android.os.Bundle
import org.androidlabs.filewall.logger.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.androidlabs.filewall.R
import org.androidlabs.filewall.SFApplication
import org.androidlabs.filewall.database.Database
import org.androidlabs.filewall.ui.theme.FilewallTheme
import org.androidlabs.filewall.ui.secureWindow

class AuthActivity : ComponentActivity() {
    private val TAG = "AuthActivity"
    private var dbHelper: Database = Database(this)
    private val sfApplication: SFApplication
        get() = application as SFApplication

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "auth on resume")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "auth on stop")
    }

    /**
     * This activity is singleTask, so a later request to show the gate resumes it rather
     * than creating it. Without this the user would be looking at the "authentication
     * required" screen with no prompt on it.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "auth on new intent")
        onAuth()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureWindow(always = true)

        enableEdgeToEdge()

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing, disabling the back button
            }
        }

        onBackPressedDispatcher.addCallback(this, callback)
        val appName = resources.getString(R.string.app_name)

        setContent {
            FilewallTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(appName) },
                        )
                    },
                ) { innerPadding ->
                    AuthScreen(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        onAuth = ::onAuth
                    )
                }
            }
        }

        sfApplication.isAccessGranted.observe(this) { isGranted ->
            if (isGranted) {
                finish()
            } else
                if (AuthManager.instance.isBioWas)
                    onAuth()
        }

        onAuth()
    }

    private fun onAuth() {
        Log.d(TAG, "auth")
        try {
            AuthManager.instance.validateSecurity(
                context = this,
                dbHelper = dbHelper,
                purpose = getString(R.string.auth_for_app_access),
                documentId = "",
                callingPackage = packageName,
                withoutRetry = true
            )
        } catch (e: Exception) {
            Log.e(
                "MainActivityViewModel",
                "failed to validate auth - ${e.message}, ${e.localizedMessage}"
            )
        } finally {
        }
    }
}

@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
    onAuth: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                stringResource(id = R.string.auth_required_without_app_name),
                textAlign = TextAlign.Center
            )

            Text(
                stringResource(id = R.string.app_name),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = {
                AuthManager.instance.isBioWas = false
                onAuth()
            }) {
                Text(text = stringResource(id = R.string.auth_retry))
            }
        }
    }
}