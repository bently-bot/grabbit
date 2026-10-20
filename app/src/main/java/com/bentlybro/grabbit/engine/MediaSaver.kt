package com.bentlybro.grabbit.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.bentlybro.grabbit.core.FileNames
import com.bentlybro.grabbit.core.MediaKind
import java.io.File

data class SavedMedia(val uri: Uri, val displayName: String, val mimeType: String)

/**
 * Copies a finished file from app-private scratch space into shared storage via MediaStore,
 * so it shows up in the gallery / music apps without any storage permission (API 29+).
 * The row is inserted as pending and only published once the copy completes, so a crash
 * mid-copy never leaves a half-written video visible.
 */
class MediaSaver(private val context: Context) {

    fun save(file: File): SavedMedia {
        val name = FileNames.sanitize(file.name)
        val kind = MediaKind.forFileName(name)
        val mime = MediaKind.mimeType(name)
        val collection = when (kind) {
            MediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            MediaKind.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            MediaKind.OTHER -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, kind.relativeDir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: error("Couldn't create $name in ${kind.relativeDir}")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out, bufferSize = 256 * 1024) }
            } ?: error("Couldn't open $name for writing")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
        // MediaStore may rename on collision ("name (1).mp4"); read back what it chose.
        val finalName = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: name
        return SavedMedia(uri, finalName, mime)
    }
}
