package org.androidlabs.filewall.provider

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.androidlabs.filewall.R
import org.androidlabs.filewall.SFApplication
import org.androidlabs.filewall.auth.AuthManager
import org.androidlabs.filewall.auth.DeviceLockReceiver
import org.androidlabs.filewall.confirm.ConfirmationData
import org.androidlabs.filewall.confirm.ConfirmationListener
import org.androidlabs.filewall.confirm.ConfirmationNotifier
import org.androidlabs.filewall.confirm.DocumentConfirmationReceiver
import org.androidlabs.filewall.confirm.RequestData
import org.androidlabs.filewall.database.AccessLog
import org.androidlabs.filewall.database.AccessLogType
import org.androidlabs.filewall.database.Database
import org.androidlabs.filewall.database.RuleItem
import org.androidlabs.filewall.logger.Log
import org.androidlabs.filewall.provider.AccessType.DENY_ACCESS
import org.androidlabs.filewall.provider.AccessType.FULL_ACCESS
import org.androidlabs.filewall.provider.AccessType.READ_ONLY_ACCESS
import org.androidlabs.filewall.provider.AccessType.WRITE_ONLY_ACCESS
import org.androidlabs.filewall.utils.Utils
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

class FilewallProvider : DocumentsProvider(), ConfirmationListener {
    companion object {
        const val AUTHORITY = "org.androidlabs.filewall.provider"
        const val ROOT_ID = "."
        const val TAG = "FilewallProvider"
        const val DIVIDER = "/"

        val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )

        fun getParentFolderOfDocumentId(documentId: String, addDivider: Boolean = false): String {
            val components = documentId.split(DIVIDER).toMutableList()
            components.removeAt(components.lastIndex)
            var result = components.joinToString(DIVIDER)
            if (addDivider) {
                result += DIVIDER
            }
            return result
        }

        fun isRoot(documentId: String): Boolean {
            return documentId == ROOT_ID
        }

        fun getFileNameFromDocumentId(documentId: String): String {
            return documentId.split(DIVIDER).last()
        }

        fun getFilePath(documentId: String): String {
            return documentId
                .replace("$ROOT_ID$DIVIDER", "")
        }

        fun getFolderOfFile(documentId: String, isDirectory: Boolean): String {
            if (isDirectory) {
                return documentId
            }
            return documentId
                .split(DIVIDER)
                .dropLast(1)
                .joinToString(DIVIDER)
        }

        fun getBaseDir(context: Context): File {
            val baseDir = File(context.filesDir, "secured")
            if (!baseDir.exists()) {
                baseDir.mkdir()
            }
            return baseDir
        }

        /**
         * True for the ids this provider hands out: the root, or the root followed by plain
         * names. Anything else - a dot or empty segment, a missing root - is not an id of
         * ours. getFilePath strips every "./" in the string, so "...//" turns into "../":
         * a rule check on the id as written and the file lookup on the id as rewritten
         * disagreed, and the lookup could leave the vault.
         */
        fun isCanonicalDocumentId(documentId: String): Boolean {
            if (documentId == ROOT_ID) return true
            if (!documentId.startsWith("$ROOT_ID$DIVIDER")) return false
            return documentId.removePrefix("$ROOT_ID$DIVIDER").split(DIVIDER)
                .none { it.isEmpty() || it == "." || it == ".." }
        }

        /** A display name is one path segment: a client may not use it to reach another folder. */
        fun isSafeDisplayName(name: String): Boolean {
            return name.isNotEmpty() && name != "." && name != ".." &&
                !name.contains(DIVIDER) && !name.contains('\u0000')
        }

        fun getFileForDocumentId(baseDir: File, docId: String): File {
            if (!isCanonicalDocumentId(docId)) {
                throw FileNotFoundException("Invalid document id")
            }
            if (docId == ROOT_ID) {
                return baseDir
            }
            val file = File(baseDir, getFilePath(docId))
            // Nothing the rules approved may resolve outside the vault, a symlink included.
            if (!file.canonicalPath.startsWith(baseDir.canonicalPath + File.separator)) {
                throw FileNotFoundException("Document is outside the vault")
            }
            return file
        }
    }

    private lateinit var ctx: Context
    private lateinit var baseDir: File
    private var lastResult: ConfirmationData? = null
    private lateinit var dbHelper: Database

    private val providerScope = CoroutineScope(Dispatchers.IO)

    private lateinit var deviceLockReceiver: DeviceLockReceiver

    override fun onCreate(): Boolean {
        Log.d(TAG, "init")
        val context = this.context!!
        ctx = context
        baseDir = getBaseDir(context)
        dbHelper = Database(context)
        ConfirmationNotifier.registerListener(this)
        dbHelper.clearAllAuth(context)
        deviceLockReceiver = DeviceLockReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        context.registerReceiver(deviceLockReceiver, filter)
        return true
    }

    override fun shutdown() {
        super.shutdown()
        ConfirmationNotifier.unregisterListener(this)
        providerScope.cancel()
    }

    private fun resolveRootProjection(projection: Array<String>?): Array<String> {
        return projection ?: arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
            DocumentsContract.Root.COLUMN_CAPACITY_BYTES
        )
    }

    override fun queryRoots(projection: Array<String>?): Cursor {
        Log.d(TAG, "queryRoots ${projection.contentToString()}")

        val result = MatrixCursor(resolveRootProjection(projection))

        val baseDir = context?.getExternalFilesDir(null) ?: return result

        val fsp = baseDir.usableSpace
        val tsp = baseDir.totalSpace

        Log.d(
            TAG,
            "baseDir: ${baseDir.absolutePath}, exists: ${baseDir.exists()}, canRead: ${baseDir.canRead()}"
        )
        Log.d(TAG, "free space:$fsp total:$tsp")

        result.newRow().apply {
            add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
            add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_ID)
            add(
                DocumentsContract.Root.COLUMN_TITLE,
                context?.getString(R.string.app_short_name) ?: "Filewall"
            )
            add(
                DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.FLAG_SUPPORTS_CREATE or DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
            )
            add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
            add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher)

            add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, fsp)
            add(DocumentsContract.Root.COLUMN_CAPACITY_BYTES, tsp)
        }

        return result
    }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        Log.d(TAG, "queryDocument $documentId ${projection.contentToString()}")
        if (documentId == "null") {
            Log.d(TAG, "doc id null package:$callingPackage")
            throw SecurityException()
        }
        val isDirectory = getFileForDocumentId(documentId).isDirectory

        val decision = evaluateAccess(
            documentId,
            isDirectory = isDirectory,
            isCreation = false,
            mode = AccessMode.LIST_OF_FILES.title
        )
        if (decision != AccessDecision.ALLOWED) {
            // The picker asks about the folder before it asks for its children, so a
            // throw here is what produced "Can't load content at the moment" on the very
            // first visit. While a request for the root is waiting to be answered,
            // describe the root so the picker can render it and say what it is waiting
            // for. queryRoots already publishes the root, so this tells the caller
            // nothing new; anything below the root still fails closed.
            if (decision == AccessDecision.PENDING && isRoot(documentId)) {
                val pending = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
                pending.newRow().includeFile(getFileForDocumentId(documentId))
                pending.extras = pendingApprovalExtras()
                return pending
            }
            throw SecurityException()
        }

        val file = getFileForDocumentId(documentId)
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        result.newRow().includeFile(file)

        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?
    ): Cursor {
        Log.d(
            TAG,
            "queryChildDocuments $parentDocumentId ${projection.contentToString()} $sortOrder"
        )

        val decision = evaluateAccess(
            parentDocumentId,
            isDirectory = true,
            isCreation = false,
            mode = AccessMode.LIST_OF_FILES.title
        )
        if (decision == AccessDecision.DENIED) {
            throw SecurityException()
        }

        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        result.setNotificationUri(context!!.contentResolver, getChildDocumentsUri(parentDocumentId))

        if (decision == AccessDecision.PENDING) {
            // A request has just been raised and nobody has answered it yet. Throwing here
            // made the picker say "Can't load content at the moment", which reads as a
            // broken provider. An empty cursor marked as loading, with a line explaining
            // what it is waiting for, says what is actually happening; onConfirmation
            // notifies this uri, so the listing fills in as soon as the user decides.
            result.extras = pendingApprovalExtras()
            return result
        }

        val parent = getFileForDocumentId(parentDocumentId)
        parent.listFiles()?.forEach { file ->
            result.newRow().includeFile(file)
        }
        return result
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        if (!isCanonicalDocumentId(parentDocumentId) || !isCanonicalDocumentId(documentId)) {
            return false
        }
        var parentPath = parentDocumentId
        if (!parentPath.endsWith(DIVIDER)) {
            parentPath += DIVIDER
        }

        val isChild = documentId.startsWith(parentPath)

        Log.d(TAG, "isChildDocument $parentDocumentId $documentId -> $isChild")

        return isChild
    }

    override fun findDocumentPath(
        parentDocumentId: String?,
        childDocumentId: String
    ): DocumentsContract.Path {
        Log.d(TAG, "findDocumentPath $parentDocumentId $childDocumentId")

        var source: File = baseDir
        if (parentDocumentId != null) source = getFileForDocumentId(parentDocumentId)
        var destination: File? = getFileForDocumentId(childDocumentId)
        val pathIds: MutableList<String?> = ArrayList()
        while (source != destination && destination != null) {
            pathIds.add(getDocumentIdForFile(destination))
            destination = destination.parentFile
        }
        pathIds.add(getDocumentIdForFile(source))
        pathIds.reverse()

        Log.d(TAG, pathIds.toString())

        return DocumentsContract.Path(getDocumentIdForFile(source), pathIds)
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: android.os.CancellationSignal?
    ): ParcelFileDescriptor? {
        Log.d(TAG, "openDocument $documentId, $mode, $signal")

        val open = OpenMode.parse(mode)

        if (!validateAccess(documentId, reason = open.reason, mode = open.accessMode)) {
            return null
        }

        val file = getFileForDocumentId(documentId)
        // The rule that answered decides what the descriptor may do; what the client asked
        // for decides how. With protection switched off no rule answered at all, and
        // lastResult still holds whatever an earlier call left there.
        val isProtectionDisabled =
            (context?.applicationContext as? SFApplication)?.isProtectionDisabled?.value == true
        val granted = if (isProtectionDisabled) FULL_ACCESS else lastResult?.accessType ?: DENY_ACCESS
        val flags = open.flagsUnder(granted) ?: return null
        return ParcelFileDescriptor.open(file, flags)
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String? {
        Log.d(TAG, "createDocument $displayName ($mimeType) in $parentDocumentId")

        if (!isSafeDisplayName(displayName)) {
            throw FileNotFoundException("Invalid display name")
        }
        val parent = getFileForDocumentId(parentDocumentId)
        val newFile = parent.resolveWithoutConflict(displayName)
        val newDocumentId = getDocumentIdForFile(newFile)
        val isDirectory = DocumentsContract.Document.MIME_TYPE_DIR == mimeType

        if (!validateAccess(
                newDocumentId,
                isDirectory,
                true,
                AccessReason.CREATE,
                AccessMode.WRITE_ONLY.title
            )
        ) {
            return null
        }

        try {
            if (isDirectory) {
                if (!newFile.mkdir())
                    throw FileNotFoundException("Failed to create new directory")
            } else {
                newFile.parentFile?.let {
                    if (!it.exists()) {
                        if (!it.mkdirs()) {
                            throw FileNotFoundException("Failed to create parent directories")
                        }
                    }
                }
                if (!newFile.createNewFile())
                    throw FileNotFoundException("Failed to create new document")
            }
            newFile.setReadable(true)
            newFile.setWritable(true)
            notifyChildDocumentsChange(parentDocumentId)
            return getDocumentIdForFile(newFile)
        } catch (e: IOException) {
            Log.e(TAG, "createDocument failed $e")
            throw FileNotFoundException(
                "Failed to create document with name $displayName in $parentDocumentId        "
            )
        }
    }

    override fun deleteDocument(documentId: String) {
        Log.d(TAG, "deleteDocument $documentId")

        if (!validateAccess(documentId, AccessReason.DELETE, AccessMode.WRITE_ONLY.title)) {
            return
        }

        val parentDocumentId = getParentDocumentId(documentId)
        val file = getFileForDocumentId(documentId)
        if (!file.deleteRecursively()) {
            throw FileNotFoundException("Failed to delete document")
        }
        notifyChildDocumentsChange(parentDocumentId)
    }

    override fun renameDocument(documentId: String, newDisplayName: String): String? {
        Log.d(TAG, "renameDocument $documentId to $newDisplayName")

        if (!validateAccess(documentId, AccessReason.RENAME, AccessMode.WRITE_ONLY.title)) {
            return null
        }

        if (!isSafeDisplayName(newDisplayName)) {
            throw FileNotFoundException("Invalid display name")
        }
        val parentDocumentId = getParentDocumentId(documentId)
        val file = getFileForDocumentId(documentId)
        val newFile = File(file.parent, newDisplayName)
        if (!file.renameTo(newFile)) {
            throw FileNotFoundException("Failed to rename document")
        }
        notifyChildDocumentsChange(parentDocumentId)
        return getDocumentIdForFile(newFile)
    }

    override fun copyDocument(sourceDocumentId: String, targetParentDocumentId: String): String {
        Log.d(TAG, "copyDocument $sourceDocumentId to $targetParentDocumentId")

        if (isSubDirectory(sourceDocumentId, targetParentDocumentId)) {
            throw FileNotFoundException("Copying to a subdirectory is not allowed")
        }

        if (!validateAccess(sourceDocumentId, AccessReason.COPY)) {
            throw SecurityException()
        }

        val parent = getFileForDocumentId(targetParentDocumentId)
        val sourceFile = getFileForDocumentId(sourceDocumentId)
        val targetFile = parent.resolveWithoutConflict(sourceFile.name)
        val isDirectory = sourceFile.isDirectory
        val targetDocumentId = getDocumentIdForFile(targetFile)

        if (!validateAccess(
                targetDocumentId,
                isDirectory,
                true,
                AccessReason.COPY,
                AccessMode.WRITE_ONLY.title
            )
        ) {
            throw SecurityException()
        }

        try {
            if (isDirectory) {
                copyDirectory(sourceFile, targetFile)
            } else {
                copyFile(sourceFile, targetFile)
            }

        } catch (e: IOException) {
            throw FileNotFoundException("Couldn't copy document '$sourceDocumentId': ${e.message}")
        }

        notifyChildDocumentsChange(targetParentDocumentId)

        return getDocumentIdForFile(targetFile)
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String
    ): String {
        Log.d(
            TAG,
            "moveDocument $sourceDocumentId from $sourceParentDocumentId to $targetParentDocumentId"
        )

        if (isSubDirectory(sourceDocumentId, targetParentDocumentId)) {
            throw FileNotFoundException("Copying to a subdirectory is not allowed")
        }

        if (!validateAccess(sourceDocumentId, AccessReason.MOVE)) {
            throw SecurityException()
        }

        val parent = getFileForDocumentId(targetParentDocumentId)
        val sourceFile = getFileForDocumentId(sourceDocumentId)
        val targetFile = parent.resolveWithoutConflict(sourceFile.name)

        if (!sourceFile.renameTo(targetFile)) {
            throw FileNotFoundException("Failed to move document")
        }

        val isDirectory = sourceFile.isDirectory

        val targetDocumentId = getDocumentIdForFile(targetFile)

        if (!validateAccess(
                targetDocumentId,
                isDirectory,
                true,
                AccessReason.MOVE,
                AccessMode.WRITE_ONLY.title
            )
        ) {
            throw SecurityException()
        }

        notifyChildDocumentsChange(sourceParentDocumentId)
        notifyChildDocumentsChange(targetParentDocumentId)

        return getDocumentIdForFile(targetFile)
    }

    private fun MatrixCursor.RowBuilder.includeFile(file: File) {
        var flags = DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                DocumentsContract.Document.FLAG_SUPPORTS_RENAME or
                DocumentsContract.Document.FLAG_SUPPORTS_COPY or
                DocumentsContract.Document.FLAG_SUPPORTS_MOVE
        flags = if (file.isDirectory) {
            flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
        } else {
            flags or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
        }

        add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, getDocumentIdForFile(file))
        add(DocumentsContract.Document.COLUMN_MIME_TYPE, getMimeType(file))
        add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name)
        add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
        add(DocumentsContract.Document.COLUMN_FLAGS, flags)
        add(DocumentsContract.Document.COLUMN_SIZE, file.length())
    }

    private fun validateAccess(
        documentId: String,
        reason: AccessReason = AccessReason.READ,
        mode: String = AccessMode.READ_ONLY.title
    ): Boolean {
        val isDirectory = getFileForDocumentId(documentId).isDirectory
        return validateAccess(documentId, isDirectory, isCreation = false, reason, mode)
    }

    private fun validateAccess(
        documentId: String,
        isDirectory: Boolean,
        isCreation: Boolean,
        reason: AccessReason = AccessReason.READ,
        mode: String = AccessMode.READ_ONLY.title
    ): Boolean {
        return evaluateAccess(documentId, isDirectory, isCreation, reason, mode) ==
            AccessDecision.ALLOWED
    }

    /**
     * Same check as [validateAccess], but it also says whether a refusal is final or
     * whether a request has just been raised and is waiting for the user.
     */
    private fun evaluateAccess(
        documentId: String,
        isDirectory: Boolean,
        isCreation: Boolean,
        reason: AccessReason = AccessReason.READ,
        mode: String = AccessMode.READ_ONLY.title
    ): AccessDecision {
        Log.d(TAG, "validateAccess $documentId, $isDirectory, $mode, $callingPackage")
        if (documentId == "null") {
            Log.d(TAG, "doc id null package:$callingPackage")
            return AccessDecision.DENIED
        }
        // The rules, the log and the file lookup must all see the same path, so an id that
        // is not in the form this provider issues is refused before any of them runs.
        if (!isCanonicalDocumentId(documentId)) {
            Log.w(TAG, "refused a malformed document id from $callingPackage")
            return AccessDecision.DENIED
        }
        val appName = Utils.getAppName(context!!, callingPackage!!)

        val action = context!!.getString(R.string.access_action, reason.title)
        val purpose = context!!.getString(R.string.access_request, appName, action, documentId)

        recordAccessLog(
            AccessLog(
                null,
                documentId,
                isDirectory,
                callingPackage!!,
                reason,
                AccessLogType.REQUEST,
                null,
                null,
                System.currentTimeMillis()
            )
        )

        context?.let { ctx ->
            val app = ctx as SFApplication
            val isDisabled = app.isProtectionDisabled.value
            if (isDisabled) {
                Log.d(TAG,"access granted because of disabled protection")
                // Without this the entry is only the REQUEST row written above, which is
                // indistinguishable from a request nobody has answered.
                recordAccessLog(
                    AccessLog(
                        null,
                        documentId,
                        isDirectory,
                        callingPackage!!,
                        reason,
                        AccessLogType.PROTECTION_DISABLED,
                        null,
                        null,
                        System.currentTimeMillis()
                    )
                )
                return AccessDecision.ALLOWED
            }
        }

        try {
            validateSecurity(documentId, purpose)
        } catch (exception: SecurityException) {
            // Authentication is pending, and the user has just been asked for it. That
            // does not refuse the access: the rules below still decide, and they record
            // the outcome. Writing DENIED here put a denial in the log next to an access
            // that was then served, and a second one next to a real refusal.
            Log.d(TAG, "authentication pending for $documentId: ${exception.message}")
        }

        val dbValidationResult =
            dbHelper.validateAccess(documentId, isDirectory, isCreation, mode, callingPackage!!)

        fun handleResult(): Boolean {
            // The outcome row used to be written before the decision and always as RULE,
            // so a refusal was recorded as though the rule had granted it. It also
            // recorded the rule's own path rather than the path that was touched, and it
            // was written even when no rule matched this call, attributing the access to
            // whichever rule happened to answer the previous one.
            fun logResult(isAllowed: Boolean): Boolean {
                Log.d(
                    TAG,
                    "validateAccess result for $documentId, $mode, $callingPackage is $isAllowed"
                )
                dbValidationResult?.let {
                    recordAccessLog(
                        AccessLog(
                            null,
                            documentId,
                            isDirectory,
                            callingPackage!!,
                            reason,
                            if (isAllowed) AccessLogType.RULE else AccessLogType.DENIED,
                            ruleId = it.ruleId!!,
                            null,
                            System.currentTimeMillis()
                        )
                    )
                }
                return isAllowed
            }

            if (dbValidationResult == null) {
                return logResult(false)
            }
            if (mode == AccessMode.LIST_OF_FILES.title) {
                return logResult(lastResult?.isListOfFilesAllowed == true)
            }

            val accessType: AccessType = lastResult?.accessType ?: DENY_ACCESS
            if (accessType == DENY_ACCESS) {
                return logResult(false)
            }

            if (accessType == FULL_ACCESS) {
                return logResult(true)
            }

            if (mode == AccessMode.WRITE_ONLY.title && accessType === READ_ONLY_ACCESS) {
                return logResult(false)
            }

            if (mode == AccessMode.READ_ONLY.title && accessType === WRITE_ONLY_ACCESS) {
                return logResult(false)
            }

            return logResult(true)
        }

        dbValidationResult?.let {
            lastResult = it
            if (handleResult()) {
                return AccessDecision.ALLOWED
            }
            // A rule matched but does not cover this operation - a read under a
            // write-only rule, say. That used to fail in silence: no request, no
            // notification, nothing in the Requests tab, and no way to grant the missing
            // permission short of editing the rule by hand. Ask, unless the rule is a
            // Block rule, which says "Block All Access and Requests" and means it.
            if (it.accessType != DENY_ACCESS) {
                raiseRequest(documentId, isDirectory, isCreation, mode, action)
            }
            return AccessDecision.DENIED
        }

        raiseRequest(documentId, isDirectory, isCreation, mode, action)

        // No rule answered this one, so there is no outcome to record yet - only the
        // REQUEST row written at the top of this function.
        Log.d(TAG, "wait confirmation end $documentId")
        return AccessDecision.PENDING
    }

    private enum class AccessDecision { ALLOWED, DENIED, PENDING }

    /**
     * Records an access log entry, unless the user has switched access logging off.
     *
     * The setting was read only by the Settings screen: turning the toggle off deleted the
     * history and then carried on recording, which is not what it says it does. It is also
     * four inserts per created document on the hot path of a bulk copy.
     */
    private fun recordAccessLog(log: AccessLog) {
        val app = context?.applicationContext as? SFApplication ?: return
        if (!app.isAccessLogEnabled) {
            return
        }
        dbHelper.insertAccessLog(log)
    }

    /** Puts the access in front of the user. Duplicate requests collapse in the table. */
    private fun raiseRequest(
        documentId: String,
        isDirectory: Boolean,
        isCreation: Boolean,
        mode: String,
        action: String
    ) {
        val request =
            RequestData(0, documentId, isDirectory, mode, callingPackage!!, isCreation, action)

        Utils.sendBroadcast(
            context = context,
            receiverClass = DocumentConfirmationReceiver::class.java
        ) {
            putExtra("request", request)
        }
    }

    /** Marks a cursor as "the answer is not in yet, and here is why". */
    private fun pendingApprovalExtras(): Bundle = Bundle().apply {
        putBoolean(DocumentsContract.EXTRA_LOADING, true)
        putString(
            DocumentsContract.EXTRA_INFO,
            context!!.getString(R.string.provider_waiting_for_approval)
        )
    }

    override fun onConfirmation(data: ConfirmationData) {
        Log.d(TAG, "onConfirmation - $data")

        val date = System.currentTimeMillis()

        val newRule = RuleItem(
            null,
            data.request.documentId,
            data.request.isDirectory,
            data.request.callingPackage,
            data.accessType,
            data.accessDuration,
            data.accessResolution,
            data.isListOfFilesAllowed,
            date,
            // insertRule appends; this is only what upsertRule carries when it updates.
            0L
        )
        // Deciding the same request twice used to leave two rules behind.
        val ruleId = dbHelper.upsertRule(newRule)
        dbHelper.deleteRequestById(data.request.id)
        lastResult = data

        lastResult?.ruleId = ruleId

        Log.d(TAG, "onConfirmation NOTIFY document:" + data.request.documentId)
        notifyChildDocumentsChange(data.request.documentId)
    }

    private fun validateSecurity(documentId: String, purpose: String) {
        Log.d(TAG, "validateSecurity $documentId $callingPackage")

        return AuthManager.instance.validateSecurity(
            context = context!!,
            dbHelper = dbHelper,
            purpose = purpose,
            documentId = documentId,
            callingPackage = callingPackage!!
        )
    }

    private fun File.resolveWithoutConflict(name: String): File {
        var file = resolve(name)
        if (file.exists()) {
            var noConflictId = 1
            while (file.exists()) {
                val extension = name.substringAfterLast('.')
                val baseName = name.substringBeforeLast('.')
                var fileName = "$baseName (${noConflictId++})"
                if (baseName != extension) {
                    fileName += ".$extension"
                }
                file = resolve(fileName)
            }
        }
        return file
    }

    private fun isSubDirectory(sourceDocumentId: String, targetParentDocumentId: String): Boolean {
        return targetParentDocumentId.startsWith(sourceDocumentId)
    }

    private fun copyFile(sourceFile: File, targetFile: File) {
        FileInputStream(sourceFile).use { inStream ->
            FileOutputStream(targetFile).use { outStream ->
                inStream.copyTo(outStream)
            }
        }
    }

    private fun copyDirectory(sourceDir: File, targetDir: File) {
        if (!sourceDir.exists()) {
            throw IllegalArgumentException("Source directory does not exist: ${sourceDir.path}")
        }
        if (!sourceDir.isDirectory) {
            throw IllegalArgumentException("Source is not a directory: ${sourceDir.path}")
        }
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        sourceDir.listFiles()?.forEach { file ->
            val targetFile = File(targetDir, file.name)
            if (file.isDirectory) {
                copyDirectory(file, targetFile)
            } else {
                copyFile(file, targetFile)
            }
        }
    }

    private fun getMimeType(file: File): String {
        return if (file.isDirectory) {
            DocumentsContract.Document.MIME_TYPE_DIR
        } else {
            val extension = file.extension
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: "application/octet-stream"
        }
    }

    private fun getChildDocumentsUri(parentDocumentId: String?): Uri {
        return DocumentsContract.buildChildDocumentsUri(AUTHORITY, parentDocumentId)
    }

    private fun notifyChildDocumentsChange(parentDocumentId: String?) {
        val childDocumentsUri = getChildDocumentsUri(parentDocumentId)
        Log.d(TAG, "notifyChildDocumentsChange $parentDocumentId")
        context!!.contentResolver.notifyChange(childDocumentsUri, null)
    }

    private fun getFileForDocumentId(docId: String): File {
        return getFileForDocumentId(baseDir, docId)
    }

    private fun getParentDocumentId(documentId: String): String {
        return documentId.split(DIVIDER).dropLast(1).joinToString(DIVIDER)
    }

    private fun getDocumentIdForFile(file: File): String {
        val id = ROOT_ID + file.absolutePath.replace(baseDir.absolutePath, "").replace("/", DIVIDER)
        return id
    }
}