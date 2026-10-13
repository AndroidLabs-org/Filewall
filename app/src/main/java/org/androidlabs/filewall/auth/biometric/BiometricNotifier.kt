package org.androidlabs.filewall.auth.biometric

interface BiometricListener {
    fun onBiometricResolution(isAllowed: Boolean)
}

object BiometricNotifier {
    private val listeners = mutableListOf<BiometricListener>()

    fun registerListener(listener: BiometricListener) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: BiometricListener) {
        listeners.remove(listener)
    }

    fun notifyResolution(isAllowed: Boolean) {
        listeners.forEach { it.onBiometricResolution(isAllowed) }
    }
}
