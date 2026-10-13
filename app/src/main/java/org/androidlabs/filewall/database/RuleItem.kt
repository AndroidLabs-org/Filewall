package org.androidlabs.filewall.database

import org.androidlabs.filewall.confirm.ConfirmationData
import org.androidlabs.filewall.confirm.RequestData
import org.androidlabs.filewall.provider.AccessResolution
import org.androidlabs.filewall.provider.AccessType
import org.androidlabs.filewall.provider.AccessDuration

data class RuleItem(
    val id: Long?,
    val file: String,
    val isDirectory: Boolean,
    val pkg: String,
    val accessType: AccessType,
    val accessDuration: AccessDuration,
    val accessResolution: AccessResolution,
    val isListOfFilesAllowed: Boolean,
    val createdAt: Long,
    var orderPosition: Long,
    var deletedAt: Long? = null
) {
    fun toConfirmationData(requestedMode: String): ConfirmationData {
        return ConfirmationData(
            RequestData(0,this.file, this.isDirectory, requestedMode, this.pkg, false, ""),
            this.accessType,
            this.accessDuration,
            this.accessResolution,
            this.isListOfFilesAllowed,
            this.id
        )
    }

    fun expirationDate(): Long? {
        if (!accessDuration.hasTimeout()) {
            return null
        }
        val date = createdAt + accessDuration.getTimeout() * 1000
        return date
    }

    val readAccess: Boolean
        get() = accessType != AccessType.DENY_ACCESS && accessType != AccessType.WRITE_ONLY_ACCESS

    val writeAccess: Boolean
        get() = accessType == AccessType.FULL_ACCESS || accessType == AccessType.WRITE_ONLY_ACCESS

    val noAccess: Boolean
        get() = accessType == AccessType.DENY_ACCESS && !isListOfFilesAllowed

    val subfoldersAccess: Boolean
        get() = accessResolution == AccessResolution.ENTIRE_FOLDER_INCLUDE_SUBFOLDERS
}