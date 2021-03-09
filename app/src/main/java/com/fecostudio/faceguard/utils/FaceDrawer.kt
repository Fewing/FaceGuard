package com.fecostudio.faceguard.utils

import android.content.Context
import android.content.res.AssetManager
import android.graphics.*
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import androidx.camera.core.CameraSelector
import com.google.mlkit.vision.face.Face
import java.io.InputStream


class FaceDrawer(context: Context) {
    private val assetManager: AssetManager = context.assets

    enum class DrawStyles(val style: Int, var bitmap: Bitmap?) {
        BlUR(1, null),
        BlACK(2, null),
        DOGE(3, null),
        SMILE_BOY(4, null),
    }

    private val faceHashMap: HashMap<Int?, Int> = HashMap() //真实人脸id对应的样式
    private val idHashMap: HashMap<Int?, Int> = HashMap() //trackingID对应的真实人脸id

    private val rs = RenderScript.create(context)
    private val blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

    private val ratio = 10
    private val radius = 5f

    private val faceRecognizer = FaceRecognizer(context)

    private lateinit var currentBitmap: Bitmap
    private var currentRotate = 90
    private lateinit var currentLensFacing: CameraSelector

    init {
        var inputStream: InputStream = assetManager.open("picture/doge.png")
        DrawStyles.DOGE.bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream = assetManager.open("picture/smileboy.png")
        DrawStyles.SMILE_BOY.bitmap = BitmapFactory.decodeStream(inputStream)
        faceHashMap[-1] = DrawStyles.BlUR.style
    }

    fun drawFace(
        faces: List<Face>,
        bitmap: Bitmap,
        canvas: Canvas,
        lensFacing: CameraSelector,
        degrees: Int
    ) {
        val matrix = getRotateMatrix(degrees, bitmap)//用于绘制原图的matrix
        val scaleMatrix = Matrix()//用于绘制马赛克的matrix
        scaleMatrix.postRotate(degrees.toFloat())
        val paint = Paint()
        if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA) {
            matrix.postScale(-1f, 1f)
            matrix.postTranslate(bitmap.height.toFloat(), 0f)
            scaleMatrix.postScale(-1f, 1f)
        }
        var scaledBitmap =
            Bitmap.createScaledBitmap(bitmap, bitmap.width / ratio, bitmap.height / ratio, false)
        scaledBitmap = Bitmap.createBitmap(
            scaledBitmap,
            0,
            0,
            scaledBitmap.width,
            scaledBitmap.height,
            scaleMatrix,
            false
        )
        blurBitmapByRender(scaledBitmap)
        canvas.drawBitmap(bitmap, matrix, paint)
        currentBitmap = bitmap
        currentLensFacing = lensFacing
        currentRotate = degrees
        for (face in faces) {
            val faceRect = if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA) {
                Rect(
                    1080 - face.boundingBox.right,
                    face.boundingBox.top,
                    1080 - face.boundingBox.left,
                    face.boundingBox.bottom
                )
            } else {
                Rect(face.boundingBox)
            }
            if (idHashMap.containsKey(face.trackingId)) {
                //旧的tracking id
                when (faceHashMap[idHashMap[face.trackingId]]) {
                    DrawStyles.BlUR.style -> {
                        val scaleFaceRect = Rect(
                            faceRect.left / ratio,
                            faceRect.top / ratio,
                            faceRect.right / ratio,
                            faceRect.bottom / ratio
                        )
                        canvas.drawBitmap(scaledBitmap, scaleFaceRect, faceRect, paint)
                    }
                    DrawStyles.BlACK.style -> {
                        paint.setARGB(255, 0, 0, 0)
                        canvas.drawRect(faceRect, paint)
                    }
                    DrawStyles.DOGE.style -> {
                        canvas.drawBitmap(DrawStyles.DOGE.bitmap!!, null, faceRect, paint)
                    }
                    DrawStyles.SMILE_BOY.style -> {
                        canvas.drawBitmap(
                            DrawStyles.SMILE_BOY.bitmap!!,
                            null,
                            faceRect,
                            paint
                        )
                    }
                }
            } else {
                //新的tracking id
                val rotateFaceRect = getRotateRect(degrees, faceRect, lensFacing)
                matrix.setRotate(degrees.toFloat())
                if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA) {
                    matrix.postScale(-1f, 1f)
                }
                if (Rect(
                        0,
                        0,
                        bitmap.width.toInt(),
                        bitmap.height.toInt()
                    ).contains(rotateFaceRect)
                ) {
                    val faceBitmap = Bitmap.createBitmap(
                        bitmap,
                        rotateFaceRect.left,
                        rotateFaceRect.top,
                        rotateFaceRect.width(),
                        rotateFaceRect.height(),
                        matrix,
                        false
                    )
                    val realFaceID = faceRecognizer.getNearestFace(faceBitmap,face.trackingId!!)
                    Log.d("tflite", "realFaceID: $realFaceID")
                    idHashMap[face.trackingId] = realFaceID
                }
            }
        }
        canvas.save()
    }
    //注册人脸
    fun setFaceStyle(face: Face, style: Int) {
        val faceRect = if (currentLensFacing == CameraSelector.DEFAULT_FRONT_CAMERA) {
            Rect(
                1080 - face.boundingBox.right,
                face.boundingBox.top,
                1080 - face.boundingBox.left,
                face.boundingBox.bottom
            )
        } else {
            Rect(face.boundingBox)
        }
        val rotateFaceRect = getRotateRect(currentRotate, faceRect, currentLensFacing)
        val matrix = Matrix()
        matrix.setRotate(currentRotate.toFloat())
        if (currentLensFacing == CameraSelector.DEFAULT_FRONT_CAMERA) {
            matrix.postScale(-1f, 1f)
        }
        if (Rect(
                0,
                0,
                currentBitmap.width,
                currentBitmap.height
            ).contains(rotateFaceRect)
        ) {
            val faceBitmap = Bitmap.createBitmap(
                currentBitmap,
                rotateFaceRect.left,
                rotateFaceRect.top,
                rotateFaceRect.width(),
                rotateFaceRect.height(),
                matrix,
                false
            )
            val realFaceID = faceRecognizer.getNearestFace(faceBitmap,face.trackingId!!)
            Log.d("tflite", "realFaceID: $realFaceID")
            if (realFaceID != -1){
                idHashMap[face.trackingId] = realFaceID //有匹配的人脸
                faceHashMap[realFaceID] = style //设置新的效果
            }else {
                faceRecognizer.registerFace(faceBitmap,face.trackingId!!)//无匹配的人脸
                idHashMap[face.trackingId] = face.trackingId!!
                faceHashMap[face.trackingId] = style
            }
        }
    }

    private fun getRotateMatrix(degrees: Int, bitmap: Bitmap): Matrix {
        val matrix = Matrix()
        when (degrees) {
            90 -> {
                matrix.postRotate(90f)
                matrix.postTranslate(bitmap.height.toFloat(), 0f)
            }
            180 -> {
                matrix.postRotate(180f)
                matrix.postTranslate(0f, bitmap.height.toFloat())
            }
            270 -> {
                matrix.postRotate(270f)
                matrix.postTranslate(0f, bitmap.width.toFloat())
            }
        }
        return matrix
    }

    //从原bitmap中获取人脸框旋转
    private fun getRotateRect(degrees: Int, rect: Rect, lensFacing: CameraSelector): Rect {
        when (degrees) {
            90 -> {
                return if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA)
                    Rect(rect.top, rect.left, rect.bottom, rect.right)
                else
                    Rect(rect.top, 1080 - rect.right, rect.bottom, 1080 - rect.left)
            }
            180 -> {
                return rect //待实现
            }
            270 -> {
                return if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA)
                    Rect(
                        1920 - rect.bottom,
                        1080 - rect.right,
                        1920 - rect.top,
                        1080 - rect.left
                    )
                else
                    Rect(1920 - rect.bottom, rect.left, 1920 - rect.top, rect.right)
            }
        }
        return rect
    }

    private fun blurBitmapByRender(bitmap: Bitmap) {
        val allocation = Allocation.createFromBitmap(rs, bitmap)
        blurScript.setInput(allocation)
        blurScript.setRadius(radius)
        blurScript.forEach(allocation)
        //rs.finish()
    }
}