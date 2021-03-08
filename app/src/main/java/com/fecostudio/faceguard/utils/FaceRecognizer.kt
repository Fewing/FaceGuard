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
import java.util.*
import kotlin.math.sqrt


class FaceRecognizer(context: Context) {
    private val model = FileUtil.loadMappedFile(
        context,
        "model/mobile_face_net.tflite"
    )
    private val options = Interpreter.Options().addDelegate(GpuDelegate())
    private val interpreter = Interpreter(model, options)
    private val registered: HashMap<Int, FloatArray> = HashMap<Int, FloatArray>()
    private var idCount = 0

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

    fun getNearestFace(bitmap: Bitmap, faceID: Int): Int {
        val tensorImage: TensorImage = loadImage(bitmap)
        val probabilityBuffer =
            TensorBuffer.createFixedSize(intArrayOf(1, 192), DataType.FLOAT32)
        interpreter.run(tensorImage.buffer, probabilityBuffer.buffer)
        val probabilityProcessor = TensorProcessor.Builder().build()
        val embeddings = probabilityProcessor.process(probabilityBuffer).floatArray;
        var distance = Float.MAX_VALUE
        //和注册的人脸比对
        if (registered.size > 0) {
            val nearest = findNearest(embeddings)
            if (nearest != null && nearest.second < 1.0) {
                val id: Int = nearest.first
                distance = nearest.second
                Log.d("tflite", "getNearestFace: $id distance: $distance")
                return id
            } else {
                registered[faceID] = embeddings
            }
        } else {
            registered[faceID] = embeddings
        }
        return 0
    }

    //返回最接近的数据
    private fun findNearest(embeddings: FloatArray): Pair<Int, Float>? {
        var ret: Pair<Int, Float>? = null
        for ((id, knownEmb) in registered) {
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