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
    private val inputImageSize = 112 // MobileFaceNet standard input size
    private val outputEmbeddingSize = 192 // MobileFaceNet standard output size

    init {
        try {
            val modelFile = FileUtil.loadMappedFile(context, "mobile_face_net.tflite")
            val options = Interpreter.Options()
            interpreter = Interpreter(modelFile, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getFaceEmbedding(bitmap: Bitmap): FloatArray {
        if (interpreter == null) {
            throw IllegalStateException("TFLite Interpreter is not initialized. Check if model file exists.")
        }

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputImageSize, inputImageSize, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(127.5f, 128.0f)) // Normalize to [-1, 1]
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        val outputBuffer = ByteBuffer.allocateDirect(outputEmbeddingSize * 4) // 4 bytes per float
        outputBuffer.order(java.nio.ByteOrder.nativeOrder())

        // The model expects [1, 112, 112, 3] input and outputs [1, 192]
        // TensorImage handles the input shape automatically if it matches
        // But we need to be careful about the output container.
        // Interpreter.run accepts Object input and Object output.

        val outputArray = Array(1) { FloatArray(outputEmbeddingSize) }
        interpreter?.run(tensorImage.buffer, outputArray)

        return outputArray[0]
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

