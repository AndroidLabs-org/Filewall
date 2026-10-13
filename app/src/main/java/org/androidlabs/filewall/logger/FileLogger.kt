package org.androidlabs.filewall.logger

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.util.LinkedList

class FileLogger(context: Context) : LogNode {
    private val maxLines = 3000
    val logFile: File

    init {
        val logDir = File(context.filesDir, "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        logFile = File(logDir, "logs.txt")
        createFileIfNeeded()
    }

    fun deleteLogs() {
        if (logFile.exists()) {
            logFile.delete()
        }
    }

    fun readLogs(): List<String> {
        createFileIfNeeded()
        return logFile.readLines()
    }

    override fun println(priority: Int, tag: String?, msg: String?, tr: Throwable?) {
        val logMessage = Log.buildLogMessage(priority, tag, msg, tr)
        writeLogToFile(logMessage)
    }

    private fun createFileIfNeeded() {
        if (!logFile.exists()) {
            logFile.createNewFile()
        }
    }

    private fun writeLogToFile(logMessage: String) {
        createFileIfNeeded()

        val lines = LinkedList(logFile.readLines())

        lines.add(logMessage)

        while (lines.size > maxLines) {
            lines.removeFirst()
        }

        PrintWriter(logFile).use { writer ->
            lines.forEach { line ->
                writer.println(line)
            }
        }
    }
}