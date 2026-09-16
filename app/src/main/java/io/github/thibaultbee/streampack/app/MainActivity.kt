package io.github.thibaultbee.streampack.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.SeekBar
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.thibaultbee.streampack.app.data.config.StreamConfig
import io.github.thibaultbee.streampack.app.data.config.StreamConfigManager
import io.github.thibaultbee.streampack.app.databinding.ActivityMainBinding
import io.github.thibaultbee.streampack.app.databinding.ControlsPanelBinding
import io.github.thibaultbee.streampack.app.utils.PermissionsManager
import io.github.thibaultbee.streampack.app.utils.showDialog
import io.github.thibaultbee.streampack.app.utils.toast
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.extensions.defaultCameraId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Enhanced log writer with thread pooling and comprehensive error handling
 * for MediaStore-based logging operations
 */
class MediaStoreLoggingTree(
    private val context: Context,
    private val fileName: String = "app_log.txt"
) : Timber.Tree() {

    /**
     * Thread pool executor for log writing operations
     * Prevents unbounded thread creation and provides better resource management
     */
    private val logWriteExecutor = java.util.concurrent.Executors.newFixedThreadPool(4)

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Filter out logs below INFO level for performance
        if (priority < Log.INFO) return

        // Generate timestamp and format log entry
        val timestamp = java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS",
            java.util.Locale.US
        ).format(java.util.Date())

        // Map priority codes to single-character representations
        val priorityChar = when (priority) {
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            else -> "?"
        }

        // Format log entry with timestamp, priority, tag, and message
        val logEntry = "$timestamp $priorityChar/$tag: $message" +
                       (t?.let { "\n${Log.getStackTraceString(it)}" } ?: "")

        // Submit log writing task to thread pool
        logWriteExecutor.execute {
            writeToMediaStore(logEntry)
        }
    }

    /**
     * Core log writing function with enhanced error handling and recovery
     */
    private fun writeToMediaStore(content: String) {
        try {
            // Route to appropriate storage method based on Android version
            val success = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    writeViaMediaStore(content)
                }
                else -> {
                    writeViaLegacyFile(content)
                }
            }

            // Log write operation result for debugging
            if (!success) {
                Log.w("MediaStoreLoggingTree", "Log write operation failed for content: $content")
            }
        } catch (e: Exception) {
a            // Comprehensive error handling with context information
            Log.e("MediaStoreLoggingTree", "Critical error in log writing: ${e.message}", e)
            // Implement fallback logging mechanism
            writeToFallbackStorage(content, e)
        }
    }

    /**
     * Write log entries to MediaStore (Android 10+ devices)
     * Uses Android's standard MediaStore API for external storage
     */
    private fun writeViaMediaStore(content: String): Boolean {
        return try {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

            // Check if file already exists in MediaStore
            val existingUri = findExistingFileUri(resolver, collection)

            val uri: android.net.Uri? = existingUri ?: run {
                // Create new media entry in MediaStore
                val values = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                resolver.insert(collection, values)
            }

            // Write content to the media entry
            uri?.let {
                resolver.openOutputStream(it, "wa")?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                }
            }

            // Return success status
            uri != null
        } catch (e: Exception) {
            Log.e("MediaStoreLoggingTree", "MediaStore write error", e)
            false
        }
    }

    /**
     * Legacy file system fallback for pre-Android 10 devices
     * Uses traditional file system operations for older Android versions
     */
    private fun writeViaLegacyFile(content: String): Boolean {
        try {
            // Get external storage directory for downloads
            val dir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )

            // Ensure directory exists
            if (!dir.exists()) {
                val created = dir.mkdirs()
                if (!created) {
                    Log.e("media_store_logging", "Failed to create directory: ${dir.absolutePath}")
                    return false
                }
            }

            // Write to file using append mode
            val logFile = java.io.File(dir, fileName)
            java.io.FileWriter(logFile, true).use { writer ->
                writer.write(content)
                writer.flush()
            }

            return true
        } catch (e: Exception) {
            Log.e("MediaStoreLoggingTree", "Legacy file write error", e)
            return false
        }
    }

    /**
     * Fallback storage mechanism when primary log writing fails
     * Attempts multiple fallback strategies to ensure logs are not lost
     */
    private fun writeToFallbackStorage(content: String, originalError: Exception) {
        try {
            // Try to write to internal storage as last resort
            val internalDir = context.filesDir
            val fallbackFile = java.io.File(internalDir, "log_fallback.txt")

            java.io.FileWriter(fallbackFile, true).use { writer ->
                val fallbackEntry = "FALLBACK: $content (Original error: ${originalError.message})\n"
                writer.write(fallbackEntry)
                writer.flush()
            }

            Log.w("MediaStoreLoggingTree", "Fallback log storage successful")
        } catch (fallbackError: Exception) {
            // If even fallback fails, log to console for debugging
            System.err.println("CRITICAL: Log writing completely failed. Original error: ${originalError.message}")
        }
    }

    /**
     * Find existing log file in MediaStore
     * Searches for file with matching name and path in external storage
     */
    private fun findExistingFileUri(
        resolver: android.content.ContentResolver,
        collection: android.net.Uri
    ): android.net.Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
                        "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(
            fileName,
            "%${android.os.Environment.DIRECTORY_DOWNLOADS}%"
        )

        return resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                )
                return ContentUris.withAppendedId(collection, id)
            }
            null
        }
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