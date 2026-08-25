package io.github.thibaultbee.streampack.app.data.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StorageRepository {
    private val _urlStringFlow = MutableStateFlow("rtsp://10.203.33.72:8890/telefono")
    val urlStringFlow: StateFlow<String> = _urlStringFlow.asStateFlow()

    fun setUrl(url: String) {
        _urlStringFlow.value = url
    }
}
