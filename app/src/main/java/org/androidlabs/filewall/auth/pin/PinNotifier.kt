package org.androidlabs.filewall.auth.pin

interface PinListener {
    fun onPinResolution(isAllowed: Boolean, fromSettings: Boolean, disableBio: Boolean)
}

object PinNotifier {
    private val listeners = mutableListOf<PinListener>()

    fun registerListener(listener: PinListener) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: PinListener) {
        listeners.remove(listener)
    }

    fun notifyResolution(isAllowed: Boolean, fromSettings: Boolean, disableBio: Boolean) {
        listeners.forEach { it.onPinResolution(isAllowed, fromSettings, disableBio) }
    }
}
