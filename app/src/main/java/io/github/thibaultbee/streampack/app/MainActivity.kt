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

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var controlBinding: ControlsPanelBinding
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(this.application)
    }

    private var currentConfig: StreamConfig = StreamConfig()
    private var isAutoStartTriggered = false

    private val streamerRequiredPermissions =
        buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // NECESSARY FOR SHOWING STREAMING FOREGROUND SERVICE NOTIFICATION
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

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

    private val requestLocalNetworkPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                viewModel.startStream()
            } else {
                toast("Local network permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        controlBinding = ControlsPanelBinding.bind(binding.root.findViewById<View>(R.id.controlsPanel))
        setContentView(binding.root)
        Timber.i("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")

        binding.root.findViewById<ImageButton>(R.id.btnSettings)?.setOnClickListener { showConfigDialog() }

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
        Timber.i("Loaded config URL: ${currentConfig.url}")
        viewModel.applyConfig(currentConfig)
        // Wire controls panel to viewModel
        controlBinding.btnZoomIn.setOnClickListener { viewModel.setZoom(1.2f) }
        controlBinding.btnZoomOut.setOnClickListener { viewModel.setZoom(0.8f) }
        controlBinding.seekExposure.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val exp = ((progress / 25.0) - 2.0).toFloat()
                    viewModel.setExposure(exp)
                    controlBinding.seekExposureValue.text = exp.toString()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        controlBinding.spinnerWB.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        viewModel.setWhiteBalance(position)
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                })
    }

    private fun bindProperties() {
        configureStreamer()

        viewModel.closedThrowableLiveData.observe(this) { error ->
            Timber.e(error, "Disconnect")
            toast("Disconnect: ${error.message}")
            handleAutoReconnect()
        }

        viewModel.pendingConnectionFailedLiveData.observe(this) { error ->
            Timber.e(error, "Connection error")
            toast("Connection error: ${error.message}")
            handleAutoReconnect()
        }

        viewModel.throwableLiveData.observe(this) { error ->
            Timber.e(error, "Error")
            toast("Error: ${error.message}")
        }

        viewModel.isStreamingLiveData.observe(this) { isStreaming ->
            if (isStreaming) {
                lockOrientation()
                StreamingForegroundService.start(applicationContext)
            } else {
                unlockOrientation()
                StreamingForegroundService.stop(applicationContext)
            }
        }

        viewModel.isTryingConnectionLiveData.observe(this) { isWaiting ->
            // Streaming starts automatically; no manual toggle
        }
    }

    private fun startStreamingWithPermissionCheck() {
        lifecycleScope.launch {
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) && viewModel.needsLocalNetworkPermission()) {
                Timber.i("Local network permission is required for Android 37+")
                requestLocalNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            } else {
                viewModel.startStream()
            }
        }
    }

    private fun handleAutoReconnect() {
        if (currentConfig.autoReconnect) {
            lifecycleScope.launch {
                Timber.i("Scheduling auto-reconnect in ${currentConfig.reconnectIntervalSec} seconds...")
                delay(currentConfig.reconnectIntervalSec * 1000L)
                if (viewModel.isStreamingLiveData.value != true) {
                    Timber.i("Attempting auto-reconnect now...")
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

        // Streaming starts automatically; no manual toggle
        lifecycleScope.launch {
            delay(500)
            startStreamingWithPermissionCheck()
        }

        if (currentConfig.autostart && !isAutoStartTriggered) {
            isAutoStartTriggered = true
            triggerAutoStart()
        }
    }

    private fun triggerAutoStart() {
        lifecycleScope.launch {
            Timber.i("Triggering auto-start stream in ${currentConfig.autostartDelayMs} ms to target: ${currentConfig.url}")
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
    private fun showConfigDialog() {
        Timber.i("Config clicked — ver 1.0")
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}