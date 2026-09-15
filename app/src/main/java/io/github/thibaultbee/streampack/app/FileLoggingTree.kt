package io.github.thibaultbee.streampack.app

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import timber.log.Timber

class MediaStoreLoggingTree(
    private val context: Context,
    private val fileName: String = "app_log.txt"
) : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < android.util.Log.INFO) return

        val timestamp = java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS",
            java.util.Locale.US
        ).format(java.util.Date())

        val priorityChar = when (priority) {
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            else -> "?"
        }

        val line = "$timestamp $priorityChar/$tag: $message\n" +
                   (t?.let { Log.getStackTraceString(it) + "\n" } ?: "")

        // Use single background thread for thread-safe file operations
        Thread {
            writeToMediaStore(line)
        }.start()
    }

    private fun writeToMediaStore(content: String) {
        try {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

            // Check if file already exists
            val existingUri = findExistingFileUri(resolver, collection)

            val uri: Uri? = if (existingUri != null) {
                existingUri
            } else {
                // Create new file in Downloads
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS
                    )
                }
                resolver.insert(collection, values)
            }

            // Write/append to the file
            uri?.let {
                resolver.openOutputStream(it, "wa")?.use { os: java.io.OutputStream ->
                    os.write(content.toByteArray())
                }
            }
        } catch (e: Exception) {
            // Use android.util.Log directly to avoid Timber recursion
            android.util.Log.e("MediaStoreLoggingTree", "Error writing log to MediaStore", e)
        }
    }

    private fun findExistingFileUri(
        resolver: android.content.ContentResolver,
        collection: Uri
    ): Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
                        "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(
            fileName,
            "%${Environment.DIRECTORY_DOWNLOADS}%"
        )

        resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                )
                return ContentUris.withAppendedId(collection, id)
            }
        }
        return null
    }
}

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            // For debugging, also log to MediaStore for easy inspection
            Timber.plant(MediaStoreLoggingTree(this))
        } else {
            Timber.plant(MediaStoreLoggingTree(this))
        }
    }
}