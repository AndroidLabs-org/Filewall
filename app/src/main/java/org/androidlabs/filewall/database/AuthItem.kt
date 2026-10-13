package org.androidlabs.filewall.database

data class AuthItem(
    val id: Long?,
    val pkg: String,
    val type: AuthType,
    val createdAt: Long
)