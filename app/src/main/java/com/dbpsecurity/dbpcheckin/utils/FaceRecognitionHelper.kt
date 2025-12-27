package com.dbpsecurity.dbpcheckin.utils

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer
import kotlin.math.sqrt

class FaceRecognitionHelper(context: Context) {

    private var interpreter: Interpreter? = null
    private var initError: Throwable? = null
    private val inputImageSize = 160 // FaceNet standard input size
    private val outputEmbeddingSize = 128 // FaceNet standard output size

    init {
        try {
            val modelFile = FileUtil.loadMappedFile(context, "facenet.tflite")
            val options = Interpreter.Options()
            interpreter = Interpreter(modelFile, options)
        } catch (e: Throwable) {
            initError = e
            e.printStackTrace()
        }
    }

    fun getFaceEmbedding(bitmap: Bitmap): FloatArray {
        if (interpreter == null) {
            throw IllegalStateException("TFLite Interpreter is not initialized. Error: ${initError?.message}", initError)
        }

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputImageSize, inputImageSize, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(127.5f, 127.5f)) // Normalize to [-1, 1]
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        val outputBuffer = ByteBuffer.allocateDirect(outputEmbeddingSize * 4) // 4 bytes per float
        outputBuffer.order(java.nio.ByteOrder.nativeOrder())

        // The model expects [1, 160, 160, 3] input and outputs [1, 128]
        val outputArray = Array(1) { FloatArray(outputEmbeddingSize) }
        interpreter?.run(tensorImage.buffer, outputArray)

        val normalized = l2Normalize(outputArray[0])
        android.util.Log.d("FaceRecognitionHelper", "Embedding: ${normalized.take(5).joinToString(", ")}...")
        return normalized
    }

    private fun l2Normalize(embedding: FloatArray): FloatArray {
        var sum = 0.0f
        for (value in embedding) {
            sum += value * value
        }
        val norm = sqrt(sum)
        if (norm == 0f) return embedding // Avoid division by zero

        return FloatArray(embedding.size) { i -> embedding[i] / norm }
    }

    fun calculateDistance(embedding1: FloatArray, embedding2: FloatArray): Float {
        var sum = 0.0f
        for (i in embedding1.indices) {
            val diff = embedding1[i] - embedding2[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    fun close() {
        interpreter?.close()
    }
}
