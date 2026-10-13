// Adapted from the Android "Common Logger" sample.
// Copyright (C) The Android Open Source Project
// Licensed under the Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0).
package org.androidlabs.filewall.logger

import android.util.Log

/**
 * Helper class for a list (or tree) of LoggerNodes.
 *
 *
 * When this is set as the head of the list,
 * an instance of it can function as a drop-in replacement for [android.util.Log].
 * Most of the methods in this class server only to map a method call in Log to its equivalent
 * in LogNode.
 */
object Log {
    // Grabbing the native values from Android's native logging facilities,
    // to make for easy migration and interop.
    private const val VERBOSE: Int = Log.VERBOSE
    private const val DEBUG: Int = Log.DEBUG
    private const val INFO: Int = Log.INFO
    private const val WARN: Int = Log.WARN
    private const val ERROR: Int = Log.ERROR
    private const val ASSERT: Int = Log.ASSERT

    private fun logLevelToString(level: Int): String {
        return when (level) {
            Log.VERBOSE -> "VERBOSE"
            Log.DEBUG -> "DEBUG"
            Log.INFO -> "INFO"
            Log.WARN -> "WARN"
            Log.ERROR -> "ERROR"
            Log.ASSERT -> "ASSERT"
            -1 -> "NONE"
            else -> "UNKNOWN"
        }
    }

    fun buildLogMessage(priority: Int, tag: String?, msg: String?, tr: Throwable?): String {
        val logBuilder = StringBuilder()
        logBuilder.append("[${logLevelToString(priority)}] ${tag ?: "NoTag"}: ${msg ?: "NoMessage"}")
        tr?.let {
            logBuilder.append("\n${it.stackTraceToString()}")
        }
        return logBuilder.toString()
    }

    // Stores multiple LogNodes in a list.
    private val logNodes: MutableList<LogNode> = mutableListOf()

    var isEnabled: Boolean = false

    // Adds a new LogNode to the list.
    fun addLogNode(node: LogNode) {
        logNodes.add(node)
    }

    // Removes a LogNode from the list.
    fun removeLogNode(node: LogNode) {
        logNodes.remove(node)
    }

    // Clears all LogNodes from the list.
    fun clearLogNodes() {
        logNodes.clear()
    }

    /**
     * Instructs the LogNode to print the log data provided. Other LogNodes can
     * be chained to the end of the LogNode as desired.
     *
     * @param priority Log level of the data being logged. Verbose, Error, etc.
     * @param tag Tag for for the log data. Can be used to organize log statements.
     * @param msg The actual message to be logged.
     * @param tr If an exception was thrown, this can be sent along for the logging facilities
     * to extract and print useful information.
     */
    @JvmOverloads
    fun println(priority: Int, tag: String?, msg: String?, tr: Throwable? = null) {
        if (!isEnabled) {
            return
        }
        for (node in logNodes) {
            node.println(priority, tag, msg, tr)
        }
    }

    /**
     * Prints a message at VERBOSE priority.
     *
     * @param tag Tag for for the log data. Can be used to organize log statements.
     * @param msg The actual message to be logged.
     * @param tr If an exception was thrown, this can be sent along for the logging facilities
     * to extract and print useful information.
     */
    @JvmOverloads
    fun v(tag: String?, msg: String?, tr: Throwable? = null) {
        println(VERBOSE, tag, msg, tr)
    }


    /**
     * Prints a message at DEBUG priority.
     *
     * @param tag Tag for for the log data. Can be used to organize log statements.
     * @param msg The actual message to be logged.
     * @param tr If an exception was thrown, this can be sent along for the logging facilities
     * to extract and print useful information.
     */
    @JvmOverloads
    fun d(tag: String?, msg: String?, tr: Throwable? = null) {
        println(DEBUG, tag, msg, tr)
    }

    /**
     * Prints a message at INFO priority.
     *
     * @param tag Tag for for the log data. Can be used to organize log statements.
     * @param msg The actual message to be logged.
     * @param tr If an exception was thrown, this can be sent along for the logging facilities
     * to extract and print useful information.
     */
    @JvmOverloads
    fun i(tag: String?, msg: String?, tr: Throwable? = null) {
        println(INFO, tag, msg, tr)
    }

    /**
     * Prints a message at WARN priority.
     *
     * @param tag Tag for for the log data. Can be used to organize log statements.
     * @param msg The actual message to be logged.
     * @param tr If an exception was thrown, this can be sent along for the logging facilities
     * to extract and print useful information.
     */
    @JvmOverloads
    fun w(tag: String?, msg: String?, tr: Throwable? = null) {
        println(WARN, tag, msg, tr)
    }

    /**
     * Prints a message at WARN priority.
     *
     * @param tag Tag for for the log data. Can be used to organize log statements.
     * @param tr If an exception was thrown, this can be sent along for the logging facilities
     * to extract and print useful information.
     */
    fun w(tag: String?, tr: Throwable?) {
        w(tag, null, tr)
    }

    /**
     * Prints a message at ERROR priority.
     *
     * @param tag Tag for for the log data. Can be used to organize log statements.
     * @param msg The actual message to be logged.
     * @param tr If an exception was thrown, this can be sent along for the logging facilities
     * to extract and print useful information.
     */
    @JvmOverloads
    fun e(tag: String?, msg: String?, tr: Throwable? = null) {
        println(ERROR, tag, msg, tr)
    }

    /**
     * Prints a message at ASSERT priority.
     *
     * @param tag Tag for for the log data. Can be used to organize log statements.
     * @param msg The actual message to be logged.
     * @param tr If an exception was thrown, this can be sent along for the logging facilities
     * to extract and print useful information.
     */
    @JvmOverloads
    fun wtf(tag: String?, msg: String?, tr: Throwable? = null) {
        println(ASSERT, tag, msg, tr)
    }

    /**
     * Prints a message at ASSERT priority.
     *
     * @param tag Tag for for the log data. Can be used to organize log statements.
     * @param tr If an exception was thrown, this can be sent along for the logging facilities
     * to extract and print useful information.
     */
    fun wtf(tag: String?, tr: Throwable?) {
        wtf(tag, null, tr)
    }
}
