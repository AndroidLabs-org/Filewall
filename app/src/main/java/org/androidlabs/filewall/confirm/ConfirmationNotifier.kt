package org.androidlabs.filewall.confirm

import org.androidlabs.filewall.provider.AccessResolution
import org.androidlabs.filewall.provider.AccessType
import org.androidlabs.filewall.provider.AccessDuration

data class ConfirmationData(
    val request: RequestData,
    val accessType: AccessType,
    val accessDuration: AccessDuration,
    val accessResolution: AccessResolution,
    val isListOfFilesAllowed: Boolean,
    var ruleId: Long? = null
)

interface ConfirmationListener {
    fun onConfirmation(data: ConfirmationData)
}

object ConfirmationNotifier {
    private val listeners = mutableListOf<ConfirmationListener>()

    fun registerListener(listener: ConfirmationListener) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: ConfirmationListener) {
        listeners.remove(listener)
    }

    fun notifyConfirmation(data: ConfirmationData) {
        listeners.forEach { it.onConfirmation(data) }
    }
}
