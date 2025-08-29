package com.tges.smartcamera.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(val context: Context) {

    private object PreferencesKeys {
        val RESOLUTION = stringPreferencesKey("resolution")
        val FPS = intPreferencesKey("fps")
        val RECORDING_MODE = stringPreferencesKey("recording_mode")
        val FACE_RECOGNITION = booleanPreferencesKey("face_recognition")
        val OVERLAY = booleanPreferencesKey("overlay")
    }

    val settingsFlow: Flow<Settings> = context.dataStore.data
        .map { preferences ->
            val resolution = preferences[PreferencesKeys.RESOLUTION] ?: "720p"
            val fps = preferences[PreferencesKeys.FPS] ?: 30
            val recordingMode = preferences[PreferencesKeys.RECORDING_MODE] ?: "Loop"
            val faceRecognition = preferences[PreferencesKeys.FACE_RECOGNITION] ?: false
            val overlay = preferences[PreferencesKeys.OVERLAY] ?: false
            Settings(resolution, fps, recordingMode, faceRecognition, overlay)
        }

    suspend fun saveSettings(settings: Settings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RESOLUTION] = settings.resolution
            preferences[PreferencesKeys.FPS] = settings.fps
            preferences[PreferencesKeys.RECORDING_MODE] = settings.recordingMode
            preferences[PreferencesKeys.FACE_RECOGNITION] = settings.faceRecognition
            preferences[PreferencesKeys.OVERLAY] = settings.overlay
        }
    }
}

data class Settings(
    val resolution: String,
    val fps: Int,
    val recordingMode: String,
    val faceRecognition: Boolean,
    val overlay: Boolean
)
