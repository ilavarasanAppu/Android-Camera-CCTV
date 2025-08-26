package com.example.smartcamerarecorder.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.example.smartcamerarecorder.analyzer.AnalysisResultListener
import com.example.smartcamerarecorder.analyzer.FaceAndMotionAnalyzer
import com.google.mlkit.vision.face.Face
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RecordingService : LifecycleService(), AnalysisResultListener {

    private var isRecording = false
    private lateinit var cameraExecutor: ExecutorService
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var currentRecording: Recording? = null

    override fun onCreate() {
        super.onCreate()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        if (isRecording) return
        isRecording = true
        startForeground(NOTIFICATION_ID, createNotification())

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val recorder = Recorder.Builder()
                .setExecutor(cameraExecutor)
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, FaceAndMotionAnalyzer(this, this))
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    videoCapture,
                    imageAnalysis
                )
                startVideoRecording()
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onFacesDetected(faces: List<Face>) {
        Log.d(TAG, "Faces detected: ${faces.size}")
    }

    override fun onFaceRecognized(face: Face, embedding: FloatArray) {
        Log.d(TAG, "Face recognized with embedding: ${embedding.joinToString()}")
    }

    override fun onMotionDetected(motionDetected: Boolean) {
        Log.d(TAG, "Motion detected: $motionDetected")
    }

    @SuppressLint("MissingPermission")
    private fun startVideoRecording() {
        val curVideoCapture = videoCapture ?: return

        val name = "video-recording-${
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis())
        }.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }
        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        currentRecording = curVideoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .withAudioEnabled()
            .withDurationLimit(VIDEO_DURATION_MS)
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        Log.d(TAG, "Recording started")
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            Log.d(TAG, "Recording finalized successfully")
                            if (isRecording) { // If still in recording state, start a new one (loop)
                                startVideoRecording()
                            }
                        } else {
                            currentRecording?.close()
                            currentRecording = null
                            Log.e(TAG, "Recording finalized with error: ${recordEvent.error}")
                        }
                    }
                }
            }
    }


    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        currentRecording?.stop()
        currentRecording = null
        cameraExecutor.shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(): android.app.Notification {
        val channelId = "recording_channel"
        val channelName = "Recording Channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Recording")
            .setContentText("Recording video in the background")
            .setSmallIcon(android.R.drawable.ic_menu_camera) // Replace with a real icon
            .build()
    }

    companion object {
        private const val TAG = "RecordingService"
        const val ACTION_START = "com.example.smartcamerarecorder.service.START"
        const val ACTION_STOP = "com.example.smartcamerarecorder.service.STOP"
        private const val NOTIFICATION_ID = 1
        private const val VIDEO_DURATION_MS = 300_000L // 5 minutes
    }
}
