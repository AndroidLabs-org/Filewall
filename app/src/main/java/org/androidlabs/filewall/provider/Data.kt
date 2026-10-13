package org.androidlabs.filewall.provider


enum class AccessType(val title: String) {
    READ_ONLY_ACCESS("Allow read-only access"),
    WRITE_ONLY_ACCESS("Allow write-only access"),
    FULL_ACCESS("Allow full access"),
    DENY_ACCESS("Deny access");

    companion object {
        fun fromTitle(title: String): AccessType? {
            return entries.find { it.title == title }
        }

        fun fromName(name: String): AccessType? {
            return entries.find { it.name == name }
        }

        fun fromPermissions(isReadAllowed: Boolean, isWriteAllowed: Boolean): AccessType {
            return when {
                isReadAllowed && isWriteAllowed -> FULL_ACCESS
                isReadAllowed -> READ_ONLY_ACCESS
                isWriteAllowed -> WRITE_ONLY_ACCESS
                else -> DENY_ACCESS
            }
        }
    }

    fun mode(): String {
        return when (this) {
            READ_ONLY_ACCESS -> AccessMode.READ_ONLY.title
            WRITE_ONLY_ACCESS -> AccessMode.WRITE_ONLY.title
            FULL_ACCESS -> AccessMode.READ_WRITE.title
            DENY_ACCESS -> ""
        }
    }
}

enum class AccessDuration(val title: String) {
    FOREVER("Forever"),
    MINUTES_1("1 minute"),
    MINUTES_10("10 minutes"),
    MINUTES_30("30 minutes"),
    MINUTES_60("60 minutes"),
    DAYS_1("1 day"),
    DAYS_7("1 week");

    companion object {
        fun fromTitle(title: String): AccessDuration? {
            return entries.find { it.title == title }
        }

        fun fromName(name: String): AccessDuration? {
            return AccessDuration.entries.find { it.name == name }
        }
    }

    fun hasTimeout(): Boolean {
        return this != FOREVER
    }

    fun getTimeout(): Long {
        return when (this) {
            MINUTES_1 -> 60
            MINUTES_10 -> 10 * 60
            MINUTES_30 -> 30 * 60
            MINUTES_60 -> 60 * 60
            DAYS_1 -> 1 * 24 * 3600
            DAYS_7 -> 7 * 24 * 3600
            FOREVER -> 0
        }
    }
}

enum class AccessResolution(val title: String) {
    THIS_FILE("This file"),
    ENTIRE_FOLDER("Entire folder"),
    ENTIRE_FOLDER_INCLUDE_SUBFOLDERS("Entire folder including subfolders");

    companion object {
        fun fromTitle(title: String): AccessResolution? {
            return entries.find { it.title == title }
        }

        fun fromName(name: String): AccessResolution? {
            return AccessResolution.entries.find { it.name == name }
        }

        fun getValues(isDirectory: Boolean): List<AccessResolution> {
            return entries.filter {
                !(it.name == THIS_FILE.name && isDirectory) &&
                        it.name != ENTIRE_FOLDER_INCLUDE_SUBFOLDERS.name
            }
        }
    }
}

enum class AccessMode(val title: String) {
    READ_ONLY("r"),
    WRITE_ONLY("w"),
    READ_WRITE("rw"),
    LIST_OF_FILES("ls");

    companion object {
        fun getNameFromMode(mode: String): String? {
            var s = ""
            if (mode == "r") s = "read"
            if (mode == "w") s = "write"
            if (mode == "rw") s = "read/write"
            if (mode == "ls") s = "list files"
            return s
        }
    }
}

enum class AccessReason(val title: String) {
    READ("access"),
    CREATE("create"),
    WRITE("write"),
    READWRITE("read-write"),
    DELETE("delete"),
    RENAME("rename"),
    COPY("copy"),
    MOVE("move");

    companion object {
        fun fromName(name: String): AccessReason? {
            return AccessReason.entries.find { it.name == name }
        }
    }
}
