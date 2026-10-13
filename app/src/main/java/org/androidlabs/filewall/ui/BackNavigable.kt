package org.androidlabs.filewall.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import org.androidlabs.filewall.R
import org.androidlabs.filewall.SFApplication
import org.androidlabs.filewall.ui.theme.FilewallTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackNavigable(
    @StringRes titleResId: Int,
    onBackPressedDispatcher: OnBackPressedDispatcher,
    content: @Composable (PaddingValues, isScreenHidden: Boolean) -> Unit,
    rightView: (@Composable () -> Unit)? = null
) {
    val currentApp = LocalContext.current.applicationContext as SFApplication
    val isScreenHidden = useVisibilityState(isAccessGrantedData = currentApp.isAccessGranted)

    FilewallTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(id = titleResId)) },
                    navigationIcon = {
                        IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.back)
                            )
                        }
                    },
                    actions = {
                        rightView?.invoke()
                    }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            content(innerPadding, isScreenHidden)
        }
    }
}