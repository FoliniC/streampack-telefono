package io.github.thibaultbee.streampack.app

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import timber.log.Timber
import java.io.File

class MediaStoreLoggingTree(
    private val context: Context,
    private val fileName: String = "app_log.txt"
) : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.INFO) return

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

        Thread {
            writeToMediaStore(line)
        }.start()
    }

    private fun writeToMediaStore(content: String) {
        try {
            // Check if we're on Android 10+ for MediaStore.Downloads
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeViaMediaStore(content)
            } else {
                writeViaLegacyFile(content)
            }
        } catch (t: Throwable) {
            // Use Throwable instead of Exception to catch all possible errors
            Log.e("MediaStoreLoggingTree", "Error writing log", t)
        }
    }

    private fun writeViaMediaStore(content: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        val existingUri = findExistingFileUri(resolver, collection)

        val uri: Uri? = existingUri ?: run {
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

        uri?.let {
            resolver.openOutputStream(it, "wa")?.use { os: java.io.OutputStream ->
                os.write(content.toByteArray())
            }
        }
    }

    // Fallback for pre- Android 10 devices (API < 29)
    private fun writeViaLegacyFile(content: String) {
        try {
            val dir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            if (!dir.exists()) {
                dir.mkdirs()
            }
            File(dir, fileName).appendText(content)
        } catch (e: Exception) {
            Log.e("MediaStoreLoggingTree", "Legacy file write error", e)
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

class StreamPack : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.plant(MediaStoreLoggingTree(this))
        } else {
            Timber.plant(MediaStoreLoggingTree(this))
        }
    }
}