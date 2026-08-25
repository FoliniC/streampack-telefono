package io.github.thibaultbee.streampack.app.data.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StorageRepository {
    private val _urlStringFlow = MutableStateFlow("rtmp://192.168.0.3:1935/honor")
    val urlStringFlow: StateFlow<String> = _urlStringFlow.asStateFlow()
}
