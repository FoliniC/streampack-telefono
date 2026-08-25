package io.github.thibaultbee.streampack.app.data.config

import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject
import java.io.File

data class StreamConfig(
    val url: String = "rtmp://10.203.33.202:1935/telefono",
    val autostart: Boolean = true,
    val autostartDelayMs: Long = 1000L,
    val autoReconnect: Boolean = true,
    val reconnectIntervalSec: Int = 5,
    val cameraId: String = "0",
    val videoWidth: Int = 1280,
    val videoHeight: Int = 720,
    val videoFps: Int = 25,
    val videoBitrate: Int = 2000000,
    val audioEnabled: Boolean = true,
    val audioBitrate: Int = 128000
)

object StreamConfigManager {
    private const val TAG = "StreamConfigManager"
    private const val CONFIG_FILE_NAME = "streampack.json"

    fun loadConfig(context: Context, intent: Intent? = null): StreamConfig {
        var config = loadFromFile(context)

        // Override from Intent extras if passed via ADB or intent launch
        intent?.extras?.let { extras ->
            val url = extras.getString("url") ?: config.url
            val autostart = if (extras.containsKey("autostart")) extras.getBoolean("autostart") else config.autostart
            val autoReconnect = if (extras.containsKey("auto_reconnect")) extras.getBoolean("auto_reconnect") else config.autoReconnect
            val reconnectSec = extras.getInt("reconnect_interval_sec", config.reconnectIntervalSec)
            val cameraId = extras.getString("camera_id") ?: config.cameraId
            val videoWidth = extras.getInt("video_width", config.videoWidth)
            val videoHeight = extras.getInt("video_height", config.videoHeight)
            val videoFps = extras.getInt("video_fps", config.videoFps)
            val videoBitrate = extras.getInt("video_bitrate", config.videoBitrate)
            val audioEnabled = if (extras.containsKey("audio_enabled")) extras.getBoolean("audio_enabled") else config.audioEnabled

            config = config.copy(
                url = url,
                autostart = autostart,
                autoReconnect = autoReconnect,
                reconnectIntervalSec = reconnectSec,
                cameraId = cameraId,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                videoFps = videoFps,
                videoBitrate = videoBitrate,
                audioEnabled = audioEnabled
            )
        }

        Log.i(TAG, "Loaded config: $config")
        return config
    }

    private fun loadFromFile(context: Context): StreamConfig {
        val candidatePaths = listOf(
            File("/sdcard/Download/$CONFIG_FILE_NAME"),
            File("/sdcard/$CONFIG_FILE_NAME"),
            File(context.getExternalFilesDir(null), CONFIG_FILE_NAME),
            File(context.filesDir, CONFIG_FILE_NAME)
        )

        for (file in candidatePaths) {
            try {
                if (file.exists() && file.canRead()) {
                    val jsonStr = file.readText()
                    val json = JSONObject(jsonStr)
                    Log.i(TAG, "Loading configuration from ${file.absolutePath}")
                    return StreamConfig(
                        url = json.optString("url", "rtmp://10.203.33.202:1935/telefono"),
                        autostart = json.optBoolean("autostart", true),
                        autostartDelayMs = json.optLong("autostart_delay_ms", 1000L),
                        autoReconnect = json.optBoolean("auto_reconnect", true),
                        reconnectIntervalSec = json.optInt("reconnect_interval_sec", 5),
                        cameraId = json.optString("camera_id", "0"),
                        videoWidth = json.optInt("video_width", 1280),
                        videoHeight = json.optInt("video_height", 720),
                        videoFps = json.optInt("video_fps", 25),
                        videoBitrate = json.optInt("video_bitrate", 2000000),
                        audioEnabled = json.optBoolean("audio_enabled", true),
                        audioBitrate = json.optInt("audio_bitrate", 128000)
                    )
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error parsing config file ${file.absolutePath}: ${e.message}")
            }
        }

        Log.i(TAG, "No config file found. Using default StreamConfig.")
        return StreamConfig()
    }
}
