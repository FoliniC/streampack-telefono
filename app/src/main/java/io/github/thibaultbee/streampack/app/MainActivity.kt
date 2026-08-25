package io.github.thibaultbee.streampack.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.thibaultbee.streampack.app.data.config.StreamConfig
import io.github.thibaultbee.streampack.app.data.config.StreamConfigManager
import io.github.thibaultbee.streampack.app.databinding.ActivityMainBinding
import io.github.thibaultbee.streampack.app.utils.PermissionsManager
import io.github.thibaultbee.streampack.app.utils.showDialog
import io.github.thibaultbee.streampack.app.utils.toast
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.extensions.defaultCameraId
import io.github.thibaultbee.streampack.core.streamers.lifecycle.StreamerLifeCycleObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(this.application)
    }

    private var currentConfig: StreamConfig = StreamConfig()
    private var isAutoStartTriggered = false

    private val streamerRequiredPermissions =
        listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

    @SuppressLint("MissingPermission")
    private val permissionsManager = PermissionsManager(
        this,
        streamerRequiredPermissions,
        onAllGranted = { onPermissionsGranted() },
        onShowPermissionRationale = { permissions, onRequiredPermissionLastTime ->
            showDialog(
                title = "Permissions denied",
                message = "Explain why you need to grant $permissions permissions to stream",
                positiveButtonText = R.string.accept,
                onPositiveButtonClick = { onRequiredPermissionLastTime() },
                negativeButtonText = R.string.denied
            )
        },
        onDenied = {
            showDialog(
                "Permissions denied",
                "You need to grant all permissions to stream",
                positiveButtonText = 0,
                negativeButtonText = 0
            )
        })

    private val streamerLifeCycleObserver by lazy { StreamerLifeCycleObserver(viewModel.streamer) }

    private val requestLocalNetworkPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                viewModel.startStream()
            } else {
                binding.liveButton.isChecked = false
                toast("Local network permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadAndApplyConfiguration(intent)
        bindProperties()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadAndApplyConfiguration(intent)
        if (currentConfig.autostart) {
            triggerAutoStart()
        }
    }

    private fun loadAndApplyConfiguration(intent: Intent?) {
        currentConfig = StreamConfigManager.loadConfig(this, intent)
        viewModel.applyConfig(currentConfig)
    }

    private fun bindProperties() {
        binding.liveButton.setOnCheckedChangeListener { view, isChecked ->
            if (view.isPressed) {
                if (isChecked) {
                    startStreamingWithPermissionCheck()
                } else {
                    viewModel.stopStream()
                }
            }
        }

        lifecycle.addObserver(streamerLifeCycleObserver)
        configureStreamer()

        viewModel.closedThrowableLiveData.observe(this) { error ->
            Log.e(TAG, "Disconnect: $error")
            toast("Disconnect: ${error.message}")
            handleAutoReconnect()
        }

        viewModel.pendingConnectionFailedLiveData.observe(this) { error ->
            Log.e(TAG, "Connection error: $error")
            toast("Connection error: ${error.message}")
            handleAutoReconnect()
        }

        viewModel.throwableLiveData.observe(this) { error ->
            Log.e(TAG, "Error: $error")
            toast("Error: ${error.message}")
        }

        viewModel.isStreamingLiveData.observe(this) { isStreaming ->
            if (isStreaming) {
                lockOrientation()
            } else {
                unlockOrientation()
            }
            binding.liveButton.isChecked = isStreaming || (viewModel.isTryingConnectionLiveData.value == true)
        }

        viewModel.isTryingConnectionLiveData.observe(this) { isWaiting ->
            binding.liveButton.isChecked = isWaiting || (viewModel.isStreamingLiveData.value == true)
        }
    }

    private fun startStreamingWithPermissionCheck() {
        lifecycleScope.launch {
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) && viewModel.needsLocalNetworkPermission()) {
                Log.i(TAG, "Local network permission is required for Android 37+")
                requestLocalNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            } else {
                viewModel.startStream()
            }
        }
    }

    private fun handleAutoReconnect() {
        if (currentConfig.autoReconnect) {
            lifecycleScope.launch {
                Log.i(TAG, "Scheduling auto-reconnect in ${currentConfig.reconnectIntervalSec} seconds...")
                delay(currentConfig.reconnectIntervalSec * 1000L)
                if (viewModel.isStreamingLiveData.value != true) {
                    Log.i(TAG, "Attempting auto-reconnect now...")
                    startStreamingWithPermissionCheck()
                }
            }
        }
    }

    private fun lockOrientation() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
    }

    private fun unlockOrientation() {
        requestedOrientation = ApplicationConstants.supportedOrientation
    }

    override fun onStart() {
        super.onStart()
        permissionsManager.requestPermissions()
    }

    @RequiresPermission(allOf = [Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO])
    private fun onPermissionsGranted() {
        setAVSource()
        setStreamerView()

        if (currentConfig.autostart && !isAutoStartTriggered) {
            isAutoStartTriggered = true
            triggerAutoStart()
        }
    }

    private fun triggerAutoStart() {
        lifecycleScope.launch {
            Log.i(TAG, "Triggering auto-start stream in ${currentConfig.autostartDelayMs} ms to target: ${currentConfig.url}")
            delay(currentConfig.autostartDelayMs)
            startStreamingWithPermissionCheck()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO])
    private fun setAVSource() {
        viewModel.setAudioSource()
        val targetCam = if (currentConfig.cameraId.isNotEmpty()) currentConfig.cameraId else defaultCameraId
        viewModel.setCameraId(targetCam)
    }

    private fun setStreamerView() {
        lifecycleScope.launch {
            binding.preview.setVideoSourceProvider(viewModel.streamer)
        }
    }

    @SuppressLint("MissingPermission")
    private fun configureStreamer() {
        viewModel.applyConfig(currentConfig)
    }

    private fun toast(message: String) {
        runOnUiThread { applicationContext.toast(message) }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}