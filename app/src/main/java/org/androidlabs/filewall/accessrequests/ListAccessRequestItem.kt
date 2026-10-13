package org.androidlabs.filewall.accessrequests

import android.graphics.drawable.Drawable
import org.androidlabs.filewall.confirm.RequestData
import org.androidlabs.filewall.provider.FilewallProvider

/**
 * One card in the Requests tab. A file manager copying a directory raises one request per
 * file, so requests that differ only in which file they name are folded into a single
 * card: [request] is the one shown, [members] is everything the card stands for.
 */
data class ListAccessRequestItem(
    val appName: String,
    val appIcon: Drawable?,
    val request: RequestData,
    val members: List<RequestData> = listOf(request)
) {
    val count: Int get() = members.size

    val isBulk: Boolean get() = members.size > 1

    val memberIds: LongArray get() = members.map { it.id }.toLongArray()

    /**
     * Whether the card stands for folders, such as listings of two sub-folders. The folder
     * the card is decided on is their parent, and a rule on a folder does not cover the
     * folders inside it unless it extends to subfolders.
     */
    val includesFolders: Boolean get() = members.any { it.isDirectory }

    /**
     * What to put in front of the user. For a batch that is the folder it lands in - one
     * decision covering the lot - rather than the first file of ten thousand.
     */
    fun requestToDecide(): RequestData {
        if (!isBulk) {
            return request
        }
        return RequestData(
            0,
            FilewallProvider.getParentFolderOfDocumentId(request.documentId),
            true,
            request.mode,
            request.callingPackage,
            request.isCreation,
            request.action
        )
    }
}