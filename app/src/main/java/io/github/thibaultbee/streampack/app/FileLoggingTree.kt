package io.github.thibaultbee.streampack.app

import android.app.Application
import timber.log.Timber

class FileLoggingTree(private val context: Application) : Timber.Tree() {
    // Log file will be stored in: /storage/emulated/0/Download/app.log
    private val logFile = java.io.File(context.getExternalFilesDir("Download"), "app.log")

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < android.util.Log.INFO) return

        val logMessage = "${System.currentTimeMillis()} [$priority] $tag: $message\n"

        try {
            java.io.FileWriter(logFile, true).use { writer ->
                writer.append(logMessage)
            }
            
            // Log success to system log for verification
            android.util.Log.d("FileLoggingTree", "Successfully wrote log to: ${logFile.absolutePath}")
        } catch (e: java.io.IOException) {
            android.util.Log.e("FileLoggingTree", "Error writing to log file: ${e.message}", e)
        }
    }
}