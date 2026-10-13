package org.androidlabs.filewall.provider

import android.os.ParcelFileDescriptor

/**
 * What a client asked for when it opened a document, read off the mode string the way the
 * platform reads it: "r", "w", "wt", "wa", "rw", "rwt".
 *
 * The provider used to compare the string with "r", "w" and "rw" and treat everything else
 * as a read, so a truncating or appending write was checked, requested and logged as a read.
 */
internal data class OpenMode(
    val read: Boolean,
    val write: Boolean,
    val truncate: Boolean,
    val append: Boolean
) {
    val reason: AccessReason
        get() = when {
            read && write -> AccessReason.READWRITE
            write -> AccessReason.WRITE
            else -> AccessReason.READ
        }

    /** The kind of rule this open needs: "r", "w" or "rw", whatever flags came with it. */
    val accessMode: String
        get() = when {
            read && write -> AccessMode.READ_WRITE.title
            write -> AccessMode.WRITE_ONLY.title
            else -> AccessMode.READ_ONLY.title
        }

    /**
     * The flags to open the file with under a rule of type [granted], or null when the rule
     * leaves nothing of what was asked.
     *
     * The descriptor carries no more than the rule grants: a write-only rule gives a
     * write-only descriptor even to an "rw" open, which used to come back readable.
     */
    fun flagsUnder(granted: AccessType): Int? {
        val canRead = granted == AccessType.READ_ONLY_ACCESS || granted == AccessType.FULL_ACCESS
        val canWrite = granted == AccessType.WRITE_ONLY_ACCESS || granted == AccessType.FULL_ACCESS
        val reads = read && canRead
        val writes = write && canWrite
        if (!reads && !writes) return null
        if (!writes) return ParcelFileDescriptor.MODE_READ_ONLY

        var flags = if (reads) {
            ParcelFileDescriptor.MODE_READ_WRITE
        } else {
            ParcelFileDescriptor.MODE_WRITE_ONLY
        }
        flags = flags or ParcelFileDescriptor.MODE_CREATE
        if (truncate) flags = flags or ParcelFileDescriptor.MODE_TRUNCATE
        if (append) flags = flags or ParcelFileDescriptor.MODE_APPEND
        return flags
    }

    companion object {
        /** @throws IllegalArgumentException for a mode the platform would reject too. */
        fun parse(mode: String): OpenMode {
            require(mode.isNotEmpty() && mode.all { it in "rwta" }) { "Bad mode: $mode" }
            val read: Boolean
            val write: Boolean
            when {
                mode.startsWith("rw") -> { read = true; write = true }
                mode.startsWith("w") -> { read = false; write = true }
                mode.startsWith("r") -> { read = true; write = false }
                else -> throw IllegalArgumentException("Bad mode: $mode")
            }
            val append = write && 'a' in mode
            // Since Android 10 a bare "w" no longer implies truncate, yet it is what
            // ContentResolver.openOutputStream(uri) and `content write` send, and a client
            // saving that way expects the file to end where its data ends. Without this a
            // shorter save keeps the old file's tail. "rw" is left alone: it is how a
            // client edits in place.
            val truncate = write && ('t' in mode || (!read && !append))
            return OpenMode(read, write, truncate, append)
        }
    }
}
