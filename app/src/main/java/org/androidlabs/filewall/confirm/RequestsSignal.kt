package org.androidlabs.filewall.confirm

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Says that the requests table has changed behind the UI's back.
 *
 * A request is written by [DocumentConfirmationReceiver], outside any screen. The Requests
 * tab only reloaded on its own lifecycle events and after the user's own actions, so a
 * request that arrived while the tab was on screen stayed invisible until the tab was left
 * and opened again.
 */
object RequestsSignal {
    private val _changes = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val changes: SharedFlow<Unit> = _changes

    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }
}
