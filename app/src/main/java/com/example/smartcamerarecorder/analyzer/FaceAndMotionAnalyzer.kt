package com.example.smartcamerarecorder.analyzer

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.PredefinedCategory

class FaceAndMotionAnalyzer(
    private val listener: AnalysisResultListener
) : ImageAnalysis.Analyzer {

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .build()
    )

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    listener.onFacesDetected(faces)
                }
                .addOnFailureListener { e ->
                    // Handle error
                }

            objectDetector.process(image)
                .addOnSuccessListener { detectedObjects ->
                    val motionDetected = detectedObjects.any {
                        it.labels.any { label ->
                            label.text == PredefinedCategory.PERSON
                                    || label.text == PredefinedCategory.VEHICLE
                        }
                    }
                    listener.onMotionDetected(motionDetected)
                }
                .addOnFailureListener { e ->
                    // Handle error
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}

interface AnalysisResultListener {
    fun onFacesDetected(faces: List<Face>)
    fun onMotionDetected(motionDetected: Boolean)
}
