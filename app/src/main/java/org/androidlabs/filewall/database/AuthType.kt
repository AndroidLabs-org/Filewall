package org.androidlabs.filewall.database


enum class AuthType(val title: String) {
    PIN("pin"),
    BIOMETRIC("biometric");

    companion object {
        fun fromName(name: String): AuthType? {
            return entries.find { it.name == name }
        }
    }
}