package io.github.thibaultbee.streampack.app.data.config

import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject
import java.io.File

import androidx.core.net.toUri

data class StreamConfig(
    val url: String = "rtmp://10.116.170.10:1935/live/telefono",
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
    val audioBitrate: Int = 128000,
    val zoomFactor: Float = 1.0f,
    val exposureCompensation: Float = 0.0f,
    val whiteBalanceIndex: Int = 0
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

        // The router hotplug handler writes the current RNDIS alias into
        // /sdcard/Download/streampack.json. Never rewrite a valid configured
        // host here: Android apps cannot invoke adb, so computeUsbAlias() would
        // fall back to an old subnet and silently send RTMP to the wrong host.
        // sanitizeUrl() is only a fallback for an empty/malformed URL.
        config = config.copy(url = sanitizeUrl(config.url))

        Log.i(TAG, "Loaded config URL: ${config.url}")
        return config
    }

    private fun computeUsbAlias(): String {
        return try {
            // Use simpler adb shell command that's more likely to work
            val pb = ProcessBuilder("/system/bin/adb", "shell", "ip", "route", "show")
            val p = pb.start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            // Find rndis0 local route (e.g., "10.116.170.0/24 dev rndis0 proto kernel scope link src 10.116.170.144")
            val lines = out.lines()
            for (line in lines) {
                if (line.contains("dev rndis0") && line.contains("/")) {
                    val parts = line.split("\\s+")
                    val cidr = parts[0]  // e.g., "10.116.170.0/24"
                    val prefix = cidr.substringBeforeLast(".")  // "10.116.170"
                    // Compute router alias based on subnet (first usable IP in subnet)
                    // For subnet 10.116.170.0/24, router alias is 10.116.170.10 (per router config)
                    return "$prefix.10"
                }
            }
            // Fallback: try default gateway (original logic)
            val m2 = Regex("via\\s+([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+)").find(out)
            if (m2 != null) {
                val gw = m2.groupValues[1]
                val gwParts = gw.split(".")
                "${gwParts[0]}.${gwParts[1]}.${gwParts[2]}.10"
            } else "10.203.33.10"
        } catch (e: Exception) {
            "10.203.33.10"
        }
    }

    fun sanitizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return "rtmp://${computeUsbAlias()}:1935/live/telefono"
        }
        return try {
            val uri = trimmed.toUri()
            val scheme = uri.scheme ?: "rtmp"
            val host = uri.host
            val port = if (uri.port != -1) uri.port else 1935
            var path = uri.path
            if (path.isNullOrEmpty() || path == "/") {
                path = "/live/telefono"
            }
            // A complete URL came from the JSON file or intent and is the
            // authoritative endpoint. In particular, preserve the dynamic
            // <RNDIS-subnet>.10 alias written by the router hotplug script.
            if (!host.isNullOrBlank()) {
                "$scheme://$host:$port$path"
            } else {
                "rtmp://${computeUsbAlias()}:$port$path"
            }
        } catch (e: Throwable) {
            "rtmp://${computeUsbAlias()}:1935/live/telefono"
        }
    }

    fun loadFromFile(context: Context): StreamConfig {
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
                        url = json.optString("url", "rtmp://10.116.170.10:1935/live/telefono"),
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
                        audioBitrate = json.optInt("audio_bitrate", 128000),
                        zoomFactor = json.optDouble("zoom_factor", 1.0).toFloat(),
                        exposureCompensation = json.optDouble("exposure_compensation", 0.0).toFloat(),
                        whiteBalanceIndex = json.optInt("white_balance_index", 0)
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