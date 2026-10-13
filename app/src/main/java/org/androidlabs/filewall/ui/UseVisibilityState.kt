package org.androidlabs.filewall.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LiveData
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.androidlabs.filewall.auth.AuthManager

@Composable
fun useVisibilityState(
    isAccessGrantedData: LiveData<Boolean>,
    initialVisibility: Boolean = false,
    onVisibilityChanged: (Boolean) -> Unit = {}
): Boolean {
    val recomposeScope = currentRecomposeScope
    val currentApp = LocalContext.current.applicationContext
    val isAccessGranted = isAccessGrantedData.observeAsState(initial = false)
    var isHidden by remember { mutableStateOf(initialVisibility) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    recomposeScope.invalidate()
                    if (AuthManager.instance.isEnabled(currentApp)) {
                        isHidden = false
                    } else if (isHidden) {
                        isHidden = false
                    }
                    onVisibilityChanged(true)
                }

                Lifecycle.Event.ON_PAUSE -> {
                    if (AuthManager.instance.isEnabled(currentApp)) {
                        isHidden = true
                    }
                    onVisibilityChanged(false)
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return isHidden || !isAccessGranted.value
}