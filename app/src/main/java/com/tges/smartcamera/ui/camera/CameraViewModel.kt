package com.tges.smartcamera.ui.camera

import android.app.Application
import android.content.Intent
import androidx.camera.core.CameraSelector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tges.smartcamera.sensor.SensorDataManager
import com.tges.smartcamera.service.RecordingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class CameraViewModel(
    private val application: Application,
    private val sensorDataManager: SensorDataManager
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(CameraState())
    val state = _state.asStateFlow()

    init {
        sensorDataManager.accelerometerData
            .onEach { values ->
                val z = values[2]
                val orientation = when {
                    z > 7 -> DeviceOrientation.FACE_UP
                    z < -7 -> DeviceOrientation.FACE_DOWN
                    else -> _state.value.orientation
                }
                if (orientation != _state.value.orientation) {
                    _state.value = _state.value.copy(orientation = orientation)
                    updateCameraLens()
                }
            }
            .launchIn(viewModelScope)
    }

    fun onPermissionResult(
        accepted: Boolean
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                permissionAccepted = accepted,
                shouldShowPermissionRationale = !accepted
            )
        }
    }

    private fun updateCameraLens() {
        val newLens = when (_state.value.orientation) {
            DeviceOrientation.FACE_UP -> CameraSelector.LENS_FACING_FRONT
            DeviceOrientation.FACE_DOWN -> CameraSelector.LENS_FACING_BACK
            else -> _state.value.lensFacing
        }
        if (newLens != _state.value.lensFacing) {
            _state.value = _state.value.copy(lensFacing = newLens)
        }
    }

    fun toggleRecording() {
        viewModelScope.launch {
            val isRecording = state.value.isRecording
            val intent = Intent(application, RecordingService::class.java).apply {
                action = if (isRecording) {
                    RecordingService.ACTION_STOP
                } else {
                    RecordingService.ACTION_START
                }
            }
            application.startService(intent)
            _state.value = state.value.copy(isRecording = !isRecording)
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CameraViewModel(
                        application,
                        SensorDataManager(application)
                    ) as T
                }
            }
        }
    }
}

data class CameraState(
    val permissionAccepted: Boolean = false,
    val shouldShowPermissionRationale: Boolean = false,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val orientation: DeviceOrientation = DeviceOrientation.IDLE,
    val isRecording: Boolean = false
)

enum class DeviceOrientation {
    FACE_UP, FACE_DOWN, IDLE
}
