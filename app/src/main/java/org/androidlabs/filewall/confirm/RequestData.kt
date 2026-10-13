package org.androidlabs.filewall.confirm

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import org.androidlabs.filewall.R
import org.androidlabs.filewall.provider.AccessMode
import org.androidlabs.filewall.provider.AccessType
import org.androidlabs.filewall.provider.FilewallProvider
import org.androidlabs.filewall.utils.Utils

data class RequestData(
    val id: Long,
    val documentId: String,
    val isDirectory: Boolean,
    val mode: String,
    val callingPackage: String,
    val isCreation: Boolean,
    val action: String,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readString()!!,
        parcel.readString()!! == "true",
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString() == "true",
        parcel.readString()!!,
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(documentId)
        parcel.writeString(isDirectory.toString())
        parcel.writeString(mode)
        parcel.writeString(callingPackage)
        parcel.writeString(isCreation.toString())
        parcel.writeString(action)
    }

    override fun describeContents(): Int {
        return 0
    }

    fun defaultAccessType(): AccessType {
        return if (mode == AccessMode.LIST_OF_FILES.title) AccessType.DENY_ACCESS else if (mode == AccessMode.READ_WRITE.title) AccessType.FULL_ACCESS else if (mode == AccessMode.WRITE_ONLY.title) AccessType.WRITE_ONLY_ACCESS else AccessType.READ_ONLY_ACCESS
    }

    fun isRootTarget(): Boolean {
        return FilewallProvider.isRoot(documentId)
    }

    /**
     * The folder the target sits in, or null at the root, which has no parent.
     * Asking about the root used to render the parent as a bare "/".
     */
    fun parentFolder(): String? {
        if (isRootTarget()) {
            return null
        }
        return FilewallProvider.getParentFolderOfDocumentId(documentId, true)
    }

    /**
     * What to call the thing being asked about. The root's document id is ".", which
     * says nothing to the user; it is named after the root itself, the same title the
     * file picker shows.
     */
    fun targetName(context: Context): String {
        if (isRootTarget()) {
            return context.getString(R.string.app_short_name)
        }
        return FilewallProvider.getFileNameFromDocumentId(documentId)
    }

    fun appName(context: Context): String {
        return Utils.getAppName(context, callingPackage)
    }

    fun appIcon(context: Context): Drawable? {
        return Utils.getAppIcon(context, callingPackage)
    }

    companion object CREATOR : Parcelable.Creator<RequestData> {
        override fun createFromParcel(parcel: Parcel): RequestData {
            return RequestData(parcel)
        }

        override fun newArray(size: Int): Array<RequestData?> {
            return arrayOfNulls(size)
        }
    }
}