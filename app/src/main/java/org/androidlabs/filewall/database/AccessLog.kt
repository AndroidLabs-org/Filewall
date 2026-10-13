package org.androidlabs.filewall.database

import org.androidlabs.filewall.provider.AccessReason

enum class AccessLogType(val title: String) {
    RULE("RULE"),
    DENIED("DENIED"),
    REQUEST("REQUEST"),

    /** Allowed without consulting any rule, because protection was switched off. */
    PROTECTION_DISABLED("PROTECTION_DISABLED");

    companion object {
        fun fromName(name: String): AccessLogType? {
            return AccessLogType.entries.find { it.name == name }
        }
    }
}


data class AccessLog(
    val id: Long?,
    val file: String,
    val isDirectory: Boolean,
    val pkg: String,
    val reason: AccessReason,
    val type: AccessLogType,
    val ruleId: Long?,
    var rule: RuleItem?,
    val createdAt: Long
)