package com.fecostudio.faceguard.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Pair
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
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

class FaceRecognizer(private val context: Context) {
    private val model = FileUtil.loadMappedFile(
        context,
        "model/model.tflite"
    )
    private val options = Interpreter.Options().apply {
        this.numThreads = 4
    }
    private val interpreter = Interpreter(model, options)
    private val registeredFaces =
        context.getSharedPreferences("registeredFaces", Context.MODE_PRIVATE)
    val faceBitmapMap = BitmapUtil.loadAllBitmap("faces", context)

    init {
        Log.d("FaceDrawer", "faceBitmapMap: ${faceBitmapMap.size} face bitmap loaded ")
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

    fun getNearestFace(bitmap: Bitmap, threshold: Double): Long {
        //和注册的人脸比对
        val start = System.currentTimeMillis()
        val faceMap = registeredFaces.all
        if (faceMap.isNotEmpty()) {
            val tensorImage: TensorImage = loadImage(bitmap)
            val probabilityBuffer =
                TensorBuffer.createFixedSize(intArrayOf(1, 512), DataType.FLOAT32)
            interpreter.run(tensorImage.buffer, probabilityBuffer.buffer)
            val probabilityProcessor = TensorProcessor.Builder().build()
            val embeddings = probabilityProcessor.process(probabilityBuffer).floatArray
            val nearest = findNearest(embeddings, faceMap)
            Log.d(
                "FaceRecognizer",
                "current registered size: ${faceMap.size}"
            )
            Log.d(
                "FaceRecognizer",
                "tflite model latency: ${System.currentTimeMillis() - start} ms"
            )
            return if (nearest != null && nearest.second < threshold) {
                Log.d("FaceRecognizer", "getNearestFace distance: ${nearest.second}")
                nearest.first
            } else {
                -1
            }
        }
        return -1
    }

    fun registerFace(bitmap: Bitmap): Long {
        val start = System.currentTimeMillis()
        val tensorImage: TensorImage = loadImage(bitmap)
        val probabilityBuffer =
            TensorBuffer.createFixedSize(intArrayOf(1, 512), DataType.FLOAT32)
        interpreter.run(tensorImage.buffer, probabilityBuffer.buffer)
        val probabilityProcessor = TensorProcessor.Builder().build()
        val embeddings = probabilityProcessor.process(probabilityBuffer).floatArray
        val embeddingsString = convertToBase64Bytes(embeddings)
//        将人脸数据保存到键值对数据库，将人脸bitmap保存到本地存储
        val faceID = System.currentTimeMillis()
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
        faceBitmapMap[faceID.toString()] = scaledBitmap
        BitmapUtil.saveBitmap("${faceID}.png", "faces", scaledBitmap, context)
        with(registeredFaces.edit()) {
            putString(faceID.toString(), embeddingsString)
            apply()
        }
        Log.v("FaceRecognizer", "tflite model latency: ${System.currentTimeMillis() - start} ms")
        Log.v("FaceRecognizer", "face id: $faceID registered")
        return faceID
    }

    fun removeFace(faceID: Long) {
        with(registeredFaces.edit()) {
            remove(faceID.toString())
            apply()
        }
        BitmapUtil.removeBitmap("${faceID}.png", "faces", context)
    }

    //返回最接近的数据
    private fun findNearest(embeddings: FloatArray, faceMap: Map<String, *>): Pair<Long, Float>? {
        var ret: Pair<Long, Float>? = null
        for ((id, knownEmbString) in faceMap) {
            val knownEmb = convertFromBase64Bytes(knownEmbString.toString())
            var distance = 0f
            for (i in embeddings.indices) {
                val diff = embeddings[i] - knownEmb[i]
                distance += diff * diff
            }
            distance = sqrt(distance.toDouble()).toFloat()
            if (ret == null || distance < ret.second) {
                ret = Pair(id.toLong(), distance)
            }
            Log.d("FaceRecognizer", "findNearest: distance $distance ")
        }
        return ret
    }
}