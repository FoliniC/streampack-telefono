package io.github.thibaultbee.streampack.app

import android.app.Application
import timber.log.Timber

class FileLoggingTree(private val context: Application) : Timber.Tree() {
    private val logFile = java.io.File(context.filesDir, "app.log")
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < android.util.Log.INFO) return

        val logMessage = "${System.currentTimeMillis()} [$priority] $tag: $message\n"

        executor.execute {
            try {
                logFile.appendText(logMessage)
            } catch (e: java.io.IOException) {
            }
        }
    }
}