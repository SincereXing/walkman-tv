package com.walkman.tv.data.store

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Tiny file-backed JSON store (kotlinx.serialization). Read/write are synchronous; callers
 *  should invoke from an IO context. */
class JsonStore<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val default: T,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): T = try {
        if (file.exists()) json.decodeFromString(serializer, file.readText()) else default
    } catch (e: Exception) {
        default
    }

    fun save(value: T) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(serializer, value))
        } catch (e: Exception) {
            // best-effort persistence
        }
    }
}
