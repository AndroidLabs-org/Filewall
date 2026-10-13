package org.androidlabs.filewall.accesslogs

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.androidlabs.filewall.R
import org.androidlabs.filewall.database.AccessLogType
import org.androidlabs.filewall.database.Database
import org.androidlabs.filewall.utils.Utils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class AccessLogsViewModel(application: Application) : AndroidViewModel(application) {
    private val dbHelper: Database = Database(application.applicationContext)

    private val _logs = MutableLiveData<List<ListAccessLogItem>>()

    private val _isSharing = MutableStateFlow(false)
    val isSharing: StateFlow<Boolean> = _isSharing.asStateFlow()

    val logs: LiveData<List<ListAccessLogItem>>
        get() = _logs

    init {
        refreshLogs()
    }



    fun shareLogs(onComplete: (File?) -> Unit) {
        viewModelScope.launch {
            _isSharing.value = true
            try {
                val context = getApplication<Application>().applicationContext
                val logs = _logs.value
                if (logs==null || logs.isEmpty()) {
                    _isSharing.value = false
                    onComplete(null)
                } else {
                    val logDir = File(context.filesDir, "logs")
                    if (!logDir.exists()) {
                        logDir.mkdirs()
                    }
                    val formatter = SimpleDateFormat("MMM dd yyyy HH:mm:ss", Locale.getDefault())
                    val logFile = File(logDir, "access_logs.csv")
                    // Mirrors the fields the Access logs screen shows. Access result, type and
                    // duration used to be visible on screen but absent from the export.
                    val header = listOf(
                        "App", "Package", "Path", "Reason", "Timestamp",
                        "Access result", "Access type", "Access duration"
                    ).joinToString(",")

                    val rows = logs.map { item ->
                        val rule = item.log.rule
                        listOf(
                            item.appName,
                            item.log.pkg,
                            item.getPath(),
                            if (item.log.type == AccessLogType.REQUEST)
                                item.log.reason.name + " request"
                            else
                                item.log.reason.name,
                            formatter.format(item.log.createdAt),
                            item.accessResultText(context),
                            rule?.accessResolution?.title.orEmpty(),
                            rule?.accessDuration?.title.orEmpty()
                        ).joinToString(",") { csvField(it) }
                    }
                    val csv = (listOf(header) + rows).joinToString("\n")
                    logFile.writeText(csv)
                    _isSharing.value = false
                    onComplete(logFile)
                }
            } catch(ex: Exception)  {
                _isSharing.value = false
                onComplete(null)
            }
        }
    }

    companion object {
        /**
         * Removes the CSV that Share logs leaves in app storage. Clearing the log or switching
         * it off promises the history is permanently deleted, so a copy of it must not outlive
         * that.
         */
        fun deleteExportedLogs(context: Context) {
            File(File(context.filesDir, "logs"), "access_logs.csv").delete()
        }
    }

    /** RFC 4180: quote every field and double any quote inside it. */
    private fun csvField(value: String): String =
        "\"" + value.replace("\"", "\"\"") + "\""

    fun refreshLogs() {
        val logs = dbHelper.getAllAccessLogs()
        val context = getApplication<Application>().applicationContext

        val uniquePkgs = logs.map { it.pkg }.toSet()

        val identifiers = uniquePkgs.associateWith { pkg ->
            Utils.getAppName(context, pkg)
        }

        val icons = uniquePkgs.associateWith { pkg ->
            Utils.getAppIcon(context, pkg)
        }

        val list = logs.map {
            ListAccessLogItem(identifiers[it.pkg]!!, icons[it.pkg], it)
        }

        _logs.postValue(list)
    }

    fun clearLogs() {
        dbHelper.clearAccessLogs()
        deleteExportedLogs(getApplication<Application>().applicationContext)
        refreshLogs()
    }

    fun deleteRuleById(id: Long) {
        //val currentTime = System.currentTimeMillis()

        dbHelper.deleteRuleById(id)

        _logs.value?.forEach { logItem ->
            if (logItem.log.ruleId == id) {
                logItem.log.rule = null
            }
        }
        _logs.postValue(_logs.value)
    }
}