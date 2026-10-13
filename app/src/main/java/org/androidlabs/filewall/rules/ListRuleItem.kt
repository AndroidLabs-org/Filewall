package org.androidlabs.filewall.rules

import android.graphics.drawable.Drawable
import org.androidlabs.filewall.database.RuleItem
import org.androidlabs.filewall.provider.AccessResolution
import org.androidlabs.filewall.provider.FilewallProvider

data class ListRuleItem(
    val appName: String,
    val appIcon: Drawable?,
    val rule: RuleItem
) {
    fun getPath(): String {
        val divider = FilewallProvider.DIVIDER
        val rootId = FilewallProvider.ROOT_ID
        val filePath = FilewallProvider.getFilePath(rule.file)
        val baseFilePath = rootId + divider
        if (filePath == rootId) {
            return baseFilePath
        }

        val path = baseFilePath + filePath
        if (rule.accessResolution == AccessResolution.THIS_FILE) {
            return path
        }
        val folderPath: String = if (rule.isDirectory) {
            path
        } else {
            path.split(divider).dropLast(1).joinToString(divider) + divider
        }

        return folderPath
    }
}