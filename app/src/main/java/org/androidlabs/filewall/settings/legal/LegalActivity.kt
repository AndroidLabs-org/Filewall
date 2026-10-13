package org.androidlabs.filewall.settings.legal

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.androidlabs.filewall.R
import org.androidlabs.filewall.SFApplication
import org.androidlabs.filewall.auth.AuthActivity
import org.androidlabs.filewall.docs.DocsViewerActivity
import org.androidlabs.filewall.settings.SettingsRow
import org.androidlabs.filewall.ui.BackNavigable

class LegalActivity : ComponentActivity() {

    private val sfApplication: SFApplication
        get() = application as SFApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        setContent {
            BackNavigable(
                titleResId = R.string.legal,
                onBackPressedDispatcher = onBackPressedDispatcher,
                content = { innerPadding, isScreenHidden ->
                    LegalContent(
                        modifier = Modifier
                            .padding(innerPadding),
                        onTerms = ::onTerms,
                        onDisclaimer = ::onDisclaimer,
                        onEULA = ::onEULA,
                        onDisclosure = ::onDisclosure,
                        onPolicy = ::onPolicy,
                    )
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        sfApplication.checkAndRunAuth()
    }

    private fun openDoc(nameId: Int, fileName: String) {
        val intent = Intent(this, DocsViewerActivity::class.java).apply {
            putExtra("name", getString(nameId))
            putExtra("filename", fileName)
        }
        startActivity(intent)
    }

    fun onTerms() {
        openDoc(R.string.terms, "Terms_of_Use")
    }

    fun onDisclaimer() {
        openDoc(R.string.disclaimer, "Disclaimer")
    }

    fun onDisclosure() {
        openDoc(R.string.disclosure, "Permission_Disclosure")
    }

    fun onEULA() {
        openDoc(R.string.eula, "EULA")
    }

    fun onPolicy() {
        openDoc(R.string.policy, "Privacy_Policy")
    }

}

@Composable
private fun LegalContent(
    modifier: Modifier,
    onTerms: () -> Unit,
    onDisclaimer: () -> Unit,
    onEULA: () -> Unit,
    onDisclosure: () -> Unit,
    onPolicy: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(modifier = modifier.verticalScroll(scrollState)) {

        SettingsRow(
            title = stringResource(id = R.string.terms),
            subtitle = null,
            iconView = {
                Image(
                    painter = painterResource(id = R.drawable.ic_article_24),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            },
            rightView = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            },
            onClick = onTerms
        )

        SettingsRow(
            title = stringResource(id = R.string.policy),
            subtitle = null,
            iconView = {
                Image(
                    painter = painterResource(id = R.drawable.ic_article_24),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            },
            rightView = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            },
            onClick = onPolicy
        )

        SettingsRow(
            title = stringResource(id = R.string.disclaimer),
            subtitle = null,
            iconView = {
                Image(
                    painter = painterResource(id = R.drawable.ic_article_24),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            },
            rightView = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            },
            onClick = onDisclaimer
        )

        SettingsRow(
            title = stringResource(id = R.string.disclosure),
            subtitle = null,
            iconView = {
                Image(
                    painter = painterResource(id = R.drawable.ic_article_24),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            },
            rightView = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            },
            onClick = onDisclosure
        )

        SettingsRow(
            title = stringResource(id = R.string.eula),
            subtitle = null,
            iconView = {
                Image(
                    painter = painterResource(id = R.drawable.ic_article_24),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            },
            rightView = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            },
            onClick = onEULA
        )
    }
}