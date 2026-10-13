package org.androidlabs.filewall.confirm

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.androidlabs.filewall.database.Database
import org.androidlabs.filewall.logger.Log
import org.androidlabs.filewall.provider.AccessDuration
import org.androidlabs.filewall.provider.AccessMode
import org.androidlabs.filewall.provider.AccessResolution
import org.androidlabs.filewall.provider.AccessType
import org.androidlabs.filewall.provider.FilewallProvider

class DocumentConfirmationViewModel(
    initialRequestData: RequestData,
    context: Context,
    /**
     * The card being decided stands for several folders, so the rule has to reach into the
     * folder it is made on: a rule without subfolders left every one of them waiting.
     */
    private val coversSubfolders: Boolean = false
) : ViewModel() {

    private val dbHelper: Database = Database(context)

    private val _accessType = MutableLiveData(initialRequestData.defaultAccessType())
    val accessType: LiveData<AccessType> get() = _accessType

    private val _accessResolution = MutableLiveData(defaultResolution(initialRequestData))
    val accessResolution: LiveData<AccessResolution> get() = _accessResolution

    private val _accessDuration =
        MutableLiveData(AccessDuration.FOREVER)
    val accessDuration: LiveData<AccessDuration> get() = _accessDuration

    var request: RequestData = initialRequestData
        private set

    private fun defaultResolution(data: RequestData): AccessResolution = when {
        !data.isDirectory -> AccessResolution.THIS_FILE
        coversSubfolders -> AccessResolution.ENTIRE_FOLDER_INCLUDE_SUBFOLDERS
        else -> AccessResolution.ENTIRE_FOLDER
    }

    /**
     * Whether the one-off seeding from the requested mode has already happened. It lives
     * here rather than in a remember{} so that a configuration change does not re-run it
     * and throw away what the user has since chosen.
     */
    var isInitialized: Boolean = false
        private set

    /**
     * Seeds the dialog with what the app already has here plus the one thing it is now
     * asking for.
     *
     * A request is raised whenever an existing rule does not cover the operation - a
     * list under a read-only rule, say - and it is raised on the same path, so approving
     * it goes through upsertRule and replaces that rule. Seeding from the requested mode
     * alone therefore silently dropped every permission the rule already carried: answer
     * "yes" to a listing request and a read+write grant became list-only.
     */
    fun applyRequestDefaults(duration: AccessDuration) {
        isInitialized = true
        val existing = accessResolution.value?.let {
            dbHelper.findOverlappingRule(
                pkg = request.callingPackage,
                file = request.documentId,
                isDirectory = request.isDirectory,
                accessResolution = it
            )
        }
        setReadAccess(existing?.readAccess == true || requestsRead())
        setWriteAccess(existing?.writeAccess == true || requestsWrite())
        setListOfFilesAllowed(
            existing?.isListOfFilesAllowed == true ||
                request.mode == AccessMode.LIST_OF_FILES.title
        )
        setAccessDuration(existing?.accessDuration ?: duration)
    }

    /**
     * READ_WRITE was matched by neither of the two mode comparisons, so a request that
     * asked to open a document for reading and writing arrived with nothing ticked at all.
     */
    private fun requestsRead(): Boolean =
        request.mode == AccessMode.READ_ONLY.title ||
            request.mode == AccessMode.READ_WRITE.title ||
            // Listing a single file is how the picker asks to look at it, and a list-files
            // grant on a file unblocks that one query and nothing after it - the next read
            // asks again. Reading is what the caller is actually after.
            (request.mode == AccessMode.LIST_OF_FILES.title && !request.isDirectory)

    private fun requestsWrite(): Boolean =
        request.mode == AccessMode.WRITE_ONLY.title ||
            request.mode == AccessMode.READ_WRITE.title

    private val _isListOfFilesAllowed =
        MutableLiveData(initialRequestData.mode == AccessMode.LIST_OF_FILES.title)
    val isListOfFilesAllowed: LiveData<Boolean> get() = _isListOfFilesAllowed

    private val _isBlocked =
        MutableLiveData(false)
    val isBlocked: LiveData<Boolean> get() = _isBlocked

    private val _isReadAllowed =
        MutableLiveData(false)
    val isReadAllowed: LiveData<Boolean> get() = _isReadAllowed

    private val _isWriteAllowed =
        MutableLiveData(false)
    val isWriteAllowed: LiveData<Boolean> get() = _isWriteAllowed

    fun setReadAccess(value: Boolean) {
        val accessType = this.accessType.value
        if (value) {
            if (accessType == AccessType.DENY_ACCESS) {
                _accessType.value = AccessType.READ_ONLY_ACCESS
            } else if (accessType == AccessType.WRITE_ONLY_ACCESS) {
                _accessType.value = AccessType.FULL_ACCESS
            }
        } else {
            if (accessType == AccessType.FULL_ACCESS) {
                _accessType.value = AccessType.WRITE_ONLY_ACCESS
            } else if (accessType == AccessType.READ_ONLY_ACCESS) {
                _accessType.value = AccessType.DENY_ACCESS
            }
        }
        _isReadAllowed.value = value
    }

    fun setWriteAccess(value: Boolean) {
        val accessType = this.accessType.value
        if (value) {
            if (accessType == AccessType.DENY_ACCESS) {
                _accessType.value = AccessType.WRITE_ONLY_ACCESS
            } else if (accessType == AccessType.READ_ONLY_ACCESS) {
                _accessType.value = AccessType.FULL_ACCESS
            }
        } else {
            if (accessType == AccessType.FULL_ACCESS) {
                _accessType.value = AccessType.READ_ONLY_ACCESS
            } else if (accessType == AccessType.WRITE_ONLY_ACCESS) {
                _accessType.value = AccessType.DENY_ACCESS
            }
        }
        _isWriteAllowed.value = value
    }

    fun setAccessDuration(accessDuration: AccessDuration) {
        _accessDuration.value = accessDuration
    }

    fun setAccessResolution(accessResolution: AccessResolution) {
        _accessResolution.value = accessResolution
    }

    fun setListOfFilesAllowed(value: Boolean) {
        _isListOfFilesAllowed.value = value
    }


    fun requestToConfirm(): RequestData {
        //if (FilewallProvider.isRoot(request.documentId)) {
            return request
        /*}
        val parentDocId = FilewallProvider.getParentFolderOfDocumentId(request.documentId)
        return RequestData(
            request.id,
            parentDocId,
            request.isDirectory,
            request.mode,
            request.callingPackage,
            request.isCreation,
            request.action
        )*/
    }

    fun setBlocked(value: Boolean) {
        if (value) {
            _accessType.value = AccessType.DENY_ACCESS
            //setAccessResolution(if (request.isDirectory) AccessResolution.ENTIRE_FOLDER_INCLUDE_SUBFOLDERS else AccessResolution.THIS_FILE)
            _isReadAllowed.value = false
            _isWriteAllowed.value = false
            _isListOfFilesAllowed.value = false

        } else
            setAccessResolution(defaultResolution(request))
        _isBlocked.value = value

    }

    fun deleteRequestById(id: Long) {
        dbHelper.deleteRequestById(id)
    }

    private val _isSaveable = MediatorLiveData<Boolean>().apply {
        addSource(isBlocked) { update() }
        addSource(isReadAllowed) { update() }
        addSource(isWriteAllowed) { update() }
        addSource(isListOfFilesAllowed) { update() }
    }

    val isSaveable: LiveData<Boolean> = _isSaveable

    private fun update() {
        Log.d("DCVM", "bl:"+isBlocked.value.toString() +
                " ra:"+isReadAllowed.value.toString()+
                " wa:"+isWriteAllowed.value.toString()+
                " lf:"+isListOfFilesAllowed.value.toString()
        )
        _isSaveable.value =
            (isBlocked.value == true
                    || isReadAllowed.value == true
                    || isWriteAllowed.value == true
                    || isListOfFilesAllowed.value == true
                    || accessResolution.value == AccessResolution.ENTIRE_FOLDER
                    || accessResolution.value == AccessResolution.ENTIRE_FOLDER_INCLUDE_SUBFOLDERS
                    )
    }

    override fun toString(): String {
        return "DocumentConfirmationViewModel(" +
                "accessType=${_accessType.value}, " +
                "accessDuration=${_accessDuration.value}, " +
                "accessResolution=${_accessResolution.value}, " +
                "request=$request" +
                ")"
    }
}