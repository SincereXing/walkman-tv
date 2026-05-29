package com.walkman.tv.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** An imported lx-music v4 user source script, mirroring iOS `UserScript`. */
@Serializable
data class UserScript(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val version: String = "",
    val author: String = "",
    val homepage: String = "",
    val rawScript: String,
    val importedAt: Long = System.currentTimeMillis(),
    val enabled: Boolean = true,
)

/** Capabilities a script reports via `lx.send('inited', { sources })`. */
@Serializable
data class ScriptCapabilities(
    val sources: Map<SourceID, SourceCapability> = emptyMap(),
)

@Serializable
data class SourceCapability(
    val type: String = "music",
    val actions: List<String> = emptyList(),
    val qualities: List<Quality> = emptyList(),
)
