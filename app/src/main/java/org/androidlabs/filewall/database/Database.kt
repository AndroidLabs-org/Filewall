package org.androidlabs.filewall.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.SystemClock
import org.androidlabs.filewall.logger.Log
import androidx.core.database.sqlite.transaction
import org.androidlabs.filewall.provider.AccessDuration
import org.androidlabs.filewall.confirm.ConfirmationData
import org.androidlabs.filewall.confirm.RequestData
import org.androidlabs.filewall.provider.AccessMode
import org.androidlabs.filewall.provider.AccessReason
import org.androidlabs.filewall.provider.AccessResolution
import org.androidlabs.filewall.provider.AccessType
import org.androidlabs.filewall.provider.FilewallProvider

class Database(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private companion object {
        const val DATABASE_NAME = "database.db"
        /**
         * 1 -> 2: access_logs loses the foreign key on rule_id. See CREATE_ACCESS.
         *
         * Bump this together with a step in onUpgrade. There was no upgrade path at all
         * before version 2 - onUpgrade threw - so the first bump would have crashed every
         * install that already had data.
         */
        const val DATABASE_VERSION = 2

        const val TABLE_RULES = "rules"

        const val RULES_COLUMN_ID = "id"
        const val RULES_COLUMN_FILE = "file"
        const val RULES_COLUMN_IS_DIRECTORY = "is_directory"
        const val RULES_COLUMN_PKG = "package"
        const val RULES_COLUMN_ACCESS_TYPE = "access_type"
        const val RULES_COLUMN_ACCESS_DURATION = "access_duration"
        const val RULES_COLUMN_ACCESS_RESOLUTION = "access_resolution"
        const val RULES_COLUMN_IS_LIST_OF_FILES_ALLOWED = "is_list_of_files_allowed"
        const val RULES_COLUMN_CREATED_AT = "created_at"
        const val RULES_COLUMN_ORDER_POSITION = "order_position"
        const val RULES_COLUMN_DELETED_AT = "deleted_at"

        const val TABLE_REQUESTS = "requests"
        const val REQUESTS_COLUMN_ID = "id"
        const val REQUESTS_COLUMN_DOCUMENT_ID = "document_id"
        const val REQUESTS_COLUMN_IS_DIRECTORY = "is_directory"
        const val REQUESTS_COLUMN_MODE = "mode"
        const val REQUESTS_COLUMN_CALLING_PACKAGE = "calling_package"
        const val REQUESTS_COLUMN_IS_CREATION = "is_creation"
        const val REQUESTS_COLUMN_ACTION = "action"

        const val TABLE_AUTH = "authorization"

        const val AUTH_COLUMN_ID = "id"
        const val AUTH_COLUMN_PKG = "package"
        const val AUTH_COLUMN_TYPE = "type"
        const val AUTH_COLUMN_CREATED_AT = "created_at"

        const val TABLE_ACCESS = "access_logs"

        const val ACCESS_COLUMN_ID = "id"
        const val ACCESS_COLUMN_FILE = "file"
        const val ACCESS_COLUMN_IS_DIRECTORY = "is_directory"
        const val ACCESS_COLUMN_PKG = "package"
        const val ACCESS_REASON = "reason"
        const val ACCESS_TYPE = "type"
        const val ACCESS_RULE_ID = "rule_id"
        const val ACCESS_COLUMN_CREATED_AT = "created_at"

        const val CREATE_RULES = """
            CREATE TABLE $TABLE_RULES (
                $RULES_COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $RULES_COLUMN_FILE TEXT NOT NULL,
                $RULES_COLUMN_IS_DIRECTORY TEXT NOT NULL,
                $RULES_COLUMN_PKG TEXT NOT NULL,
                $RULES_COLUMN_ACCESS_TYPE TEXT NOT NULL,
                $RULES_COLUMN_ACCESS_DURATION TEXT NOT NULL,
                $RULES_COLUMN_ACCESS_RESOLUTION TEXT NOT NULL,
                $RULES_COLUMN_IS_LIST_OF_FILES_ALLOWED TEXT NOT NULL,
                $RULES_COLUMN_CREATED_AT INTEGER NOT NULL,
                $RULES_COLUMN_ORDER_POSITION INTEGER NOT NULL,
                $RULES_COLUMN_DELETED_AT INTEGER DEFAULT NULL
            )
        """

        const val CREATE_REQUESTS = """
            CREATE TABLE $TABLE_REQUESTS (
                $REQUESTS_COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,                 
                $REQUESTS_COLUMN_DOCUMENT_ID TEXT NOT NULL,
                $REQUESTS_COLUMN_IS_DIRECTORY TEXT NOT NULL,
                $REQUESTS_COLUMN_CALLING_PACKAGE TEXT NOT NULL,
                $REQUESTS_COLUMN_MODE TEXT NOT NULL,
                $REQUESTS_COLUMN_IS_CREATION TEXT NOT NULL,
                $REQUESTS_COLUMN_ACTION TEXT NOT NULL,
                UNIQUE($REQUESTS_COLUMN_DOCUMENT_ID, $REQUESTS_COLUMN_IS_DIRECTORY, $REQUESTS_COLUMN_MODE, $REQUESTS_COLUMN_CALLING_PACKAGE, $REQUESTS_COLUMN_IS_CREATION, $REQUESTS_COLUMN_ACTION)
            )
        """

        const val CREATE_AUTH = """
            CREATE TABLE $TABLE_AUTH (
                $AUTH_COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $AUTH_COLUMN_PKG TEXT NOT NULL,
                $AUTH_COLUMN_TYPE TEXT NOT NULL,
                $AUTH_COLUMN_CREATED_AT INTEGER NOT NULL
            )
        """

        // No foreign key on $ACCESS_RULE_ID. It used to carry ON DELETE CASCADE, which
        // would erase an access log entry when the rule it names is deleted - the wrong
        // default for an audit trail, and the opposite of what the app actually does,
        // since SQLite leaves foreign keys off and nothing here turns them on. The rows
        // outlive the rule on purpose; $ACCESS_RULE_ID is a reference to what decided the
        // access, not a dependency on it.
        const val CREATE_ACCESS = """
            CREATE TABLE $TABLE_ACCESS (
                $ACCESS_COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $ACCESS_COLUMN_FILE TEXT NOT NULL,
                $ACCESS_COLUMN_IS_DIRECTORY TEXT NOT NULL,
                $ACCESS_COLUMN_PKG TEXT NOT NULL,
                $ACCESS_REASON TEXT NOT NULL,
                $ACCESS_TYPE TEXT NOT NULL,
                $ACCESS_RULE_ID INTEGER DEFAULT NULL,
                $ACCESS_COLUMN_CREATED_AT INTEGER NOT NULL
            )
        """

        const val RULES_WHERE_MATCH = "$RULES_COLUMN_PKG = ? AND $RULES_COLUMN_DELETED_AT IS NULL"

        const val AUTH_WHERE_MATCH_PKG = "$AUTH_COLUMN_PKG = ?"
        const val AUTH_WHERE_MATCH = "$AUTH_COLUMN_PKG = ? AND $AUTH_COLUMN_TYPE = ?"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_RULES)
        db.execSQL(CREATE_AUTH)
        db.execSQL(CREATE_ACCESS)
        db.execSQL(CREATE_REQUESTS)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d("Database", "onUpgrade $oldVersion -> $newVersion")
        if (oldVersion < 2) {
            dropAccessLogForeignKey(db)
        }
    }

    /**
     * Rebuilds access_logs without the foreign key on rule_id, carrying every row over.
     *
     * SQLite cannot drop a constraint, so the table is recreated and copied. The
     * constraint declared ON DELETE CASCADE, which would erase an access log entry when
     * the rule it names is deleted - the wrong default for an audit trail, and the
     * opposite of what the app does, since foreign keys are off and nothing turns them
     * on. Fresh installs have been created without it since; this brings the rest into
     * line. SQLiteOpenHelper already runs this inside a transaction.
     */
    private fun dropAccessLogForeignKey(db: SQLiteDatabase) {
        val columns = listOf(
            ACCESS_COLUMN_ID,
            ACCESS_COLUMN_FILE,
            ACCESS_COLUMN_IS_DIRECTORY,
            ACCESS_COLUMN_PKG,
            ACCESS_REASON,
            ACCESS_TYPE,
            ACCESS_RULE_ID,
            ACCESS_COLUMN_CREATED_AT
        ).joinToString(", ")

        db.execSQL("ALTER TABLE $TABLE_ACCESS RENAME TO ${TABLE_ACCESS}_old")
        db.execSQL(CREATE_ACCESS)
        db.execSQL(
            "INSERT INTO $TABLE_ACCESS ($columns) SELECT $columns FROM ${TABLE_ACCESS}_old"
        )
        db.execSQL("DROP TABLE ${TABLE_ACCESS}_old")
    }

    /**
     * The id of the live rule that already covers this app, path and resolution, if any.
     * Two rules that differ only in their permissions overlap rather than replace each
     * other, and the effective outcome of a set of overlapping rules is not readable from
     * any one of them, so a second one is never what the user meant.
     */
    fun findOverlappingRuleId(rule: RuleItem): Long? {
        val db = readableDatabase
        val selection = "$RULES_COLUMN_FILE = ? AND $RULES_COLUMN_IS_DIRECTORY = ? AND " +
                "$RULES_COLUMN_PKG = ? AND $RULES_COLUMN_ACCESS_RESOLUTION = ? AND " +
                "$RULES_COLUMN_DELETED_AT IS NULL"

        val cursor = db.query(
            TABLE_RULES,
            arrayOf(RULES_COLUMN_ID),
            selection,
            arrayOf(
                rule.file,
                rule.isDirectory.toString(),
                rule.pkg,
                rule.accessResolution.name
            ),
            null, null, "$RULES_COLUMN_CREATED_AT ASC", "1"
        )
        val id = if (cursor.moveToFirst()) {
            cursor.getLong(cursor.getColumnIndexOrThrow(RULES_COLUMN_ID))
        } else {
            null
        }
        cursor.close()
        return id
    }

    /**
     * The live rule a decision on this app, path and resolution would replace, if any.
     * Same match [upsertRule] uses, so this is what a confirmation is about to overwrite.
     */
    fun findOverlappingRule(
        pkg: String,
        file: String,
        isDirectory: Boolean,
        accessResolution: AccessResolution
    ): RuleItem? = getAllRules().firstOrNull {
        it.pkg == pkg &&
            it.file == file &&
            it.isDirectory == isDirectory &&
            it.accessResolution == accessResolution
    }

    /**
     * Writes [rule], replacing the one that already covers the same app, path and
     * resolution instead of adding a second one beside it. Returns the rule's id.
     */
    fun upsertRule(rule: RuleItem): Long {
        val existingId = findOverlappingRuleId(rule)
            ?: return insertRule(rule)

        updateRule(rule.copy(id = existingId))
        return existingId
    }

    fun isRuleExists(rule: RuleItem): Boolean {
        val db = readableDatabase
        val selection = "$RULES_COLUMN_FILE = ? AND $RULES_COLUMN_IS_DIRECTORY = ? AND " +
                "$RULES_COLUMN_PKG = ? AND $RULES_COLUMN_ACCESS_TYPE = ? AND " +
                "$RULES_COLUMN_ACCESS_RESOLUTION = ? AND $RULES_COLUMN_IS_LIST_OF_FILES_ALLOWED = ? AND $RULES_COLUMN_DELETED_AT IS NULL"

        val selectionArgs = arrayOf(
            rule.file,
            rule.isDirectory.toString(),
            rule.pkg,
            rule.accessType.name,
            rule.accessResolution.name,
            rule.isListOfFilesAllowed.toString()
        )

        val cursor = db.query(
            TABLE_RULES,
            arrayOf("id"),
            selection,
            selectionArgs,
            null,
            null,
            null
        )

        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    /**
     * One past the last rule, so a new one goes to the bottom of the list.
     *
     * order_position decides which rule answers an access - validateAccess sorts by it and
     * takes the first match - so the column has to mean one thing. It did not: the manual
     * Add rule form passed the current rule count while the confirmation dialog passed
     * System.currentTimeMillis(), which put every request-approved rule behind every
     * hand-made one no matter what order the user had put them in. The count was also not
     * safe on its own: delete a rule from the middle and the next insert collides with a
     * position already in use.
     */
    private fun nextOrderPosition(): Long {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT MAX($RULES_COLUMN_ORDER_POSITION) FROM $TABLE_RULES " +
                "WHERE $RULES_COLUMN_DELETED_AT IS NULL",
            null
        )
        val next = if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) + 1 else 0L
        cursor.close()
        return next
    }

    fun insertRule(rule: RuleItem): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(RULES_COLUMN_FILE, rule.file)
            put(RULES_COLUMN_IS_DIRECTORY, rule.isDirectory.toString())
            put(RULES_COLUMN_PKG, rule.pkg)
            put(RULES_COLUMN_ACCESS_TYPE, rule.accessType.name)
            put(RULES_COLUMN_ACCESS_DURATION, rule.accessDuration.name)
            put(RULES_COLUMN_ACCESS_RESOLUTION, rule.accessResolution.name)
            put(RULES_COLUMN_IS_LIST_OF_FILES_ALLOWED, rule.isListOfFilesAllowed.toString())
            put(RULES_COLUMN_CREATED_AT, rule.createdAt)
            put(RULES_COLUMN_ORDER_POSITION, nextOrderPosition())
        }
        return db.insert(TABLE_RULES, null, values)
    }

    fun getRulesCount(): Long {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_RULES WHERE $RULES_COLUMN_DELETED_AT IS NULL",
            null
        )

        var count = 0L
        if (cursor.moveToFirst()) {
            count = cursor.getLong(0)
        }
        cursor.close()
        return count
    }

    fun getAllRules(): List<RuleItem> {
        return getRulesWithSkippedDurations(arrayOf())//arrayOf(AccessDuration.ONCE.name))
    }

    fun getAllRequests(): List<RequestData> {
        return getRequests()
    }

    fun clearExpiredRules() {
        val requestItems = getRulesWithSkippedDurations(arrayOf(AccessDuration.FOREVER.name))
        val currentDate = System.currentTimeMillis()

        val rules = requestItems.filter {
            val expirationDate = it.expirationDate()
            expirationDate != null && expirationDate <= currentDate
        }
        if (rules.isEmpty()) {
            return
        }
        val ids = rules.map { it.id!! }
        deleteItemsByIds(TABLE_RULES, ids)
    }

    fun updateRulesOrderPositions(ruleUpdates: List<Pair<Long, Long>>) {
        val db = writableDatabase

        db.transaction {
            try {
                for ((ruleId, newOrderPosition) in ruleUpdates) {
                    val values = ContentValues().apply {
                        put(RULES_COLUMN_ORDER_POSITION, newOrderPosition)
                    }

                    update(
                        TABLE_RULES,
                        values,
                        "$RULES_COLUMN_ID = ?",
                        arrayOf(ruleId.toString())
                    )
                }
            } finally {
            }
        }
    }

    /**
     * Marks a rule deleted rather than removing the row.
     *
     * The schema was built for this: getAllAccessLogs keeps an entry when its rule is
     * live *or* when the entry predates the rule's deletion, which only means something
     * if the row is still there to read. Hard-deleting it left every log entry the rule
     * had decided pointing at nothing, so the audit trail could say an access happened
     * but not what had been allowed at the time.
     *
     * Expiry still removes rows outright - an expired rule was never a decision anyone
     * took back - and clearing the access log purges the soft-deleted ones with it.
     */
    fun deleteRuleById(id: Long) {
        softDeleteRules(listOf(id))
    }

    private fun softDeleteRules(ids: List<Long>) {
        if (ids.isEmpty()) {
            return
        }
        val db = writableDatabase
        val values = ContentValues().apply {
            put(RULES_COLUMN_DELETED_AT, System.currentTimeMillis())
        }
        ids.chunked(500).forEach { chunk ->
            db.update(
                TABLE_RULES,
                values,
                "$RULES_COLUMN_ID ${generateInClause(chunk.size)}",
                chunk.map { it.toString() }.toTypedArray()
            )
        }
    }

    /** Chunked: a bulk copy can leave more pending requests than SQLite takes parameters. */
    fun deleteRequestsByIds(ids: List<Long>) {
        ids.chunked(500).forEach { deleteItemsByIds(TABLE_REQUESTS, it) }
    }

    fun deleteRequestById(id: Long) {
        val db = writableDatabase
        val whereClause = "$REQUESTS_COLUMN_ID = ?"
        val whereArgs = arrayOf(id.toString())
        db.delete(TABLE_REQUESTS, whereClause, whereArgs)
    }

    fun insertAuth(item: AuthItem) {
        val db = writableDatabase

        val whereValues = arrayOf(item.pkg, item.type.name)

        val cursor = db.query(
            TABLE_AUTH,
            arrayOf(AUTH_COLUMN_PKG),
            AUTH_WHERE_MATCH,
            whereValues,
            null,
            null,
            null
        )

        if (cursor.moveToFirst()) {
            val updateValues = ContentValues().apply {
                put(AUTH_COLUMN_CREATED_AT, item.createdAt)
            }
            db.update(
                TABLE_AUTH,
                updateValues,
                AUTH_WHERE_MATCH,
                whereValues
            ).toLong()
        } else {
            val values = ContentValues().apply {
                put(AUTH_COLUMN_PKG, item.pkg)
                put(AUTH_COLUMN_TYPE, item.type.name)
                put(AUTH_COLUMN_CREATED_AT, item.createdAt)
            }
            db.insert(TABLE_AUTH, null, values)
        }.also {
            cursor.close()
        }
    }

    fun clearAllAuth(context: Context) {

        val types = arrayOf(AuthType.BIOMETRIC.name, AuthType.PIN.name)

        val db = writableDatabase
        val cursor: Cursor = db.query(
            TABLE_AUTH,
            arrayOf(
                AUTH_COLUMN_ID,
                AUTH_COLUMN_PKG,
                AUTH_COLUMN_TYPE,
                AUTH_COLUMN_CREATED_AT
            ),
            "$AUTH_COLUMN_TYPE ${generateInClause(types.size)}",
            types,
            null, null, null
        )

        val items = getAuthWithCursor(cursor)

        if (items.isEmpty()) {
            return
        }

        val ids = items.map { it.id!! }
        deleteItemsByIds(TABLE_AUTH, ids)
    }

    fun insertAccessLog(log: AccessLog): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(ACCESS_COLUMN_FILE, log.file)
            put(ACCESS_COLUMN_IS_DIRECTORY, log.isDirectory.toString())
            put(ACCESS_COLUMN_PKG, log.pkg)
            put(ACCESS_REASON, log.reason.name)
            put(ACCESS_TYPE, log.type.name)
            put(ACCESS_RULE_ID, log.ruleId)
            put(ACCESS_COLUMN_CREATED_AT, log.createdAt)
        }

        return db.insert(TABLE_ACCESS, null, values)
    }

    fun getAllAccessLogs(): List<AccessLog> {
        val query = """
        SELECT 
            a.$ACCESS_COLUMN_ID,
            a.$ACCESS_COLUMN_FILE,
            a.$ACCESS_COLUMN_IS_DIRECTORY,
            a.$ACCESS_COLUMN_PKG,
            a.$ACCESS_REASON,
            a.$ACCESS_TYPE,
            a.$ACCESS_RULE_ID,
            a.$ACCESS_COLUMN_CREATED_AT,
            r.$RULES_COLUMN_FILE AS ${appendRulePrefix(RULES_COLUMN_FILE)},
            r.$RULES_COLUMN_IS_DIRECTORY AS ${appendRulePrefix(RULES_COLUMN_IS_DIRECTORY)},
            r.$RULES_COLUMN_PKG AS ${appendRulePrefix(RULES_COLUMN_PKG)},
            r.$RULES_COLUMN_ACCESS_TYPE AS ${appendRulePrefix(RULES_COLUMN_ACCESS_TYPE)},
            r.$RULES_COLUMN_ACCESS_DURATION AS ${appendRulePrefix(RULES_COLUMN_ACCESS_DURATION)},
            r.$RULES_COLUMN_ACCESS_RESOLUTION AS ${
            appendRulePrefix(RULES_COLUMN_ACCESS_RESOLUTION)
        },
            r.$RULES_COLUMN_IS_LIST_OF_FILES_ALLOWED AS ${
            appendRulePrefix(RULES_COLUMN_IS_LIST_OF_FILES_ALLOWED)
        },
            r.$RULES_COLUMN_CREATED_AT AS ${appendRulePrefix(RULES_COLUMN_CREATED_AT)},
            r.$RULES_COLUMN_ORDER_POSITION AS ${appendRulePrefix(RULES_COLUMN_ORDER_POSITION)},
            r.$RULES_COLUMN_DELETED_AT AS ${appendRulePrefix(RULES_COLUMN_DELETED_AT)}
        FROM $TABLE_ACCESS a
        LEFT JOIN $TABLE_RULES r
        ON a.$ACCESS_RULE_ID = r.$RULES_COLUMN_ID
        WHERE a.$ACCESS_RULE_ID IS NULL
           OR (r.$RULES_COLUMN_DELETED_AT IS NULL OR a.$ACCESS_COLUMN_CREATED_AT <= r.$RULES_COLUMN_DELETED_AT)
        ORDER BY a.$ACCESS_COLUMN_CREATED_AT DESC
    """
        val db = readableDatabase
        val cursor = db.rawQuery(query, null)

        return getAccessLogWithCursor(cursor)
    }

    fun clearAccessLogs() {
        val db = writableDatabase
        db.delete(TABLE_ACCESS, null, null)
        db.delete(TABLE_RULES, "$RULES_COLUMN_DELETED_AT IS NOT NULL", null)
    }

    private fun generateInClause(count: Int): String {
        val placeholders = List(count) { "?" }.joinToString(", ")
        return "IN ($placeholders)"
    }

    private fun deleteItemsByIds(table: String, ids: List<Long>) {
        val db = writableDatabase
        val whereArgs = ids.map { it.toString() }.toTypedArray()
        val whereClause = "id ${generateInClause(ids.size)}"
        db.delete(table, whereClause, whereArgs)
    }

    /*private fun softDeleteItemsByIds(table: String, deleteAtKey: String, ids: List<Long>) {
        val db = writableDatabase
        val whereArgs = ids.map { it.toString() }.toTypedArray()
        val whereClause = "id ${generateInClause(ids.size)}"

        val contentValues = ContentValues().apply {
            put(deleteAtKey, System.currentTimeMillis())
        }

        db.update(table, contentValues, whereClause, whereArgs)
    }*/

    fun validateAccess(
        documentId: String,
        isDirectory: Boolean,
        isCreation: Boolean,
        mode: String,
        callingPackage: String
    ): ConfirmationData? {
        clearExpiredRules()
        val rules = getRulesItems(callingPackage)
        if (rules.isEmpty()) {
            return null
        }

        var filteredRules = rules.filter {
            when (it.accessResolution) {
                AccessResolution.THIS_FILE -> it.file == documentId
                AccessResolution.ENTIRE_FOLDER_INCLUDE_SUBFOLDERS -> {  // EXTEND
                    val accessFolder = getFolder(documentId, isDirectory)
                    accessFolder.startsWith(
                        getFolder(
                            it.file,
                            it.isDirectory
                        )
                    )
                }

                AccessResolution.ENTIRE_FOLDER -> {   // NO EXTEND
                    val ruleFolder = getFolder(it.file, it.isDirectory)
                    val accessFolder = getFolder(documentId, isDirectory)
                    ruleFolder == accessFolder
                }
            }
        }

        filteredRules = filteredRules.filter {
            if (mode == AccessMode.LIST_OF_FILES.title) {
                it.isListOfFilesAllowed || it.accessType == AccessType.DENY_ACCESS
            } else {
                !(it.isListOfFilesAllowed && it.accessType == AccessType.DENY_ACCESS)
            }
        }

        if (mode == AccessMode.WRITE_ONLY.title) {
            // A rule that does not grant write steps aside so a later one can answer, but a
            // Block rule must still win: its mode string has no 'w', and dropping it let a
            // blocked app fall through to the no-rule path and raise write requests.
            filteredRules = filteredRules.filter {
                it.accessType == AccessType.DENY_ACCESS || it.accessType.mode().indexOf(mode) != -1
            }
        }

        return filteredRules.firstOrNull()?.toConfirmationData(mode)
    }

    fun validateAuth(
        documentId: String,
        callingPackage: String
    ): Boolean {
        val rules = getRulesItems(callingPackage)
        if (documentId.isNotEmpty() && rules.isNotEmpty()) {
            val filteredRules = rules.filter {
                if (it.accessType != AccessType.READ_ONLY_ACCESS && it.accessType != AccessType.FULL_ACCESS && !it.isListOfFilesAllowed) {
                    false
                } else {
                    when (it.accessResolution) {
                        AccessResolution.THIS_FILE -> it.file == documentId
                        AccessResolution.ENTIRE_FOLDER_INCLUDE_SUBFOLDERS ->
                            getFolder(documentId, it.isDirectory).startsWith(
                                getFolder(
                                    it.file,
                                    it.isDirectory
                                )
                            )

                        AccessResolution.ENTIRE_FOLDER ->
                            getFolder(it.file, it.isDirectory) == getFolder(
                                documentId,
                                it.isDirectory
                            )
                    }
                }
            }
            if (filteredRules.isNotEmpty()) {
                return true
            }
        }

        val items = getAuthItems(callingPackage)

        return items.isNotEmpty()
    }

    fun isAuthorized(
        callingPackage: String
    ): Pair<Boolean, Long?> {
        val items = getAuthItems(callingPackage)
        return Pair(items.isNotEmpty(), null)
    }

    private fun getFolder(file: String, isDirectory: Boolean) =
        FilewallProvider.getFolderOfFile(file, isDirectory)

    private fun getRulesItems(callingPackage: String): List<RuleItem> {
        val cursor: Cursor = readableDatabase.query(
            TABLE_RULES,
            arrayOf(
                RULES_COLUMN_ID,
                RULES_COLUMN_FILE,
                RULES_COLUMN_IS_DIRECTORY,
                RULES_COLUMN_PKG,
                RULES_COLUMN_ACCESS_TYPE,
                RULES_COLUMN_ACCESS_DURATION,
                RULES_COLUMN_ACCESS_RESOLUTION,
                RULES_COLUMN_IS_LIST_OF_FILES_ALLOWED,
                RULES_COLUMN_CREATED_AT,
                RULES_COLUMN_ORDER_POSITION
            ),
            RULES_WHERE_MATCH,
            arrayOf(callingPackage),
            null, null, "$RULES_COLUMN_ORDER_POSITION ASC"
        )

        val currentDate = System.currentTimeMillis()

        val rules = getRulesWithCursor(cursor).filter {
            val expirationDate = it.expirationDate()
            expirationDate == null || expirationDate >= currentDate
        }
        return rules
    }

    private fun getAuthItems(callingPackage: String): List<AuthItem> {
        val cursor: Cursor = readableDatabase.query(
            TABLE_AUTH,
            arrayOf(
                AUTH_COLUMN_ID,
                AUTH_COLUMN_PKG,
                AUTH_COLUMN_TYPE,
                AUTH_COLUMN_CREATED_AT
            ),
            AUTH_WHERE_MATCH_PKG,
            arrayOf(callingPackage),
            null, null, null
        )

        return getAuthWithCursor(cursor)
    }

    private fun getRulesWithSkippedDurations(durationsToSkip: Array<String>): List<RuleItem> {
        val db = readableDatabase
        val whereClause =
            "$RULES_COLUMN_ACCESS_DURATION NOT ${generateInClause(durationsToSkip.size)} AND $RULES_COLUMN_DELETED_AT IS NULL"
        val cursor: Cursor = db.query(
            TABLE_RULES,
            arrayOf(
                RULES_COLUMN_ID,
                RULES_COLUMN_FILE,
                RULES_COLUMN_IS_DIRECTORY,
                RULES_COLUMN_PKG,
                RULES_COLUMN_ACCESS_TYPE,
                RULES_COLUMN_ACCESS_DURATION,
                RULES_COLUMN_ACCESS_RESOLUTION,
                RULES_COLUMN_IS_LIST_OF_FILES_ALLOWED,
                RULES_COLUMN_CREATED_AT,
                RULES_COLUMN_ORDER_POSITION
            ),
            whereClause, durationsToSkip, null, null, "$RULES_COLUMN_ORDER_POSITION ASC"
        )

        return getRulesWithCursor(cursor)
    }

    private fun getRulesWithCursor(cursor: Cursor): List<RuleItem> {
        val rules = mutableListOf<RuleItem>()
        with(cursor) {
            while (moveToNext()) {
                val id = getLong(getColumnIndexOrThrow(RULES_COLUMN_ID))
                val file = getString(getColumnIndexOrThrow(RULES_COLUMN_FILE))
                val isDirectory =
                    getString(getColumnIndexOrThrow(RULES_COLUMN_IS_DIRECTORY)) == "true"
                val pkg = getString(getColumnIndexOrThrow(RULES_COLUMN_PKG))
                val accessType = getString(getColumnIndexOrThrow(RULES_COLUMN_ACCESS_TYPE))
                val accessDuration = getString(getColumnIndexOrThrow(RULES_COLUMN_ACCESS_DURATION))
                val accessResolution =
                    getString(getColumnIndexOrThrow(RULES_COLUMN_ACCESS_RESOLUTION))
                val isListOfFilesAllowed =
                    getString(getColumnIndexOrThrow(RULES_COLUMN_IS_LIST_OF_FILES_ALLOWED)) == "true"
                val createdAt = getLong(getColumnIndexOrThrow(RULES_COLUMN_CREATED_AT))
                val orderPosition = getLong(getColumnIndexOrThrow(RULES_COLUMN_ORDER_POSITION))
                rules.add(
                    RuleItem(
                        id,
                        file,
                        isDirectory,
                        pkg,
                        AccessType.fromName(accessType)!!,
                        AccessDuration.fromName(accessDuration)!!,
                        AccessResolution.fromName(accessResolution)!!,
                        isListOfFilesAllowed,
                        createdAt,
                        orderPosition
                    )
                )
            }
            close()
        }
        return rules
    }

    private fun getAuthWithCursor(cursor: Cursor): List<AuthItem> {
        val rules = mutableListOf<AuthItem>()
        with(cursor) {
            while (moveToNext()) {
                val id = getLong(getColumnIndexOrThrow(AUTH_COLUMN_ID))
                val pkg = getString(getColumnIndexOrThrow(AUTH_COLUMN_PKG))
                val type = getString(getColumnIndexOrThrow(AUTH_COLUMN_TYPE))
                val createdAt = getLong(getColumnIndexOrThrow(AUTH_COLUMN_CREATED_AT))
                rules.add(
                    AuthItem(
                        id,
                        pkg,
                        AuthType.fromName(type)!!,
                        createdAt
                    )
                )
            }
            close()
        }
        return rules
    }

    private fun appendRulePrefix(key: String): String {
        return "${TABLE_RULES}_$key"
    }

    private fun getAccessLogWithCursor(cursor: Cursor): List<AccessLog> {
        val logs = mutableListOf<AccessLog>()
        with(cursor) {
            while (moveToNext()) {
                val id = getLong(getColumnIndexOrThrow(ACCESS_COLUMN_ID))
                val file = getString(getColumnIndexOrThrow(ACCESS_COLUMN_FILE))
                val isDirectory =
                    getString(getColumnIndexOrThrow(ACCESS_COLUMN_IS_DIRECTORY)) == "true"
                val pkg = getString(getColumnIndexOrThrow(ACCESS_COLUMN_PKG))
                val reason = getString(getColumnIndexOrThrow(ACCESS_REASON))
                val type = getString(getColumnIndexOrThrow(ACCESS_TYPE))
                val ruleId = getLong(getColumnIndexOrThrow(ACCESS_RULE_ID))
                val createdAt = getLong(getColumnIndexOrThrow(ACCESS_COLUMN_CREATED_AT))

                var rule: RuleItem? = null

                if (ruleId != 0L) {
                    val ruleFile =
                        getString(getColumnIndexOrThrow(appendRulePrefix(RULES_COLUMN_FILE)))
                    val ruleIsDirectory =
                        getString(getColumnIndexOrThrow(appendRulePrefix(RULES_COLUMN_IS_DIRECTORY))) == "true"
                    val rulePkg =
                        getString(getColumnIndexOrThrow(appendRulePrefix(RULES_COLUMN_PKG)))
                    val ruleAccessType =
                        getString(getColumnIndexOrThrow(appendRulePrefix(RULES_COLUMN_ACCESS_TYPE)))
                    val ruleAccessDuration = getString(
                        getColumnIndexOrThrow(
                            appendRulePrefix(RULES_COLUMN_ACCESS_DURATION)
                        )
                    )
                    val ruleAccessResolution =
                        getString(
                            getColumnIndexOrThrow(
                                appendRulePrefix(
                                    RULES_COLUMN_ACCESS_RESOLUTION
                                )
                            )
                        )
                    val ruleIsListOfFilesAllowed = getString(
                        getColumnIndexOrThrow(
                            appendRulePrefix(RULES_COLUMN_IS_LIST_OF_FILES_ALLOWED)
                        )
                    ) == "true"
                    val ruleCreatedAt =
                        getLong(getColumnIndexOrThrow(appendRulePrefix(RULES_COLUMN_CREATED_AT)))
                    val ruleOrderPosition =
                        getLong(getColumnIndexOrThrow(appendRulePrefix(RULES_COLUMN_ORDER_POSITION)))
                    val ruleDeletedAt =
                        getLong(getColumnIndexOrThrow(appendRulePrefix(RULES_COLUMN_DELETED_AT)))
                    if (ruleFile == null || rulePkg == null || ruleAccessType == null || ruleAccessDuration == null || ruleAccessResolution == null) {
                        //rule = null
                    } else
                        rule = RuleItem(
                            ruleId,
                            ruleFile,
                            ruleIsDirectory,
                            rulePkg,
                            AccessType.fromName(ruleAccessType)!!,
                            AccessDuration.fromName(ruleAccessDuration)!!,
                            AccessResolution.fromName(ruleAccessResolution)!!,
                            ruleIsListOfFilesAllowed,
                            ruleCreatedAt,
                            ruleOrderPosition,
                            if (ruleDeletedAt == 0L) null else ruleDeletedAt
                        )
                }

                logs.add(
                    AccessLog(
                        id,
                        file,
                        isDirectory,
                        pkg,
                        AccessReason.fromName(reason)!!,
                        AccessLogType.fromName(type)!!,
                        ruleId,
                        rule,
                        createdAt
                    )
                )
            }
            close()
        }

        return logs
    }


    private fun getRequests(): List<RequestData> {
        val db = readableDatabase
        val cursor: Cursor = db.query(
            TABLE_REQUESTS,
            arrayOf(
                REQUESTS_COLUMN_ID,
                REQUESTS_COLUMN_DOCUMENT_ID,
                REQUESTS_COLUMN_IS_DIRECTORY,
                REQUESTS_COLUMN_MODE,
                REQUESTS_COLUMN_CALLING_PACKAGE,
                REQUESTS_COLUMN_IS_CREATION,
                REQUESTS_COLUMN_ACTION
            ),
            "", null, null, null, ""
        )

        return getRequestsWithCursor(cursor)
    }

    private fun getRequestsWithCursor(cursor: Cursor): List<RequestData> {
        val requests = mutableListOf<RequestData>()
        with(cursor) {
            while (moveToNext()) {
                val id = getLong(getColumnIndexOrThrow(REQUESTS_COLUMN_ID))
                val docId = getString(getColumnIndexOrThrow(REQUESTS_COLUMN_DOCUMENT_ID))
                val isDirectory =
                    getString(getColumnIndexOrThrow(REQUESTS_COLUMN_IS_DIRECTORY)) == "true"
                val mode = getString(getColumnIndexOrThrow(REQUESTS_COLUMN_MODE))
                val pkg = getString(getColumnIndexOrThrow(REQUESTS_COLUMN_CALLING_PACKAGE))
                val isCreation =
                    getString(getColumnIndexOrThrow(REQUESTS_COLUMN_IS_CREATION)) == "true"
                val action = getString(getColumnIndexOrThrow(REQUESTS_COLUMN_ACTION))
                requests.add(
                    RequestData(
                        id,
                        docId,
                        isDirectory,
                        mode,
                        pkg,
                        isCreation,
                        action
                    )
                )
            }
            close()
        }
        return requests
    }


    fun insertRequest(request: RequestData): Long {
        Log.d("DB", "insert req:" + request.toString())
        val db = writableDatabase

        val cursor = db.query(
            TABLE_REQUESTS,
            arrayOf(REQUESTS_COLUMN_ID),// REQUESTS_COLUMN_MODE),
            "$REQUESTS_COLUMN_DOCUMENT_ID = ? AND $REQUESTS_COLUMN_IS_DIRECTORY = ? AND $REQUESTS_COLUMN_CALLING_PACKAGE = ? AND $REQUESTS_COLUMN_IS_CREATION = ? AND $REQUESTS_COLUMN_ACTION = ?",
            arrayOf(
                request.documentId,
                request.isDirectory.toString().lowercase(),
                request.callingPackage,
                request.isCreation.toString().lowercase(),
                request.action
            ),
            null, null, null
        )

        if (cursor.moveToFirst()) {
            Log.d("DB", "insert req: exists!")
            val existingId = cursor.getLong(cursor.getColumnIndexOrThrow(REQUESTS_COLUMN_ID))
            cursor.close()
            return existingId
        }

        cursor.close()
        Log.d("DB", "insert req: adding new")
        val values = ContentValues().apply {
            put(REQUESTS_COLUMN_DOCUMENT_ID, request.documentId)
            put(REQUESTS_COLUMN_IS_DIRECTORY, request.isDirectory.toString().lowercase())
            put(REQUESTS_COLUMN_MODE, request.mode)
            put(REQUESTS_COLUMN_CALLING_PACKAGE, request.callingPackage)
            put(REQUESTS_COLUMN_IS_CREATION, request.isCreation.toString().lowercase())
            put(REQUESTS_COLUMN_ACTION, request.action)
        }
        return db.insertWithOnConflict(TABLE_REQUESTS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun updateRule(rule: RuleItem) {
        val db = writableDatabase
        db.transaction {
            try {
                val values = ContentValues().apply {
                    put(RULES_COLUMN_ACCESS_DURATION, rule.accessDuration.name)
                    put(RULES_COLUMN_ACCESS_TYPE, rule.accessType.name)
                    put(RULES_COLUMN_IS_LIST_OF_FILES_ALLOWED, rule.isListOfFilesAllowed.toString())
                    put(RULES_COLUMN_CREATED_AT, rule.createdAt)
                    put(RULES_COLUMN_ACCESS_RESOLUTION, rule.accessResolution.name)
                }
                update(
                    TABLE_RULES,
                    values,
                    "$RULES_COLUMN_ID = ?",
                    arrayOf(rule.id.toString())
                )
            } finally {
            }
        }
    }

    fun getActiveRequestsCount(): Long {
        val db = readableDatabase
        val cursor: Cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_REQUESTS", null)
        var count = 0L
        if (cursor.moveToFirst()) {
            count = cursor.getLong(0)
        }
        cursor.close()
        return count
    }

    fun deleteAllRequests() {
        val db = writableDatabase
        db.execSQL("DELETE FROM $TABLE_REQUESTS")
        db.close()
    }

    fun deleteAllRules() {
        softDeleteRules(getAllRules().mapNotNull { it.id })
    }

    /*fun hasRulesNoExtend(): Boolean {
        val db = readableDatabase
        val query = """
        SELECT EXISTS(
            SELECT 1 FROM $TABLE_RULES
            WHERE $RULES_COLUMN_ACCESS_RESOLUTION != 'ENTIRE_FOLDER_INCLUDE_SUBFOLDERS'
            AND $RULES_COLUMN_DELETED_AT IS NULL
            LIMIT 1
        )
        """
        val cursor = db.rawQuery(query, null)
        var exists = false
        if (cursor.moveToFirst()) {
            exists = cursor.getInt(0) == 1
        }
        cursor.close()
        return exists
    }*/
}