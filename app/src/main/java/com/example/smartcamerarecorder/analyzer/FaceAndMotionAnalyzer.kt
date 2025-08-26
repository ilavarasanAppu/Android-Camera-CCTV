package com.example.smartcamerarecorder.analyzer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.PredefinedCategory
import java.io.ByteArrayOutputStream

class FaceAndMotionAnalyzer(
    context: Context,
    private val listener: AnalysisResultListener
) : ImageAnalysis.Analyzer {

    private val faceRecognizer = FaceRecognizer(context)

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
                    if (faces.isNotEmpty()) {
                        val bitmap = imageProxy.toBitmap()
                        if (bitmap != null) {
                            for (face in faces) {
                                try {
                                    val boundingBox = face.boundingBox
                                    // Ensure the bounding box is within the bitmap dimensions
                                    val left = boundingBox.left.coerceAtLeast(0)
                                    val top = boundingBox.top.coerceAtLeast(0)
                                    val right = boundingBox.right.coerceAtMost(bitmap.width)
                                    val bottom = boundingBox.bottom.coerceAtMost(bitmap.height)
                                    val width = right - left
                                    val height = bottom - top

                                    if (width > 0 && height > 0) {
                                        val croppedBitmap = Bitmap.createBitmap(
                                            bitmap,
                                            left,
                                            top,
                                            width,
                                            height
                                        )
                                        val embedding = faceRecognizer.recognize(croppedBitmap)
                                        listener.onFaceRecognized(face, embedding)
                                    }
                                } catch (e: Exception) {
                                    // Log or handle exception during cropping or recognition
                                }
                            }
                        }
                    }
                    // Notify listener about the detected faces, even if recognition fails for some
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

    private fun ImageProxy.toBitmap(): Bitmap? {
        val image = this.image ?: return null
        if (image.format == ImageFormat.YUV_420_888) {
            val yBuffer = image.planes[0].buffer // Y
            val uBuffer = image.planes[1].buffer // U
            val vBuffer = image.planes[2].buffer // V

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
            val imageBytes = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }
        return null
    }
}

interface AnalysisResultListener {
    fun onFacesDetected(faces: List<Face>)
    fun onFaceRecognized(face: Face, embedding: FloatArray)
    fun onMotionDetected(motionDetected: Boolean)
}
