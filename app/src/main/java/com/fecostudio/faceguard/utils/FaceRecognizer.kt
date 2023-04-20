package com.fecostudio.faceguard.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Pair
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorProcessor
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.sqrt

class FaceRecognizer(context: Context) {
    private val model = FileUtil.loadMappedFile(
        context,
        "model/mobile_face_net.tflite"
    )
    private val options = Interpreter.Options().apply {
        this.addDelegate(GpuDelegate())
    }
    private val interpreter = Interpreter(model, options)
    private val registeredFaces =
        context.getSharedPreferences("registeredFaces", Context.MODE_PRIVATE)

    init {
        if (!registeredFaces.contains("faceNumber")) {
            with(registeredFaces.edit()) {
                putInt("faceNumber", 0)
                apply()
            }
        }
    }

    private fun loadImage(bitmap: Bitmap): TensorImage {
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(112, 112, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
            .add(NormalizeOp(0.toFloat(), 255.toFloat()))
            .build()
        var tensorImage = TensorImage()
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)
        return tensorImage
    }

    private fun convertToBase64Bytes(floatArray: FloatArray): String {
        val buff: ByteBuffer = ByteBuffer.allocate(4 * floatArray.size)
        for (amplitude in floatArray) {
            buff.putFloat(amplitude)
        }
        return Base64.getEncoder().encodeToString(buff.array())
    }

    private fun convertFromBase64Bytes(base64String: String): FloatArray {
        val bytes = Base64.getDecoder().decode(base64String)
        val buff = ByteBuffer.wrap(bytes)
        val floatArray = FloatArray(bytes.size / 4)
        for (i in floatArray.indices) {
            floatArray[i] = buff.float
        }
        return floatArray
    }

    fun getNearestFace(bitmap: Bitmap): Int {
        //和注册的人脸比对
        val start = System.currentTimeMillis()
        if (registeredFaces.getInt("faceNumber", 0) > 0) {
            val tensorImage: TensorImage = loadImage(bitmap)
            val probabilityBuffer =
                TensorBuffer.createFixedSize(intArrayOf(1, 192), DataType.FLOAT32)
            interpreter.run(tensorImage.buffer, probabilityBuffer.buffer)
            val probabilityProcessor = TensorProcessor.Builder().build()
            val embeddings = probabilityProcessor.process(probabilityBuffer).floatArray
            val nearest = findNearest(embeddings)
            Log.d(
                "FaceRecognizer",
                "current registered size: ${registeredFaces.getInt("faceNumber", 0)}"
            )
            Log.v(
                "FaceRecognizer",
                "tflite model latency: ${System.currentTimeMillis() - start} ms"
            )
            return if (nearest != null && nearest.second < 0.9) {
                Log.d("FaceRecognizer", "getNearestFace distance: ${nearest.second}")
                nearest.first
            } else {
                -1
            }
        }
        return -1
    }

    fun registerFace(bitmap: Bitmap): Int {
        val start = System.currentTimeMillis()
        val tensorImage: TensorImage = loadImage(bitmap)
        val probabilityBuffer =
            TensorBuffer.createFixedSize(intArrayOf(1, 192), DataType.FLOAT32)
        interpreter.run(tensorImage.buffer, probabilityBuffer.buffer)
        val probabilityProcessor = TensorProcessor.Builder().build()
        val embeddings = probabilityProcessor.process(probabilityBuffer).floatArray
        val embeddingsString = convertToBase64Bytes(embeddings)
        with(registeredFaces.edit()) {
            putInt("", 1)
        }
//        将人脸数据保存到键值对数据库
        val faceID = registeredFaces.getInt("faceNumber", 0)
        with(registeredFaces.edit()) {
            putInt("faceNumber", faceID + 1)
            putString(faceID.toString(), embeddingsString)
            apply()
        }
        Log.d("FaceRecognizer", "tflite model latency: ${System.currentTimeMillis() - start} ms")
        Log.d("FaceRecognizer", "face id: $faceID registered")
        return faceID
    }

    //返回最接近的数据
    private fun findNearest(embeddings: FloatArray): Pair<Int, Float>? {
        var ret: Pair<Int, Float>? = null
        for (id in 0 until registeredFaces.getInt("faceNumber", 0)) {
            val knownEmbString = registeredFaces.getString(id.toString(), "")
            val knownEmb = convertFromBase64Bytes(knownEmbString!!)
            var distance = 0f
            for (i in embeddings.indices) {
                val diff = embeddings[i] - knownEmb[i]
                distance += diff * diff
            }
            distance = sqrt(distance.toDouble()).toFloat()
            if (ret == null || distance < ret.second) {
                ret = Pair(id, distance)
            }
        }
        return ret
    }
}