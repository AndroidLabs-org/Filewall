// Adapted from the Android "Common Logger" sample.
// Copyright (C) The Android Open Source Project
// Licensed under the Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0).
package org.androidlabs.filewall.logger

/**
 * Simple [LogNode] filter, removes everything except the message.
 * Useful for situations like on-screen log output where you don't want a lot of metadata displayed,
 * just easy-to-read message updates as they're happening.
 */
class MessageOnlyLogFilter : LogNode {
    /**
     * Returns the next LogNode in the chain.
     */
    /**
     * Sets the LogNode data will be sent to..
     */
    var next: LogNode? = null

    /**
     * Takes the "next" LogNode as a parameter, to simplify chaining.
     *
     * @param next The next LogNode in the pipeline.
     */
    constructor(next: LogNode?) {
        this.next = next
    }

    constructor()

    override fun println(priority: Int, tag: String?, msg: String?, tr: Throwable?) {
        next?.println(priority, tag, msg, tr)
    }
}
