package io.github.thibaultbee.streampack.app

import android.Manifest
import android.media.AudioFormat
import android.media.MediaFormat
import android.os.Build
import android.util.Size
import androidx.annotation.RequiresPermission
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import io.github.thibaultbee.streampack.app.data.config.StreamConfig
import io.github.thibaultbee.streampack.app.data.rotation.RotationRepository
import io.github.thibaultbee.streampack.app.data.storage.StorageRepository
import io.github.thibaultbee.streampack.app.utils.NetworkUtils
import io.github.thibaultbee.streampack.core.elements.sources.audio.audiorecord.MicrophoneSourceFactory
import io.github.thibaultbee.streampack.core.interfaces.releaseBlocking
import io.github.thibaultbee.streampack.core.interfaces.setCameraId
import io.github.thibaultbee.streampack.core.interfaces.startStream
import io.github.thibaultbee.streampack.core.streamers.single.AudioConfig
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.VideoConfig
import io.github.thibaultbee.streampack.core.utils.extensions.isClosedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(
    private val storageRepository: StorageRepository,
    private val rotationRepository: RotationRepository,
    val streamer: SingleStreamer
) : ViewModel() {
    private val defaultDispatcher = Dispatchers.Default

    /**
     * A LiveData to observe the stream state.
     */
    val isStreamingLiveData: LiveData<Boolean>
        get() = streamer.isStreamingFlow.asLiveData()

    /**
     * A LiveData to observe the pending connection state.
     */
    private val _isTryingConnectionLiveData = MutableLiveData<Boolean>()
    val isTryingConnectionLiveData: LiveData<Boolean> = _isTryingConnectionLiveData

    /**
     * A LiveData to observe async disconnection errors.
     */
    val closedThrowableLiveData: LiveData<Throwable> =
        streamer.throwableFlow.filterNotNull().filter { it.isClosedException }.asLiveData()

    /**
     * A LiveData to observe streamer errors.
     */
    val throwableLiveData: LiveData<Throwable> =
        streamer.throwableFlow.filterNotNull().filter { !it.isClosedException }.asLiveData()

    /** The connection failed to established */
    private val _pendingConnectionFailedFlow = MutableStateFlow<Throwable?>(null)
    val pendingConnectionFailedLiveData: LiveData<Throwable> =
        _pendingConnectionFailedFlow.filterNotNull().asLiveData()

    init {
        /**
         * Listens to device rotation.
         */
        viewModelScope.launch(defaultDispatcher) {
            rotationRepository.rotationFlow.collect {
                streamer.setTargetRotation(it)
            }
        }
    }

    fun applyConfig(config: StreamConfig) {
        storageRepository.setUrl(config.url)
        setVideoConfig(
            width = config.videoWidth,
            height = config.videoHeight,
            fps = config.videoFps,
            startBitrate = config.videoBitrate
        )
        if (config.audioEnabled) {
            setAudioConfig(startBitrate = config.audioBitrate)
        }
    }

    /**
     * Starts the stream.
     */
    fun startStream() {
        viewModelScope.launch {
            _isTryingConnectionLiveData.postValue(true)
            try {
                streamer.startStream(storageRepository.urlStringFlow.first())
            } catch (t: Throwable) {
                _pendingConnectionFailedFlow.emit(t)
            } finally {
                _isTryingConnectionLiveData.postValue(false)
            }
        }
    }

    /**
     * Stops the stream.
     */
    fun stopStream() {
        viewModelScope.launch {
            streamer.stopStream()
        }
    }

    /**
     * Sets the audio configuration.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun setAudioConfig(startBitrate: Int = 128000) {
        val audioConfig = AudioConfig(
            mimeType = MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate = 44100,
            startBitrate = startBitrate,
            channelConfig = AudioFormat.CHANNEL_IN_STEREO
        )

        viewModelScope.launch {
            streamer.setAudioConfig(audioConfig)
        }
    }

    /**
     * Sets the video configuration.
     */
    fun setVideoConfig(width: Int = 1280, height: Int = 720, fps: Int = 25, startBitrate: Int = 2000000) {
        val videoConfig = VideoConfig(
            mimeType = MediaFormat.MIMETYPE_VIDEO_AVC,
            resolution = Size(width, height),
            fps = fps,
            startBitrate = startBitrate
        )

        viewModelScope.launch {
            streamer.setVideoConfig(videoConfig)
        }
    }

    /**
     * Sets the microphone as the audio source.
     */
    fun setAudioSource() {
        viewModelScope.launch {
            streamer.setAudioSource(MicrophoneSourceFactory())
        }
    }

    /**
     * Sets the camera with the given id as the video source.
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    fun setCameraId(cameraId: String) {
        viewModelScope.launch {
            streamer.setCameraId(cameraId)
        }
    }

    suspend fun needsLocalNetworkPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return false
        val descriptor = storageRepository.urlStringFlow.first()
        val host = descriptor.toUri().host
        return host?.let { NetworkUtils.isLocalHost(it) } ?: false
    }

    override fun onCleared() {
        streamer.releaseBlocking()
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}