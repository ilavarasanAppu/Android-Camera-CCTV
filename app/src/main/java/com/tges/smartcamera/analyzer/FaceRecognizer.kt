package com.tges.smartcamera.analyzer

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.random.Random

class FaceRecognizer(context: Context) {

    private val interpreter: Interpreter

    init {
        val model = loadModelFile(context)
        val options = Interpreter.Options()
        // Configure the interpreter options if needed (e.g., use GPU delegate)
        interpreter = Interpreter(model, options)
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun recognize(bitmap: Bitmap): FloatArray {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_IMAGE_WIDTH, INPUT_IMAGE_HEIGHT, true)
        val byteBuffer = convertBitmapToByteBuffer(scaledBitmap)

        val output = Array(1) { FloatArray(OUTPUT_SIZE) }
        interpreter.run(byteBuffer, output)

        return output[0]
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * BATCH_SIZE * INPUT_IMAGE_WIDTH * INPUT_IMAGE_HEIGHT * PIXEL_SIZE)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(INPUT_IMAGE_WIDTH * INPUT_IMAGE_HEIGHT)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        for (i in 0 until INPUT_IMAGE_WIDTH) {
            for (j in 0 until INPUT_IMAGE_HEIGHT) {
                val `val` = intValues[pixel++]
                byteBuffer.putFloat((((`val` shr 16) and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                byteBuffer.putFloat((((`val` shr 8) and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                byteBuffer.putFloat(((`val` and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
            }
        }
        return byteBuffer
    }

    companion object {
        private const val MODEL_FILE = "mobilefacenet.tflite"
        private const val BATCH_SIZE = 1
        private const val INPUT_IMAGE_WIDTH = 112
        private const val INPUT_IMAGE_HEIGHT = 112
        private const val PIXEL_SIZE = 3
        private const val OUTPUT_SIZE = 192
        private const val IMAGE_MEAN = 127.5f
        private const val IMAGE_STD = 128.0f
    }
}
