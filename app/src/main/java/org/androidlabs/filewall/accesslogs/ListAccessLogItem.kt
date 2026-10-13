package org.androidlabs.filewall.accesslogs

import android.content.Context
import android.graphics.drawable.Drawable
import org.androidlabs.filewall.R
import org.androidlabs.filewall.database.AccessLog
import org.androidlabs.filewall.database.AccessLogType
import org.androidlabs.filewall.provider.FilewallProvider

data class ListAccessLogItem(
    val appName: String,
    val appIcon: Drawable?,
    val log: AccessLog
) {
    fun getPath(): String {
        val divider = FilewallProvider.DIVIDER
        val rootId = FilewallProvider.ROOT_ID
        val filePath = FilewallProvider.getFilePath(log.file)
        val baseFilePath = rootId + divider
        if (filePath == rootId) {
            return baseFilePath
        }
        return baseFilePath + filePath
    }

    /**
     * The badges the row shows under "Access result", as one line. Three places need
     * this - the row itself, the CSV export and the spoken description - so it lives
     * here rather than being written out again in each of them.
     */
    fun accessResultText(context: Context): String {
        if (log.type == AccessLogType.REQUEST) {
            return ""
        }
        val deletedMarker = context.getString(R.string.rule_since_deleted)
        val rule = log.rule
            ?: return listOfNotNull(
                when (log.type) {
                    AccessLogType.DENIED -> context.getString(R.string.denied)
                    AccessLogType.PROTECTION_DISABLED ->
                        context.getString(R.string.allowed_protection_disabled)
                    else -> null
                },
                context.getString(R.string.rule_since_deleted)
                    .takeIf { (log.ruleId ?: 0L) > 0L }
            ).joinToString(" ")

        val parts = mutableListOf<String>()
        if (rule.noAccess) {
            parts.add(context.getString(R.string.no_access))
        } else {
            if (rule.readAccess) parts.add(context.getString(R.string.read))
            if (rule.writeAccess) parts.add(context.getString(R.string.write))
        }
        if (rule.subfoldersAccess) parts.add(context.getString(R.string.subfolders))
        if (rule.isListOfFilesAllowed) parts.add(context.getString(R.string.list_of_files))
        if (rule.deletedAt != null) parts.add(deletedMarker)
        return parts.joinToString(" ")
    }
}