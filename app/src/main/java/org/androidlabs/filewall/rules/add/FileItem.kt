package org.androidlabs.filewall.rules.add

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean
) {
    val folderPath: String
        get() = if (isDirectory) path else (path.substringBeforeLast("/") + "/")

    val rulePath: String
        get() = if (isDirectory) {
            if (path=="/") "." else ".$path"
        } else ".$path"
}